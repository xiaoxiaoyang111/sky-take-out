package com.sky.rental.model;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

/** Explicit read-only actions; the client cannot provide customer IDs or tool SQL. */
public final class RentalAssistantModels {
    private RentalAssistantModels() { }
    @Data public static class Request {
        private String sessionId;
        private String message;
        private String action = "ASK";
        private Boolean allowModel;
        private Long equipmentId;
        private LocalDate startDate;
        private LocalDate endDate;
    }
    @Data public static class Turn {
        private String question;
        private String answer;
        private String mode;
        private String createdAt;
        private List<String> sources;
    }
}
