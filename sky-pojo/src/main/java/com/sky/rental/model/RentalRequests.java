package com.sky.rental.model;
import lombok.Data;
import java.math.BigDecimal;
public final class RentalRequests {
    private RentalRequests() { }
    @Data public static class Login { private String username; private String password; private String name; }
    @Data public static class Category { private String name; private Integer sort; private Integer status; }
    @Data public static class Equipment { private Long categoryId; private String name; private String brand; private String description; private String image; private BigDecimal dailyRate; private BigDecimal deposit; private Integer status; }
    @Data public static class Asset { private Long equipmentId; private String assetCode; }
    @Data public static class AssetStatus { private String status; }
}
