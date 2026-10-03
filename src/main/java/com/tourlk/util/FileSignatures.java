package com.tourlk.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Magic-byte check shared by the upload validators: the declared content type
 * is only trusted if the file's leading bytes agree with it, so a renamed
 * executable is rejected.
 */
public final class FileSignatures {

    private FileSignatures() {
    }

    public static boolean matches(MultipartFile file, String contentType) {
        byte[] head = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            return false;
        }
        return switch (contentType) {
            case "image/jpeg" -> read >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
            case "image/png" -> read >= 8 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G'
                    && head[4] == 0x0D && head[5] == 0x0A && head[6] == 0x1A && head[7] == 0x0A;
            case "image/gif" -> read >= 6 && startsWith(head, "GIF87a") || read >= 6 && startsWith(head, "GIF89a");
            case "image/webp" -> read >= 12 && startsWith(head, "RIFF") && head[8] == 'W' && head[9] == 'E'
                    && head[10] == 'B' && head[11] == 'P';
            case "application/pdf" -> read >= 5 && startsWith(head, "%PDF-");
            default -> false;
        };
    }

    private static boolean startsWith(byte[] bytes, String prefix) {
        byte[] expected = prefix.getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < expected.length; i++) {
            if (bytes[i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

}
