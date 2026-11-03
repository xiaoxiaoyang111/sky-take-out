package com.sky.rental.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;

/** Explicit offline provisioning; no public administrator registration endpoint. */
@Component
@Profile("bootstrap")
public class RentalAdminBootstrap implements ApplicationRunner,org.springframework.context.ApplicationContextAware {
    private org.springframework.context.ConfigurableApplicationContext context;
    @Override public void setApplicationContext(org.springframework.context.ApplicationContext value){if(value instanceof org.springframework.context.ConfigurableApplicationContext)this.context=(org.springframework.context.ConfigurableApplicationContext)value;}
    private final JdbcTemplate jdbc;private final String username,password;
    public RentalAdminBootstrap(JdbcTemplate jdbc,@Value("${RENTAL_BOOTSTRAP_USERNAME:}") String username,@Value("${RENTAL_BOOTSTRAP_PASSWORD:}") String password){this.jdbc=jdbc;this.username=username;this.password=password;}
    @Override public void run(ApplicationArguments args){
        int bytes=password.getBytes(StandardCharsets.UTF_8).length;
        if(!username.matches("[A-Za-z0-9_]{3,32}")||password.length()<12||bytes>72)throw new IllegalArgumentException("Bootstrap requires a valid username and a 12-character minimum password (at most 72 UTF-8 bytes)");
        if(jdbc.queryForObject("select count(*) from employee where username=?",Integer.class,username)>0)throw new IllegalStateException("Bootstrap refuses to overwrite an existing employee");
        jdbc.update("insert into employee(name,username,password,status,create_time,update_time) values(?,?,?,1,now(),now())","门店管理员",username,new BCryptPasswordEncoder().encode(password));
        org.slf4j.LoggerFactory.getLogger(RentalAdminBootstrap.class).info("Rental administrator created; remove bootstrap profile and credentials before regular startup");
        if(context!=null)context.close();
    }
}
