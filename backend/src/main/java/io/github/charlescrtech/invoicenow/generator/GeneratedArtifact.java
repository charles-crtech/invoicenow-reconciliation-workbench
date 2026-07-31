package io.github.charlescrtech.invoicenow.generator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class GeneratedArtifact {

    private final String name;
    private final byte[] content;
    private final String sha256;

    private GeneratedArtifact(String name, byte[] content) {
        this.name = Objects.requireNonNull(name, "artifact name must not be null");
        this.content = Objects.requireNonNull(content, "artifact content must not be null").clone();
        this.sha256 = sha256(this.content);
    }

    public static GeneratedArtifact utf8(String name, String content) {
        Objects.requireNonNull(content, "artifact text must not be null");
        return new GeneratedArtifact(name, content.getBytes(StandardCharsets.UTF_8));
    }

    public String name() {
        return name;
    }

    public byte[] content() {
        return content.clone();
    }

    public String utf8Content() {
        return new String(content, StandardCharsets.UTF_8);
    }

    public int byteCount() {
        return content.length;
    }

    public String sha256() {
        return sha256;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java runtime does not provide SHA-256", exception);
        }
    }
}
