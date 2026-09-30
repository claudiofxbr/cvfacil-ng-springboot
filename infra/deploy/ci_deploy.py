#!/usr/bin/env python3
"""Build + cutover reutilizavel para backend e frontend do CVFacil.NG na VPS.

Reaproveita as env vars ja configuradas no container em producao (nunca
recebe segredos do GitHub Actions) — so troca a imagem. Mantem 1 geracao
anterior de cada servico como rollback (a mais antiga eh removida).

Publicacao: desde 30/09, o Caddy dedicado (porta 8443) saiu de cena — a VPS
passou a usar EasyPanel/Traefik (80/443) como borda unica, igual ao
PortalCursos.NG. Os containers entram na rede docker "easypanel" com labels
Traefik (nao mais "-p 8443:..."); a URL publica perdeu a porta customizada.

Uso: python3 ci_deploy.py <git-sha-curto>
"""
import json
import subprocess
import sys
import time
import urllib.request

# Dominio publico unico, atras do Traefik (sem porta customizada desde a
# migracao para EasyPanel).
PUBLIC_HOST = "cvfacil-ng.xavierbr-vps.tech"
TRAEFIK_NETWORK = "easypanel"

# Corrige, a cada deploy, env vars publicas que ainda apontavam para o Caddy
# antigo (":8443") — assim a correcao se propaga sozinha daqui pra frente,
# sem precisar de comando manual na VPS tocando em env var nenhuma vez.
ENV_URL_OVERRIDES = {
    "JWT_ISSUER": f"https://{PUBLIC_HOST}",
    "FRONTEND_BASE_URL": f"https://{PUBLIC_HOST}",
    "CORS_ALLOWED_ORIGINS": f"https://{PUBLIC_HOST}",
    "PAGSEGURO_NOTIFICATION_URL": f"https://{PUBLIC_HOST}/api/credits/webhook/pagseguro",
}


def fix_stale_8443_env(env_list):
    """Substitui o valor de chaves conhecidas que ainda tem ':8443' — o
    resto do env (segredos inclusive) passa direto, sem ser lido/logado."""
    fixed = []
    for entry in env_list:
        key = entry.split("=", 1)[0]
        if key in ENV_URL_OVERRIDES and ":8443" in entry:
            fixed.append(f"{key}={ENV_URL_OVERRIDES[key]}")
        else:
            fixed.append(entry)
    return fixed


def run(cmd, **kw):
    print("+", " ".join(cmd))
    return subprocess.run(cmd, check=True, **kw)


def run_ok(cmd, **kw):
    return subprocess.run(cmd, capture_output=True, text=True, **kw)


def wait_healthy(url, attempts=10, delay=3):
    """Poll url ate responder 200. Da tempo pro app subir (JVM/Next.js) antes
    de decidir que o deploy falhou de verdade."""
    for attempt in range(1, attempts + 1):
        try:
            with urllib.request.urlopen(url, timeout=3) as resp:
                if resp.status == 200:
                    return True
        except Exception as exc:
            print(f"  healthcheck {url} tentativa {attempt}/{attempts}: {exc}")
        time.sleep(delay)
    return False


