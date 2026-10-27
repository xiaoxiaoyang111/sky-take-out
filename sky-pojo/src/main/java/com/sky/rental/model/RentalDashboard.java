package com.sky.rental.model;

import lombok.Data;

@Data
public class RentalDashboard {
    private Long equipmentCount;
    private Long assetCount;
    private Long pendingCount;
    private Long rentedCount;
    private Long completedCount;
    private java.math.BigDecimal rentalRevenue;
}
