package pt.ipt.technochained.blockchain;

import pt.ipt.technochained.crypto.CryptoUtils;
import pt.ipt.technochained.model.Transaction;

import java.util.ArrayList;
import java.util.List;

public final class MerkleTree {

    private MerkleTree() {}

    public static String computeMerkleRoot(List<Transaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return CryptoUtils.sha256Hex("");
        }

        List<String> currentLevel = new ArrayList<>(transactions.size());
        for (Transaction tx : transactions) {
            currentLevel.add(tx.getTxId());
        }

        while (currentLevel.size() > 1) {
            if (currentLevel.size() % 2 != 0) {
                currentLevel.add(currentLevel.get(currentLevel.size() - 1));
            }

            List<String> nextLevel = new ArrayList<>(currentLevel.size() / 2);
            for (int i = 0; i < currentLevel.size(); i += 2) {
                String combined = currentLevel.get(i) + currentLevel.get(i + 1);
                nextLevel.add(CryptoUtils.sha256Hex(combined));
            }
            currentLevel = nextLevel;
        }

        return currentLevel.get(0);
    }
}
