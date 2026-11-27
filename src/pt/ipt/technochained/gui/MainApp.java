package pt.ipt.technochained.gui;

import pt.ipt.technochained.network.P2PNode;

import javax.swing.*;

public class MainApp {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            String host = "localhost";
            int port = 10001;

            if (args.length >= 2) {
                host = args[0].trim();
                port = Integer.parseInt(args[1].trim());
            } else {
                JTextField txtHost = new JTextField(host);
                JTextField txtPort = new JTextField(String.valueOf(port));
                Object[] inputs = {
                        "Endereço IP / Host deste Nó (use localhost na mesma máquina):", txtHost,
                        "Porto RMI livre (ex.: 10001, 10002, 10003):", txtPort
                };
                int choice = JOptionPane.showConfirmDialog(null, inputs,
                        "Arrancar Nó TechnoChained (Java RMI)", JOptionPane.OK_CANCEL_OPTION);
                if (choice != JOptionPane.OK_OPTION) {
                    System.exit(0);
                }
                host = txtHost.getText().trim();
                try {
                    port = Integer.parseInt(txtPort.getText().trim());
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(null, "Porto inválido.", "Erro", JOptionPane.ERROR_MESSAGE);
                    System.exit(1);
                }
            }

            try {
                P2PNode node = new P2PNode(host, port);
                node.start();
                MainFrame frame = new MainFrame(node, host);
                frame.setVisible(true);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null,
                        "Erro ao iniciar o nó RMI no porto " + port + " (verifique se o porto já está em uso):\n"
                                + ex.getMessage(),
                        "Erro de Arranque", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}
