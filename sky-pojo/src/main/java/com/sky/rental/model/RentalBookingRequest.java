package com.sky.rental.model;

import lombok.Data;

@Data
public class RentalBookingRequest {
    private Long equipmentId;
    private java.time.LocalDate startDate;
    private java.time.LocalDate endDate;
    private String requestId;
    private String remark;
}
