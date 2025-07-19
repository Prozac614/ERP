package com.jsh.erp.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis性能优化配置类
 * 为ERP性能优化提供缓存支持
 */
@Configuration
@EnableCaching
public class RedisOptimizationConfig {

    /**
     * 配置RedisTemplate，用于性能优化相关的缓存操作
     */
    @Bean
    public RedisTemplate<String, Object> optimizationRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // 设置key序列化方式
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // 设置value序列化方式
        GenericJackson2JsonRedisSerializer jackson2JsonRedisSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jackson2JsonRedisSerializer);
        template.setHashValueSerializer(jackson2JsonRedisSerializer);
        
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 配置缓存管理器，为不同类型的缓存设置不同的过期时间
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(2)) // 默认2小时过期
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));

        // 为不同的缓存设置不同的过期时间
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // 每日出库汇总缓存 - 12小时过期
        cacheConfigurations.put("dailyOutSummary", defaultConfig.entryTtl(Duration.ofHours(12)));
        
        // 商品期间汇总缓存 - 6小时过期
        cacheConfigurations.put("materialPeriodSummary", defaultConfig.entryTtl(Duration.ofHours(6)));
        
        // 商品库存查询缓存 - 30分钟过期
        cacheConfigurations.put("materialStock", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        
        // 优化查询结果缓存 - 1小时过期
        cacheConfigurations.put("optimizedQuery", defaultConfig.entryTtl(Duration.ofHours(1)));
        
        // 统计数据缓存 - 4小时过期
        cacheConfigurations.put("statistics", defaultConfig.entryTtl(Duration.ofHours(4)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    /**
     * 自定义缓存键生成器
     */
    @Bean
    public KeyGenerator optimizationKeyGenerator() {
        return (target, method, params) -> {
            StringBuilder sb = new StringBuilder();
            sb.append(target.getClass().getSimpleName()).append(":");
            sb.append(method.getName()).append(":");
            
            for (Object param : params) {
                if (param != null) {
                    sb.append(param.toString()).append(":");
                }
            }
            
            // 移除最后一个冒号
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) == ':') {
                sb.deleteCharAt(sb.length() - 1);
            }
            
            return sb.toString();
        };
    }
} 