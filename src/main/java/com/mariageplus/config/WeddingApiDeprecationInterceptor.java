package com.mariageplus.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Intercepteur de dépréciation des routes legacy /api/weddings/**.
 *
 * Le nouveau modèle « Event » (racine unifiée — voir docs/DESIGN_EVENT_AS_ROOT.md)
 * remplace progressivement les routes /api/weddings. Cet intercepteur marque
 * chaque réponse legacy avec les headers standard Deprecation / Sunset / Link,
 * et journalise un avertissement (une fois par requête) pour suivre en
 * production qui utilise encore l'ancienne API avant suppression définitive.
 *
 * Date de suppression prévue (Sunset) : 01/01/2027 — à ajuster selon la
 * bascule du front.
 */
@Slf4j
@Component
public class WeddingApiDeprecationInterceptor implements HandlerInterceptor {

    /** Date de suppression définitive des routes /api/weddings (header Sunset). */
    private static final String SUNSET_DATE = "Fri, 01 Jan 2027 00:00:00 GMT";

    private static final String DEPRECATION_MESSAGE =
            "Route /api/weddings dépréciée : utilisez /api/events (voir docs/DESIGN_EVENT_AS_ROOT.md)";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        response.setHeader("Deprecation", "true");
        response.setHeader("Sunset", SUNSET_DATE);
        response.setHeader("Link", "</api/events>; rel=\"successor-version\"");
        response.setHeader("Deprecation-Message", DEPRECATION_MESSAGE);
        log.warn("{} {} — {}", HttpMethod.valueOf(request.getMethod()), request.getRequestURI(), DEPRECATION_MESSAGE);
        return true;
    }
}
