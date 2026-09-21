package com.smartlife.server.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 * Mapper 注册统一用接口上的 @Mapper，不要在这里加 @MapperScan——
 * 两者同时存在时，@MapperScan 会让 @Mapper 失效（自动扫描被短路），
 * 留着就是误导。
 * 依赖坐标必须是 mybatis-plus-spring-boot3-starter，用错则启动不报错但 Mapper 注入全失败。
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 分页插件。
     * 没有它，Page 查询不会真正分页——SQL 里不会拼 LIMIT，
     * 而是把全表捞回来在内存里切，数据量一大就是事故。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
