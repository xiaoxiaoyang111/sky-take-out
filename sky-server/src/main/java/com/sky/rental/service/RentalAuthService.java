package com.sky.rental.service;

import com.sky.entity.Employee;
import com.sky.exception.BaseException;
import com.sky.mapper.EmployeeMapper;
import com.sky.rental.auth.RentalContext;
import com.sky.rental.mapper.RentalCustomerMapper;
import com.sky.rental.model.RentalCustomer;
import com.sky.rental.model.RentalRequests;
import com.sky.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RentalAuthService {
    private final RentalCustomerMapper customers;
    private final EmployeeMapper employees;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final String secret;
    public RentalAuthService(RentalCustomerMapper customers, EmployeeMapper employees,
                             @Value("${rental.jwt-secret}") String secret) {
        this.customers=customers; this.employees=employees; this.secret=secret;
    }
    public Map<String,Object> register(RentalRequests.Login request) {
        validateCredentials(request, true);
        if (request.getName()==null || request.getName().trim().isEmpty() || request.getName().trim().length()>32) throw new BaseException("姓名须为1至32字符");
        RentalCustomer c = new RentalCustomer(); c.setUsername(request.getUsername());
        c.setPasswordHash(encoder.encode(request.getPassword())); c.setDisplayName(request.getName().trim()); c.setCreatedAt(LocalDateTime.now());
        try { customers.insert(c); } catch (DuplicateKeyException ex) { throw new BaseException("用户名已存在"); }
        return loginResult(c.getId(), "USER", c.getUsername(), c.getDisplayName());
    }
    public Map<String,Object> login(RentalRequests.Login request, boolean admin) {
        validateCredentials(request, false);
        if (admin) {
            Employee e=employees.getByUsername(request.getUsername());
            String digest=DigestUtils.md5DigestAsHex(request.getPassword().getBytes(StandardCharsets.UTF_8));
            if(e==null || !digest.equals(e.getPassword())) throw new BaseException("用户名或密码错误");
            if(!Integer.valueOf(1).equals(e.getStatus())) throw new BaseException("员工账号已停用");
            return loginResult(e.getId(),"ADMIN",e.getUsername(),e.getName());
        }
        RentalCustomer c=customers.find(request.getUsername());
        if(c==null || !encoder.matches(request.getPassword(),c.getPasswordHash())) throw new BaseException("用户名或密码错误");
        return loginResult(c.getId(),"USER",c.getUsername(),c.getDisplayName());
    }
    private void validateCredentials(RentalRequests.Login r, boolean register) {
        if(r==null || r.getUsername()==null || r.getPassword()==null || r.getUsername().length()>32 || r.getUsername().isEmpty()) throw new BaseException("请输入用户名和密码");
        int bytes=r.getPassword().getBytes(StandardCharsets.UTF_8).length;
        if(bytes<1 || bytes>72 || register && (bytes<8 || !r.getUsername().matches("[A-Za-z0-9_]{3,32}"))) throw new BaseException("用户名须为3至32位字母数字下划线，密码须为8至72字节");
    }
    private Map<String,Object> loginResult(Long id,String role,String username,String name) {
        Map<String,Object> claims=new LinkedHashMap<>(); claims.put("aud","rental-v1"); claims.put("id",id); claims.put("role",role); claims.put("username",username); claims.put("name",name);
        Map<String,Object> result=new LinkedHashMap<>(); result.put("token",JwtUtil.createJWT(secret,7200000,claims)); result.put("role",role);
        Map<String,Object> user=new LinkedHashMap<>();user.put("id",id);user.put("username",username);user.put("name",name);result.put("user",user);return result;
    }
    public Map<String,Object> me() {
        Map<String,Object> result=new LinkedHashMap<>(); result.put("id",RentalContext.getUserId());result.put("role",RentalContext.getRole());result.put("name",RentalContext.getName());result.put("username",RentalContext.getUsername());return result;
    }
}
