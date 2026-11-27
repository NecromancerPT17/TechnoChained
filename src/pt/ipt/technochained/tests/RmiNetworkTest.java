package pt.ipt.technochained.tests;

import pt.ipt.technochained.blockchain.Block;
import pt.ipt.technochained.blockchain.CredentialRecord;
import pt.ipt.technochained.model.Credential;
import pt.ipt.technochained.model.CredentialStatus;
import pt.ipt.technochained.model.Transaction;
import pt.ipt.technochained.network.NodeListener;
import pt.ipt.technochained.network.P2PNode;
import pt.ipt.technochained.wallet.Wallet;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class RmiNetworkTest {

    public static void main(String[] args) throws Exception {
        P2PNode node1 = new P2PNode("localhost", 10001);
        P2PNode node2 = new P2PNode("localhost", 10002);
        P2PNode node3 = new P2PNode("localhost", 10003);

        NodeListener consoleLogger = new NodeListener() {
            @Override public void onLog(String message) { System.out.println(message); }
            @Override public void onPeersChanged(Set<String> peers) {}
            @Override public void onMempoolChanged(List<Transaction> txs) {}
            @Override public void onBlockchainChanged(List<Block> chain) {}
        };
        node1.addListener(consoleLogger);
        node2.addListener(consoleLogger);
        node3.addListener(consoleLogger);

        try {
            node1.start();
            node2.start();
            node3.start();

            node2.connectToPeer("localhost:10001");
            node3.connectToPeer("localhost:10002");
            Thread.sleep(500);

            System.out.println("\n--- Pares RMI Descobertos (Gossip) ---");
            System.out.println("Node 1: " + node1.getConnectedPeers());
            System.out.println("Node 2: " + node2.getConnectedPeers());
            System.out.println("Node 3: " + node3.getConnectedPeers());

            Path tempDir = Path.of("test_wallets");
            Wallet academia = Wallet.createAndSave("academia_test", tempDir, "pass123".toCharArray());
            Wallet aluno = Wallet.createAndSave("aluno_test", tempDir, "pass456".toCharArray());

            Credential cred = new Credential(
                    "Rafael Vieira", "rafael@ipt.pt", "Eng. Informática",
                    "Computação Distribuída", List.of("Criptografia", "Blockchain", "Java RMI"),
                    "19 valores", "https://github.com/technochained",
                    "IPT Tomar", academia.getPublicKey(), aluno.getDid()
            );
            Path certPath = tempDir.resolve("cert_rafael.json");
            Transaction txEmit = academia.issueCredential(cred, certPath);

            // Validar leitura do JSON puro gerado sem dependências externas
            Credential reloaded = Credential.loadFromFile(certPath);
            System.out.println("JSON recarregado com assinatura válida? " + reloaded.verifySignature());

            node1.submitTransaction(txEmit);
            Thread.sleep(500);

            node2.minePendingTransactions(4);
            Thread.sleep(500);

            String hash = reloaded.calculateHash();
            CredentialRecord recOnNode3 = node3.findCredentialRecord(hash).orElse(null);
            System.out.println("\nVerificador no Node 3 vê: " + recOnNode3);

            Transaction txRevoke = academia.createStatusChangeTransaction(
                    aluno.getDid(), hash, CredentialStatus.REVOGADA);
            node3.submitTransaction(txRevoke);
            Thread.sleep(500);

            node1.minePendingTransactions(4);
            Thread.sleep(500);

            System.out.println("Estado final no Node 2: " + node2.findCredentialRecord(hash).orElse(null));
            System.out.println("Cadeias válidas? N1=" + node1.isBlockchainValid()
                    + ", N2=" + node2.isBlockchainValid()
                    + ", N3=" + node3.isBlockchainValid());
        } finally {
            node1.shutdown();
            node2.shutdown();
            node3.shutdown();
        }
    }
}
