package com.javatutorial.product_service.config;

import com.javatutorial.product_service.dto.ProductResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
public class CacheConfig {

    // No default TTL means "cache forever" - a stale product would never self-correct.
    // 60s here so a stale-cache bug surfaces in a minute during manual testing, not in
    // a demo an hour later. A real system would size this per endpoint's staleness budget.
    @Bean
    RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(60))
                // JacksonJsonRedisSerializer<ProductResponse> - bound to ONE known type, unlike
                // GenericJacksonJsonRedisSerializer which needs to embed type info in the JSON
                // to know what to deserialize back into. That embedding only kicks in for
                // non-final classes (DefaultTyping.NON_FINAL) - a record is implicitly final,
                // so no type info gets written, and reading it back produced a raw LinkedHashMap
                // instead of a ProductResponse (ClassCastException, seen live). Since every
                // entry in this cache is the same type, we do not need polymorphism at all.
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new JacksonJsonRedisSerializer<>(ProductResponse.class)));
    }
}
