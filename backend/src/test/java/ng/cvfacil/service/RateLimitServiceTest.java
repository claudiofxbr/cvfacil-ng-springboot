package ng.cvfacil.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Testes unitários para RateLimitService.
 *
 * <p>Redis é mockado — os testes verificam a lógica de allow/deny e o comportamento de fail-open
 * quando o Redis está indisponível.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

  @Mock private StringRedisTemplate redis;

  private RateLimitService service;

  @BeforeEach
  void setup() {
    service = new RateLimitService(redis);
  }

  @Test
  void allow_whenCountBelowLimit_returnsTrue() {
    when(redis.execute(any(RedisScript.class), anyList(), any(String.class))).thenReturn(3L);
    assertThat(service.allow("test-key", 5, Duration.ofMinutes(1))).isTrue();
  }

  @Test
  void allow_whenCountEqualsLimit_returnsTrue() {
    when(redis.execute(any(RedisScript.class), anyList(), any(String.class))).thenReturn(5L);
    assertThat(service.allow("test-key", 5, Duration.ofMinutes(1))).isTrue();
  }

  @Test
  void allow_whenCountExceedsLimit_returnsFalse() {
    when(redis.execute(any(RedisScript.class), anyList(), any(String.class))).thenReturn(6L);
    assertThat(service.allow("test-key", 5, Duration.ofMinutes(1))).isFalse();
  }

  @Test
  void allow_whenRedisReturnsNull_returnsTrue() {
    // null pode ocorrer em cenários de timeout — fail-open
    when(redis.execute(any(RedisScript.class), anyList(), any(String.class))).thenReturn(null);
    assertThat(service.allow("test-key", 5, Duration.ofMinutes(1))).isTrue();
  }

  @Test
  void allow_whenRedisUnavailable_returnsTrue_failOpen() {
    when(redis.execute(any(RedisScript.class), anyList(), any(String.class)))
        .thenThrow(new RedisConnectionFailureException("Connection refused"));
    // Deve retornar true (fail-open) e NÃO lançar exceção
    assertThat(service.allow("test-key", 5, Duration.ofMinutes(1))).isTrue();
  }

  @Test
  void allow_prefixesKeyWithRl() {
    when(redis.execute(any(RedisScript.class), anyList(), any(String.class))).thenReturn(1L);
    service.allow("login:1.2.3.4", 5, Duration.ofMinutes(1));
    verify(redis)
        .execute(
            any(RedisScript.class),
            eq(List.of("rl:login:1.2.3.4")),
            eq("60") // window de 60 segundos
            );
  }

  @Test
  void allow_passesWindowInSeconds() {
    when(redis.execute(any(RedisScript.class), anyList(), any(String.class))).thenReturn(1L);
    service.allow("key", 10, Duration.ofSeconds(90));
    verify(redis).execute(any(RedisScript.class), anyList(), eq("90"));
  }
}
