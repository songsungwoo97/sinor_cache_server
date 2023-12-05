package com.sinor.cache.config;

import java.time.Duration;
import java.util.Objects;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class RedisConfig {

	@Bean
	public RedisConnectionFactory redisConnectionFactory() {
		return new LettuceConnectionFactory("redisHost", 6379); // 여러 다른 Redis 연결 방법이 있을 수 있습니다.
	}

	@Bean
	public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
		RedisTemplate<String, String> template = new RedisTemplate<>();
		template.setConnectionFactory(redisConnectionFactory);
		template.setKeySerializer(new StringRedisSerializer());
		template.setValueSerializer(new StringRedisSerializer());
		return template;
	}

	@Bean
	public RedisCacheManager redisCacheManager(RedisTemplate<String, String> redisTemplate) {
		RedisCacheConfiguration cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
			.entryTtl(Duration.ofMinutes(10))
			.disableCachingNullValues();

		return RedisCacheManager.builder(Objects.requireNonNull(redisTemplate.getConnectionFactory()))
			.cacheDefaults(cacheConfig)
			.build();
	}

    /*@Bean
    public Map<String, RedisCacheManager> redisCacheManagers() {
        // 여러 RedisCacheManager를 저장할 Map
        Map<String, RedisCacheManager> redisCacheManagers = new HashMap<>();
        redisCacheManagers.put("appProducts", redisCacheManager(redisTemplate(redisConnectionFactory())));
        redisCacheManagers.put("appUsers", redisCacheManager(redisTemplate(redisConnectionFactory())));
        return redisCacheManagers;
    }*/

	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper();
	}
}
