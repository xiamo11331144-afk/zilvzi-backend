package com.zilvzi.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zilvzi.pojo.Result;
import com.zilvzi.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行登录接口
        if (request.getRequestURI().endsWith("/api/admin/login")) {
            return true;
        }

        // 放行OPTIONS请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 获取Token
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            writeError(response, 401, "请先登录后台");
            return false;
        }

        token = token.substring(7);

        // 验证Token是否有效
        if (!jwtUtil.validateToken(token)) {
            writeError(response, 401, "Token无效或已过期");
            return false;
        }

        // 检查是否是管理员Token
        if (!jwtUtil.isAdminToken(token)) {
            writeError(response, 403, "无管理员权限");
            return false;
        }

        return true;
    }

    private void writeError(HttpServletResponse response, int code, String message) throws Exception {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(code);
        Result<?> result = Result.error(code, message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}