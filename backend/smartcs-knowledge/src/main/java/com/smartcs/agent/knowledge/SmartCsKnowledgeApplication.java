package com.smartcs.agent.knowledge;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * SmartCS knowledge service entry point.
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, MybatisPlusAutoConfiguration.class})
public class SmartCsKnowledgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartCsKnowledgeApplication.class, args);
    }
}
