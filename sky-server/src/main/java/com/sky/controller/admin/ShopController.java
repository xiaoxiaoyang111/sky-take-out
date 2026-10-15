package com.sky.controller.admin;

import com.sky.result.Result;
import com.sky.service.ShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/shop")
public class ShopController {
    @Autowired
    private ShopService shopService;

    @GetMapping("/status")
    public Result<Integer> getStatus() {
        return Result.success(shopService.getStatus());
    }

    @PutMapping("/{status}")
    public Result<String> setStatus(@PathVariable Integer status) {
        shopService.setStatus(status);
        return Result.success();
    }

}
