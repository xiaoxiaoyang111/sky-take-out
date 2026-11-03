package com.sky.rental;

import com.sky.rental.service.RentalAssistantStore;
import com.sky.rental.service.RentalModelClient;
import com.sky.utils.JwtUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariables({@EnabledIfEnvironmentVariable(named="SKY_DB_INTEGRATION",matches="true"),@EnabledIfEnvironmentVariable(named="SKY_REDIS_INTEGRATION",matches="true")})
class RentalAssistantHttpIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Value("${rental.jwt-secret}") String jwtSecret;
    @MockBean RentalModelClient model;
    String prefix,session,token,equipmentName;
    Long customerId,categoryId,equipmentId;
    @BeforeEach void setup() {
        prefix="AL"+UUID.randomUUID().toString().replace("-","").substring(0,12);session=UUID.randomUUID().toString();equipmentName=prefix+"旅行相机";
        jdbc.update("insert into rental_customer(username,password_hash,display_name) values(?,?,?)",prefix,"test-only-hash",prefix);
        customerId=jdbc.queryForObject("select id from rental_customer where username=?",Long.class,prefix);
        jdbc.update("insert into rental_category(name,sort,status) values(?,0,1)",prefix);
        categoryId=jdbc.queryForObject("select id from rental_category where name=?",Long.class,prefix);
        jdbc.update("insert into rental_equipment(category_id,name,daily_rate,deposit,status) values(?,?,12.5,100,1)",categoryId,equipmentName);
        equipmentId=jdbc.queryForObject("select id from rental_equipment where category_id=?",Long.class,categoryId);
        jdbc.update("insert into rental_asset(equipment_id,asset_code,status) values(?,?,'READY')",equipmentId,prefix);
        Map<String,Object> claims=new HashMap<>();claims.put("aud","rental-v1");claims.put("id",customerId);claims.put("role","USER");claims.put("name",prefix);claims.put("username",prefix);token=JwtUtil.createJWT(jwtSecret,60000,claims);
        when(model.configured()).thenReturn(true);
        when(model.answer(anyString(),anyList(),anyString())).thenReturn("推荐"+equipmentName+"，虚构的型号不会有店内链接。");
    }
    @AfterEach void clean() {
        if(customerId!=null){Set<String> keys=redis.keys(RentalAssistantStore.prefix(customerId)+"*");if(keys!=null&&!keys.isEmpty())redis.delete(keys);}
        if(equipmentId!=null){jdbc.update("delete from rental_asset where equipment_id=?",equipmentId);jdbc.update("delete from rental_equipment where id=?",equipmentId);}
        if(categoryId!=null)jdbc.update("delete from rental_category where id=?",categoryId);
        if(customerId!=null)jdbc.update("delete from rental_customer where id=?",customerId);
    }
    String body(String action) { return "{\"sessionId\":\""+session+"\",\"action\":\""+action+"\",\"allowModel\":true,\"message\":\"租期三天请推荐器材\"}"; }
    @Test void httpReturnsVerifiedLinksAndPreservesThemInRedisHistory() throws Exception {
        mvc.perform(post("/rental/assistant/messages").header("Authorization","Bearer "+token).contentType("application/json;charset=UTF-8").content(body("ASK")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1)).andExpect(jsonPath("$.data.mode").value("MODEL"))
            .andExpect(jsonPath("$.data.equipmentLinks.length()").value(1)).andExpect(jsonPath("$.data.equipmentLinks[0].id").value(equipmentId)).andExpect(jsonPath("$.data.equipmentLinks[0].name").value(equipmentName));
        mvc.perform(get("/rental/assistant/history").param("sessionId",session).header("Authorization","Bearer "+token))
            .andExpect(jsonPath("$.data[0].equipmentLinks[0].id").value(equipmentId));
        verify(model).answer(contains(equipmentName),anyList(),anyString());
    }
    @Test void moreThanOldDailyQuotaSucceedsAndLegacyCountersAreIgnored() throws Exception {
        String base=RentalAssistantStore.prefix(customerId);
        redis.opsForValue().set(base+"minute","6",Duration.ofMinutes(1));redis.opsForValue().set(base+"day","100",Duration.ofDays(1));
        for(int i=0;i<105;i++) mvc.perform(post("/rental/assistant/messages").header("Authorization","Bearer "+token).contentType("application/json").content(body("RULES"))).andExpect(jsonPath("$.code").value(1));
        mvc.perform(get("/rental/assistant/status")).andExpect(jsonPath("$.data.unlimited").value(true)).andExpect(jsonPath("$.data.minuteLimit").value(0));
        assertEquals("6",redis.opsForValue().get(base+"minute"));assertEquals("100",redis.opsForValue().get(base+"day"));verify(model,never()).answer(anyString(),anyList(),anyString());
    }
}
