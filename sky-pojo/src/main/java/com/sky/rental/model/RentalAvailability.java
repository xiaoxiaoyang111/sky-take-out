package com.sky.rental.model;

import lombok.Data;

@Data
public class RentalAvailability {
    private Long equipmentId;
    private java.time.LocalDate startDate;
    private java.time.LocalDate endDate;
    private Integer availableCount;
}
