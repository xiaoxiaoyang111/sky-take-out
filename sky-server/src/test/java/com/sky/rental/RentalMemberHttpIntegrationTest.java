package com.sky.rental;

import com.sky.utils.JwtUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="SKY_DB_INTEGRATION",matches="true")
class RentalMemberHttpIntegrationTest {
    @Value("${sky.upload.path}") String uploadPath;
    java.util.List<String> uploaded=new ArrayList<>();
    @AfterEach void removeAvatarFiles() throws Exception {for(String url:uploaded)java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(uploadPath).toAbsolutePath().normalize().resolve(url.substring("/images/".length())));}
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Value("${rental.jwt-secret}") String secret;
    Long uid,other,category,equipment,asset,order;String prefix,token,otherToken;
    String token(Long id,String role){Map<String,Object> c=new HashMap<>();c.put("aud","rental-v1");c.put("id",id);c.put("role",role);c.put("name","old-name");c.put("username",prefix);return JwtUtil.createJWT(secret,60000,c);}
    @BeforeEach void setup(){
        prefix="MC"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        jdbc.update("insert into rental_customer(username,password_hash,display_name) values(?,?,?)",prefix,"fixture-hash",prefix);uid=jdbc.queryForObject("select id from rental_customer where username=?",Long.class,prefix);
        jdbc.update("insert into rental_customer(username,password_hash,display_name) values(?,?,?)",prefix+"b","fixture-hash",prefix);other=jdbc.queryForObject("select id from rental_customer where username=?",Long.class,prefix+"b");
        jdbc.update("insert into rental_category(name,status) values(?,1)",prefix);category=jdbc.queryForObject("select id from rental_category where name=?",Long.class,prefix);
        jdbc.update("insert into rental_equipment(category_id,name,daily_rate,deposit,status) values(?,?,12.5,100,1)",category,prefix);equipment=jdbc.queryForObject("select id from rental_equipment where category_id=?",Long.class,category);
        jdbc.update("insert into rental_asset(equipment_id,asset_code,status) values(?,?,'READY')",equipment,prefix);asset=jdbc.queryForObject("select id from rental_asset where asset_code=?",Long.class,prefix);
        jdbc.update("insert into rental_order(order_no,customer_id,equipment_id,asset_id,equipment_name,asset_code,start_date,end_date,rental_days,daily_rate,rental_amount,deposit_amount,status,request_id) values(?,?,?,?,?,?,curdate(),date_add(curdate(),interval 1 day),1,12.5,12.5,100,'COMPLETED','member-fixture')",prefix,uid,equipment,asset,prefix,prefix);order=jdbc.queryForObject("select id from rental_order where order_no=?",Long.class,prefix);
        token=token(uid,"USER");otherToken=token(other,"USER");
    }
    @AfterEach void cleanup(){if(order!=null){jdbc.update("delete from rental_order_event where order_id=?",order);jdbc.update("delete from rental_order where id=?",order);}if(equipment!=null){jdbc.update("delete from rental_asset where equipment_id=?",equipment);jdbc.update("delete from rental_equipment where id=?",equipment);}if(category!=null)jdbc.update("delete from rental_category where id=?",category);if(uid!=null)jdbc.update("delete from rental_customer where id=?",uid);if(other!=null)jdbc.update("delete from rental_customer where id=?",other);}
    @Test void nicknamePersistsAndCurrentProfileDoesNotUseStaleJwtName() throws Exception {
        mvc.perform(put("/rental/member/profile").header("Authorization","Bearer "+token).contentType("application/json;charset=UTF-8").content("{\"name\":\"龙院同学\"}")).andExpect(jsonPath("$.code").value(1));
        mvc.perform(get("/rental/me").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.data.name").value("龙院同学")).andExpect(jsonPath("$.data.passwordHash").doesNotExist());assertEquals("龙院同学",jdbc.queryForObject("select display_name from rental_customer where id=?",String.class,uid));
    }
    @Test void favoritesAreIdempotentOwnedAndRetainDisabledItems() throws Exception {
        for(int i=0;i<2;i++)mvc.perform(put("/rental/favorites/"+equipment).header("Authorization","Bearer "+token)).andExpect(jsonPath("$.code").value(1));
        mvc.perform(get("/rental/favorites").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.records[0].available").value(true));
        mvc.perform(delete("/rental/favorites/"+equipment).header("Authorization","Bearer "+otherToken)).andExpect(jsonPath("$.code").value(1));
        jdbc.update("update rental_equipment set status=0 where id=?",equipment);
        mvc.perform(get("/rental/favorites").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.records[0].available").value(false));
        mvc.perform(get("/rental/favorites").header("Authorization","Bearer "+otherToken)).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(delete("/rental/favorites/"+equipment).header("Authorization","Bearer "+token)).andExpect(jsonPath("$.code").value(1));assertEquals(0,jdbc.queryForObject("select count(*) from rental_favorite where customer_id=?",Integer.class,uid));
    }
    String review(){return "{\"orderId\":"+order+",\"rating\":5,\"content\":\"器材完好，交接顺利\"}";}
    @Test void completedOrderReviewPersistsOnceAndIsPrivate() throws Exception {
        mvc.perform(post("/rental/reviews").header("Authorization","Bearer "+token).contentType("application/json;charset=UTF-8").content(review())).andExpect(jsonPath("$.code").value(1));
        mvc.perform(post("/rental/reviews").header("Authorization","Bearer "+token).contentType("application/json;charset=UTF-8").content(review())).andExpect(jsonPath("$.code").value(0));
        mvc.perform(get("/rental/reviews").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.records[0].equipmentName").value(prefix)).andExpect(jsonPath("$.data.records[0].rating").value(5));
        mvc.perform(get("/rental/reviews").header("Authorization","Bearer "+otherToken)).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/rental/member/summary").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.data.reviews").value(1)).andExpect(jsonPath("$.data.orders").value(1));
    }
    @Test void unfinishedOrForeignOrderCannotBeReviewed() throws Exception {
        mvc.perform(post("/rental/reviews").header("Authorization","Bearer "+otherToken).contentType("application/json").content(review())).andExpect(jsonPath("$.code").value(0));jdbc.update("update rental_order set status='RENTED' where id=?",order);
        mvc.perform(post("/rental/reviews").header("Authorization","Bearer "+token).contentType("application/json").content(review())).andExpect(jsonPath("$.code").value(0));assertEquals(0,jdbc.queryForObject("select count(*) from rental_review where order_id=?",Integer.class,order));
    }
    @Test void employeeAndGuestCannotReadMemberData() throws Exception {
        mvc.perform(get("/rental/member/summary")).andExpect(status().isUnauthorized());mvc.perform(get("/rental/favorites").header("Authorization","Bearer "+token(1L,"ADMIN"))).andExpect(status().isForbidden());
    }
    byte[] avatarPng() throws Exception {java.io.ByteArrayOutputStream data=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(8,8,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",data);return data.toByteArray();}
    @Test void uploadedAvatarPersistsAcrossLoginAndOnlyUpdatesCurrentCustomer() throws Exception {
        String current=null;
        for(int i=0;i<2;i++){
            String body=mvc.perform(multipart("/rental/member/avatar").file(new org.springframework.mock.web.MockMultipartFile("file","../../avatar.png","image/png",avatarPng())).param("customerId",other.toString()).header("Authorization","Bearer "+token)).andExpect(jsonPath("$.code").value(1)).andReturn().getResponse().getContentAsString();
            current=new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).path("data").path("avatarUrl").asText();assertTrue(current.matches("/images/rental-[a-f0-9-]{36}\\.png"));uploaded.add(current);
        }
        assertNotEquals(uploaded.get(0),uploaded.get(1));
        assertEquals(current,jdbc.queryForObject("select avatar_url from rental_customer_avatar where customer_id=?",String.class,uid));assertEquals(0,jdbc.queryForObject("select count(*) from rental_customer_avatar where customer_id=?",Integer.class,other));
        mvc.perform(get("/rental/me").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.data.avatarUrl").value(current));
        jdbc.update("update rental_customer set password_hash=? where id=?",new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("avatar-fixture-password"),uid);
        mvc.perform(post("/rental/auth/login").contentType("application/json").content("{\"username\":\""+prefix+"\",\"password\":\"avatar-fixture-password\"}")).andExpect(jsonPath("$.data.user.avatarUrl").value(current));
        mvc.perform(put("/rental/member/profile").header("Authorization","Bearer "+token).contentType("application/json").content("{\"name\":\"updated-name\"}")).andExpect(jsonPath("$.data.avatarUrl").value(current));
    }
    @Test void avatarEndpointRejectsGuestEmployeeAndDisguisedImage() throws Exception {
        mvc.perform(multipart("/rental/member/avatar").file(new org.springframework.mock.web.MockMultipartFile("file","avatar.png","image/png",avatarPng()))).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/rental/member/avatar").file(new org.springframework.mock.web.MockMultipartFile("file","avatar.png","image/png",avatarPng())).header("Authorization","Bearer "+token(1L,"ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(multipart("/rental/member/avatar").file(new org.springframework.mock.web.MockMultipartFile("file","fake.png","image/png","<script>bad</script>".getBytes())).header("Authorization","Bearer "+token)).andExpect(jsonPath("$.code").value(0));
        assertEquals(0,jdbc.queryForObject("select count(*) from rental_customer_avatar where customer_id=?",Integer.class,uid));
    }
}
