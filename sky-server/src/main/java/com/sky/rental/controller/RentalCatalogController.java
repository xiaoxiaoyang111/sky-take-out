package com.sky.rental.controller;
import com.sky.result.Result;
import com.sky.result.PageResult;
import com.sky.rental.model.*;
import com.sky.rental.service.RentalCatalogService;
import com.sky.rental.service.RentalCatalogCache;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/rental")
public class RentalCatalogController {
    private final RentalCatalogService catalog;
    private final RentalCatalogCache cache;
    public RentalCatalogController(RentalCatalogService catalog,RentalCatalogCache cache) { this.catalog=catalog;this.cache=cache; }
    @GetMapping("/categories") public Result<List<RentalCategory>> categories() { return Result.success(java.util.Arrays.asList(cache.read("categories",RentalCategory[].class,()->catalog.categories(false).toArray(new RentalCategory[0])))); }
    @GetMapping("/admin/categories") public Result<List<RentalCategory>> adminCategories() { return Result.success(catalog.categories(true)); }
    @PostMapping("/admin/categories") public Result<RentalCategory> addCategory(@RequestBody RentalRequests.Category r) { return Result.success(catalog.saveCategory(null,r)); }
    @PutMapping("/admin/categories/{id}") public Result<RentalCategory> editCategory(@PathVariable Long id,@RequestBody RentalRequests.Category r) { return Result.success(catalog.saveCategory(id,r)); }
    @GetMapping("/equipment") public Result<PageResult> equipment(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="12") int pageSize,@RequestParam(required=false) Long categoryId,@RequestParam(required=false) String keyword) { return Result.success(cache.read("equipment|"+page+"|"+pageSize+"|"+categoryId+"|"+(keyword==null?"":keyword),PageResult.class,()->catalog.equipment(false,page,pageSize,categoryId,keyword))); }
    @GetMapping("/equipment/{id}") public Result<RentalEquipment> detail(@PathVariable Long id) { return Result.success(cache.read("equipment:"+id,RentalEquipment.class,()->catalog.equipment(id,false))); }
    @GetMapping("/admin/equipment/{id}") public Result<RentalEquipment> adminDetail(@PathVariable Long id) { return Result.success(catalog.equipment(id,true)); }
    @GetMapping("/admin/equipment") public Result<PageResult> adminEquipment(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="12") int pageSize,@RequestParam(required=false) Long categoryId,@RequestParam(required=false) String keyword) { return Result.success(catalog.equipment(true,page,pageSize,categoryId,keyword)); }
    @PostMapping("/admin/equipment") public Result<RentalEquipment> addEquipment(@RequestBody RentalRequests.Equipment r) { return Result.success(catalog.saveEquipment(null,r)); }
    @PutMapping("/admin/equipment/{id}") public Result<RentalEquipment> editEquipment(@PathVariable Long id,@RequestBody RentalRequests.Equipment r) { return Result.success(catalog.saveEquipment(id,r)); }
    @GetMapping("/admin/assets") public Result<List<RentalAsset>> assets(@RequestParam Long equipmentId) { return Result.success(catalog.assets(equipmentId)); }
    @PostMapping("/admin/assets") public Result<RentalAsset> addAsset(@RequestBody RentalRequests.Asset r) { return Result.success(catalog.addAsset(r)); }
    @PutMapping("/admin/assets/{id}/status") public Result<RentalAsset> assetStatus(@PathVariable Long id,@RequestBody RentalRequests.AssetStatus r) { return Result.success(catalog.assetStatus(id,r)); }
}
