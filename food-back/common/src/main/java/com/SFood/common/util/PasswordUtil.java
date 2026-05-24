package com.SFood.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 密码加密工具类（SHA256）
 */
public class PasswordUtil {
    
    /**
     * SHA256加密
     * @param password 明文密码
     * @return 加密后的密码
     */
    public static String sha256(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA256加密算法不可用", e);
        }
    }
    
    /**
     * 验证密码
     * @param inputPassword 输入的密码
     * @param storedPassword 存储的加密密码
     * @return 是否匹配
     */
    public static boolean verify(String inputPassword, String storedPassword) {
        return sha256(inputPassword).equals(storedPassword);
    }
}