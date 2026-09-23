package com.smartlife.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云 OSS 四件套，来源 application-local.yml（gitignore）或环境变量。
 */
@Data
@Component
@ConfigurationProperties(prefix = "smartlife.oss")
public class OssProperties {

    private String endpoint;

    private String bucket;

    private String accessKeyId;

    private String accessKeySecret;
}
