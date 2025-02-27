package com.sparta.product.redis;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class RedisUtility {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisUtility(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // Redis에서 분산락을 획득
    public boolean tryLock(String key) {
        return redisTemplate.opsForValue().setIfAbsent(key, "LOCK", 10, TimeUnit.SECONDS);
    }

    // Redis 락 해제
    public void releaseLock(String key) {
        redisTemplate.delete(key);
    }




    // ( 변경 후 )
    public boolean acquireLock(String lockKey, long timeout, TimeUnit timeUnit) {
        int maxRetryCount = 10; // 최대 재시도 횟수
        long retryInterval = 100; // 재시도 간격 (밀리초 단위)

        for (int i = 0; i < maxRetryCount; i++) {
            Boolean success = redisTemplate.opsForValue().setIfAbsent(lockKey, "locked", timeout, timeUnit);
            if (Boolean.TRUE.equals(success)) {
                return true; // 락을 성공적으로 획득한 경우 true 반환
            }
            try {
                Thread.sleep(retryInterval); // 재시도 간격만큼 대기
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 스레드 인터럽트 상태 복구
                throw new IllegalStateException("락 획득 중 인터럽트가 발생했습니다.", e);
            }
        }

        return false; // 최대 재시도 횟수를 초과하면 false 반환
    }

    // TTL 없이 저장 (기본 저장 방식 유지)
    public void saveToCache(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    // TTL 설정 가능
    public void saveToCache(String key, Object value, long ttlInSeconds) {
        redisTemplate.opsForValue().set(key, value, ttlInSeconds, TimeUnit.SECONDS);
    }


    // 제네릭을 활용한 값 조회 메서드
    public <T> T getFromCache(String key, Class<T> type) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value != null && type.isInstance(value)) {
            return type.cast(value); // 지정된 타입으로 안전하게 반환
        }
        return null; // 캐시에 값이 없거나 타입이 일치하지 않으면 null 반환
    }


    // 값 삭제
    public void deleteFromCache(String key) {
        redisTemplate.delete(key);
    }
}