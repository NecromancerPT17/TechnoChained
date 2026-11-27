package pt.ipt.technochained.blockchain;

import pt.ipt.technochained.model.CredentialStatus;

import java.io.Serial;
import java.io.Serializable;

public record CredentialRecord(
        String credentialHash,
        String issuerDid,
        String holderDid,
        CredentialStatus status,
        int lastBlockIndex,
        long lastUpdatedAt
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
