package com.sky.rental.model;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class RentalAsset { private Long id; private Long equipmentId; private String assetCode; private String status; private LocalDateTime createdAt; }
