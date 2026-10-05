package com.tourlk.controller;

import com.tourlk.dto.AuthResponseDto;
import com.tourlk.dto.ChangePasswordRequestDto;
import com.tourlk.dto.DeactivateAccountRequestDto;
import com.tourlk.dto.LicenceDocumentDownload;
import com.tourlk.dto.LicenceSubmitRequestDto;
import com.tourlk.dto.RejectLicenceRequestDto;
import com.tourlk.dto.UpdateProfileRequestDto;
import com.tourlk.dto.UserResponseDto;
import com.tourlk.entity.User;
import com.tourlk.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Self-service profile ({@code /me}, any signed-in user) plus ADMIN-only
 * user management. {@code /me} is a literal path, so it always wins over
 * {@code /{id}}.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Profile editing and admin user management")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponseDto> me(Authentication authentication) {
        return ResponseEntity.ok(userService.getProfile(currentUser(authentication)));
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AuthResponseDto> updateMe(@Valid @RequestBody UpdateProfileRequestDto request,
                                                     Authentication authentication) {
        return ResponseEntity.ok(userService.updateProfile(currentUser(authentication), request));
    }

    @PutMapping("/me/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequestDto request,
                                                Authentication authentication) {
        userService.changePassword(currentUser(authentication), request);
        return ResponseEntity.ok().build();
    }

    /** Self-service deactivation; the caller must re-enter their password. */
    @PutMapping("/me/deactivate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deactivateMe(@Valid @RequestBody DeactivateAccountRequestDto request,
                                              Authentication authentication) {
        userService.deactivateOwnAccount(currentUser(authentication), request.getPassword());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponseDto>> list(@RequestParam(required = false) String search) {
        return ResponseEntity.ok(userService.searchUsers(search));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponseDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponseDto> deactivate(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(userService.deactivateUser(id, currentUser(authentication)));
    }

    @PutMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponseDto> reactivate(@PathVariable Long id) {
        return ResponseEntity.ok(userService.reactivateUser(id));
    }

    @PutMapping("/{id}/promote-admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponseDto> promoteToAdmin(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(userService.promoteToAdmin(id, currentUser(authentication)));
    }

    @PutMapping("/{id}/demote-admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponseDto> demoteAdmin(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(userService.demoteAdmin(id, currentUser(authentication)));
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        userService.deleteUser(id, currentUser(authentication));
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------
    // Licence verification (GUIDE / DRIVER submit, ADMIN reviews)
    // ------------------------------------------------------------------

    /** Multipart: licenceNumber, licenceExpiry and a {@code file} (JPEG/PNG/WebP/PDF, max 5 MB). */
    @PutMapping(value = "/me/licence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('GUIDE','DRIVER')")
    public ResponseEntity<UserResponseDto> submitLicence(@Valid @ModelAttribute LicenceSubmitRequestDto request,
                                                          @RequestPart(value = "file", required = false) MultipartFile file,
                                                          Authentication authentication) {
        return ResponseEntity.ok(userService.submitLicence(currentUser(authentication), request, file));
    }

    /** Owner or ADMIN only; images and PDFs are served inline. */
    @GetMapping("/{id}/licence-document")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> licenceDocument(@PathVariable Long id, Authentication authentication) {
        LicenceDocumentDownload download = userService.getLicenceDocument(id, currentUser(authentication));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(download.fileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(download.resource());
    }

    @GetMapping("/licences/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponseDto>> pendingLicences() {
        return ResponseEntity.ok(userService.getPendingLicences());
    }

    @PutMapping("/{id}/licence/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponseDto> verifyLicence(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(userService.verifyLicence(id, currentUser(authentication)));
    }

    @PutMapping("/{id}/licence/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponseDto> rejectLicence(@PathVariable Long id,
                                                          @Valid @RequestBody RejectLicenceRequestDto request,
                                                          Authentication authentication) {
        return ResponseEntity.ok(userService.rejectLicence(id, request.getReason(), currentUser(authentication)));
    }

    private User currentUser(Authentication authentication) {
        return userService.getByEmail(authentication.getName());
    }

}