def deploy_service(name, image_tag, dockerfile_dir, container_name, port_map,
                    health_url, extra_build_args=None, traefik_labels=None):
    build_cmd = ["docker", "build", "-t", image_tag]
    for arg in (extra_build_args or []):
        build_cmd += ["--build-arg", arg]
    build_cmd.append(dockerfile_dir)
    run(build_cmd)

    old_name = container_name + "-prev"
    # remove rollback generation anterior (mantem so 1 nivel de historico)
    run_ok(["docker", "rm", "-f", old_name])

    inspect = run_ok(
        ["docker", "inspect", container_name, "--format", "{{json .Config.Env}}"]
    )
    if inspect.returncode == 0:
        env = fix_stale_8443_env(json.loads(inspect.stdout))
        run_ok(["docker", "stop", container_name])
        run_ok(["docker", "rename", container_name, old_name])
    else:
        # Container nao existe (ex.: sumiu manualmente, como o frontend em
        # 30/09) — sobe do zero, sem env herdado, em vez de travar o deploy.
        print(f"aviso: {container_name} nao existe ainda, subindo pela primeira vez")
        env = []

    cmd = ["docker", "run", "-d", "--name", container_name,
           "--network", "cvfacil-sb-net", "-p", port_map, "--restart", "unless-stopped"]
    for e in env:
        cmd += ["--env", e]
    for label in (traefik_labels or []):
        cmd += ["--label", label]
    cmd.append(image_tag)

    result = run_ok(cmd)
    started = result.returncode == 0
    if started:
        # "docker run" so aceita uma rede na criacao — conecta na do Traefik
        # depois. Falha aqui nao derruba o deploy (o app ja subiu, so nao
        # fica acessivel via Traefik ainda) mas fica registrada no log.
        connect = run_ok(["docker", "network", "connect", TRAEFIK_NETWORK, container_name])
        if connect.returncode != 0 and "already exists" not in connect.stderr:
            print(f"aviso: falha ao conectar {container_name} na rede {TRAEFIK_NETWORK}: {connect.stderr.strip()}")
    # O "docker run" so garante que o container iniciou — nao que a app
    # responde de verdade (ex: erro de config, migracao pendente). Por isso
    # o rollback so e considerado seguro apos o healthcheck HTTP passar.
    healthy = started and wait_healthy(health_url)

    if not started or not healthy:
        motivo = "docker run falhou" if not started else f"healthcheck {health_url} nao respondeu 200"
        print(f"FALHOU o deploy de {name}: {motivo}")
        if not started:
            print(result.stderr)
        else:
            # Sem isso, o motivo do crash (stack trace, erro de bean, etc.) se perde
            # assim que o container e removido no rollback abaixo.
            logs = run_ok(["docker", "logs", "--tail", "200", container_name])
            print(f"--- docker logs {container_name} (ultimas 200 linhas, stdout) ---")
            print(logs.stdout)
            print(f"--- docker logs {container_name} (ultimas 200 linhas, stderr) ---")
            print(logs.stderr)
        print(f"Revertendo {name} para a versao anterior...")
        run_ok(["docker", "stop", container_name])
        run_ok(["docker", "rm", "-f", container_name])
        run_ok(["docker", "rename", old_name, container_name])
        run_ok(["docker", "start", container_name])
        sys.exit(1)

    print(f"{name} OK: {result.stdout.strip()}")


