#!/usr/bin/env python3
"""Build + cutover reutilizavel para backend e frontend do CVFacil.NG na VPS.

Reaproveita as env vars ja configuradas no container em producao (nunca
recebe segredos do GitHub Actions) — so troca a imagem. Mantem 1 geracao
anterior de cada servico como rollback (a mais antiga eh removida).

Uso: python3 ci_deploy.py <git-sha-curto>
"""
import json
import subprocess
import sys
import time
import urllib.request


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
                    health_url, extra_build_args=None):
    build_cmd = ["docker", "build", "-t", image_tag]
    for arg in (extra_build_args or []):
        build_cmd += ["--build-arg", arg]
    build_cmd.append(dockerfile_dir)
    run(build_cmd)

    old_name = container_name + "-prev"
    # remove rollback generation anterior (mantem so 1 nivel de historico)
    run_ok(["docker", "rm", "-f", old_name])

    env = json.loads(run_ok(
        ["docker", "inspect", container_name, "--format", "{{json .Config.Env}}"]
    ).stdout)

    run_ok(["docker", "stop", container_name])
    run_ok(["docker", "rename", container_name, old_name])

    cmd = ["docker", "run", "-d", "--name", container_name,
           "--network", "cvfacil-sb-net", "-p", port_map, "--restart", "unless-stopped"]
    for e in env:
        cmd += ["--env", e]
    cmd.append(image_tag)

    result = run_ok(cmd)
    started = result.returncode == 0
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

    deploy_service(
        "backend",
        f"cvfacil-backend-demo:ci-{sha}",
        "/root/cvfacil-build/backend",
        "cvfacil-sb-backend",
        "8095:8080",
        health_url="http://localhost:8095/actuator/health",
    )

    deploy_service(
        "frontend",
        f"cvfacil-sb-frontend-https2:ci-{sha}",
        "/root/cvfacil-build/frontend",
        "cvfacil-sb-frontend",
        "3012:3000",
        health_url="http://localhost:3012/login",
        extra_build_args=["NEXT_PUBLIC_API_BASE_URL=https://cvfacil-ng.xavierbr-vps.tech:8443"],
    )

    # Evita acumulo indefinido de imagens antigas (uma nova tag por deploy).
    run_ok(["docker", "image", "prune", "-f"])


if __name__ == "__main__":
    main()
