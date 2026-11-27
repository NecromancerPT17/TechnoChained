package pt.ipt.technochained.blockchain;

import pt.ipt.technochained.model.CredentialStatus;
import pt.ipt.technochained.model.Transaction;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;

public class Blockchain implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final List<Block> chain;

    public Blockchain() {
        this.chain = new ArrayList<>();
        this.chain.add(Block.createGenesisBlock());
    }

    public synchronized Block getLatestBlock() {
        return chain.get(chain.size() - 1);
    }

    public synchronized int size() {
        return chain.size();
    }

    public synchronized List<Block> getChainSnapshot() {
        return new ArrayList<>(chain);
    }

    public synchronized Block createCandidateBlock(List<Transaction> pendingTransactions, int difficulty) {
        Block latest = getLatestBlock();

        Set<String> seenTxIds = new HashSet<>();
        Map<String, CredentialRecord> state = computeCurrentState(this.chain, seenTxIds);

        List<Transaction> validTxs = new ArrayList<>();
        for (Transaction tx : pendingTransactions) {
            if (applyTransactionRule(tx, seenTxIds, state, latest.getIndex() + 1)) {
                validTxs.add(tx);
            }
        }

        return new Block(latest.getIndex() + 1, latest.getHash(), validTxs, difficulty);
    }

    public synchronized boolean addMinedBlock(Block newBlock) {
        if (newBlock == null) return false;
        Block latest = getLatestBlock();

        if (newBlock.getIndex() != latest.getIndex() + 1) return false;
        if (!newBlock.getPreviousHash().equals(latest.getHash())) return false;
        if (newBlock.getDifficulty() < 1) return false;
        if (!newBlock.hasValidStructureAndPoW()) return false;

        Set<String> seenTxIds = new HashSet<>();
        Map<String, CredentialRecord> state = computeCurrentState(this.chain, seenTxIds);
        if (state == null) return false;

        for (Transaction tx : newBlock.getTransactions()) {
            if (!applyTransactionRule(tx, seenTxIds, state, newBlock.getIndex())) {
                return false;
            }
        }

        chain.add(newBlock);
        return true;
    }

    public synchronized boolean isTransactionAllowed(Transaction tx) {
        if (tx == null) return false;
        Set<String> seenTxIds = new HashSet<>();
        Map<String, CredentialRecord> state = computeCurrentState(this.chain, seenTxIds);
        if (state == null) return false;
        return applyTransactionRule(tx, seenTxIds, state, chain.size());
    }

    public synchronized boolean replaceChain(List<Block> candidateChain) {
        if (candidateChain == null || candidateChain.size() <= this.chain.size()) {
            return false;
        }
        if (!isChainValid(candidateChain)) {
            return false;
        }
        this.chain.clear();
        this.chain.addAll(candidateChain);
        return true;
    }

    public synchronized boolean isValid() {
        return isChainValid(this.chain);
    }

    public static boolean isChainValid(List<Block> candidateChain) {
        return computeCurrentState(candidateChain, new HashSet<>()) != null;
    }

    public synchronized Optional<CredentialRecord> findCredentialRecord(String credentialHash) {
        if (credentialHash == null || credentialHash.isBlank()) {
            return Optional.empty();
        }
        Map<String, CredentialRecord> state = computeCurrentState(this.chain, new HashSet<>());
        if (state == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(state.get(credentialHash));
    }

    public synchronized Set<String> getConfirmedTransactionIds() {
        Set<String> ids = new HashSet<>();
        for (Block block : chain) {
            for (Transaction tx : block.getTransactions()) {
                ids.add(tx.getTxId());
            }
        }
        return ids;
    }

    private static Map<String, CredentialRecord> computeCurrentState(
            List<Block> candidateChain, Set<String> seenTxIds) {

        if (candidateChain == null || candidateChain.isEmpty()) {
            return null;
        }

        Block expectedGenesis = Block.createGenesisBlock();
        Block actualGenesis = candidateChain.get(0);
        if (!isSameGenesis(expectedGenesis, actualGenesis)) {
            return null;
        }

        Map<String, CredentialRecord> stateMap = new HashMap<>();

        for (int i = 1; i < candidateChain.size(); i++) {
            Block current = candidateChain.get(i);
            Block previous = candidateChain.get(i - 1);

            if (current.getIndex() != i) return null;
            if (!current.getPreviousHash().equals(previous.getHash())) return null;
            if (current.getDifficulty() < 1) return null;
            if (!current.hasValidStructureAndPoW()) return null;

            for (Transaction tx : current.getTransactions()) {
                if (!applyTransactionRule(tx, seenTxIds, stateMap, current.getIndex())) {
                    return null;
                }
            }
        }
        return stateMap;
    }

    private static boolean isSameGenesis(Block expected, Block actual) {
        return actual != null
                && actual.getIndex() == 0
                && expected.getPreviousHash().equals(actual.getPreviousHash())
                && expected.getTimestamp() == actual.getTimestamp()
                && expected.getMerkleRoot().equals(actual.getMerkleRoot())
                && expected.getHash().equals(actual.getHash())
                && actual.getTransactions().isEmpty();
    }

    private static boolean applyTransactionRule(
            Transaction tx,
            Set<String> seenTxIds,
            Map<String, CredentialRecord> stateMap,
            int blockIndex) {

        if (tx == null || !tx.isValid()) {
            return false;
        }
        if (!seenTxIds.add(tx.getTxId())) {
            return false;
        }

        CredentialRecord existing = stateMap.get(tx.getCredentialHash());

        if (existing == null) {
            if (tx.getStatus() != CredentialStatus.ATIVO) {
                return false;
            }
        } else {
            if (!existing.issuerDid().equals(tx.getIssuerDid())) {
                return false;
            }
            if (!existing.holderDid().equals(tx.getHolderDid())) {
                return false;
            }
            if (existing.status() == CredentialStatus.REVOGADA) {
                return false;
            }
            if (existing.status() == tx.getStatus()) {
                return false;
            }
        }

        stateMap.put(tx.getCredentialHash(), new CredentialRecord(
                tx.getCredentialHash(),
                tx.getIssuerDid(),
                tx.getHolderDid(),
                tx.getStatus(),
                blockIndex,
                tx.getTimestamp()
        ));
        return true;
    }
}
