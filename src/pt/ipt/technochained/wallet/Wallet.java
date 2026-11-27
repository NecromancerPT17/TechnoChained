package pt.ipt.technochained.wallet;

import pt.ipt.technochained.crypto.CryptoUtils;
import pt.ipt.technochained.model.Credential;
import pt.ipt.technochained.model.CredentialStatus;
import pt.ipt.technochained.model.Transaction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

public class Wallet {

    private final String ownerLabel;
    private final PublicKey publicKey;
    private final PrivateKey privateKey;
    private final String did;

    private Wallet(String ownerLabel, PublicKey publicKey, PrivateKey privateKey) {
        this.ownerLabel = ownerLabel;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.did = CryptoUtils.deriveDID(publicKey);
    }

    public static Wallet createAndSave(String ownerLabel, Path directory, char[] password)
            throws GeneralSecurityException, IOException {
        KeyPair kp = CryptoUtils.generateKeyPair();
        Wallet wallet = new Wallet(ownerLabel, kp.getPublic(), kp.getPrivate());
        wallet.saveToDirectory(directory, password);
        return wallet;
    }

    public static Wallet load(String ownerLabel, Path directory, char[] password)
            throws GeneralSecurityException, IOException {
        Path pubFile = directory.resolve(ownerLabel + ".pub");
        Path privFile = directory.resolve(ownerLabel + ".priv.enc");

        String pubBase64 = Files.readString(pubFile, StandardCharsets.UTF_8).trim();
        PublicKey pubKey = CryptoUtils.publicKeyFromBase64(pubBase64);

        byte[] encryptedPriv = Files.readAllBytes(privFile);
        byte[] pkcs8Bytes = CryptoUtils.decryptWithPassword(encryptedPriv, password);
        PrivateKey privKey = CryptoUtils.privateKeyFromBytes(pkcs8Bytes);

        return new Wallet(ownerLabel, pubKey, privKey);
    }

    private void saveToDirectory(Path directory, char[] password)
            throws IOException, GeneralSecurityException {
        Files.createDirectories(directory);
        Path pubFile = directory.resolve(ownerLabel + ".pub");
        Path privFile = directory.resolve(ownerLabel + ".priv.enc");

        Files.writeString(pubFile, CryptoUtils.publicKeyToBase64(publicKey), StandardCharsets.UTF_8);
        byte[] encryptedPriv = CryptoUtils.encryptWithPassword(privateKey.getEncoded(), password);
        Files.write(privFile, encryptedPriv);
    }

    public Transaction issueCredential(Credential credential, Path outputJsonPath)
            throws GeneralSecurityException, IOException {
        credential.sign(this.privateKey);
        credential.saveToFile(outputJsonPath);

        Transaction tx = new Transaction(
                this.publicKey,
                credential.getHolderDid(),
                credential.calculateHash(),
                CredentialStatus.ATIVO
        );
        tx.sign(this.privateKey);
        return tx;
    }

    public Transaction createStatusChangeTransaction(String holderDid, String credentialHash,
                                                     CredentialStatus newStatus)
            throws GeneralSecurityException {
        Transaction tx = new Transaction(this.publicKey, holderDid, credentialHash, newStatus);
        tx.sign(this.privateKey);
        return tx;
    }

    public String getOwnerLabel() { return ownerLabel; }
    public PublicKey getPublicKey() { return publicKey; }
    public PrivateKey getPrivateKey() { return privateKey; }
    public String getDid() { return did; }
}
