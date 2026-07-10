package com.smartcs.agent.notification;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SmartCS notification service entry point.
 *
 * <p>通知服务当前使用 JdbcTemplate 访问事件表，因此只关闭未使用的 MyBatis Mapper 自动配置。
 * 数据源自动配置必须保留，供 JdbcTemplate 建立 MySQL 连接。
 */
@SpringBootApplication(exclude = MybatisPlusAutoConfiguration.class)
public class SmartCsNotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartCsNotificationApplication.class, args);
    }
}
