package pt.ipt.technochained.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.*;
import javax.crypto.spec.*;

public final class CryptoUtils {

    // Configuração Central (troque para RSA / 2048 / SHA256withRSA se preferir)
    public static final String KEY_ALGORITHM = "EC";
    public static final int KEY_SIZE = 256;
    public static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";
    public static final String HASH_ALGORITHM = "SHA-256";

    // Parâmetros PBE/AES para proteção da carteira local
    private static final String KDF_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String CIPHER_TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final int PBKDF2_ITERATIONS = 65536;
    private static final int AES_KEY_BITS = 256;
    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 16;

    private CryptoUtils() {}

    // --- 1. INTEGRIDADE: HASH SHA-256 E CODIFICAÇÕES ---

    public static byte[] sha256Bytes(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);
            return md.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Algoritmo SHA-256 indisponível", e);
        }
    }

    public static String sha256Hex(byte[] data) {
        return HexFormat.of().formatHex(sha256Bytes(data));
    }

    public static String sha256Hex(String text) {
        return sha256Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String toBase64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    public static byte[] fromBase64(String base64) {
        return Base64.getDecoder().decode(base64);
    }

    // --- 2. IDENTIDADE: CHAVES ASSIMÉTRICAS E DIDs ---

    public static KeyPair generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(KEY_ALGORITHM);
        keyGen.initialize(KEY_SIZE, new SecureRandom());
        return keyGen.generateKeyPair();
    }

    public static String deriveDID(PublicKey publicKey) {
        String hash = sha256Hex(publicKey.getEncoded());
        return "did:technochained:" + hash.substring(0, 16);
    }

    public static String publicKeyToBase64(PublicKey publicKey) {
        return toBase64(publicKey.getEncoded());
    }

    public static PublicKey publicKeyFromBase64(String base64Key) throws GeneralSecurityException {
        byte[] encoded = fromBase64(base64Key);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(encoded);
        KeyFactory kf = KeyFactory.getInstance(KEY_ALGORITHM);
        return kf.generatePublic(spec);
    }

    public static PrivateKey privateKeyFromBytes(byte[] pkcs8Bytes) throws GeneralSecurityException {
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(pkcs8Bytes);
        KeyFactory kf = KeyFactory.getInstance(KEY_ALGORITHM);
        return kf.generatePrivate(spec);
    }

    // --- 3. AUTENTICIDADE E NÃO-REPÚDIO: ASSINATURAS DIGITAIS ---

    public static String signToBase64(byte[] data, PrivateKey privateKey) throws GeneralSecurityException {
        Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
        sig.initSign(privateKey);
        sig.update(data);
        return toBase64(sig.sign());
    }

    public static String signToBase64(String text, PrivateKey privateKey) throws GeneralSecurityException {
        return signToBase64(text.getBytes(StandardCharsets.UTF_8), privateKey);
    }

    public static boolean verifyBase64Signature(byte[] data, String signatureBase64, PublicKey publicKey) {
        try {
            Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
            sig.initVerify(publicKey);
            sig.update(data);
            return sig.verify(fromBase64(signatureBase64));
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean verifyBase64Signature(String text, String signatureBase64, PublicKey publicKey) {
        return verifyBase64Signature(text.getBytes(StandardCharsets.UTF_8), signatureBase64, publicKey);
    }

    // --- 4. CONFIDENCIALIDADE: PROTEÇÃO DE CARTEIRA (PBKDF2 + AES) ---

    public static byte[] encryptWithPassword(byte[] plainData, char[] password) throws GeneralSecurityException {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(salt);
        random.nextBytes(iv);

        SecretKey aesKey = deriveAesKey(password, salt);

        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, new IvParameterSpec(iv));
        byte[] cipherText = cipher.doFinal(plainData);

        ByteBuffer buffer = ByteBuffer.allocate(SALT_LENGTH + IV_LENGTH + cipherText.length);
        buffer.put(salt);
        buffer.put(iv);
        buffer.put(cipherText);
        return buffer.array();
    }

    public static byte[] decryptWithPassword(byte[] encryptedBlob, char[] password) throws GeneralSecurityException {
        if (encryptedBlob.length <= SALT_LENGTH + IV_LENGTH) {
            throw new IllegalArgumentException("Ficheiro cifrado inválido ou corrompido.");
        }
        ByteBuffer buffer = ByteBuffer.wrap(encryptedBlob);
        byte[] salt = new byte[SALT_LENGTH];
        byte[] iv = new byte[IV_LENGTH];
        buffer.get(salt);
        buffer.get(iv);
        byte[] cipherText = new byte[buffer.remaining()];
        buffer.get(cipherText);

        SecretKey aesKey = deriveAesKey(password, salt);

        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, aesKey, new IvParameterSpec(iv));
        return cipher.doFinal(cipherText);
    }

    private static SecretKey deriveAesKey(char[] password, byte[] salt) throws GeneralSecurityException {
        KeySpec spec = new PBEKeySpec(password, salt, PBKDF2_ITERATIONS, AES_KEY_BITS);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KDF_ALGORITHM);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }
}
