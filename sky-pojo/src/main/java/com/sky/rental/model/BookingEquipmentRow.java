package com.sky.rental.model;

import lombok.Data;

@Data
public class BookingEquipmentRow {
    private Long id;
    private String name;
    private java.math.BigDecimal dailyRate;
    private java.math.BigDecimal deposit;
    private Integer status;
    private Integer categoryStatus;
}
