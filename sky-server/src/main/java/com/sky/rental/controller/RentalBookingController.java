package com.sky.rental.controller;

import com.sky.rental.model.*;
import com.sky.rental.service.RentalBookingService;
import com.sky.result.Result;
import com.sky.result.PageResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/rental")
public class RentalBookingController {
    private final RentalBookingService service;
    public RentalBookingController(RentalBookingService service) { this.service=service; }
    @GetMapping("/equipment/{id}/availability")
    public Result<RentalAvailability> availability(@PathVariable Long id,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate) { return Result.success(service.availability(id,startDate,endDate)); }
    @GetMapping("/equipment/{id}/quote")
    public Result<RentalQuote> quote(@PathVariable Long id,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate) { return Result.success(service.quote(id,startDate,endDate)); }
    @PostMapping("/orders")
    public Result<RentalOrder> create(@RequestBody RentalBookingRequest request) { return Result.success(service.create(request)); }
    @GetMapping("/orders")
    public Result<PageResult> orders(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="12") int pageSize,@RequestParam(required=false) String status) { return Result.success(service.list(false,page,pageSize,status,null)); }
    @GetMapping("/orders/{id}")
    public Result<RentalOrder> detail(@PathVariable Long id) { return Result.success(service.detail(id,false)); }
    @PostMapping("/orders/{id}/cancel")
    public Result<RentalOrder> cancel(@PathVariable Long id,@RequestBody(required=false) RentalOrderAction body) { return Result.success(service.transition(id,"cancel",body==null?null:body.getNote())); }
    @GetMapping("/admin/orders")
    public Result<PageResult> adminOrders(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="12") int pageSize,@RequestParam(required=false) String status,@RequestParam(required=false) String keyword) { return Result.success(service.list(true,page,pageSize,status,keyword)); }
    @GetMapping("/admin/orders/{id}")
    public Result<RentalOrder> adminDetail(@PathVariable Long id) { return Result.success(service.detail(id,true)); }
    @PostMapping("/admin/orders/{id}/{action:confirm|reject|pickup|return}")
    public Result<RentalOrder> action(@PathVariable Long id,@PathVariable String action,@RequestBody(required=false) RentalOrderAction body) { return Result.success(service.transition(id,action,body==null?null:body.getNote())); }
    @GetMapping("/admin/dashboard")
    public Result<RentalDashboard> dashboard() { return Result.success(service.dashboard()); }
}
