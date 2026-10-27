package com.sky.rental.model;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class RentalCustomer { private Long id; private String username; private String passwordHash; private String displayName; private LocalDateTime createdAt; }
