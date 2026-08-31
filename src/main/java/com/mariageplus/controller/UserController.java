package com.mariageplus.controller;

import com.mariageplus.dto.PageResponse;
import com.mariageplus.dto.user.PasswordChangeRequest;
import com.mariageplus.dto.user.UserRequest;
import com.mariageplus.dto.user.UserResponse;
import com.mariageplus.security.CurrentUser;
import com.mariageplus.security.UserInfo;
import com.mariageplus.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Gestion des utilisateurs")
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Liste paginée des utilisateurs (SUPER_ADMIN)")
    public ResponseEntity<PageResponse<UserResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(userService.getAll(page, size, sortBy, sortDir));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Créer un utilisateur (SUPER_ADMIN)")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Utilisateur par ID (SUPER_ADMIN)")
    public ResponseEntity<UserResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Modifier un utilisateur (SUPER_ADMIN)")
    public ResponseEntity<UserResponse> update(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Activer / désactiver un utilisateur (SUPER_ADMIN)")
    public ResponseEntity<UserResponse> toggleActive(@PathVariable Long id) {
        return ResponseEntity.ok(userService.toggleActive(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Supprimer un utilisateur (SUPER_ADMIN)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Profil de l'utilisateur connecté")
    public ResponseEntity<?> me(@CurrentUser UserInfo currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(userService.getById(currentUser.getId()));
    }

    @PutMapping("/me")
    @Operation(summary = "Modifier son profil")
    public ResponseEntity<?> updateMe(@CurrentUser UserInfo currentUser,
                                      @Valid @RequestBody UserRequest request) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(userService.updateProfile(currentUser.getId(), request));
    }

    @PutMapping("/me/password")
    @Operation(summary = "Changer son mot de passe")
    public ResponseEntity<?> changePassword(@CurrentUser UserInfo currentUser,
                                            @Valid @RequestBody PasswordChangeRequest request) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        userService.changePassword(currentUser.getId(), request.getOldPassword(), request.getNewPassword());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/me/avatar")
    @Operation(summary = "Uploader sa photo de profil (JPEG, PNG, GIF, WebP — max 2 Mo)")
    public ResponseEntity<?> uploadAvatar(@CurrentUser UserInfo currentUser,
                                          @RequestParam("file") MultipartFile file) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            userService.updateAvatar(currentUser.getId(), file.getBytes());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        } catch (java.io.IOException e) {
            return ResponseEntity.internalServerError().body(java.util.Map.of("error", "Impossible de lire le fichier"));
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/avatar")
    @Operation(summary = "Photo de profil de l'utilisateur connecté")
    public ResponseEntity<byte[]> getAvatar(@CurrentUser UserInfo currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        byte[] image = userService.getAvatar(currentUser.getId());
        if (image == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(detectMediaType(image)))
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePrivate())
                .body(image);
    }

    @DeleteMapping("/me/avatar")
    @Operation(summary = "Supprimer sa photo de profil")
    public ResponseEntity<?> deleteAvatar(@CurrentUser UserInfo currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        userService.deleteAvatar(currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    private String detectMediaType(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) return "image/jpeg";
        if (b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P') return "image/png";
        if (b.length >= 3 && b[0] == 'G' && b[1] == 'I') return "image/gif";
        return "image/webp";
    }
}