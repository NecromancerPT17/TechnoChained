package pt.ipt.technochained.miner;

import pt.ipt.technochained.blockchain.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class ParallelMiner {

    private static final long NONCE_CHUNK_SIZE = 10_000L;

    private final int numThreads;
    private final ExecutorService executor;

    private volatile AtomicBoolean activeStopSignal;
    private volatile AtomicBoolean activeExternalCancel;
    private volatile AtomicLong activeHashCounter;
    private volatile boolean mining;

    public ParallelMiner() {
        this(Runtime.getRuntime().availableProcessors());
    }

    public ParallelMiner(int numThreads) {
        if (numThreads < 1) {
            throw new IllegalArgumentException("O número de threads tem de ser >= 1.");
        }
        this.numThreads = numThreads;
        AtomicInteger threadId = new AtomicInteger(1);

        this.executor = Executors.newFixedThreadPool(numThreads, runnable -> {
            Thread t = new Thread(runnable, "Miner-Worker-" + threadId.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
    }

    public Optional<MiningResult> mine(Block candidateBlock) {
        if (candidateBlock == null) {
            throw new IllegalArgumentException("O bloco candidato não pode ser nulo.");
        }

        AtomicLong nextNonceChunk = new AtomicLong(0L);
        AtomicBoolean stopSignal = new AtomicBoolean(false);
        AtomicBoolean externalCancel = new AtomicBoolean(false);
        AtomicLong totalHashes = new AtomicLong(0L);
        AtomicReference<FoundSolution> winnerRef = new AtomicReference<>(null);

        this.activeStopSignal = stopSignal;
        this.activeExternalCancel = externalCancel;
        this.activeHashCounter = totalHashes;
        this.mining = true;

        int difficulty = candidateBlock.getDifficulty();
        long startTime = System.currentTimeMillis();
        List<Future<?>> futures = new ArrayList<>(numThreads);

        try {
            for (int i = 0; i < numThreads; i++) {
                futures.add(executor.submit(() -> runWorker(
                        candidateBlock, difficulty, nextNonceChunk,
                        stopSignal, totalHashes, winnerRef
                )));
            }

            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    externalCancel.set(true);
                    stopSignal.set(true);
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    externalCancel.set(true);
                    stopSignal.set(true);
                    throw new RuntimeException("Erro inesperado numa thread de mineração", e.getCause());
                }
            }

            long elapsedMillis = System.currentTimeMillis() - startTime;
            FoundSolution solution = winnerRef.get();

            if (solution != null && !externalCancel.get()) {
                candidateBlock.seal(solution.nonce(), solution.hash());
                return Optional.of(new MiningResult(
                        candidateBlock,
                        solution.nonce(),
                        solution.hash(),
                        totalHashes.get(),
                        elapsedMillis,
                        numThreads,
                        solution.threadName()
                ));
            }
            return Optional.empty();

        } finally {
            this.mining = false;
        }
    }

    private void runWorker(Block block, int difficulty, AtomicLong nextNonceChunk,
                           AtomicBoolean stopSignal, AtomicLong totalHashes,
                           AtomicReference<FoundSolution> winnerRef) {
        long localCount = 0L;
        try {
            while (!stopSignal.get()) {
                long startNonce = nextNonceChunk.getAndAdd(NONCE_CHUNK_SIZE);
                if (startNonce < 0) {
                    break;
                }
                long endNonce = startNonce + NONCE_CHUNK_SIZE;

                for (long nonce = startNonce; nonce < endNonce; nonce++) {
                    if (stopSignal.get()) {
                        return;
                    }

                    String candidateHash = block.calculateHashForNonce(nonce);
                    localCount++;

                    if (Block.meetsDifficulty(candidateHash, difficulty)) {
                        FoundSolution solution = new FoundSolution(
                                nonce, candidateHash, Thread.currentThread().getName()
                        );
                        if (winnerRef.compareAndSet(null, solution)) {
                            stopSignal.set(true);
                        }
                        return;
                    }
                }

                totalHashes.addAndGet(localCount);
                localCount = 0L;
            }
        } finally {
            if (localCount > 0) {
                totalHashes.addAndGet(localCount);
            }
        }
    }

    public void cancelCurrentMining() {
        AtomicBoolean ext = this.activeExternalCancel;
        AtomicBoolean stop = this.activeStopSignal;
        if (ext != null) ext.set(true);
        if (stop != null) stop.set(true);
    }

    public long getCurrentHashesTried() {
        AtomicLong counter = this.activeHashCounter;
        return (counter != null) ? counter.get() : 0L;
    }

    public boolean isMining() {
        return mining;
    }

    public int getNumThreads() {
        return numThreads;
    }

    public void shutdown() {
        cancelCurrentMining();
        executor.shutdownNow();
    }

    private record FoundSolution(long nonce, String hash, String threadName) {}
}
