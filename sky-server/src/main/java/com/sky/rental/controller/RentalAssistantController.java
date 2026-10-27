package com.sky.rental.controller;

import com.sky.rental.model.RentalAssistantModels.*;
import com.sky.rental.service.RentalAssistantService;
import com.sky.result.Result;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/rental/assistant")
public class RentalAssistantController {
    private final RentalAssistantService service;
    public RentalAssistantController(RentalAssistantService service) { this.service=service; }
    @GetMapping("/status") public Result<Map<String,Object>> status() { return Result.success(service.status()); }
    @GetMapping("/history") public Result<List<Turn>> history(@RequestParam String sessionId) { return Result.success(service.history(sessionId)); }
    @PostMapping("/messages") public Result<Turn> ask(@RequestBody Request request) { return Result.success(service.ask(request)); }
    @DeleteMapping("/history") public Result<String> clear(@RequestParam String sessionId) { service.clear(sessionId);return Result.success("会话已清除"); }
}
