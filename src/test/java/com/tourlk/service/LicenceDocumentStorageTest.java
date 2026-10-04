package com.tourlk.service;

import com.tourlk.exception.BadRequestException;
import com.tourlk.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for {@link LicenceDocumentStorage}: type allow-list, magic bytes, 5 MB cap, safe storage. */
class LicenceDocumentStorageTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
    private static final byte[] WEBP = "RIFF....WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] GIF = "GIF89a....".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    Path tempDir;

    private LicenceDocumentStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LicenceDocumentStorage(tempDir.toString());
    }

    private MultipartFile file(String name, String type, byte[] bytes) {
        return new MockMultipartFile("file", name, type, bytes);
    }

    @Test
    void validate_acceptsJpegPngWebpAndPdf() {
        assertThatCode(() -> {
            storage.validate(file("a.jpg", "image/jpeg", JPEG));
            storage.validate(file("a.png", "image/png", PNG));
            storage.validate(file("a.webp", "image/webp", WEBP));
            storage.validate(file("a.pdf", "application/pdf", PDF));
        }).doesNotThrowAnyException();
    }

    @Test
    void validate_missingFile_isRejected() {
        assertThatThrownBy(() -> storage.validate(null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> storage.validate(file("", "application/pdf", new byte[0])))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void validate_wrongFileType_isRejected() {
        assertThatThrownBy(() -> storage.validate(file("notes.txt", "text/plain", "hello".getBytes())))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("JPEG, PNG, WebP or PDF");
        assertThatThrownBy(() -> storage.validate(file("a.gif", "image/gif", GIF)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void validate_oversizedFile_isRejected() {
        byte[] big = new byte[(int) LicenceDocumentStorage.MAX_FILE_BYTES + 1];
        System.arraycopy(PDF, 0, big, 0, PDF.length);

        assertThatThrownBy(() -> storage.validate(file("big.pdf", "application/pdf", big)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("5 MB");
    }

    @Test
    void validate_renamedExecutable_isRejectedBySignature() {
        byte[] exe = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0, 4, 0, 0, 0};

        assertThatThrownBy(() -> storage.validate(file("licence.pdf", "application/pdf", exe)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not look like");
        assertThatThrownBy(() -> storage.validate(file("licence.png", "image/png", exe)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void store_usesServerGeneratedName_ignoringTheUsersFileName() {
        String stored = storage.store(7L, file("../../evil.exe", "application/pdf", PDF));

        assertThat(stored).matches("7/[0-9a-f-]{36}\\.pdf");
        assertThat(Files.exists(tempDir.resolve(stored))).isTrue();
        assertThat(storage.contentTypeOf(stored)).isEqualTo("application/pdf");
    }

    @Test
    void load_pathTraversal_isRejected() {
        assertThatThrownBy(() -> storage.load("../secret.txt")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void load_missingFile_isNotFound() {
        assertThatThrownBy(() -> storage.load("7/missing.pdf")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteQuietly_removesTheFile_andToleratesNull() {
        String stored = storage.store(7L, file("a.png", "image/png", PNG));

        storage.deleteQuietly(stored);
        storage.deleteQuietly(null);

        assertThat(Files.exists(tempDir.resolve(stored))).isFalse();
    }

}
