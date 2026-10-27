package com.sky.rental;

import com.sky.exception.BaseException;
import com.sky.rental.auth.RentalContext;
import com.sky.rental.model.*;
import com.sky.rental.service.RentalAuthService;
import com.sky.rental.service.RentalCatalogService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named="SKY_DB_INTEGRATION",matches="true")
class RentalCatalogPersistenceTest {
    @Autowired RentalCatalogService catalog;
    @Autowired RentalAuthService auth;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void init() { RentalContext.set(1L,"ADMIN","测试","test"); }
    @AfterEach void clean() { RentalContext.clear(); }
    @Test void categoryEquipmentAssetRoundTripAndPublicVisibility() {
        String suffix=UUID.randomUUID().toString().substring(0,8);
        RentalRequests.Category category=new RentalRequests.Category();category.setName("测试类别"+suffix);category.setStatus(1);category.setSort(0);
        RentalCategory c=catalog.saveCategory(null,category);
        RentalRequests.Equipment request=new RentalRequests.Equipment();request.setCategoryId(c.getId());request.setName("测试相机"+suffix);request.setDailyRate(new BigDecimal("89.00"));request.setDeposit(new BigDecimal("500.00"));request.setStatus(1);
        RentalEquipment e=catalog.saveEquipment(null,request);assertEquals(c.getName(),e.getCategoryName());assertEquals(1L,e.getCreateUser());
        RentalRequests.Asset asset=new RentalRequests.Asset();asset.setEquipmentId(e.getId());asset.setAssetCode("IT-"+suffix);RentalAsset a=catalog.addAsset(asset);
        assertEquals(1L,catalog.equipment(e.getId(),false).getAssetCount());
        RentalRequests.AssetStatus status=new RentalRequests.AssetStatus();status.setStatus("MAINTENANCE");assertEquals("MAINTENANCE",catalog.assetStatus(a.getId(),status).getStatus());
        assertEquals(1L,catalog.equipment(false,1,12,c.getId(),suffix).getTotal());
        request.setStatus(0);catalog.saveEquipment(e.getId(),request);assertEquals(0L,catalog.equipment(false,1,12,c.getId(),suffix).getTotal());assertThrows(BaseException.class,()->catalog.equipment(e.getId(),false));
    }
    @Test void registeredCustomerCanLoginAndHashNeverLeaks() {
        RentalRequests.Login request=new RentalRequests.Login();request.setUsername("IT_"+UUID.randomUUID().toString().replace("-","").substring(0,15));request.setPassword("integration-only-password");request.setName("独立测试用户");
        Map<String,Object> registered=auth.register(request);assertEquals("USER",registered.get("role"));
        String hash=jdbc.queryForObject("select password_hash from rental_customer where username=?",String.class,request.getUsername());assertTrue(hash.startsWith("$2"));assertNotEquals(request.getPassword(),hash);
        assertEquals("USER",auth.login(request,false).get("role"));assertThrows(BaseException.class,()->auth.register(request));
    }
}
