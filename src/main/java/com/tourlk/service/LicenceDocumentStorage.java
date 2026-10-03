package com.tourlk.service;

import com.tourlk.exception.BadRequestException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.util.FileSignatures;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

/**
 * Validates and stores licence documents on local disk: one JPEG, PNG, WebP or
 * PDF of at most 5 MB. As with ticket attachments, the declared content type
 * must be on the allow-list <em>and</em> the leading bytes must match it, and
 * the stored name is server-generated ({@code <userId>/<uuid>.<ext>}); the
 * user's file name is never used as a path.
 */
@Slf4j
@Component
public class LicenceDocumentStorage {

    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    private static final Map<String, String> ALLOWED = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "application/pdf", "pdf");

    private static final Map<String, String> TYPE_BY_EXTENSION = Map.of(
            "jpg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp",
            "pdf", "application/pdf");

    private final Path root;

    public LicenceDocumentStorage(@Value("${app.uploads.licence-dir:uploads/licences}") String directory) {
        this.root = Paths.get(directory).toAbsolutePath().normalize();
    }

    /** Validates without writing anything. */
    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("A licence document file is required");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BadRequestException("The licence document must be 5 MB or smaller");
        }
        String contentType = normalizedType(file);
        if (!FileSignatures.matches(file, contentType)) {
            throw new BadRequestException("The file does not look like a valid " + contentType + " document");
        }
    }

    /** @return the stored path, relative to the storage root. */
    public String store(Long userId, MultipartFile file) {
        String contentType = normalizedType(file);
        String storedPath = userId + "/" + UUID.randomUUID() + "." + ALLOWED.get(contentType);
        Path target = resolve(storedPath);

        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not store the licence document", e);
        }
        return storedPath;
    }

    public Resource load(String storedPath) {
        Path path = resolve(storedPath);
        if (!Files.isReadable(path)) {
            throw new ResourceNotFoundException("The licence document is no longer available");
        }
        return new PathResource(path);
    }

    /** Content type implied by the stored extension (always one we generated). */
    public String contentTypeOf(String storedPath) {
        String extension = storedPath.substring(storedPath.lastIndexOf('.') + 1).toLowerCase();
        return TYPE_BY_EXTENSION.getOrDefault(extension, "application/octet-stream");
    }

    /** Best-effort cleanup of a replaced or orphaned file. */
    public void deleteQuietly(String storedPath) {
        if (storedPath == null) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(storedPath));
        } catch (IOException | RuntimeException e) {
            log.warn("Could not delete licence document {}", storedPath, e);
        }
    }

    private String normalizedType(MultipartFile file) {
        String declared = file.getContentType() == null ? "" : file.getContentType().toLowerCase().split(";")[0].trim();
        if (!ALLOWED.containsKey(declared)) {
            throw new BadRequestException("Only JPEG, PNG, WebP or PDF files are allowed for the licence document");
        }
        return declared;
    }

    private Path resolve(String storedPath) {
        Path resolved = root.resolve(storedPath).normalize();
        if (!resolved.startsWith(root)) {
            throw new BadRequestException("Invalid document path");
        }
        return resolved;
    }

}
