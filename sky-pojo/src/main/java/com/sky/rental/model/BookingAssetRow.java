package com.sky.rental.model;

import lombok.Data;

@Data
public class BookingAssetRow {
    private Long id;
    private Long equipmentId;
    private String assetCode;
    private String status;
}
