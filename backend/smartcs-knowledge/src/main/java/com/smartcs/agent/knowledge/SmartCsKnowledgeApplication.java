package com.smartcs.agent.knowledge;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SmartCS knowledge service entry point.
 *
 * <p>Knowledge 服务使用 JdbcTemplate 访问 MySQL FAQ，因此保留数据源自动配置；当前没有 MyBatis Mapper，
 * 只关闭未使用的 MyBatis-Plus 自动配置。
 */
@SpringBootApplication(exclude = MybatisPlusAutoConfiguration.class)
public class SmartCsKnowledgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartCsKnowledgeApplication.class, args);
    }
}
