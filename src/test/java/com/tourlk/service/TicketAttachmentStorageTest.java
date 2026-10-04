package com.tourlk.service;

import com.tourlk.exception.BadRequestException;
import com.tourlk.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link TicketAttachmentStorage} against a temp directory:
 * the 3-file / 5 MB / images-and-PDF-only rules, content sniffing, and safe storage.
 */
class TicketAttachmentStorageTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
    private static final byte[] GIF = "GIF89a....".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP = "RIFF....WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    Path tempDir;

    private TicketAttachmentStorage storage;

    @BeforeEach
    void setUp() {
        storage = new TicketAttachmentStorage(tempDir.toString());
    }

    private MultipartFile file(String name, String type, byte[] bytes) {
        return new MockMultipartFile("files", name, type, bytes);
    }

    // ------------------------------------------------------------------
    // validate
    // ------------------------------------------------------------------

    @Test
    void validate_acceptsImagesAndPdf() {
        List<MultipartFile> files = List.of(
                file("a.png", "image/png", PNG), file("b.jpg", "image/jpeg", JPEG), file("c.pdf", "application/pdf", PDF));

        assertThat(storage.validate(files)).hasSize(3);
    }

    @Test
    void validate_acceptsGifAndWebp() {
        assertThat(storage.validate(List.of(file("a.gif", "image/gif", GIF), file("b.webp", "image/webp", WEBP))))
                .hasSize(2);
    }

    @Test
    void validate_dropsEmptyParts_soAnUntouchedFileInputIsNotAnError() {
        assertThat(storage.validate(List.of(file("", "application/octet-stream", new byte[0])))).isEmpty();
        assertThat(storage.validate(null)).isEmpty();
    }

    @Test
    void validate_exactlyThreeFiles_isAllowed_fourIsRejected() {
        MultipartFile one = file("a.png", "image/png", PNG);

        assertThat(storage.validate(List.of(one, one, one))).hasSize(3);
        assertThatThrownBy(() -> storage.validate(List.of(one, one, one, one)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("at most 3");
    }

    @Test
    void validate_fileOverFiveMegabytes_isRejected_exactlyFiveIsAllowed() {
        byte[] exactly = new byte[5 * 1024 * 1024];
        System.arraycopy(PDF, 0, exactly, 0, PDF.length);
        byte[] over = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PDF, 0, over, 0, PDF.length);

        assertThat(storage.validate(List.of(file("ok.pdf", "application/pdf", exactly)))).hasSize(1);
        assertThatThrownBy(() -> storage.validate(List.of(file("big.pdf", "application/pdf", over))))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("5 MB");
    }

    @Test
    void validate_disallowedTypes_areRejected() {
        for (String type : List.of("application/x-msdownload", "text/html", "image/svg+xml", "application/zip",
                "text/plain", "application/octet-stream")) {
            assertThatThrownBy(() -> storage.validate(List.of(file("x.bin", type, PNG))))
                    .as(type).isInstanceOf(BadRequestException.class);
        }
    }

    @Test
    void validate_missingContentType_isRejected() {
        assertThatThrownBy(() -> storage.validate(List.of(file("x.png", null, PNG))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void validate_contentNotMatchingDeclaredType_isRejected() {
        // An HTML/script payload renamed and declared as an image or PDF.
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

        for (String type : List.of("image/png", "image/jpeg", "image/gif", "image/webp", "application/pdf")) {
            assertThatThrownBy(() -> storage.validate(List.of(file("evil", type, html))))
                    .as(type).isInstanceOf(BadRequestException.class).hasMessageContaining("valid");
        }
        assertThatThrownBy(() -> storage.validate(List.of(file("a.jpg", "image/jpeg", PNG))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void validate_contentTypeWithParameters_isNormalised() {
        assertThat(storage.validate(List.of(file("a.pdf", "Application/PDF; charset=binary", PDF)))).hasSize(1);
    }

    // ------------------------------------------------------------------
    // store / load / delete
    // ------------------------------------------------------------------

    @Test
    void store_writesUnderServerGeneratedPath_notTheUsersFileName() throws IOException {
        TicketAttachmentStorage.StoredFile stored = storage.store(7L, file("holiday photo.png", "image/png", PNG));

        assertThat(stored.storedPath()).matches("7/[0-9a-f-]{36}\\.png");
        assertThat(stored.fileName()).isEqualTo("holiday photo.png");
        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.sizeBytes()).isEqualTo(PNG.length);
        assertThat(Files.readAllBytes(tempDir.resolve(stored.storedPath()))).isEqualTo(PNG);
    }

    @Test
    void store_pathTraversalInFileName_isStrippedAndNeverUsedAsPath() {
        TicketAttachmentStorage.StoredFile stored =
                storage.store(7L, file("../../../etc/passwd.png", "image/png", PNG));

        assertThat(stored.fileName()).isEqualTo("passwd.png");
        assertThat(tempDir.resolve(stored.storedPath()).normalize()).startsWith(tempDir);
        try (Stream<Path> all = Files.walk(tempDir)) {
            assertThat(all.filter(Files::isRegularFile)).hasSize(1);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void store_windowsStylePathAndControlCharsInFileName_areSanitised() {
        TicketAttachmentStorage.StoredFile stored =
                storage.store(7L, file("C:\\Users\\me\\scan\"1\n.pdf", "application/pdf", PDF));

        assertThat(stored.fileName()).isEqualTo("scan1.pdf");
    }

    @Test
    void store_blankFileName_fallsBackToGenericName() {
        assertThat(storage.store(7L, file("", "image/png", PNG)).fileName()).isEqualTo("attachment.png");
    }

    @Test
    void load_returnsTheStoredBytes() throws IOException {
        TicketAttachmentStorage.StoredFile stored = storage.store(7L, file("a.pdf", "application/pdf", PDF));

        assertThat(storage.load(stored.storedPath()).getInputStream().readAllBytes()).isEqualTo(PDF);
    }

    @Test
    void load_missingFile_throwsResourceNotFound() {
        assertThatThrownBy(() -> storage.load("7/missing.png")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void load_pathOutsideTheUploadsDirectory_isRejected() {
        assertThatThrownBy(() -> storage.load("../outside.txt")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> storage.load("7/../../outside.txt")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void deleteQuietly_removesTheFile_andIgnoresMissingOnes() {
        TicketAttachmentStorage.StoredFile stored = storage.store(7L, file("a.png", "image/png", PNG));

        storage.deleteQuietly(stored.storedPath());
        storage.deleteQuietly(stored.storedPath());
        storage.deleteQuietly("../outside.txt");

        assertThat(Files.exists(tempDir.resolve(stored.storedPath()))).isFalse();
    }

}