def main():
    sha = sys.argv[1] if len(sys.argv) > 1 else "latest"

    # Roteador da API tem que vencer o catch-all do frontend no mesmo Host —
    # prioridade explicita, nao só a regra mais especifica (mais previsível
    # e sobrevive a mudanças futuras no PathPrefix).
    backend_labels = [
        "traefik.enable=true",
        f"traefik.docker.network={TRAEFIK_NETWORK}",
        "traefik.http.routers.cvfacil-api-http.entrypoints=http",
        # Traefik 3.x: PathPrefix() so aceita UM parametro por chamada (nao
        # uma lista separada por virgula, que era sintaxe de outra versao) --
        # combina com || para os 3 prefixos da API. Erro real de producao em
        # 30/09: com a lista, o router nunca registrava (log do Traefik:
        # "unexpected number of parameters; got 3, expected one of [1]").
        f"traefik.http.routers.cvfacil-api-http.rule=Host(`{PUBLIC_HOST}`) && (PathPrefix(`/api`) || PathPrefix(`/oauth2`) || PathPrefix(`/login/oauth2`))",
        "traefik.http.routers.cvfacil-api-http.middlewares=redirect-to-https@file",
        "traefik.http.routers.cvfacil-api-http.priority=10",
        "traefik.http.routers.cvfacil-api.entrypoints=https",
        f"traefik.http.routers.cvfacil-api.rule=Host(`{PUBLIC_HOST}`) && (PathPrefix(`/api`) || PathPrefix(`/oauth2`) || PathPrefix(`/login/oauth2`))",
        "traefik.http.routers.cvfacil-api.tls=true",
        "traefik.http.routers.cvfacil-api.tls.certresolver=letsencrypt",
        "traefik.http.routers.cvfacil-api.service=cvfacil-api-svc",
        "traefik.http.routers.cvfacil-api.priority=10",
        "traefik.http.services.cvfacil-api-svc.loadbalancer.server.port=8080",
        "traefik.http.services.cvfacil-api-svc.loadbalancer.healthcheck.path=/actuator/health",
        "traefik.http.services.cvfacil-api-svc.loadbalancer.healthcheck.interval=10s",
        "traefik.http.services.cvfacil-api-svc.loadbalancer.healthcheck.timeout=5s",
    ]
    frontend_labels = [
        "traefik.enable=true",
        f"traefik.docker.network={TRAEFIK_NETWORK}",
        "traefik.http.routers.cvfacil-web-http.entrypoints=http",
        f"traefik.http.routers.cvfacil-web-http.rule=Host(`{PUBLIC_HOST}`)",
        "traefik.http.routers.cvfacil-web-http.middlewares=redirect-to-https@file",
        # Prioridade 1 empatava com o catch-all global da propria EasyPanel
        # (https-error-page@file, HostRegexp(`.+`), tambem priority=1) -- no
        # empate o Traefik nao desempatava a nosso favor e a requisicao ia
        # pro "app not found" da EasyPanel em vez do nosso frontend (502 real
        # em producao em 30/09, confirmado via API do Traefik). 5 fica acima
        # do catch-all (1) e abaixo da API (10), sem depender de como a
        # EasyPanel prioriza suas proprias rotas.
        "traefik.http.routers.cvfacil-web-http.priority=5",
        "traefik.http.routers.cvfacil-web.entrypoints=https",
        f"traefik.http.routers.cvfacil-web.rule=Host(`{PUBLIC_HOST}`)",
        "traefik.http.routers.cvfacil-web.tls=true",
        "traefik.http.routers.cvfacil-web.tls.certresolver=letsencrypt",
        "traefik.http.routers.cvfacil-web.service=cvfacil-web-svc",
        "traefik.http.routers.cvfacil-web.priority=5",
        "traefik.http.services.cvfacil-web-svc.loadbalancer.server.port=3000",
    ]

    deploy_service(
        "backend",
        f"cvfacil-backend-demo:ci-{sha}",
        "/root/cvfacil-build/backend",
        "cvfacil-sb-backend",
        # Loopback apenas — Traefik fala com o container pela rede docker
        # "easypanel", nao precisa (nem deve) ficar exposto em 0.0.0.0.
        "127.0.0.1:8095:8080",
        health_url="http://localhost:8095/actuator/health",
        traefik_labels=backend_labels,
    )

    deploy_service(
        "frontend",
        f"cvfacil-sb-frontend-https2:ci-{sha}",
        "/root/cvfacil-build/frontend",
        "cvfacil-sb-frontend",
        "127.0.0.1:3012:3000",
        health_url="http://localhost:3012/login",
        # Sem porta customizada — atras do Traefik o dominio publico e so
        # https://{PUBLIC_HOST}, a antiga ":8443" saiu com o Caddy dedicado.
        extra_build_args=[f"NEXT_PUBLIC_API_BASE_URL=https://{PUBLIC_HOST}"],
        traefik_labels=frontend_labels,
    )

    # Evita acumulo indefinido de imagens antigas (uma nova tag por deploy).
    run_ok(["docker", "image", "prune", "-f"])


if __name__ == "__main__":
    main()
