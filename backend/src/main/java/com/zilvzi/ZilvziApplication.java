package com.zilvzi;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 自律搭子监督与习惯养成平台 - 启动类
 */
@SpringBootApplication
@MapperScan("com.zilvzi.mapper")
@EnableScheduling
public class ZilvziApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZilvziApplication.class, args);
        System.out.println("=============================================");
        System.out.println("    自律搭子监督与习惯养成平台 启动成功!");
        System.out.println("    访问地址: http://localhost:8081/admin");
        System.out.println("=============================================");
    }
}