package com.sky.rental.controller;
import com.sky.rental.model.RentalMemberModels.*;
import com.sky.rental.service.RentalMemberService;
import com.sky.result.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/rental")
public class RentalMemberController {
    private final RentalMemberService member;
    public RentalMemberController(RentalMemberService member){this.member=member;}
    @GetMapping("/member/summary") public Result<Map<String,Object>> summary(){return Result.success(member.summary());}
    @PutMapping("/member/profile") public Result<Map<String,Object>> profile(@RequestBody ProfileRequest r){return Result.success(member.updateProfile(r));}
    @GetMapping("/favorites") public Result<PageResult> favorites(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="12") int pageSize){return Result.success(member.favorites(page,pageSize));}
    @GetMapping("/favorites/{id}/status") public Result<Boolean> favoriteStatus(@PathVariable Long id){return Result.success(member.favoriteStatus(id));}
    @PutMapping("/favorites/{id}") public Result<String> favorite(@PathVariable Long id){member.favorite(id,true);return Result.success("已收藏");}
    @DeleteMapping("/favorites/{id}") public Result<String> unfavorite(@PathVariable Long id){member.favorite(id,false);return Result.success("已取消收藏");}
    @GetMapping("/reviews") public Result<PageResult> reviews(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="12") int pageSize){return Result.success(member.reviews(page,pageSize));}
    @PostMapping("/reviews") public Result<String> review(@RequestBody ReviewRequest r){member.review(r);return Result.success("评价已提交");}
}
