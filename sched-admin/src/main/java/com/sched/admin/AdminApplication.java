package com.sched.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 调度中心启动类。
 * 扫描范围放宽到 com.sched，否则 sched-core / sched-dao 里的 @Service 不会被发现。
 */
@SpringBootApplication(scanBasePackages = "com.sched")
@MapperScan({"com.sched.dao.mapper"})
@EnableScheduling
public class AdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
