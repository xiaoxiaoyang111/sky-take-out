package com.sky.rental.controller;
import com.sky.result.Result;
import com.sky.rental.service.RentalImageService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@RestController
@RequestMapping("/rental/admin")
public class RentalImageController {
    private final RentalImageService images;
    public RentalImageController(RentalImageService images) { this.images=images; }
    @PostMapping("/upload") public Result<String> upload(@RequestParam("file") MultipartFile file) { return Result.success(images.upload(file)); }
}
