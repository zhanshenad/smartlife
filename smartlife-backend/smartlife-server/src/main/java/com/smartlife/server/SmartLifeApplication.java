package com.smartlife.server;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 启动类。
 * scanBasePackages 必须显式写成 com.smartlife：本类位于
 * com.smartlife.server，默认只扫这个包及其子包，扫不到 com.smartlife.common
 * 里的 GlobalExceptionHandler——不写它的表现是"异常兜底莫名不生效"，很难排查。
 */
@Slf4j
@SpringBootApplication(scanBasePackages = "com.smartlife")
public class SmartLifeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartLifeApplication.class, args);
        log.info("""

                ==========================================================
                  智慧生活 SmartLife 启动成功
                  接口文档：http://localhost:8086/doc.html
                ==========================================================
                """);
    }
}
