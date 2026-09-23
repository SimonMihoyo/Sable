package me.kirara.sable.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 摘要工具 — 用于审计日志哈希链、文件校验等场景。
 */
public final class DigestUtil {

    /** 哈希链首条日志的 prev_hash 占位值（全零 64 位）。 */
    public static final String GENESIS_HASH = "0".repeat(64);

    private DigestUtil() {
    }

    /**
     * 计算 SHA-256 十六进制摘要。
     */
    public static String sha256(String raw) {
        byte[] bytes = raw == null ? new byte[0] : raw.getBytes(StandardCharsets.UTF_8);
        return sha256(bytes);
    }

    /**
     * 计算 SHA-256 十六进制摘要。
     */
    public static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
