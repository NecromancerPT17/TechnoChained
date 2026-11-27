package pt.ipt.technochained.miner;

import pt.ipt.technochained.blockchain.Block;

public record MiningResult(
        Block minedBlock,
        long winningNonce,
        String winningHash,
        long totalHashesTried,
        long elapsedMillis,
        int threadsUsed,
        String winningThreadName
) {
    public double hashesPerSecond() {
        if (elapsedMillis <= 0) {
            return totalHashesTried * 1000.0;
        }
        return (totalHashesTried * 1000.0) / elapsedMillis;
    }

    @Override
    public String toString() {
        return String.format(
                "Bloco #%d minerado em %d ms (%d threads | %,d hashes | %,.0f H/s | nonce=%d | hash=%s...)",
                minedBlock.getIndex(), elapsedMillis, threadsUsed,
                totalHashesTried, hashesPerSecond(), winningNonce,
                winningHash.substring(0, 14)
        );
    }
}
