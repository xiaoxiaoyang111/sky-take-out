package com.sky.rental.model;

import lombok.Data;

@Data
public class RentalOrderEvent {
    private Long id;
    private Long orderId;
    private String actorRole;
    private Long actorId;
    private String action;
    private String note;
    private java.time.LocalDateTime createdAt;
}
