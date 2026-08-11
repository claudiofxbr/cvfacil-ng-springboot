package ng.cvfacil.service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * Rate limit baseado em contador+TTL Redis com operação atômica via script Lua.
 *
 * <p>RACE CONDITION CORRIGIDA: a versão anterior usava dois comandos Redis separados (INCR +
 * EXPIRE), não atômicos. Em ambiente distribuído (múltiplas instâncias) isso causava dois
 * problemas: 1. Duas instâncias incrementam simultaneamente → apenas uma define o TTL → o contador
 * pode nunca expirar (rate limit permanente acidental). 2. INCR bem-sucedido + falha no EXPIRE →
 * contador sem TTL, cresce para sempre.
 *
 * <p>SOLUÇÃO: script Lua executado atomicamente pelo Redis (single-threaded). O script incrementa
 * e, se for o primeiro acesso (count == 1), define o TTL na mesma transação — garantindo
 * atomicidade sem precisar de MULTI/EXEC.
 *
 * <p>FAIL-OPEN CORRIGIDO: antes, uma queda do Redis liberava 100% das chamadas (ex.: força bruta em
 * /api/auth/login sem nenhuma proteção). Agora existe um segundo nível de defesa em memória (por
 * instância, não distribuído — é um fallback, não substitui o Redis) que continua aplicando o
 * limite localmente enquanto o Redis estiver fora do ar, e loga em ERROR (não warn) para que a
 * queda seja visível em alertas.
 */
@Service
public class RateLimitService {

  private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

  /** Fallback em memória usado somente quando o Redis está indisponível. */
  private final ConcurrentHashMap<String, LocalWindow> localFallback = new ConcurrentHashMap<>();

  private static final class LocalWindow {
    final AtomicInteger count = new AtomicInteger(0);
    volatile long resetAtMillis;
  }

  /**
   * Script Lua atômico: - INCR na chave - Se count == 1 (primeira requisição nesta janela): define
   * EXPIRE em segundos - Retorna o valor atual do contador
   *
   * <p>Executado atomicamente pelo Redis — sem race condition possível.
   */
  private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;

  static {
    RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
    RATE_LIMIT_SCRIPT.setResultType(Long.class);
    RATE_LIMIT_SCRIPT.setScriptText(
        "local count = redis.call('INCR', KEYS[1])\n"
            + "if count == 1 then\n"
            + "  redis.call('EXPIRE', KEYS[1], ARGV[1])\n"
            + "end\n"
            + "return count");
  }

  private final StringRedisTemplate redis;

  public RateLimitService(StringRedisTemplate redis) {
    this.redis = redis;
  }

  /**
   * Verifica e incrementa o contador de rate limit.
   *
   * @param key Chave identificadora (ex: "login:192.168.1.1")
   * @param limit Número máximo de requisições permitidas na janela
   * @param window Duração da janela de tempo
   * @return true se a requisição é permitida, false se o limite foi excedido
   */
  public boolean allow(String key, int limit, Duration window) {
    try {
      Long count =
          redis.execute(
              RATE_LIMIT_SCRIPT, List.of("rl:" + key), String.valueOf(window.getSeconds()));
      return count == null || count <= limit;
    } catch (RedisConnectionFailureException e) {
      log.error(
          "[rate-limit] Redis indisponível para '{}' — usando fallback local em memória "
              + "(protege apenas esta instância; não é distribuído). {}",
          key,
          e.getMessage());
      return allowLocalFallback(key, limit, window);
    }
  }

  /**
   * Janela fixa em memória, por chave. Só entra em uso durante uma queda do Redis — não substitui a
   * proteção distribuída, apenas evita que o rate limit desapareça por completo nesse intervalo.
   */
  private boolean allowLocalFallback(String key, int limit, Duration window) {
    long now = System.currentTimeMillis();
    if (localFallback.size() > 10_000) {
      // Evita crescimento sem limite do mapa se o Redis ficar fora do ar por
      // muito tempo com muitas chaves distintas (ex.: IPs diferentes).
      localFallback.entrySet().removeIf(e -> now >= e.getValue().resetAtMillis);
    }
    LocalWindow w =
        localFallback.compute(
            key,
            (k, existing) -> {
              if (existing == null || now >= existing.resetAtMillis) {
                LocalWindow fresh = new LocalWindow();
                fresh.resetAtMillis = now + window.toMillis();
                return fresh;
              }
              return existing;
            });
    return w.count.incrementAndGet() <= limit;
  }
}
