package com.sky.rental.model;

import lombok.Data;

@Data
public class RentalOrder {
    private Long id;
    private String orderNo;
    private Long customerId;
    private Long equipmentId;
    private Long assetId;
    private String equipmentName;
    private String assetCode;
    private java.time.LocalDate startDate;
    private java.time.LocalDate endDate;
    private Integer rentalDays;
    private java.math.BigDecimal dailyRate;
    private java.math.BigDecimal rentalAmount;
    private java.math.BigDecimal depositAmount;
    private String status;
    private String requestId;
    private String remark;
    private String returnNote;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
    private java.time.LocalDateTime confirmedAt;
    private java.time.LocalDateTime pickedUpAt;
    private java.time.LocalDateTime returnedAt;
    private java.util.List<RentalOrderEvent> events;
}
