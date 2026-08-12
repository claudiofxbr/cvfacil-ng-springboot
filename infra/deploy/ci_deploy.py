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


def run(cmd, **kw):
    print("+", " ".join(cmd))
    return subprocess.run(cmd, check=True, **kw)


def run_ok(cmd, **kw):
    return subprocess.run(cmd, capture_output=True, text=True, **kw)


def deploy_service(name, image_tag, dockerfile_dir, container_name, port_map,
                    extra_build_args=None):
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
    if result.returncode != 0:
        print(f"FALHOU o run de {name}: {result.stderr}")
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
    )

    deploy_service(
        "frontend",
        f"cvfacil-sb-frontend-https2:ci-{sha}",
        "/root/cvfacil-build/frontend",
        "cvfacil-sb-frontend",
        "3012:3000",
        extra_build_args=["NEXT_PUBLIC_API_BASE_URL=https://cvfacil-ng.xavierbr-vps.tech:8443"],
    )


if __name__ == "__main__":
    main()
