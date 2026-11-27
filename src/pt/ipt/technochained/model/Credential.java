package pt.ipt.technochained.model;

import pt.ipt.technochained.crypto.CryptoUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.*;

public class Credential {

    private String holderName;
    private String holderContact;
    private String holderBio;

    private String courseName;
    private List<String> completedModules;
    private String grade;
    private String projectReference;

    private String issuerDid;
    private String issuerName;
    private String issuerPublicKeyBase64;
    private String holderDid;
    private long issuedAt;

    private String issuerSignature;

    public Credential(String holderName, String holderContact, String holderBio,
                      String courseName, List<String> completedModules, String grade,
                      String projectReference, String issuerName,
                      PublicKey issuerPublicKey, String holderDid) {
        this.holderName = holderName;
        this.holderContact = holderContact;
        this.holderBio = holderBio;
        this.courseName = courseName;
        this.completedModules = (completedModules != null) ? new ArrayList<>(completedModules) : new ArrayList<>();
        this.grade = grade;
        this.projectReference = projectReference;
        this.issuerName = issuerName;
        this.issuerPublicKeyBase64 = CryptoUtils.publicKeyToBase64(issuerPublicKey);
        this.issuerDid = CryptoUtils.deriveDID(issuerPublicKey);
        this.holderDid = holderDid;
        this.issuedAt = System.currentTimeMillis();
    }

    private Credential() {}

    public String getCanonicalPayload() {
        String modulesJoined = (completedModules == null) ? "" : String.join(";", completedModules);
        return String.join("|",
                safe(holderName),
                safe(holderContact),
                safe(holderBio),
                safe(courseName),
                modulesJoined,
                safe(grade),
                safe(projectReference),
                safe(issuerDid),
                safe(issuerName),
                safe(issuerPublicKeyBase64),
                safe(holderDid),
                String.valueOf(issuedAt)
        );
    }

    public void sign(PrivateKey issuerPrivateKey) throws GeneralSecurityException {
        this.issuerSignature = CryptoUtils.signToBase64(getCanonicalPayload(), issuerPrivateKey);
    }

    public String calculateHash() {
        if (issuerSignature == null || issuerSignature.isBlank()) {
            throw new IllegalStateException("A credencial tem de ser assinada antes de calcular o hash.");
        }
        return CryptoUtils.sha256Hex(getCanonicalPayload() + "|" + issuerSignature);
    }

    public boolean verifySignature() {
        if (issuerPublicKeyBase64 == null || issuerSignature == null || issuerDid == null) {
            return false;
        }
        try {
            PublicKey pubKey = CryptoUtils.publicKeyFromBase64(issuerPublicKeyBase64);
            if (!CryptoUtils.deriveDID(pubKey).equals(issuerDid)) {
                return false;
            }
            return CryptoUtils.verifyBase64Signature(getCanonicalPayload(), issuerSignature, pubKey);
        } catch (Exception e) {
            return false;
        }
    }

    public String toJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("holderName", holderName);
        map.put("holderContact", holderContact);
        map.put("holderBio", holderBio);
        map.put("courseName", courseName);
        map.put("completedModules", completedModules != null ? completedModules : List.of());
        map.put("grade", grade);
        map.put("projectReference", projectReference);
        map.put("issuerDid", issuerDid);
        map.put("issuerName", issuerName);
        map.put("issuerPublicKeyBase64", issuerPublicKeyBase64);
        map.put("holderDid", holderDid);
        map.put("issuedAt", issuedAt);
        map.put("issuerSignature", issuerSignature);
        return JsonUtils.toPrettyJson(map);
    }

    public static Credential fromJson(String json) {
        Map<String, Object> map = JsonUtils.parseObject(json);
        Credential c = new Credential();
        c.holderName = asString(map.get("holderName"));
        c.holderContact = asString(map.get("holderContact"));
        c.holderBio = asString(map.get("holderBio"));
        c.courseName = asString(map.get("courseName"));
        c.completedModules = asStringList(map.get("completedModules"));
        c.grade = asString(map.get("grade"));
        c.projectReference = asString(map.get("projectReference"));
        c.issuerDid = asString(map.get("issuerDid"));
        c.issuerName = asString(map.get("issuerName"));
        c.issuerPublicKeyBase64 = asString(map.get("issuerPublicKeyBase64"));
        c.holderDid = asString(map.get("holderDid"));
        Object ts = map.get("issuedAt");
        c.issuedAt = (ts instanceof Number n) ? n.longValue() : 0L;
        c.issuerSignature = asString(map.get("issuerSignature"));
        return c;
    }

    public void saveToFile(Path path) throws IOException {
        Files.writeString(path, toJson(), StandardCharsets.UTF_8);
    }

    public static Credential loadFromFile(Path path) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        return fromJson(json);
    }

    private static String asString(Object o) {
        return (o == null) ? null : o.toString();
    }

    private static List<String> asStringList(Object o) {
        List<String> list = new ArrayList<>();
        if (o instanceof List<?> raw) {
            for (Object item : raw) {
                if (item != null) list.add(item.toString());
            }
        }
        return list;
    }

    private String safe(String s) {
        return (s == null) ? "" : s;
    }

    public String getHolderName() { return holderName; }
    public String getHolderContact() { return holderContact; }
    public String getHolderBio() { return holderBio; }
    public String getCourseName() { return courseName; }
    public List<String> getCompletedModules() { return completedModules; }
    public String getGrade() { return grade; }
    public String getProjectReference() { return projectReference; }
    public String getIssuerDid() { return issuerDid; }
    public String getIssuerName() { return issuerName; }
    public String getIssuerPublicKeyBase64() { return issuerPublicKeyBase64; }
    public String getHolderDid() { return holderDid; }
    public long getIssuedAt() { return issuedAt; }
    public String getIssuerSignature() { return issuerSignature; }
}
