package me.kirara.sable;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Sablé 数据合规管理系统 — 启动入口。
 */
@SpringBootApplication
@EnableTransactionManagement
@EnableScheduling
@MapperScan("me.kirara.sable.**.mapper")
public class SableApplication {

    public static void main(String[] args) {
        SpringApplication.run(SableApplication.class, args);
    }

}
