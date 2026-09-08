package com.mariageplus.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariageplus.dto.auth.LoginRequest;
import com.mariageplus.dto.auth.LoginResponse;
import com.mariageplus.dto.auth.RegisterRequest;
import com.mariageplus.security.CurrentUser;
import com.mariageplus.security.UserInfo;
import com.mariageplus.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Authentification et inscription")
public class AuthController {

    private final AuthService authService;
    private final ObjectMapper objectMapper;

    @PostMapping("/register")
    @Operation(summary = "Inscription d'un organisateur (avec création de son organisation)")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request,
                                                  HttpServletRequest httpRequest,
                                                  @RequestHeader(value = "X-Client-Platform", required = false) String platform) {
        LoginResponse response = authService.register(request);
        return withRefreshCookie(response, HttpStatus.CREATED, httpRequest, isWeb(platform));
    }

    @PostMapping("/login")
    @Operation(summary = "Connexion (email + mot de passe)")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest,
                                               @RequestHeader(value = "X-Client-Platform", required = false) String platform) {
        return withRefreshCookie(authService.login(request), HttpStatus.OK, httpRequest, isWeb(platform));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renouvellement du token d'accès via refresh token")
    public ResponseEntity<LoginResponse> refresh(@CookieValue(name = "mp_refresh", required = false) String cookieToken,
                                                 @RequestBody(required = false) String rawBody,
                                                 HttpServletRequest httpRequest,
                                                 @RequestHeader(value = "X-Client-Platform", required = false) String platform) {
        String token = cookieToken != null ? cookieToken : extractRefreshToken(rawBody);
        return withRefreshCookie(authService.refreshToken(token), HttpStatus.OK, httpRequest, isWeb(platform));
    }

    /**
     * Normalise le corps de {@code POST /auth/refresh} en acceptant le refresh token
     * sous trois formes (le format brut reste prioritaire) :
     *  1. le JWT en texte brut : {@code eyJhbGciOi...};
     *  2. un wrapper JSON : {@code {"refreshToken": "eyJhbGciOi..."}} (piège fréquent côté client) ;
     *  3. le JWT recopié avec des guillemets : {@code "eyJhbGciOi..."} ou des espaces autour.
     */
    String extractRefreshToken(String body) {
        if (body == null) {
            return null;
        }
        String trimmed = body.trim();
        // 3. retire les guillemets de recopie éventuels.
        if (trimmed.length() >= 2 && trimmed.charAt(0) == '"' && trimmed.charAt(trimmed.length() - 1) == '"') {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        // 2. si le corps est un objet JSON, on extrait le champ refreshToken / token.
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                var node = objectMapper.readTree(trimmed);
                if (node.isObject() && node.has("refreshToken")) {
                    return node.get("refreshToken").asText().trim();
                }
                if (node.isObject() && node.has("token")) {
                    return node.get("token").asText().trim();
                }
            } catch (JsonProcessingException ex) {
                // Pas un JSON valide : on retombe sur le texte brut ci-dessous.
            }
        }
        return trimmed;
    }

    @PostMapping("/logout")
    @Operation(summary = "Déconnexion (révoque les refresh tokens et invalide les JWT)")
    public ResponseEntity<Void> logout(@CurrentUser UserInfo currentUser, HttpServletRequest request) {
        if (currentUser != null) {
            authService.logout(currentUser.getId());
        }
        return ResponseEntity.noContent().header("Set-Cookie", clearRefreshCookie(request.isSecure())).build();
    }

    private ResponseEntity<LoginResponse> withRefreshCookie(LoginResponse response, HttpStatus status,
                                                             HttpServletRequest request, boolean webClient) {
        boolean secure = request.isSecure();
        ResponseCookie cookie = ResponseCookie.from("mp_refresh", response.getRefreshToken())
                .httpOnly(true)
                .secure(secure)
                .sameSite(secure ? "None" : "Lax")
                .path("/auth")
                .maxAge(7 * 24 * 60 * 60)
                .build();
        LoginResponse body = webClient
                ? new LoginResponse(response.getAccessToken(), null, response.getExpiresIn(),
                response.getTokenType(), response.getUser())
                : response;
        return ResponseEntity.status(status).header("Set-Cookie", cookie.toString()).body(body);
    }

    private boolean isWeb(String platform) {
        return "web".equalsIgnoreCase(platform);
    }

    private String clearRefreshCookie(boolean secure) {
        return ResponseCookie.from("mp_refresh", "")
                .httpOnly(true).secure(secure).sameSite(secure ? "None" : "Lax").path("/auth").maxAge(0).build().toString();
    }

    @GetMapping("/me")
    @Operation(summary = "Profil de l'utilisateur connecté")
    public ResponseEntity<?> me(@CurrentUser UserInfo currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(authService.me(currentUser.getId()));
    }
}
