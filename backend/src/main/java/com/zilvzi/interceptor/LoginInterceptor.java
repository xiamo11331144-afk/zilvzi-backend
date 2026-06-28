package com.zilvzi.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zilvzi.pojo.Result;
import com.zilvzi.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行OPTIONS请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 获取Token
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            writeError(response, 401, "未登录，请先登录");
            return false;
        }

        token = token.substring(7);

        // 验证Token
        if (!jwtUtil.validateToken(token)) {
            System.out.println("[Auth] Token验证失败: " + request.getMethod() + " " + request.getRequestURI());
            writeError(response, 401, "Token无效或已过期，请重新登录");
            return false;
        }

        // 将用户ID放入request
        Long userId = jwtUtil.getUserId(token);
        request.setAttribute("userId", userId);

        System.out.println("[Auth] 通过: userId=" + userId + " " + request.getMethod() + " " + request.getRequestURI());
        return true;
    }

    private void writeError(HttpServletResponse response, int code, String message) throws Exception {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(code);
        Result<?> result = Result.error(code, message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}