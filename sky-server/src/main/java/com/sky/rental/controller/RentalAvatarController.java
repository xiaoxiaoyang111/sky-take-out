package com.sky.rental.controller;
import com.sky.rental.service.RentalAvatarService;
import com.sky.result.Result;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
@RestController
@RequestMapping("/rental/member")
public class RentalAvatarController {
    private final RentalAvatarService avatars;
    public RentalAvatarController(RentalAvatarService avatars){this.avatars=avatars;}
    @PostMapping("/avatar") public Result<Map<String,String>> avatar(@RequestParam("file") MultipartFile file){return Result.success(avatars.upload(file));}
}
