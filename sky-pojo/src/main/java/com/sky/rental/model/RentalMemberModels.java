package com.sky.rental.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.math.BigDecimal;

public final class RentalMemberModels {
    private RentalMemberModels() { }
    @Data public static class ProfileRequest { private String name; }
    @Data public static class ReviewRequest { private Long orderId; private Integer rating; private String content; }
    @Data public static class FavoriteView {
        private Long id;private Long equipmentId;private String equipmentName;private String image;
        private String brand;private BigDecimal dailyRate;private BigDecimal deposit;private Boolean available;private LocalDateTime createdAt;
    }
    @Data public static class ReviewView {
        private Long id;private Long orderId;private String equipmentName;private Integer rating;private String content;private LocalDateTime createdAt;
    }
}
