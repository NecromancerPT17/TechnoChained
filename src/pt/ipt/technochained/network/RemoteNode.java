package pt.ipt.technochained.network;

import pt.ipt.technochained.blockchain.Block;
import pt.ipt.technochained.model.Transaction;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface RemoteNode extends Remote {

    String getAddress() throws RemoteException;

    void registerPeer(String peerAddress) throws RemoteException;

    List<String> getPeers() throws RemoteException;

    void receiveTransaction(Transaction tx, String senderAddress) throws RemoteException;

    void receiveBlock(Block block, String senderAddress) throws RemoteException;

    List<Block> getBlockchain() throws RemoteException;

    List<Transaction> getPendingTransactions() throws RemoteException;
}
