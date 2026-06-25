package com.smartcs.agent.notification;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * SmartCS notification service entry point.
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, MybatisPlusAutoConfiguration.class})
public class SmartCsNotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartCsNotificationApplication.class, args);
    }
}
