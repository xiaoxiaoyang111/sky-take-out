package com.sky.rental.model;

import lombok.Data;

@Data
public class RentalQuote {
    private Long equipmentId;
    private java.time.LocalDate startDate;
    private java.time.LocalDate endDate;
    private Integer rentalDays;
    private java.math.BigDecimal dailyRate;
    private java.math.BigDecimal rentalAmount;
    private java.math.BigDecimal depositAmount;
    private java.math.BigDecimal totalAmount;
    private Integer availableCount;
}
