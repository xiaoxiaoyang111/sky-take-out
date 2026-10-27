package com.sky.rental.controller;
import com.sky.result.Result;
import com.sky.rental.model.RentalRequests;
import com.sky.rental.service.RentalAuthService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/rental")
public class RentalAuthController {
    private final RentalAuthService auth;
    public RentalAuthController(RentalAuthService auth) { this.auth=auth; }
    @PostMapping("/auth/register") public Result<Map<String,Object>> register(@RequestBody RentalRequests.Login request) { return Result.success(auth.register(request)); }
    @PostMapping("/auth/login") public Result<Map<String,Object>> login(@RequestBody RentalRequests.Login request) { return Result.success(auth.login(request,false)); }
    @PostMapping("/auth/admin-login") public Result<Map<String,Object>> adminLogin(@RequestBody RentalRequests.Login request) { return Result.success(auth.login(request,true)); }
    @GetMapping("/me") public Result<Map<String,Object>> me() { return Result.success(auth.me()); }
}
