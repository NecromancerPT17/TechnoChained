package pt.ipt.technochained.tests;

import pt.ipt.technochained.blockchain.Block;
import pt.ipt.technochained.blockchain.Blockchain;
import pt.ipt.technochained.miner.MiningResult;
import pt.ipt.technochained.miner.ParallelMiner;

import java.util.List;
import java.util.Optional;

public class SpeedupBenchmarkTest {

    public static void main(String[] args) {
        int difficulty = 5;
        int cores = Runtime.getRuntime().availableProcessors();

        System.out.println("=== Benchmark Proof of Work (Dificuldade = " + difficulty + ", Cores = " + cores + ") ===");

        Blockchain bcSeq = new Blockchain();
        Block blockSeq = bcSeq.createCandidateBlock(List.of(), difficulty);
        ParallelMiner seqMiner = new ParallelMiner(1);
        MiningResult resSeq = seqMiner.mine(blockSeq).orElseThrow();
        seqMiner.shutdown();
        System.out.println("SEQUENCIAL (1 thread):  " + resSeq);

        Blockchain bcPar = new Blockchain();
        Block blockPar = bcPar.createCandidateBlock(List.of(), difficulty);
        ParallelMiner parMiner = new ParallelMiner(cores);
        MiningResult resPar = parMiner.mine(blockPar).orElseThrow();
        System.out.println("PARALELO (" + cores + " threads): " + resPar);

        double speedupHashrate = resPar.hashesPerSecond() / resSeq.hashesPerSecond();
        System.out.printf("Fator de Aceleração (Hashrate Paralelo / Sequencial): %.2fx%n", speedupHashrate);
        System.out.println("Bloco aceite e cadeia válida? "
                + (bcPar.addMinedBlock(blockPar) && bcPar.isValid()));

        Block hardBlock = bcPar.createCandidateBlock(List.of(), 7);
        Thread canceller = new Thread(() -> {
            try {
                Thread.sleep(500);
                parMiner.cancelCurrentMining();
            } catch (InterruptedException ignored) {}
        });
        canceller.start();

        Optional<MiningResult> cancelled = parMiner.mine(hardBlock);
        System.out.println("Cancelamento funcionou? " + cancelled.isEmpty());
        parMiner.shutdown();
    }
}
