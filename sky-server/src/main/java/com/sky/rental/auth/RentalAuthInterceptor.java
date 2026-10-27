package com.sky.rental.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.result.Result;
import com.sky.entity.Employee;
import com.sky.mapper.EmployeeMapper;
import com.sky.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class RentalAuthInterceptor implements HandlerInterceptor {
    private final String secret;
    private final EmployeeMapper employees;
    public RentalAuthInterceptor(@Value("${rental.jwt-secret}") String secret, EmployeeMapper employees) { this.secret=secret; this.employees=employees; }
    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) throws Exception {
        RentalContext.clear();
        // Authorize the route Spring resolved, not the raw URI (encoded/matrix paths may differ).
        Object matchedPattern=request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String path=matchedPattern==null
                ? request.getRequestURI().substring(request.getContextPath().length())
                : matchedPattern.toString();
        String method=request.getMethod();
        if("POST".equals(method) && (path.equals("/rental/auth/register") || path.equals("/rental/auth/login") || path.equals("/rental/auth/admin-login"))) return true;
        if(("GET".equals(method) || "HEAD".equals(method)) && (path.equals("/rental/assistant/status") || path.equals("/rental/categories") || path.equals("/rental/equipment")
                || path.equals("/rental/equipment/{id}") || path.equals("/rental/equipment/{id}/availability")
                || path.equals("/rental/equipment/{id}/quote") || path.matches("/rental/equipment/[0-9]+(/(availability|quote))?"))) return true;
        String header=request.getHeader("Authorization");
        if(header==null || !header.startsWith("Bearer ")) return error(response,401,"请先登录");
        try {
            Claims c=JwtUtil.parseJWT(secret,header.substring(7));
            if(!"rental-v1".equals(c.getAudience())) return error(response,401,"登录已失效");
            String role=c.get("role",String.class); Number id=c.get("id",Number.class);
            if(id==null || id.longValue()<=0 || !("USER".equals(role)||"ADMIN".equals(role))) return error(response,401,"登录已失效");
            if((path.startsWith("/rental/admin/") && !"ADMIN".equals(role)) || ((path.startsWith("/rental/orders") || path.startsWith("/rental/assistant/")) && !"USER".equals(role))) return error(response,403,"无权访问");
            if("ADMIN".equals(role)) {
                Employee employee=employees.getById(id.longValue());
                if(employee==null || !Integer.valueOf(1).equals(employee.getStatus())) return error(response,401,"员工账号已停用或不存在");
            }
            RentalContext.set(id.longValue(),role,c.get("name",String.class),c.get("username",String.class));
            return true;
        } catch (RuntimeException ex) { return error(response,401,"登录已失效"); }
    }
    private boolean error(HttpServletResponse response,int status,String message) throws Exception {
        response.setStatus(status);response.setContentType("application/json;charset=UTF-8");new ObjectMapper().writeValue(response.getWriter(),Result.error(message));return false;
    }
    @Override public void afterCompletion(HttpServletRequest request,HttpServletResponse response,Object handler,Exception ex) { RentalContext.clear(); }
}
