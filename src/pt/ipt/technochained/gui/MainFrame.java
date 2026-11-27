package pt.ipt.technochained.gui;

import pt.ipt.technochained.blockchain.Block;
import pt.ipt.technochained.blockchain.CredentialRecord;
import pt.ipt.technochained.miner.MiningResult;
import pt.ipt.technochained.model.Credential;
import pt.ipt.technochained.model.CredentialStatus;
import pt.ipt.technochained.model.Transaction;
import pt.ipt.technochained.network.NodeListener;
import pt.ipt.technochained.network.P2PNode;
import pt.ipt.technochained.wallet.Wallet;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class MainFrame extends JFrame implements NodeListener {

    private static final Path WALLETS_DIR = Path.of("wallets");
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final P2PNode node;
    private final String nodeAddressLabel;
    private List<Block> displayedChain = new ArrayList<>();

    private Wallet issuerWallet;
    private Wallet holderWallet;

    private MiningWorker activeMiningWorker;

    // Separador 1 (Nó P2P & Blockchain)
    private JTextField txtPeerAddress;
    private JButton btnConnectPeer;
    private DefaultListModel<String> peersListModel;
    private DefaultTableModel mempoolTableModel;
    private DefaultTableModel chainTableModel;
    private JSpinner spnDifficulty;
    private JButton btnMine;
    private JButton btnCancelMine;
    private JProgressBar miningProgressBar;
    private JLabel lblMiningStatus;
    private JTextArea txtLogs;

    // Separador 2 (Emissor)
    private JLabel lblIssuerStatus;
    private JTextField txtHolderName, txtHolderContact, txtHolderBio;
    private JTextField txtCourseName, txtModules, txtGrade, txtProjectRef, txtTargetHolderDid;
    private JTextField txtRevokeHolderDid, txtRevokeHash;
    private JComboBox<CredentialStatus> cmbNewStatus;

    // Separador 3 (Titular)
    private JLabel lblHolderStatus;
    private JTextArea txtHolderCredentialView;

    // Separador 4 (Verificador)
    private JLabel lblVerifyBanner;
    private JTextArea txtVerifyDetails;

    public MainFrame(P2PNode node, String host) {
        this.node = node;
        this.nodeAddressLabel = "//" + host + ":" + node.getPort() + "/TechnoChainedNode";

        setTitle("TechnoChained — Nó P2P RMI [" + host + ":" + node.getPort() + "]");
        setSize(1080, 720);
        setLocationByPlatform(true);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (activeMiningWorker != null && !activeMiningWorker.isDone()) {
                    activeMiningWorker.cancel(true);
                }
                node.shutdown();
                dispose();
                System.exit(0);
            }
        });

        initUI();

        node.addListener(this);
        onBlockchainChanged(node.getLocalBlockchainSnapshot());
        onPeersChanged(node.getConnectedPeers());
        onMempoolChanged(node.getLocalMempool());
    }

    private void initUI() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("1. Nó P2P & Blockchain", buildNodeTab());
        tabs.addTab("2. Emissor (Academia)", buildIssuerTab());
        tabs.addTab("3. Titular (Carteira)", buildHolderTab());
        tabs.addTab("4. Verificador (Recrutador)", buildVerifierTab());

        setLayout(new BorderLayout());
        add(tabs, BorderLayout.CENTER);
    }

    // --- SEPARADOR 1: NÓ P2P, MEMPOOL, MINERAÇÃO E BLOCKCHAIN ---

    private JPanel buildNodeTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel topBar = new JPanel(new GridLayout(2, 1, 6, 6));

        JPanel peerBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        peerBar.setBorder(new TitledBorder("Rede P2P (Java RMI) — Este nó: " + nodeAddressLabel));
        txtPeerAddress = new JTextField("localhost:10001", 20);
        btnConnectPeer = new JButton("Ligar ao Par");
        btnConnectPeer.addActionListener(e -> connectToPeerAsync());
        JButton btnValidateChain = new JButton("Validar Integridade da Cadeia");
        btnValidateChain.addActionListener(e -> validateChainAction());
        peerBar.add(new JLabel("Endereço do Par (host:porto):"));
        peerBar.add(txtPeerAddress);
        peerBar.add(btnConnectPeer);
        peerBar.add(Box.createHorizontalStrut(20));
        peerBar.add(btnValidateChain);

        JPanel mineBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        mineBar.setBorder(new TitledBorder("Consenso Proof of Work (Mineração Paralela — "
                + node.getMiner().getNumThreads() + " threads)"));
        spnDifficulty = new JSpinner(new SpinnerNumberModel(4, 1, 7, 1));
        btnMine = new JButton("Minerar Bloco");
        btnMine.addActionListener(e -> startMiningAction());
        btnCancelMine = new JButton("Cancelar");
        btnCancelMine.setEnabled(false);
        btnCancelMine.addActionListener(e -> cancelMiningAction());
        miningProgressBar = new JProgressBar();
        miningProgressBar.setPreferredSize(new Dimension(150, 20));
        lblMiningStatus = new JLabel("Pronto.");

        mineBar.add(new JLabel("Dificuldade (zeros):"));
        mineBar.add(spnDifficulty);
        mineBar.add(btnMine);
        mineBar.add(btnCancelMine);
        mineBar.add(miningProgressBar);
        mineBar.add(lblMiningStatus);

        topBar.add(peerBar);
        topBar.add(mineBar);
        panel.add(topBar, BorderLayout.NORTH);

        peersListModel = new DefaultListModel<>();
        JList<String> peersList = new JList<>(peersListModel);
        JScrollPane peersScroll = new JScrollPane(peersList);
        peersScroll.setBorder(new TitledBorder("Pares RMI Conectados"));
        peersScroll.setPreferredSize(new Dimension(280, 140));

        mempoolTableModel = new DefaultTableModel(
                new String[]{"Tx ID", "Estado", "Hash Credencial", "Emissor DID"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable mempoolTable = new JTable(mempoolTableModel);
        JScrollPane mempoolScroll = new JScrollPane(mempoolTable);
        mempoolScroll.setBorder(new TitledBorder("Mempool (Transações Pendentes)"));

        JSplitPane upperSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, peersScroll, mempoolScroll);
        upperSplit.setResizeWeight(0.3);

        chainTableModel = new DefaultTableModel(
                new String[]{"#", "Txs", "Dif.", "Nonce", "Merkle Root", "Hash Anterior", "Hash Bloco"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable chainTable = new JTable(chainTableModel);
        chainTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        chainTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = chainTable.getSelectedRow();
                    if (row >= 0 && row < displayedChain.size()) {
                        showBlockDetailsDialog(displayedChain.get(row));
                    }
                }
            }
        });
        JScrollPane chainScroll = new JScrollPane(chainTable);
        chainScroll.setBorder(new TitledBorder("Blockchain Local Sincronizada (duplo clique para inspecionar bloco)"));

        JSplitPane centerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, upperSplit, chainScroll);
        centerSplit.setResizeWeight(0.4);
        panel.add(centerSplit, BorderLayout.CENTER);

        txtLogs = new JTextArea(6, 80);
        txtLogs.setEditable(false);
        txtLogs.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(txtLogs);
        logScroll.setBorder(new TitledBorder("Eventos do Nó"));
        panel.add(logScroll, BorderLayout.SOUTH);

        return panel;
    }

    private void showBlockDetailsDialog(Block b) {
        StringBuilder sb = new StringBuilder();
        sb.append("BLOCO #").append(b.getIndex()).append("\n");
        sb.append("==========================================================\n");
        sb.append("Timestamp:     ").append(b.getTimestamp() == 0L ? "0 (Génesis)" : DATE_FMT.format(new Date(b.getTimestamp()))).append("\n");
        sb.append("Dificuldade:   ").append(b.getDifficulty()).append("\n");
        sb.append("Nonce:         ").append(b.getNonce()).append("\n");
        sb.append("Hash Anterior: ").append(b.getPreviousHash()).append("\n");
        sb.append("Merkle Root:   ").append(b.getMerkleRoot()).append("\n");
        sb.append("Hash Bloco:    ").append(b.getHash()).append("\n\n");
        sb.append("TRANSAÇÕES (").append(b.getTransactions().size()).append("):\n");
        sb.append("----------------------------------------------------------\n");
        if (b.getTransactions().isEmpty()) {
            sb.append("(Sem transações — Bloco Génesis)\n");
        } else {
            for (Transaction tx : b.getTransactions()) {
                sb.append("• TxID:    ").append(tx.getTxId()).append("\n");
                sb.append("  Estado:  ").append(tx.getStatus()).append("\n");
                sb.append("  Hash:    ").append(tx.getCredentialHash()).append("\n");
                sb.append("  Emissor: ").append(tx.getIssuerDid()).append("\n");
                sb.append("  Titular: ").append(tx.getHolderDid()).append("\n\n");
            }
        }
        JTextArea area = new JTextArea(sb.toString(), 20, 72);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JOptionPane.showMessageDialog(this, new JScrollPane(area),
                "Detalhes do Bloco #" + b.getIndex(), JOptionPane.INFORMATION_MESSAGE);
    }

    private void connectToPeerAsync() {
        String target = txtPeerAddress.getText().trim();
        if (target.isEmpty()) return;
        btnConnectPeer.setEnabled(false);

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return node.connectToPeer(target);
            }

            @Override
            protected void done() {
                btnConnectPeer.setEnabled(true);
                try {
                    if (!get()) {
                        JOptionPane.showMessageDialog(MainFrame.this,
                                "Não foi possível ligar ao par: " + target,
                                "Erro de Ligação P2P", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void startMiningAction() {
        if (node.getLocalMempool().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Não existem transações pendentes na mempool para minerar.",
                    "Mempool Vazia", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int difficulty = (int) spnDifficulty.getValue();
        btnMine.setEnabled(false);
        btnCancelMine.setEnabled(true);
        miningProgressBar.setIndeterminate(true);
        lblMiningStatus.setText("A minerar...");

        activeMiningWorker = new MiningWorker(
                node,
                difficulty,
                hashes -> lblMiningStatus.setText(String.format("A minerar... (%,d hashes)", hashes)),
                resultOpt -> {
                    btnMine.setEnabled(true);
                    btnCancelMine.setEnabled(false);
                    miningProgressBar.setIndeterminate(false);
                    if (resultOpt.isPresent()) {
                        MiningResult r = resultOpt.get();
                        lblMiningStatus.setText(String.format(
                                "Bloco #%d minerado em %d ms (%,.0f H/s)",
                                r.minedBlock().getIndex(), r.elapsedMillis(), r.hashesPerSecond()));
                    } else {
                        lblMiningStatus.setText("Mineração interrompida / cancelada.");
                    }
                }
        );
        activeMiningWorker.execute();
    }

    private void cancelMiningAction() {
        if (activeMiningWorker != null && !activeMiningWorker.isDone()) {
            activeMiningWorker.cancel(true);
            node.cancelMining();
        }
    }

    private void validateChainAction() {
        boolean valid = node.isBlockchainValid();
        JOptionPane.showMessageDialog(this,
                valid ? "Cadeia íntegra! Todos os hashes, Árvores de Merkle, PoW e assinaturas são válidos."
                      : "ALERTA: A cadeia local apresenta inconsistências criptográficas!",
                "Validação da Blockchain",
                valid ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
    }

    // --- SEPARADOR 2: EMISSOR (ACADEMIA) ---

    private JPanel buildIssuerTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel walletPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        walletPanel.setBorder(new TitledBorder("Identidade Criptográfica do Emissor (Protegida por Password PBE/AES)"));
        JButton btnCreateIssuer = new JButton("Criar Carteira Emissor");
        JButton btnLoadIssuer = new JButton("Abrir Carteira Emissor");
        lblIssuerStatus = new JLabel("Nenhuma carteira carregada.");
        btnCreateIssuer.addActionListener(e -> promptWalletAction(true, true));
        btnLoadIssuer.addActionListener(e -> promptWalletAction(true, false));
        walletPanel.add(btnCreateIssuer);
        walletPanel.add(btnLoadIssuer);
        walletPanel.add(lblIssuerStatus);
        panel.add(walletPanel, BorderLayout.NORTH);

        JPanel formPanel = new JPanel(new GridLayout(9, 2, 8, 6));
        formPanel.setBorder(new TitledBorder("Emitir Nova Credencial (JSON Off-Chain + Transação ATIVO On-Chain)"));

        txtHolderName = new JTextField("Rafael Vieira");
        txtHolderContact = new JTextField("rafael@ipt.pt");
        txtHolderBio = new JTextField("Estudante de Engenharia Informática");
        txtCourseName = new JTextField("Computação Distribuída");
        txtModules = new JTextField("Segurança JCA, Blockchain, Programação Paralela, Java RMI");
        txtGrade = new JTextField("18 valores");
        txtProjectRef = new JTextField("https://github.com/grupo/technochained");
        txtTargetHolderDid = new JTextField("did:technochained:...");

        formPanel.add(new JLabel("Nome do Titular (Off-Chain):")); formPanel.add(txtHolderName);
        formPanel.add(new JLabel("Contacto do Titular (Off-Chain):")); formPanel.add(txtHolderContact);
        formPanel.add(new JLabel("Biografia (Off-Chain):")); formPanel.add(txtHolderBio);
        formPanel.add(new JLabel("Curso / Certificação:")); formPanel.add(txtCourseName);
        formPanel.add(new JLabel("Módulos Concluídos (separados por vírgula):")); formPanel.add(txtModules);
        formPanel.add(new JLabel("Avaliação Final:")); formPanel.add(txtGrade);
        formPanel.add(new JLabel("Referência / Projeto Avaliado:")); formPanel.add(txtProjectRef);
        formPanel.add(new JLabel("DID do Titular (copiado da Carteira do Aluno):")); formPanel.add(txtTargetHolderDid);

        JButton btnIssue = new JButton("Assinar, Exportar JSON e Submeter à Rede P2P");
        btnIssue.addActionListener(e -> issueCredentialAction());
        formPanel.add(new JLabel(""));
        formPanel.add(btnIssue);
        panel.add(formPanel, BorderLayout.CENTER);

        JPanel revokePanel = new JPanel(new GridLayout(2, 4, 8, 6));
        revokePanel.setBorder(new TitledBorder("Alterar Estado de Credencial Existente (Suspensão / Revogação)"));
        txtRevokeHolderDid = new JTextField();
        txtRevokeHash = new JTextField();
        cmbNewStatus = new JComboBox<>(new CredentialStatus[]{CredentialStatus.SUSPENSA, CredentialStatus.REVOGADA});
        JButton btnLoadJsonForRevoke = new JButton("Carregar do JSON...");
        btnLoadJsonForRevoke.addActionListener(e -> populateRevokeFromJson());
        JButton btnSubmitStatus = new JButton("Submeter Alteração à Rede");
        btnSubmitStatus.addActionListener(e -> submitStatusChangeAction());

        revokePanel.add(new JLabel("DID do Titular:")); revokePanel.add(txtRevokeHolderDid);
        revokePanel.add(new JLabel("Novo Estado:")); revokePanel.add(cmbNewStatus);
        revokePanel.add(new JLabel("Hash SHA-256 da Credencial:")); revokePanel.add(txtRevokeHash);
        revokePanel.add(btnLoadJsonForRevoke); revokePanel.add(btnSubmitStatus);
        panel.add(revokePanel, BorderLayout.SOUTH);

        return panel;
    }

    private void issueCredentialAction() {
        if (issuerWallet == null) {
            JOptionPane.showMessageDialog(this, "Carregue ou crie primeiro a carteira do Emissor.",
                    "Carteira em Falta", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String targetDid = txtTargetHolderDid.getText().trim();
        if (!targetDid.startsWith("did:technochained:")) {
            JOptionPane.showMessageDialog(this, "Introduza um DID de Titular válido (ex.: did:technochained:...).",
                    "DID Inválido", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Guardar Ficheiro JSON da Credencial Assinada");
        chooser.setFileFilter(new FileNameExtensionFilter("Ficheiros JSON (*.json)", "json"));
        chooser.setSelectedFile(new File("credencial_" + txtHolderName.getText().trim().replaceAll("\\s+", "_") + ".json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        try {
            List<String> modules = Arrays.stream(txtModules.getText().split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();

            Credential cred = new Credential(
                    txtHolderName.getText().trim(),
                    txtHolderContact.getText().trim(),
                    txtHolderBio.getText().trim(),
                    txtCourseName.getText().trim(),
                    modules,
                    txtGrade.getText().trim(),
                    txtProjectRef.getText().trim(),
                    issuerWallet.getOwnerLabel(),
                    issuerWallet.getPublicKey(),
                    targetDid
            );

            Path outPath = chooser.getSelectedFile().toPath();
            Transaction tx = issuerWallet.issueCredential(cred, outPath);
            boolean submitted = node.submitTransaction(tx);

            txtRevokeHolderDid.setText(targetDid);
            txtRevokeHash.setText(cred.calculateHash());

            JOptionPane.showMessageDialog(this,
                    "Credencial assinada e guardada em:\n" + outPath
                            + "\n\nHash SHA-256: " + cred.calculateHash()
                            + "\nSubmetida à rede P2P: " + (submitted ? "SIM (pendente de mineração)" : "REJEITADA"),
                    "Emissão Concluída", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro ao emitir credencial: " + ex.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void populateRevokeFromJson() {
        File file = chooseJsonFile("Selecionar Credencial JSON");
        if (file == null) return;
        try {
            Credential cred = Credential.loadFromFile(file.toPath());
            txtRevokeHolderDid.setText(cred.getHolderDid());
            txtRevokeHash.setText(cred.calculateHash());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro ao ler JSON: " + ex.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void submitStatusChangeAction() {
        if (issuerWallet == null) {
            JOptionPane.showMessageDialog(this, "Carregue primeiro a carteira do Emissor.",
                    "Carteira em Falta", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            CredentialStatus newStatus = (CredentialStatus) cmbNewStatus.getSelectedItem();
            Transaction tx = issuerWallet.createStatusChangeTransaction(
                    txtRevokeHolderDid.getText().trim(),
                    txtRevokeHash.getText().trim(),
                    newStatus
            );
            boolean ok = node.submitTransaction(tx);
            JOptionPane.showMessageDialog(this,
                    ok ? "Transação de alteração para " + newStatus + " propagada na rede P2P! Minere um bloco para a confirmar."
                       : "Transação rejeitada! Confirme que a credencial já está confirmada num bloco e que é o Emissor original.",
                    "Alteração de Estado", ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    // --- SEPARADOR 3: TITULAR (CARTEIRA LOCAL) ---

    private JPanel buildHolderTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        top.setBorder(new TitledBorder("Carteira Soberana do Titular (Chaves + Dados Off-Chain)"));
        JButton btnCreateHolder = new JButton("Criar Carteira Titular");
        JButton btnLoadHolder = new JButton("Abrir Carteira Titular");
        JButton btnCopyDid = new JButton("Copiar Meu DID");
        JButton btnInspectJson = new JButton("Abrir Credencial JSON Local...");
        lblHolderStatus = new JLabel("Nenhuma carteira carregada.");

        btnCreateHolder.addActionListener(e -> promptWalletAction(false, true));
        btnLoadHolder.addActionListener(e -> promptWalletAction(false, false));
        btnCopyDid.addActionListener(e -> {
            if (holderWallet != null) {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(holderWallet.getDid()), null);
                txtTargetHolderDid.setText(holderWallet.getDid());
                JOptionPane.showMessageDialog(this, "DID copiado: " + holderWallet.getDid());
            } else {
                JOptionPane.showMessageDialog(this, "Crie ou abra primeiro a carteira do Titular.");
            }
        });
        btnInspectJson.addActionListener(e -> inspectHolderCredentialAction());

        top.add(btnCreateHolder);
        top.add(btnLoadHolder);
        top.add(btnCopyDid);
        top.add(btnInspectJson);
        top.add(lblHolderStatus);
        panel.add(top, BorderLayout.NORTH);

        txtHolderCredentialView = new JTextArea();
        txtHolderCredentialView.setEditable(false);
        txtHolderCredentialView.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane scroll = new JScrollPane(txtHolderCredentialView);
        scroll.setBorder(new TitledBorder("Conteúdo Off-Chain da Credencial (Privado / RGPD)"));
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    private void inspectHolderCredentialAction() {
        File file = chooseJsonFile("Abrir Ficheiro de Credencial (.json)");
        if (file == null) return;
        try {
            Credential c = Credential.loadFromFile(file.toPath());
            StringBuilder sb = new StringBuilder();
            sb.append("Ficheiro: ").append(file.getAbsolutePath()).append("\n\n");
            sb.append("Titular:       ").append(c.getHolderName()).append("\n");
            sb.append("Contacto:      ").append(c.getHolderContact()).append("\n");
            sb.append("Biografia:     ").append(c.getHolderBio()).append("\n");
            sb.append("DID Titular:   ").append(c.getHolderDid()).append("\n");
            sb.append("--------------------------------------------------\n");
            sb.append("Curso:         ").append(c.getCourseName()).append("\n");
            sb.append("Módulos:       ").append(String.join(", ", c.getCompletedModules())).append("\n");
            sb.append("Avaliação:     ").append(c.getGrade()).append("\n");
            sb.append("Projeto:       ").append(c.getProjectReference()).append("\n");
            sb.append("--------------------------------------------------\n");
            sb.append("Emissor:       ").append(c.getIssuerName()).append(" (").append(c.getIssuerDid()).append(")\n");
            sb.append("Data Emissão:  ").append(DATE_FMT.format(new Date(c.getIssuedAt()))).append("\n");
            sb.append("Hash SHA-256:  ").append(c.getIssuerSignature() != null ? c.calculateHash() : "(sem assinatura)").append("\n");
            sb.append("Assinatura OK: ").append(c.verifySignature() ? "SIM" : "NÃO (ADULTERADA)").append("\n");
            txtHolderCredentialView.setText(sb.toString());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Ficheiro JSON inválido: " + ex.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    // --- SEPARADOR 4: VERIFICADOR (RECRUTADOR) ---

    private JPanel buildVerifierTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel top = new JPanel(new BorderLayout(10, 10));
        JButton btnVerify = new JButton("Selecionar Ficheiro JSON do Candidato e Verificar na Blockchain");
        btnVerify.setFont(btnVerify.getFont().deriveFont(Font.BOLD, 14f));
        btnVerify.addActionListener(e -> verifyCredentialAction());

        lblVerifyBanner = new JLabel("Aguardando ficheiro de credencial (.json)...", SwingConstants.CENTER);
        lblVerifyBanner.setOpaque(true);
        lblVerifyBanner.setBackground(new Color(230, 230, 230));
        lblVerifyBanner.setForeground(Color.DARK_GRAY);
        lblVerifyBanner.setFont(lblVerifyBanner.getFont().deriveFont(Font.BOLD, 18f));
        lblVerifyBanner.setBorder(new EmptyBorder(16, 10, 16, 10));

        top.add(btnVerify, BorderLayout.NORTH);
        top.add(lblVerifyBanner, BorderLayout.CENTER);
        panel.add(top, BorderLayout.NORTH);

        txtVerifyDetails = new JTextArea();
        txtVerifyDetails.setEditable(false);
        txtVerifyDetails.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane scroll = new JScrollPane(txtVerifyDetails);
        scroll.setBorder(new TitledBorder("Relatório Técnico de Verificação Criptográfica e On-Chain"));
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    private void verifyCredentialAction() {
        File file = chooseJsonFile("Selecionar Credencial JSON do Candidato");
        if (file == null) return;

        try {
            Credential cred = Credential.loadFromFile(file.toPath());
            boolean sigValid = cred.verifySignature();
            String hash = (cred.getIssuerSignature() != null && !cred.getIssuerSignature().isBlank())
                    ? cred.calculateHash() : null;
            Optional<CredentialRecord> recordOpt = (hash != null)
                    ? node.findCredentialRecord(hash) : Optional.empty();

            StringBuilder report = new StringBuilder();
            report.append("Ficheiro analisado: ").append(file.getName()).append("\n");
            report.append("Candidato:          ").append(cred.getHolderName())
                  .append(" (").append(cred.getHolderDid()).append(")\n");
            report.append("Curso / Nota:       ").append(cred.getCourseName())
                  .append(" — ").append(cred.getGrade()).append("\n");
            report.append("Emissor declarado:  ").append(cred.getIssuerName())
                  .append(" (").append(cred.getIssuerDid()).append(")\n");
            report.append("Hash SHA-256 calc.: ").append(hash != null ? hash : "INDISPONÍVEL").append("\n\n");

            report.append("=== RESULTADO DAS VERIFICAÇÕES ===\n");
            report.append("1. Assinatura Digital e DID do Emissor: ")
                  .append(sigValid ? "[OK] VÁLIDA\n" : "[FALHA] INVÁLIDA OU FICHEIRO ADULTERADO\n");

            if (recordOpt.isEmpty()) {
                report.append("2. Ancoragem na Blockchain Local:       [FALHA] HASH NÃO ENCONTRADO\n");
                report.append("3. Estado da Credencial:                DESCONHECIDO\n");

                if (!sigValid) {
                    setVerifyBanner("CREDENCIAL ADULTERADA / INVÁLIDA", new Color(190, 40, 40));
                } else {
                    setVerifyBanner("NÃO ENCONTRADA NA BLOCKCHAIN (NÃO MINERADA OU FORJADA)", new Color(210, 120, 20));
                }
            } else {
                CredentialRecord rec = recordOpt.get();
                boolean metadataMatch = rec.issuerDid().equals(cred.getIssuerDid())
                        && rec.holderDid().equals(cred.getHolderDid());

                report.append("2. Ancoragem na Blockchain Local:       [OK] ENCONTRADO NO BLOCO #")
                      .append(rec.lastBlockIndex()).append("\n");
                report.append("   Correspondência DIDs On-Chain:       ")
                      .append(metadataMatch ? "[OK]\n" : "[FALHA]\n");
                report.append("3. Estado Atual On-Chain:               ").append(rec.status())
                      .append(" (atualizado em ").append(DATE_FMT.format(new Date(rec.lastUpdatedAt()))).append(")\n");

                if (sigValid && metadataMatch && rec.status() == CredentialStatus.ATIVO) {
                    setVerifyBanner("CREDENCIAL AUTÊNTICA E ATIVA", new Color(34, 139, 34));
                } else if (rec.status() == CredentialStatus.SUSPENSA) {
                    setVerifyBanner("CREDENCIAL SUSPENSA PELO EMISSOR", new Color(210, 120, 20));
                } else if (rec.status() == CredentialStatus.REVOGADA) {
                    setVerifyBanner("CREDENCIAL REVOGADA PELO EMISSOR", new Color(190, 40, 40));
                } else {
                    setVerifyBanner("CREDENCIAL INVÁLIDA / INCONSISTENTE", new Color(190, 40, 40));
                }
            }

            txtVerifyDetails.setText(report.toString());
        } catch (Exception ex) {
            setVerifyBanner("ERRO AO LER FICHEIRO JSON", new Color(190, 40, 40));
            txtVerifyDetails.setText("Detalhes do erro: " + ex.getMessage());
        }
    }

    private void setVerifyBanner(String text, Color bgColor) {
        lblVerifyBanner.setText(text);
        lblVerifyBanner.setBackground(bgColor);
        lblVerifyBanner.setForeground(Color.WHITE);
    }

    // --- Auxiliares ---

    private void promptWalletAction(boolean isIssuer, boolean createNew) {
        JTextField txtName = new JTextField(isIssuer ? "academia_ipt" : "aluno_rafael");
        JPasswordField txtPass = new JPasswordField();
        Object[] fields = {
                "Nome da Carteira (ficheiro):", txtName,
                "Password de Encriptação (PBKDF2 + AES):", txtPass
        };
        int res = JOptionPane.showConfirmDialog(this, fields,
                (createNew ? "Criar" : "Abrir") + " Carteira " + (isIssuer ? "Emissor" : "Titular"),
                JOptionPane.OK_CANCEL_OPTION);
        if (res != JOptionPane.OK_OPTION) return;

        String label = txtName.getText().trim();
        char[] password = txtPass.getPassword();
        if (label.isEmpty() || password.length == 0) return;

        try {
            Wallet w = createNew
                    ? Wallet.createAndSave(label, WALLETS_DIR, password)
                    : Wallet.load(label, WALLETS_DIR, password);

            if (isIssuer) {
                this.issuerWallet = w;
                lblIssuerStatus.setText("Ativa: " + w.getOwnerLabel() + " | " + w.getDid());
            } else {
                this.holderWallet = w;
                lblHolderStatus.setText("Ativa: " + w.getOwnerLabel() + " | " + w.getDid());
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Falha ao " + (createNew ? "criar" : "abrir") + " carteira (password incorreta ou ficheiro inexistente):\n"
                            + ex.getMessage(),
                    "Erro de Carteira", JOptionPane.ERROR_MESSAGE);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private File chooseJsonFile(String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileFilter(new FileNameExtensionFilter("Ficheiros JSON (*.json)", "json"));
        return (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
                ? chooser.getSelectedFile() : null;
    }

    // --- Callbacks do NodeListener (sempre na EDT) ---

    @Override
    public void onLog(String message) {
        SwingUtilities.invokeLater(() -> {
            txtLogs.append(DATE_FMT.format(new Date()) + " " + message + "\n");
            txtLogs.setCaretPosition(txtLogs.getDocument().getLength());
        });
    }

    @Override
    public void onPeersChanged(Set<String> peers) {
        SwingUtilities.invokeLater(() -> {
            peersListModel.clear();
            peers.stream().sorted().forEach(peersListModel::addElement);
        });
    }

    @Override
    public void onMempoolChanged(List<Transaction> pendingTransactions) {
        SwingUtilities.invokeLater(() -> {
            mempoolTableModel.setRowCount(0);
            for (Transaction tx : pendingTransactions) {
                mempoolTableModel.addRow(new Object[]{
                        shortHash(tx.getTxId()),
                        tx.getStatus(),
                        shortHash(tx.getCredentialHash()),
                        tx.getIssuerDid()
                });
            }
        });
    }

    @Override
    public void onBlockchainChanged(List<Block> chainSnapshot) {
        SwingUtilities.invokeLater(() -> {
            this.displayedChain = new ArrayList<>(chainSnapshot);
            chainTableModel.setRowCount(0);
            for (Block b : displayedChain) {
                chainTableModel.addRow(new Object[]{
                        b.getIndex(),
                        b.getTransactions().size(),
                        b.getDifficulty(),
                        b.getNonce(),
                        shortHash(b.getMerkleRoot()),
                        shortHash(b.getPreviousHash()),
                        shortHash(b.getHash())
                });
            }
        });
    }

    private String shortHash(String h) {
        return (h != null && h.length() > 14) ? h.substring(0, 14) + "..." : h;
    }
}
