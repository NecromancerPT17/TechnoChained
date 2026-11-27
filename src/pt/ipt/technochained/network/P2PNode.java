package pt.ipt.technochained.network;

import pt.ipt.technochained.blockchain.Block;
import pt.ipt.technochained.blockchain.Blockchain;
import pt.ipt.technochained.blockchain.CredentialRecord;
import pt.ipt.technochained.miner.MiningResult;
import pt.ipt.technochained.miner.ParallelMiner;
import pt.ipt.technochained.model.Transaction;

import java.io.Serial;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class P2PNode extends UnicastRemoteObject implements RemoteNode {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String host;
    private final int port;
    private final String address;

    private final Blockchain blockchain;
    private final ParallelMiner miner;

    private final Set<String> peers = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Transaction> mempool = new ConcurrentHashMap<>();
    private final Set<String> seenBlockHashes = ConcurrentHashMap.newKeySet();
    private final CopyOnWriteArrayList<NodeListener> listeners = new CopyOnWriteArrayList<>();

    private final ReentrantLock stateLock = new ReentrantLock();
    private final ExecutorService networkExecutor;

    public P2PNode(String host, int port) throws RemoteException {
        super(port);
        this.host = host;
        this.port = port;
        this.address = RmiUtils.getRemoteName(host, port, RmiUtils.DEFAULT_SERVICE_NAME);
        this.blockchain = new Blockchain();
        this.miner = new ParallelMiner();
        this.seenBlockHashes.add(blockchain.getLatestBlock().getHash());

        this.networkExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "P2P-Net-" + port);
            t.setDaemon(true);
            return t;
        });
    }

    public void start() throws Exception {
        RmiUtils.startRemoteObject(this, host, port, RmiUtils.DEFAULT_SERVICE_NAME);
        log("Nó P2P (Java RMI) iniciado em " + address);
    }

    public void shutdown() {
        log("A encerrar nó " + address + "...");
        miner.shutdown();
        networkExecutor.shutdownNow();
        RmiUtils.stopRemoteObject(this, address);
    }

    public void addListener(NodeListener listener) {
        if (listener != null) listeners.add(listener);
    }

    public void removeListener(NodeListener listener) {
        listeners.remove(listener);
    }

    public boolean connectToPeer(String remoteAddress) {
        String normalized = normalizeAddress(remoteAddress);
        if (normalized == null || normalized.equals(this.address)) {
            return false;
        }
        try {
            RemoteNode remote = (RemoteNode) RmiUtils.getRemote(normalized);
            String confirmedAddress = remote.getAddress();

            if (confirmedAddress.equals(this.address)) {
                return false;
            }

            boolean isNew = peers.add(confirmedAddress);
            remote.registerPeer(this.address);

            if (isNew) {
                log("Ligado ao par RMI: " + confirmedAddress);
                notifyPeersChanged();
            }

            syncChainFromRemote(remote, confirmedAddress);

            List<Transaction> remotePending = remote.getPendingTransactions();
            if (remotePending != null) {
                for (Transaction tx : remotePending) {
                    acceptTransactionLocally(tx);
                }
            }

            List<String> remotePeers = remote.getPeers();
            if (remotePeers != null) {
                for (String discovered : remotePeers) {
                    if (!discovered.equals(this.address) && !peers.contains(discovered)) {
                        networkExecutor.submit(() -> connectToPeer(discovered));
                    }
                }
            }
            return true;
        } catch (Exception e) {
            log("Falha ao ligar ao par " + normalized + ": " + e.getMessage());
            return false;
        }
    }

    private void syncWithPeerAsync(String peerAddress) {
        if (peerAddress == null || peerAddress.equals(this.address)) return;
        networkExecutor.submit(() -> {
            try {
                RemoteNode remote = (RemoteNode) RmiUtils.getRemote(peerAddress);
                syncChainFromRemote(remote, peerAddress);
            } catch (Exception e) {
                log("Falha ao sincronizar cadeia com " + peerAddress + ": " + e.getMessage());
            }
        });
    }

    private void syncChainFromRemote(RemoteNode remote, String remoteAddress) throws RemoteException {
        List<Block> candidateChain = remote.getBlockchain();
        boolean replaced = false;

        stateLock.lock();
        try {
            if (blockchain.replaceChain(candidateChain)) {
                replaced = true;
                for (Block b : candidateChain) {
                    seenBlockHashes.add(b.getHash());
                }
                pruneConfirmedFromMempoolLocked();
            }
        } finally {
            stateLock.unlock();
        }

        if (replaced) {
            miner.cancelCurrentMining();
            log("Cadeia sincronizada com " + remoteAddress + " (novo tamanho: " + blockchain.size() + " blocos).");
            notifyBlockchainChanged();
            notifyMempoolChanged();
        }
    }

    public boolean submitTransaction(Transaction tx) {
        boolean accepted = acceptTransactionLocally(tx);
        if (accepted) {
            log("Transação submetida localmente: " + tx);
            broadcastTransactionAsync(tx, this.address);
        } else {
            log("Transação rejeitada (inválida, duplicada ou violação de estado): " + tx);
        }
        return accepted;
    }

    public Optional<MiningResult> minePendingTransactions(int difficulty) {
        Block candidate;
        stateLock.lock();
        try {
            if (mempool.isEmpty()) {
                log("Mempool vazia: não existem transações pendentes para minerar.");
                return Optional.empty();
            }
            List<Transaction> pendingSorted = new ArrayList<>(mempool.values());
            pendingSorted.sort(Comparator.comparingLong(Transaction::getTimestamp));

            candidate = blockchain.createCandidateBlock(pendingSorted, difficulty);
            if (candidate.getTransactions().isEmpty()) {
                log("Nenhuma transação pendente válida para incluir no bloco.");
                return Optional.empty();
            }
        } finally {
            stateLock.unlock();
        }

        log("A iniciar mineração paralela do Bloco #" + candidate.getIndex()
                + " (" + candidate.getTransactions().size() + " txs, dificuldade=" + difficulty + ")...");

        Optional<MiningResult> resultOpt = miner.mine(candidate);
        if (resultOpt.isEmpty()) {
            log("Mineração do Bloco #" + candidate.getIndex() + " foi cancelada.");
            return Optional.empty();
        }

        MiningResult result = resultOpt.get();
        Block minedBlock = result.minedBlock();
        boolean added;

        stateLock.lock();
        try {
            added = blockchain.addMinedBlock(minedBlock);
            if (added) {
                seenBlockHashes.add(minedBlock.getHash());
                pruneConfirmedFromMempoolLocked();
            }
        } finally {
            stateLock.unlock();
        }

        if (added) {
            log("SUCESSO! " + result);
            notifyBlockchainChanged();
            notifyMempoolChanged();
            broadcastBlockAsync(minedBlock, this.address);
            return resultOpt;
        } else {
            log("Bloco #" + minedBlock.getIndex() + " ficou obsoleto (outro nó avançou a cadeia primeiro).");
            return Optional.empty();
        }
    }

    public void cancelMining() {
        miner.cancelCurrentMining();
    }

    // --- Métodos Remotos RMI ---

    @Override
    public String getAddress() throws RemoteException {
        return this.address;
    }

    @Override
    public void registerPeer(String peerAddress) throws RemoteException {
        if (peerAddress != null && !peerAddress.equals(this.address)) {
            if (peers.add(peerAddress)) {
                log("Novo par registado: " + peerAddress);
                notifyPeersChanged();
            }
        }
    }

    @Override
    public List<String> getPeers() throws RemoteException {
        return new ArrayList<>(peers);
    }

    @Override
    public void receiveTransaction(Transaction tx, String senderAddress) throws RemoteException {
        if (tx == null) return;
        if (acceptTransactionLocally(tx)) {
            log("Transação recebida da rede (" + senderAddress + "): " + tx);
            broadcastTransactionAsync(tx, senderAddress);
        }
    }

    @Override
    public void receiveBlock(Block block, String senderAddress) throws RemoteException {
        if (block == null || block.getHash() == null) return;
        if (seenBlockHashes.contains(block.getHash())) return;

        boolean accepted = false;
        boolean needsSync = false;

        stateLock.lock();
        try {
            if (seenBlockHashes.contains(block.getHash())) return;
            int localTipIndex = blockchain.getLatestBlock().getIndex();

            if (block.getIndex() == localTipIndex + 1) {
                if (blockchain.addMinedBlock(block)) {
                    accepted = true;
                    seenBlockHashes.add(block.getHash());
                    pruneConfirmedFromMempoolLocked();
                } else {
                    needsSync = true;
                }
            } else if (block.getIndex() > localTipIndex + 1) {
                needsSync = true;
            }
        } finally {
            stateLock.unlock();
        }

        if (accepted) {
            miner.cancelCurrentMining();
            log("Bloco #" + block.getIndex() + " aceite da rede (" + senderAddress + "): " + block);
            notifyBlockchainChanged();
            notifyMempoolChanged();
            broadcastBlockAsync(block, senderAddress);
        } else if (needsSync) {
            log("Detetada divergência/atraso no Bloco #" + block.getIndex()
                    + " de " + senderAddress + ". A sincronizar cadeia...");
            syncWithPeerAsync(senderAddress);
        }
    }

    @Override
    public List<Block> getBlockchain() throws RemoteException {
        stateLock.lock();
        try {
            return blockchain.getChainSnapshot();
        } finally {
            stateLock.unlock();
        }
    }

    @Override
    public List<Transaction> getPendingTransactions() throws RemoteException {
        return new ArrayList<>(mempool.values());
    }

    // --- Auxiliares internos ---

    private boolean acceptTransactionLocally(Transaction tx) {
        if (tx == null || tx.getTxId() == null) return false;
        if (mempool.containsKey(tx.getTxId())) return false;

        boolean added = false;
        stateLock.lock();
        try {
            if (!mempool.containsKey(tx.getTxId())
                    && !blockchain.getConfirmedTransactionIds().contains(tx.getTxId())
                    && blockchain.isTransactionAllowed(tx)) {
                mempool.put(tx.getTxId(), tx);
                added = true;
            }
        } finally {
            stateLock.unlock();
        }

        if (added) {
            notifyMempoolChanged();
        }
        return added;
    }

    private void pruneConfirmedFromMempoolLocked() {
        Set<String> confirmed = blockchain.getConfirmedTransactionIds();
        mempool.keySet().removeAll(confirmed);
    }

    private void broadcastTransactionAsync(Transaction tx, String excludeAddress) {
        for (String peerAddr : peers) {
            if (peerAddr.equals(excludeAddress) || peerAddr.equals(this.address)) continue;
            networkExecutor.submit(() -> {
                try {
                    RemoteNode peer = (RemoteNode) RmiUtils.getRemote(peerAddr);
                    peer.receiveTransaction(tx, this.address);
                } catch (Exception e) {
                    handleUnreachablePeer(peerAddr, e);
                }
            });
        }
    }

    private void broadcastBlockAsync(Block block, String excludeAddress) {
        for (String peerAddr : peers) {
            if (peerAddr.equals(excludeAddress) || peerAddr.equals(this.address)) continue;
            networkExecutor.submit(() -> {
                try {
                    RemoteNode peer = (RemoteNode) RmiUtils.getRemote(peerAddr);
                    peer.receiveBlock(block, this.address);
                } catch (Exception e) {
                    handleUnreachablePeer(peerAddr, e);
                }
            });
        }
    }

    private void handleUnreachablePeer(String peerAddr, Exception e) {
        log("Par inacessível (" + peerAddr + ") removido da lista: " + e.getMessage());
        if (peers.remove(peerAddr)) {
            notifyPeersChanged();
        }
    }

    private String normalizeAddress(String input) {
        if (input == null || input.isBlank()) return null;
        String trimmed = input.trim();
        if (trimmed.startsWith("//")) return trimmed;
        if (trimmed.contains(":")) {
            return "//" + trimmed + "/" + RmiUtils.DEFAULT_SERVICE_NAME;
        }
        return null;
    }

    public Optional<CredentialRecord> findCredentialRecord(String credentialHash) {
        stateLock.lock();
        try {
            return blockchain.findCredentialRecord(credentialHash);
        } finally {
            stateLock.unlock();
        }
    }

    public boolean isBlockchainValid() {
        stateLock.lock();
        try {
            return blockchain.isValid();
        } finally {
            stateLock.unlock();
        }
    }

    public List<Block> getLocalBlockchainSnapshot() {
        stateLock.lock();
        try {
            return blockchain.getChainSnapshot();
        } finally {
            stateLock.unlock();
        }
    }

    public Set<String> getConnectedPeers() {
        return Collections.unmodifiableSet(new HashSet<>(peers));
    }

    public List<Transaction> getLocalMempool() {
        return new ArrayList<>(mempool.values());
    }

    public ParallelMiner getMiner() {
        return miner;
    }

    public int getPort() {
        return port;
    }

    private void log(String msg) {
        String formatted = "[" + port + "] " + msg;
        for (NodeListener l : listeners) {
            l.onLog(formatted);
        }
    }

    private void notifyPeersChanged() {
        Set<String> snapshot = getConnectedPeers();
        for (NodeListener l : listeners) l.onPeersChanged(snapshot);
    }

    private void notifyMempoolChanged() {
        List<Transaction> snapshot = getLocalMempool();
        for (NodeListener l : listeners) l.onMempoolChanged(snapshot);
    }

    private void notifyBlockchainChanged() {
        List<Block> snapshot = getLocalBlockchainSnapshot();
        for (NodeListener l : listeners) l.onBlockchainChanged(snapshot);
    }
}
