package com.zilvzi.config;

import com.zilvzi.mapper.UserMapper;
import com.zilvzi.pojo.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 启动时自动创建管理员账号（如果不存在）
 */
@Component
public class AdminInitializer implements CommandLineRunner {

    @Autowired
    private UserMapper userMapper;

    @Override
    public void run(String... args) {
        String adminUsername = "admin";
        String adminPassword = "admin123";

        User existing = userMapper.selectByUsername(adminUsername);
        if (existing == null) {
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            User admin = new User();
            admin.setUsername(adminUsername);
            admin.setPassword(encoder.encode(adminPassword));
            admin.setNickname("管理员");
            admin.setRole(1);
            admin.setLevel(99);
            admin.setExperience(0);
            userMapper.insert(admin);
            System.out.println("[Admin] 已自动创建管理员账号: " + adminUsername + " / " + adminPassword);
        } else if (existing.getRole() == null || existing.getRole() != 1) {
            existing.setRole(1);
            userMapper.updateAdmin(existing);
            System.out.println("[Admin] 已将用户 " + adminUsername + " 提升为管理员");
        } else {
            System.out.println("[Admin] 管理员账号已存在: " + adminUsername);
        }
    }
}