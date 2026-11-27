package pt.ipt.technochained.model;

import pt.ipt.technochained.crypto.CryptoUtils;

import java.io.Serial;
import java.io.Serializable;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Objects;

public class Transaction implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String txId;
    private final String issuerDid;
    private final String issuerPublicKeyBase64;
    private final String holderDid;
    private final String credentialHash;
    private final CredentialStatus status;
    private final long timestamp;
    private String signature;

    public Transaction(PublicKey issuerPublicKey, String holderDid,
                       String credentialHash, CredentialStatus status) {
        this.issuerPublicKeyBase64 = CryptoUtils.publicKeyToBase64(issuerPublicKey);
        this.issuerDid = CryptoUtils.deriveDID(issuerPublicKey);
        this.holderDid = Objects.requireNonNull(holderDid);
        this.credentialHash = Objects.requireNonNull(credentialHash);
        this.status = Objects.requireNonNull(status);
        this.timestamp = System.currentTimeMillis();
        this.txId = CryptoUtils.sha256Hex(getSigningPayload());
    }

    public String getSigningPayload() {
        return String.join("|",
                issuerDid,
                issuerPublicKeyBase64,
                holderDid,
                credentialHash,
                status.name(),
                String.valueOf(timestamp)
        );
    }

    public void sign(PrivateKey issuerPrivateKey) throws GeneralSecurityException {
        this.signature = CryptoUtils.signToBase64(getSigningPayload(), issuerPrivateKey);
    }

    public boolean isValid() {
        if (txId == null || issuerDid == null || issuerPublicKeyBase64 == null
                || holderDid == null || credentialHash == null || status == null
                || signature == null) {
            return false;
        }
        try {
            if (!txId.equals(CryptoUtils.sha256Hex(getSigningPayload()))) {
                return false;
            }
            PublicKey pubKey = CryptoUtils.publicKeyFromBase64(issuerPublicKeyBase64);
            if (!CryptoUtils.deriveDID(pubKey).equals(issuerDid)) {
                return false;
            }
            return CryptoUtils.verifyBase64Signature(getSigningPayload(), signature, pubKey);
        } catch (Exception e) {
            return false;
        }
    }

    public String getTxId() { return txId; }
    public String getIssuerDid() { return issuerDid; }
    public String getIssuerPublicKeyBase64() { return issuerPublicKeyBase64; }
    public String getHolderDid() { return holderDid; }
    public String getCredentialHash() { return credentialHash; }
    public CredentialStatus getStatus() { return status; }
    public long getTimestamp() { return timestamp; }
    public String getSignature() { return signature; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transaction that)) return false;
        return Objects.equals(txId, that.txId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(txId);
    }

    @Override
    public String toString() {
        return "Tx[" + txId.substring(0, 8) + "... | " + status + " | hash="
                + credentialHash.substring(0, 8) + "... | emissor=" + issuerDid + "]";
    }
}
