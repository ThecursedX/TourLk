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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Validates and stores ticket attachments on local disk.
 * <p>
 * Rules: at most {@value #MAX_FILES} files per message, each at most 5 MB,
 * and only images (JPEG/PNG/GIF/WebP) or PDF. The declared content type
 * must be on the allow-list <em>and</em> the file's leading bytes must match
 * it, so a renamed executable is rejected. Files are stored under a
 * server-generated name ({@code <ticketId>/<uuid>.<ext>}); the user's file
 * name is never used as a path, so there is no path traversal.
 */
@Slf4j
@Component
public class TicketAttachmentStorage {

    public static final int MAX_FILES = 3;
    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    /** Allowed content types -> the extension stored on disk. */
    private static final Map<String, String> ALLOWED = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/gif", "gif",
            "image/webp", "webp",
            "application/pdf", "pdf");

    /** A file written to disk, not yet linked to a ticket reply. */
    public record StoredFile(String storedPath, String fileName, String contentType, long sizeBytes) {
    }

    private final Path root;

    public TicketAttachmentStorage(@Value("${app.uploads.ticket-dir:uploads/tickets}") String directory) {
        this.root = Paths.get(directory).toAbsolutePath().normalize();
    }

    /**
     * Drops empty file parts (browsers send one for an untouched file input)
     * and validates the rest, without writing anything.
     *
     * @return the non-empty files, in order
     * @throws BadRequestException on too many files, an oversized file, or a disallowed type
     */
    public List<MultipartFile> validate(List<MultipartFile> files) {
        List<MultipartFile> present = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    present.add(file);
                }
            }
        }

        if (present.size() > MAX_FILES) {
            throw new BadRequestException("You can attach at most " + MAX_FILES + " files per message");
        }
        for (MultipartFile file : present) {
            validateOne(file);
        }
        return present;
    }

    public StoredFile store(Long ticketId, MultipartFile file) {
        String contentType = normalizedType(file);
        String storedPath = ticketId + "/" + UUID.randomUUID() + "." + ALLOWED.get(contentType);
        Path target = resolve(storedPath);

        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not store the attachment", e);
        }
        return new StoredFile(storedPath, displayName(file.getOriginalFilename(), contentType),
                contentType, file.getSize());
    }

    public Resource load(String storedPath) {
        Path path = resolve(storedPath);
        if (!Files.isReadable(path)) {
            throw new ResourceNotFoundException("The attached file is no longer available");
        }
        return new PathResource(path);
    }

    /** Best-effort cleanup, used when the database write that should reference the file fails. */
    public void deleteQuietly(String storedPath) {
        try {
            Files.deleteIfExists(resolve(storedPath));
        } catch (IOException | RuntimeException e) {
            log.warn("Could not delete orphaned attachment {}", storedPath, e);
        }
    }

    private void validateOne(MultipartFile file) {
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BadRequestException("'" + safeName(file) + "' is larger than 5 MB");
        }
        String contentType = normalizedType(file);
        if (!FileSignatures.matches(file, contentType)) {
            throw new BadRequestException("'" + safeName(file) + "' does not look like a valid " + contentType + " file");
        }
    }

    private String normalizedType(MultipartFile file) {
        String declared = file.getContentType() == null ? "" : file.getContentType().toLowerCase().split(";")[0].trim();
        if (!ALLOWED.containsKey(declared)) {
            throw new BadRequestException("'" + safeName(file) + "' is not allowed: only images (JPEG, PNG, GIF, WebP) and PDF files can be attached");
        }
        return declared;
    }

    private Path resolve(String storedPath) {
        Path resolved = root.resolve(storedPath).normalize();
        if (!resolved.startsWith(root)) {
            throw new BadRequestException("Invalid attachment path");
        }
        return resolved;
    }

    /** Original name reduced to a plain, bounded file name; falls back to "attachment.<ext>". */
    private String displayName(String original, String contentType) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (name.isEmpty()) {
            name = "attachment." + ALLOWED.get(contentType);
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private String safeName(MultipartFile file) {
        return displayName(file.getOriginalFilename(), "application/pdf");
    }

}
