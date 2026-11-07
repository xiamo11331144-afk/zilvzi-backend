package com.zilvzi.config;

import com.zilvzi.mapper.UserMapper;
import com.zilvzi.pojo.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Optionally creates the bootstrap administrator when explicitly configured.
 */
@Component
public class AdminInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    @Autowired
    private UserMapper userMapper;

    @Value("${admin.bootstrap.username:admin}")
    private String adminUsername;

    @Value("${admin.bootstrap.password:}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (adminPassword == null || adminPassword.isBlank()) {
            log.info("Admin bootstrap skipped: no bootstrap password is configured");
            return;
        }
        if (adminUsername == null || adminUsername.isBlank()) {
            throw new IllegalStateException("Admin bootstrap username must not be blank");
        }

        User existing = userMapper.selectByUsername(adminUsername);
        if (existing == null) {
            User admin = new User();
            admin.setUsername(adminUsername);
            admin.setPassword(new BCryptPasswordEncoder().encode(adminPassword));
            admin.setNickname("管理员");
            admin.setRole(1);
            admin.setLevel(99);
            admin.setExperience(0);
            userMapper.insert(admin);
            log.info("Bootstrap administrator created for configured username");
        } else if (existing.getRole() == null || existing.getRole() != 1) {
            throw new IllegalStateException(
                    "Admin bootstrap username already belongs to a non-administrator; no role changes were made");
        } else {
            log.info("Bootstrap administrator already exists");
        }
    }
}
