package pt.ipt.technochained.gui;

import pt.ipt.technochained.miner.MiningResult;
import pt.ipt.technochained.network.P2PNode;

import javax.swing.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

public class MiningWorker extends SwingWorker<Optional<MiningResult>, Long> {

    private final P2PNode node;
    private final int difficulty;
    private final LongConsumer onProgressHashes;
    private final Consumer<Optional<MiningResult>> onFinished;

    public MiningWorker(P2PNode node, int difficulty,
                        LongConsumer onProgressHashes,
                        Consumer<Optional<MiningResult>> onFinished) {
        this.node = node;
        this.difficulty = difficulty;
        this.onProgressHashes = onProgressHashes;
        this.onFinished = onFinished;
    }

    @Override
    protected Optional<MiningResult> doInBackground() throws Exception {
        CompletableFuture<Optional<MiningResult>> miningFuture =
                CompletableFuture.supplyAsync(() -> node.minePendingTransactions(difficulty));

        while (!miningFuture.isDone()) {
            if (isCancelled()) {
                node.cancelMining();
                break;
            }
            publish(node.getMiner().getCurrentHashesTried());
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                if (isCancelled()) {
                    node.cancelMining();
                    break;
                }
            }
        }

        publish(node.getMiner().getCurrentHashesTried());
        return miningFuture.get();
    }

    @Override
    protected void process(List<Long> chunks) {
        if (!chunks.isEmpty() && onProgressHashes != null) {
            long latestHashes = chunks.get(chunks.size() - 1);
            onProgressHashes.accept(latestHashes);
        }
    }

    @Override
    protected void done() {
        if (onFinished == null) return;
        try {
            if (isCancelled()) {
                onFinished.accept(Optional.empty());
            } else {
                onFinished.accept(get());
            }
        } catch (Exception e) {
            onFinished.accept(Optional.empty());
        }
    }
}
