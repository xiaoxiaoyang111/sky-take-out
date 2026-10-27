package com.sky.rental.model;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data
public class RentalEquipment {
    private Long id; private Long categoryId; private String categoryName; private String name; private String brand;
    private String description; private String image; private BigDecimal dailyRate; private BigDecimal deposit;
    private Integer status; private Long assetCount; private LocalDateTime createdAt; private LocalDateTime updatedAt;
    private Long createUser; private Long updateUser;
}
