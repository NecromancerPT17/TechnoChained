package pt.ipt.technochained.network;

import pt.ipt.technochained.blockchain.Block;
import pt.ipt.technochained.model.Transaction;

import java.util.List;
import java.util.Set;

public interface NodeListener {
    void onLog(String message);
    void onPeersChanged(Set<String> peers);
    void onMempoolChanged(List<Transaction> pendingTransactions);
    void onBlockchainChanged(List<Block> chainSnapshot);
}
