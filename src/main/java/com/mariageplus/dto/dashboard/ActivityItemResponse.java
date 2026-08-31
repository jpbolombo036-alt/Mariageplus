package com.mariageplus.dto.dashboard;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * Une entrée de l'« Activité récente » du dashboard : issue des traces
 * d'audit réelles ({@code audit_logs}) de l'organisation de l'événement.
 */
@Value
@Builder
public class ActivityItemResponse {
    Long id;
    String action;
    String entityType;
    Long entityId;
    String details;
    LocalDateTime performedAt;
    Long userId;
}
