package com.sky.rental.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.exception.BaseException;
import com.sky.result.PageResult;
import com.sky.rental.auth.RentalContext;
import com.sky.rental.mapper.RentalCatalogMapper;
import com.sky.rental.model.*;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class RentalCatalogService {
    private final RentalCatalogMapper mapper;
    private RentalCatalogCache cache;
    @org.springframework.beans.factory.annotation.Autowired
    public void setCache(RentalCatalogCache cache) { this.cache=cache; }
    private void invalidate() { if(cache!=null) cache.invalidateAfterCommit(); }
    public RentalCatalogService(RentalCatalogMapper mapper) { this.mapper=mapper; }
    public List<RentalCategory> categories(boolean admin) { if(admin) requireAdmin(); return mapper.categories(admin); }
    @Transactional
    public RentalCategory saveCategory(Long id,RentalRequests.Category request) {
        requireAdmin();
        if(request==null) throw new BaseException("请填写分类信息");
        String name=text(request.getName(),50,true,"分类名称"); validStatus(request.getStatus());
        if(request.getSort()==null || request.getSort()<0 || request.getSort()>100000) throw new BaseException("排序须为0至100000");
        RentalCategory c=id==null?new RentalCategory():mapper.category(id);
        if(c==null) throw new BaseException("分类不存在");
        c.setName(name); c.setSort(request.getSort());c.setStatus(request.getStatus());
        try { if(id==null) mapper.insertCategory(c);else mapper.updateCategory(c); } catch(DuplicateKeyException ex) { throw new BaseException("分类名称已存在"); } invalidate();return c;
    }
    public PageResult equipment(boolean admin,int page,int pageSize,Long categoryId,String keyword) {
        if(admin) requireAdmin();
        if(page<1 || pageSize<1 || pageSize>100 || categoryId!=null && categoryId<1) throw new BaseException("分页或分类参数无效");
        if(keyword!=null && keyword.length()>100) throw new BaseException("搜索关键字过长");
        try { PageHelper.startPage(page,pageSize); List<RentalEquipment> rows=mapper.equipmentList(admin,categoryId,keyword); return new PageResult(((Page<?>)rows).getTotal(),rows); }
        finally { PageHelper.clearPage(); }
    }
    public RentalEquipment equipment(Long id,boolean admin) {
        if(admin) requireAdmin();
        RentalEquipment e=mapper.getEquipment(id);
        if(e==null) throw new BaseException("器材不存在或已停用");
        RentalCategory category=admin?null:mapper.category(e.getCategoryId());
        if(!admin && (!Integer.valueOf(1).equals(e.getStatus()) || category==null || !Integer.valueOf(1).equals(category.getStatus()))) throw new BaseException("器材不存在或已停用");
        return e;
    }
    @Transactional
    public RentalEquipment saveEquipment(Long id,RentalRequests.Equipment request) {
        requireAdmin();
        if(request==null) throw new BaseException("请填写器材信息");
        validStatus(request.getStatus());
        RentalCategory category=mapper.category(request.getCategoryId());
        if(category==null || !Integer.valueOf(1).equals(category.getStatus())) throw new BaseException("请选择启用的分类");
        RentalEquipment e=id==null?new RentalEquipment():mapper.lockEquipment(id);
        if(e==null) throw new BaseException("器材不存在");
        BeanUtils.copyProperties(request,e);
        e.setName(text(request.getName(),100,true,"器材名称"));e.setBrand(text(request.getBrand(),50,false,"品牌"));
        e.setDescription(text(request.getDescription(),2000,false,"说明"));e.setImage(text(request.getImage(),500,false,"图片"));
        if(!e.getImage().isEmpty() && !(e.getImage().startsWith("/illustrations/") || e.getImage().startsWith("/images/") || e.getImage().startsWith("https://") || e.getImage().startsWith("http://"))) throw new BaseException("图片地址格式无效");
        money(request.getDailyRate(),true);money(request.getDeposit(),false);
        LocalDateTime now=LocalDateTime.now();e.setUpdatedAt(now);e.setUpdateUser(RentalContext.getUserId());
        if(id==null) { e.setCreatedAt(now);e.setCreateUser(RentalContext.getUserId());mapper.insertEquipment(e); } else mapper.updateEquipment(e);
        invalidate();return mapper.getEquipment(e.getId());
    }
    public List<RentalAsset> assets(Long equipmentId) {
        requireAdmin();
        if(equipmentId==null || mapper.getEquipment(equipmentId)==null) throw new BaseException("请选择有效器材");
        return mapper.assets(equipmentId);
    }
    @Transactional
    public RentalAsset addAsset(RentalRequests.Asset request) {
        requireAdmin();
        if(request==null || request.getEquipmentId()==null) throw new BaseException("请选择器材");
        if(mapper.lockEquipment(request.getEquipmentId())==null) throw new BaseException("器材不存在");
        RentalAsset a=new RentalAsset();a.setEquipmentId(request.getEquipmentId());a.setAssetCode(text(request.getAssetCode(),64,true,"资产编号"));a.setStatus("READY");a.setCreatedAt(LocalDateTime.now());
        try { mapper.insertAsset(a); } catch(DuplicateKeyException ex) { throw new BaseException("资产编号已存在"); } invalidate();return a;
    }
    @Transactional
    public RentalAsset assetStatus(Long id,RentalRequests.AssetStatus request) {
        requireAdmin();
        if(request==null || !Arrays.asList("READY","MAINTENANCE","RETIRED").contains(request.getStatus())) throw new BaseException("无效实物状态");
        RentalAsset snapshot=mapper.asset(id);if(snapshot==null) throw new BaseException("实物不存在");
        mapper.lockEquipment(snapshot.getEquipmentId());RentalAsset a=mapper.lockAsset(id);
        if(!"READY".equals(request.getStatus()) && !mapper.occupyingOrders(id).isEmpty()) throw new BaseException("实物存在未完成订单，不能维修或退役");
        a.setStatus(request.getStatus());mapper.updateAsset(a);invalidate();return a;
    }
    private static void requireAdmin() {
        if(RentalContext.getUserId()==null || !"ADMIN".equals(RentalContext.getRole())) throw new BaseException("无权执行此操作");
    }
    private static void validStatus(Integer status) { if(status==null || status!=0 && status!=1) throw new BaseException("状态须为0或1"); }
    private static String text(String value,int max,boolean required,String label) {
        String result=value==null?"":value.trim();if(result.length()>max || required && result.isEmpty()) throw new BaseException(label+"长度不符合要求");return result;
    }
    private static void money(BigDecimal value,boolean positive) {
        if(value==null || value.signum()<0 || positive && value.signum()==0 || value.compareTo(new BigDecimal("99999999.99"))>0 || value.stripTrailingZeros().scale()>2) throw new BaseException("金额须在允许范围内且最多两位小数，日租金须大于0");
    }
}
