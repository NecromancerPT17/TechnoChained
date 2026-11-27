package pt.ipt.technochained.blockchain;

import pt.ipt.technochained.crypto.CryptoUtils;
import pt.ipt.technochained.model.Transaction;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Block implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int index;
    private final String previousHash;
    private final long timestamp;
    private final String merkleRoot;
    private final int difficulty;
    private long nonce;
    private String hash;

    private final List<Transaction> transactions;

    public Block(int index, String previousHash, List<Transaction> transactions, int difficulty) {
        this(index, previousHash, System.currentTimeMillis(), transactions, difficulty);
    }

    private Block(int index, String previousHash, long timestamp, List<Transaction> transactions, int difficulty) {
        if (difficulty < 0) {
            throw new IllegalArgumentException("A dificuldade não pode ser negativa.");
        }
        this.index = index;
        this.previousHash = Objects.requireNonNull(previousHash);
        this.timestamp = timestamp;
        this.transactions = (transactions != null) ? new ArrayList<>(transactions) : new ArrayList<>();
        this.merkleRoot = MerkleTree.computeMerkleRoot(this.transactions);
        this.difficulty = difficulty;
        this.nonce = 0L;
        this.hash = calculateHashForNonce(0L);
    }

    public static Block createGenesisBlock() {
        String zeroHash = "0".repeat(64);
        Block genesis = new Block(0, zeroHash, 0L, List.of(), 0);
        genesis.seal(0L, genesis.calculateHashForNonce(0L));
        return genesis;
    }

    public String calculateHashForNonce(long candidateNonce) {
        String header = index + "|"
                + previousHash + "|"
                + timestamp + "|"
                + merkleRoot + "|"
                + difficulty + "|"
                + candidateNonce;
        return CryptoUtils.sha256Hex(header);
    }

    public void seal(long winningNonce, String winningHash) {
        String expected = calculateHashForNonce(winningNonce);
        if (!expected.equals(winningHash)) {
            throw new IllegalArgumentException("O hash fornecido não corresponde ao nonce.");
        }
        if (!meetsDifficulty(winningHash, difficulty)) {
            throw new IllegalArgumentException("O hash não cumpre a dificuldade exigida (" + difficulty + ").");
        }
        this.nonce = winningNonce;
        this.hash = winningHash;
    }

    public static boolean meetsDifficulty(String candidateHash, int difficulty) {
        if (candidateHash == null || difficulty < 0 || candidateHash.length() < difficulty) {
            return false;
        }
        for (int i = 0; i < difficulty; i++) {
            if (candidateHash.charAt(i) != '0') {
                return false;
            }
        }
        return true;
    }

    public boolean hasValidStructureAndPoW() {
        String recomputedMerkle = MerkleTree.computeMerkleRoot(transactions);
        if (!recomputedMerkle.equals(merkleRoot)) {
            return false;
        }
        String recomputedHash = calculateHashForNonce(nonce);
        if (!recomputedHash.equals(hash)) {
            return false;
        }
        return meetsDifficulty(hash, difficulty);
    }

    public int getIndex() { return index; }
    public String getPreviousHash() { return previousHash; }
    public long getTimestamp() { return timestamp; }
    public String getMerkleRoot() { return merkleRoot; }
    public int getDifficulty() { return difficulty; }
    public long getNonce() { return nonce; }
    public String getHash() { return hash; }
    public List<Transaction> getTransactions() { return Collections.unmodifiableList(transactions); }

    @Override
    public String toString() {
        return String.format("Block #%d [txs=%d, diff=%d, nonce=%d, hash=%s...]",
                index, transactions.size(), difficulty, nonce, hash.substring(0, 12));
    }
}
