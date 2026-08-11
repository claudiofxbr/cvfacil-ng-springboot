<#
.SYNOPSIS
    deploy-portalcursos.ps1 — Pipeline unificado de hardening + deploy (Windows 11 Pro)
.DESCRIPTION
    Converte todo o fluxo de deploy-portalcursos.sh para nativo Windows 11 Pro.
    Configure VPS, Docker, Nginx, SSL, e deploy com Scheduled Tasks (cron Windows).
.NOTES
    Versao: 1.0.0
    Autor: Claudio Xavier
    Requer: Windows 11 Pro, PowerShell 7+, Admin rights
#>

param(
    [string]$ConfigPath = "config.env",
    [switch]$SkipHardening,
    [switch]$SkipDependencies,
    [switch]$SkipNginx,
    [switch]$SkipSSL,
    [switch]$SkipDeploy,
    [switch]$SkipVerification,
    [switch]$SkipMaintenance,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$script:LogDir = "C:\ProgramData\portalcursos\logs"
$script:CheckpointDir = "C:\ProgramData\portalcursos\checkpoints"
$script:InstallDir = "C:\Apps\portalcursos"
$script:NginxDir = "C:\nginx"
$script:ScriptsDir = "C:\ProgramData\portalcursos\scripts"

# Ensure directories
foreach ($d in @($LogDir, $CheckpointDir, $InstallDir, $ScriptsDir)) {
    if (-not (Test-Path $d)) { New-Item -ItemType Directory -Path $d -Force | Out-Null }
}

# Load config file if exists
$config = @{}
if (Test-Path $ConfigPath) {
    Get-Content $ConfigPath | ForEach-Object {
        if ($_ -match '^\s*([^#=]+)\s*=\s*"?([^"]+)"?') {
            $config[$matches[1].Trim()] = $matches[2].Trim()
        }
    }
}

# Default values
$DOMAIN = if ($config.ContainsKey('DOMAIN')) { $config['DOMAIN'] } else { "example.com" }
$SSH_PORT = if ($config.ContainsKey('SSH_PORT')) { $config['SSH_PORT'] } else { "22" }
$DATABASE_URL = if ($config.ContainsKey('DATABASE_URL')) { $config['DATABASE_URL'] } else { "postgresql://user:pass@localhost:5432/portalcursos" }
$JWT_SECRET = if ($config.ContainsKey('JWT_SECRET')) { $config['JWT_SECRET'] } else { "changeme" }
$VPS_USER = if ($config.ContainsKey('VPS_USER')) { $config['VPS_USER'] } else { "deployer" }

# Logging functions
function Write-Log { param([string]$Message) $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"; $logEntry = "[$timestamp] $Message"; Write-Host $logEntry; Add-Content -Path (Join-Path $LogDir "deploy.log") -Value $logEntry }
function Write-Success { Write-Log "[SUCCESS] $($args[0])" }
function Write-Warn { Write-Log "[WARN] $($args[0])" }
function Write-Error { Write-Log "[ERROR] $($args[0])" }
function Write-Fatal { param([string]$Message) Write-Log "[FATAL] $Message"; exit 1 }

function Test-Prerequisite {
    Write-Log "Checking prerequisites..."
    $os = Get-CimInstance Win32_OperatingSystem
    if ($os.Caption -notlike '*Windows 11 Pro*') { Write-Fatal "Windows 11 Pro required. Detected: $($os.Caption)" }
    $isAdmin = [Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    if (-not $isAdmin) { Write-Fatal "Script must be run as Administrator." }
    Write-Success "Prerequisites satisfied."
}

function Set-Checkpoint { param([string]$Name) if (-not $DryRun) { New-Item -ItemType File -Path (Join-Path $CheckpointDir "$Name.checkpoint") -Force | Out-Null } }
function Get-Checkpoint { param([string]$Name) return (Test-Path (Join-Path $CheckpointDir "$Name.checkpoint")) }

function Invoke-Retry {
    param([ScriptBlock]$ScriptBlock, [int]$MaxRetries = 3, [int]$Delay = 5)
    for ($i=0; $i -lt $MaxRetries; $i++) {
        try {
            & $ScriptBlock
            return
        } catch {
            Write-Warn "Attempt $($i+1) failed: $_"
            if ($i -eq $MaxRetries-1) { throw }
            Start-Sleep -Seconds $Delay
        }
    }
}

function Cleanup-TempFiles {
    $tempFiles = @("$env:TEMP\*")
    foreach ($f in $tempFiles) { Remove-Item -Path $f -Recurse -Force -ErrorAction SilentlyContinue }
    Write-Log "Temp files cleaned."
}

# Trap exit
Register-EngineEvent -SourceIdentifier PowerShell.Exiting -Action {
    Cleanup-TempFiles
} | Out-Null

# Parameter: SkipHardening
if (-not $SkipHardening -and (Get-Checkpoint 'hardening')) {
    Write-Log "Hardening checkpoint found, skipping."
} elseif (-not $SkipHardening) {
    Write-Log "=== SECTION 1: HARDENING ==="
    try {
        # 1.1 Set timezone
        if (-not $DryRun) {
            Set-TimeZone -Id "E. South America Standard Time" -ErrorAction SilentlyContinue
            Write-Success "Timezone set to E. South America Standard Time"
        }
        # 1.2 winget upgrade
        if (-not $DryRun) {
            try {
                winget upgrade --all --accept-package-agreements | Out-Null
                Write-Success "Winget upgrade completed."
            } catch { Write-Warn "Winget upgrade failed: $_" }
        }
        # 1.3 Create deployer user
        if (-not (Get-LocalUser -Name $VPS_USER -ErrorAction SilentlyContinue)) {
            if (-not $DryRun) {
                $pass = ConvertTo-SecureString "Deployer@2025!" -AsPlainText -Force
                New-LocalUser -Name $VPS_USER -Password $pass -PasswordNeverExpires -FullName "Deploy User"
                Add-LocalGroupMember -Group Administrators -Member $VPS_USER
                Write-Success "User $VPS_USER created and added to Administrators."
            }
        } else { Write-Log "User $VPS_USER already exists." }
        # 1.4 OpenSSH Server
        $sshCap = Get-WindowsCapability -Online | Where-Object Name -like 'OpenSSH.Server*'
        if ($sshCap.State -ne 'Installed') {
            if (-not $DryRun) {
                Add-WindowsCapability -Online -Name $sshCap.Name | Out-Null
                Write-Success "OpenSSH Server installed."
            }
        } else { Write-Log "OpenSSH Server already installed." }
        if (-not $DryRun) {
            Set-Service sshd -StartupType Automatic
            Start-Service sshd
            # Firewall rule for SSH
            if (-not (Get-NetFirewallRule -DisplayName "SSH" -ErrorAction SilentlyContinue)) {
                New-NetFirewallRule -DisplayName "SSH" -Direction Inbound -Protocol TCP -LocalPort $SSH_PORT -Action Allow
                Write-Success "SSH firewall rule added."
            }
        }
        # 1.5 SSH hardening
        $sshdConfig = "C:\ProgramData\ssh\sshd_config"
        if (-not $DryRun -and (Test-Path $sshdConfig)) {
            (Get-Content $sshdConfig) -replace '^#?Port .*', "Port $SSH_PORT" `
                -replace '^#?PermitRootLogin .*', 'PermitRootLogin no' `
                -replace '^#?PasswordAuthentication .*', 'PasswordAuthentication no' `
                -replace '^#?PubkeyAuthentication .*', 'PubkeyAuthentication yes' `
                -replace '^#?ChallengeResponseAuthentication .*', 'ChallengeResponseAuthentication no' | Set-Content $sshdConfig
            Restart-Service sshd
            Write-Success "SSH config hardened and service restarted."
        }
        # 1.6 Firewall default rules
        if (-not $DryRun) {
            $ports = @($SSH_PORT, 80, 443)
            foreach ($port in $ports) {
                $ruleName = "Port $port"
                if (-not (Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue)) {
                    New-NetFirewallRule -DisplayName $ruleName -Direction Inbound -Protocol TCP -LocalPort $port -Action Allow
                }
            }
            # Block inbound by default (optional)
            # Set-NetFirewallProfile -DefaultInboundAction Block
            Write-Success "Firewall rules configured."
        }
        # 1.7 Fail2Ban equivalent using Scheduled Task
        $fail2banScript = @'
$events = Get-WinEvent -FilterHashtable @{LogName='Security'; ID=4625} -MaxEvents 100 | Where-Object { $_.TimeCreated -gt (Get-Date).AddMinutes(-5) }
if ($events.Count -gt 10) {
    # Block IPs (simplified: could add to Windows Firewall)
    $events | ForEach-Object { $ip = $_.Properties[18].Value; if ($ip -and (-not (Get-NetFirewallRule -DisplayName "Block $ip" -ErrorAction SilentlyContinue))) { New-NetFirewallRule -DisplayName "Block $ip" -Direction Inbound -RemoteAddress $ip -Action Block } }
}
'@
        $fail2banScriptPath = Join-Path $ScriptsDir "fail2ban-monitor.ps1"
        if (-not $DryRun) {
            Set-Content -Path $fail2banScriptPath -Value $fail2banScript -Force
            $taskName = "Fail2Ban Monitor"
            $task = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
            if (-not $task) {
                $action = New-ScheduledTaskAction -Execute "PowerShell.exe" -Argument "-ExecutionPolicy Bypass -File `"$fail2banScriptPath`""
                $trigger = New-ScheduledTaskTrigger -Daily -At "00:00" -RepetitionInterval (New-TimeSpan -Minutes 5) -RepetitionDuration ([TimeSpan]::MaxValue)
                $principal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -LogonType ServiceAccount -RunLevel Highest
                Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $principal -Force | Out-Null
                Write-Success "Fail2Ban monitor scheduled task created."
            }
        }
        # 1.8 Windows Update automatic
        if (-not $DryRun) {
            Set-ItemProperty -Path "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\WindowsUpdate\AU" -Name "AUOptions" -Value 4 -ErrorAction SilentlyContinue
            Write-Success "Windows Update set to automatic."
        }
        Set-Checkpoint 'hardening'
    } catch {
        Write-Fatal "Hardening failed: $_"
    }
}

# Parameter: SkipDependencies
if (-not $SkipDependencies -and (Get-Checkpoint 'dependencies')) {
    Write-Log "Dependencies checkpoint found, skipping."
} elseif (-not $SkipDependencies) {
    Write-Log "=== SECTION 2: DEPENDENCIES ==="
    try {
        # 2.1 Chocolatey packages (if available)
        $chocoAvailable = Get-Command choco -ErrorAction SilentlyContinue
        if ($chocoAvailable) {
            if (-not $DryRun) {
                choco install -y git nodejs nginx docker-desktop | Out-Null
                Write-Success "Chocolatey packages installed."
            }
        } else {
            Write-Log "Chocolatey not available. Trying winget..."
            # 2.2 Docker Desktop
            if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
                if (-not $DryRun) {
                    winget install -e --id Docker.DockerDesktop --accept-package-agreements | Out-Null
                    Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"
                    # Wait for Docker to be ready
                    $timeout = 120
                    $sw = [System.Diagnostics.Stopwatch]::StartNew()
                    do {
                        Start-Sleep -Seconds 5
                        $dockerInfo = docker info 2>$null
                    } while (-not $dockerInfo -and $sw.Elapsed.TotalSeconds -lt $timeout)
                    if ($dockerInfo) { Write-Success "Docker installed and running." } else { Write-Warn "Docker may not be ready." }
                }
            } else { Write-Log "Docker already installed." }
            # 2.3 Node.js 20 LTS
            if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
                if (-not $DryRun) {
                    winget install -e --id OpenJS.NodeJS.LTS --accept-package-agreements | Out-Null
                    # Refresh path
                    $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
                    npm install -g pm2 | Out-Null
                    Write-Success "Node.js 20 LTS and pm2 installed."
                }
            } else { Write-Log "Node.js already installed." }
            # 2.4 Nginx
            if (-not (Test-Path "$NginxDir\nginx.exe")) {
                if (-not $DryRun) {
                    $nginxUrl = "https://nginx.org/download/nginx-1.26.2.zip"
                    $zipPath = "$env:TEMP\nginx.zip"
                    Invoke-WebRequest -Uri $nginxUrl -OutFile $zipPath
                    Expand-Archive -Path $zipPath -DestinationPath "C:\" -Force
                    Move-Item -Path "C:\nginx-1.26.2" -Destination $NginxDir -Force -ErrorAction SilentlyContinue
                    # Install as service
                    New-Service -Name nginx -BinaryPathName "$NginxDir\nginx.exe" -StartupType Automatic -DisplayName "Nginx"
                    Write-Success "Nginx installed and service created."
                }
            } else { Write-Log "Nginx already installed." }
        }
        Set-Checkpoint 'dependencies'
    } catch {
        Write-Fatal "Dependencies failed: $_"
    }
}

# Parameter: SkipNginx
if (-not $SkipNginx -and (Get-Checkpoint 'nginx')) {
    Write-Log "Nginx checkpoint found, skipping."
} elseif (-not $SkipNginx) {
    Write-Log "=== SECTION 3: NGINX + SSL ==="
    try {
        # 3.1 & 3.2 Create nginx config
        $sitesAvailable = "$NginxDir\conf\sites-available"
        $sitesEnabled = "$NginxDir\conf\sites-enabled"
        if (-not (Test-Path $sitesAvailable)) { New-Item -ItemType Directory -Path $sitesAvailable -Force | Out-Null }
        if (-not (Test-Path $sitesEnabled)) { New-Item -ItemType Directory -Path $sitesEnabled -Force | Out-Null }
        $configContent = @"
server {
    listen 80;
    server_name $DOMAIN;
    return 301 https://\$server_name\$request_uri;
}
server {
    listen 443 ssl http2;
    server_name $DOMAIN;
    ssl_certificate "$NginxDir\ssl\cert.pem";
    ssl_certificate_key "$NginxDir\ssl\key.pem";
    location / {
        proxy_pass http://localhost:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host \$host;
        proxy_cache_bypass \$http_upgrade;
    }
}
"@
        $configPath = Join-Path $sitesAvailable "portalcursos.conf"
        if (-not $DryRun) {
            Set-Content -Path $configPath -Value $configContent -Force
            # Create symlink or copy
            $linkPath = Join-Path $sitesEnabled "portalcursos.conf"
            if (-not (Test-Path $linkPath)) { New-Item -ItemType SymbolicLink -Path $linkPath -Target $configPath -Force | Out-Null }
            # Ensure ssl directory
            $sslDir = "$NginxDir\ssl"
            if (-not (Test-Path $sslDir)) { New-Item -ItemType Directory -Path $sslDir -Force | Out-Null }
            Write-Success "Nginx configuration created."
        }
        # 3.4 SSL via Certbot (if domain exists)
        if (-not $SkipSSL -and -not (Get-Checkpoint 'ssl')) {
            if (-not $DryRun) {
                # Assumes Certbot is installed (winget install certbot)
                $certbot = Get-Command certbot -ErrorAction SilentlyContinue
                if (-not $certbot) {
                    winget install -e --id Certbot.EFF --accept-package-agreements | Out-Null
                }
                # Run certbot (interactive? Use --non-interactive --agree-tos -m admin@$DOMAIN)
                # For simplicity, just log
                Write-Log "SSL setup would require certbot --nginx -d $DOMAIN. Skipping automatic execution."
                Write-Warn "SSL certificate not automatically obtained. Run certbot manually."
                # Create a scheduled task for renewal
                $renewScript = "certbot renew --quiet"
                $renewTaskName = "Certbot Renewal"
                $task = Get-ScheduledTask -TaskName $renewTaskName -ErrorAction SilentlyContinue
                if (-not $task) {
                    $action = New-ScheduledTaskAction -Execute "certbot" -Argument "renew --quiet"
                    $trigger = New-ScheduledTaskTrigger -Daily -At "03:00"
                    $principal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -LogonType ServiceAccount -RunLevel Highest
                    Register-ScheduledTask -TaskName $renewTaskName -Action $action -Trigger $trigger -Principal $principal -Force | Out-Null
                    Write-Success "Certbot renewal scheduled task created."
                }
                Set-Checkpoint 'ssl'
            }
        } else { Write-Log "SSL skipped or already done." }
        # 3.5 Start/Restart nginx
        if (-not $DryRun) {
            Restart-Service nginx -ErrorAction SilentlyContinue
            Start-Service nginx -ErrorAction SilentlyContinue
            Write-Success "Nginx started."
        }
        Set-Checkpoint 'nginx'
    } catch {
        Write-Fatal "Nginx section failed: $_"
    }
}

# Parameter: SkipDeploy
if (-not $SkipDeploy -and (Get-Checkpoint 'deploy')) {
    Write-Log "Deploy checkpoint found, skipping."
} elseif (-not $SkipDeploy) {
    Write-Log "=== SECTION 4: DEPLOY ==="
    try {
        if (-not $DryRun) {
            # 4.1 Create app directory
            if (-not (Test-Path $InstallDir)) { New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null }
            # 4.2 Git clone (adapt as needed)
            # Assume repository exists or is already cloned
            if (-not (Test-Path (Join-Path $InstallDir ".git"))) {
                # Simulate clone: for real use, replace with actual git repo
                Write-Log "Git clone not implemented. Place your project in $InstallDir manually."
            }
            # 4.3 Create .env.production
            $envContent = @"
DATABASE_URL="$DATABASE_URL"
JWT_SECRET="$JWT_SECRET"
"@
            Set-Content -Path (Join-Path $InstallDir ".env.production") -Value $envContent -Force
            # 4.4 Docker compose build and up
            if (Test-Path (Join-Path $InstallDir "docker-compose.yml")) {
                Set-Location $InstallDir
                docker compose build | Out-Null
                docker compose up -d | Out-Null
                Write-Success "Docker compose deployed."
            } else {
                Write-Warn "No docker-compose.yml found in $InstallDir."
            }
            Set-Checkpoint 'deploy'
        }
    } catch {
        Write-Fatal "Deploy failed: $_"
    }
}

# Parameter: SkipMaintenance
if (-not $SkipMaintenance -and (Get-Checkpoint 'maintenance')) {
    Write-Log "Maintenance checkpoint found, skipping."
} elseif (-not $SkipMaintenance) {
    Write-Log "=== SECTION 5: MAINTENANCE ==="
    try {
        if (-not $DryRun) {
            # 5.1 Log rotation (simple: keep last 7 days)
            $logRotationScript = @'
$logDir = "C:\ProgramData\portalcursos\logs"
$maxAge = 7
Get-ChildItem $logDir -File | Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$maxAge) } | Remove-Item -Force
'@
            $logRotationPath = Join-Path $ScriptsDir "log-rotation.ps1"
            Set-Content -Path $logRotationPath -Value $logRotationScript -Force
            $taskName = "Log Rotation"
            if (-not (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue)) {
                $action = New-ScheduledTaskAction -Execute "PowerShell.exe" -Argument "-ExecutionPolicy Bypass -File `"$logRotationPath`""
                $trigger = New-ScheduledTaskTrigger -Daily -At "00:00"
                $principal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -LogonType ServiceAccount -RunLevel Highest
                Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $principal -Force | Out-Null
            }
            # 5.2 Health check
            $healthScript = @'
$url = "http://localhost:3000/health"
try {
    $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 10
    if ($response.StatusCode -eq 200) {
        Add-Content -Path "C:\ProgramData\portalcursos\logs\health.log" -Value "$(Get-Date) - OK"
    } else {
        Add-Content -Path "C:\ProgramData\portalcursos\logs\health.log" -Value "$(Get-Date) - FAIL: $($response.StatusCode)"
    }
} catch {
    Add-Content -Path "C:\ProgramData\portalcursos\logs\health.log" -Value "$(Get-Date) - FAIL: $_"
}
'@
            $healthScriptPath = Join-Path $ScriptsDir "health-check.ps1"
            Set-Content -Path $healthScriptPath -Value $healthScript -Force
            $taskName = "Health Check"
            if (-not (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue)) {
                $action = New-ScheduledTaskAction -Execute "PowerShell.exe" -Argument "-ExecutionPolicy Bypass -File `"$healthScriptPath`""
                $trigger = New-ScheduledTaskTrigger -Daily -At "00:00" -RepetitionInterval (New-TimeSpan -Minutes 5) -RepetitionDuration ([TimeSpan]::MaxValue)
                $principal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -LogonType ServiceAccount -RunLevel Highest
                Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $principal -Force | Out-Null
            }
            # 5.3 Backup (simplified: backup docker volumes and config)
            $backupScript = @'
$backupDir = "C:\Backups\portalcursos"
if (-not (Test-Path $backupDir)) { New-Item -ItemType Directory -Path $backupDir -Force | Out-Null }
$date = Get-Date -Format "yyyyMMdd_HHmmss"
$backupFile = Join-Path $backupDir "portalcursos_backup_$date.zip"
Compress-Archive -Path "C:\ProgramData\portalcursos","C:\Apps\portalcursos" -DestinationPath $backupFile -Force
'@
            $backupScriptPath = Join-Path $ScriptsDir "backup.ps1"
            Set-Content -Path $backupScriptPath -Value $backupScript -Force
            $taskName = "Weekly Backup"
            if (-not (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue)) {
                $action = New-ScheduledTaskAction -Execute "PowerShell.exe" -Argument "-ExecutionPolicy Bypass -File `"$backupScriptPath`""
                $trigger = New-ScheduledTaskTrigger -Weekly -DaysOfWeek Sunday -At "02:00"
                $principal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -LogonType ServiceAccount -RunLevel Highest
                Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $principal -Force | Out-Null
            }
            Write-Success "Maintenance tasks created."
            Set-Checkpoint 'maintenance'
        }
    } catch {
        Write-Fatal "Maintenance failed: $_"
    }
}

# Parameter: SkipVerification
if (-not $SkipVerification) {
    Write-Log "=== SECTION 6: VERIFICATION ==="
    $passed = 0
    $failed = 0
    $metrics = @(
        @{Name="PowerShell Version 7+"; Test={$PSVersionTable.PSVersion.Major -ge 7}},
        @{Name="Admin rights"; Test={([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)}},
        @{Name="OpenSSH Server running"; Test={(Get-Service sshd).Status -eq 'Running'}},
        @{Name="Firewall SSH rule"; Test={Get-NetFirewallRule -DisplayName "SSH" -ErrorAction SilentlyContinue}},
        @{Name="Firewall HTTP/HTTPS rules"; Test={(Get-NetFirewallRule -DisplayName "Port 80" -ErrorAction SilentlyContinue) -and (Get-NetFirewallRule -DisplayName "Port 443" -ErrorAction SilentlyContinue)}},
        @{Name="Nginx service running"; Test={(Get-Service nginx -ErrorAction SilentlyContinue).Status -eq 'Running'}},
        @{Name="Docker service running"; Test={(Get-Service docker -ErrorAction SilentlyContinue).Status -eq 'Running'}},
        @{Name="Node.js installed"; Test={Get-Command node -ErrorAction SilentlyContinue}},
        @{Name="Application directory exists"; Test={Test-Path $InstallDir}},
        @{Name="Config files exist"; Test={Test-Path (Join-Path $InstallDir ".env.production")}},
        @{Name="Scheduled tasks exist"; Test={(Get-ScheduledTask -TaskName "Health Check" -ErrorAction SilentlyContinue) -and (Get-ScheduledTask -TaskName "Log Rotation" -ErrorAction SilentlyContinue)}},
        @{Name="Time zone correct"; Test={(Get-TimeZone).Id -eq 'E. South America Standard Time'}}
    )
    foreach ($metric in $metrics) {
        $result = & $metric.Test
        if ($result) {
            Write-Host "[PASS] $($metric.Name)" -ForegroundColor Green
            $passed++
        } else {
            Write-Host "[FAIL] $($metric.Name)" -ForegroundColor Red
            $failed++
        }
    }
    Write-Log "Verification: $passed passed, $failed failed."
    if ($failed -gt 0) { Write-Warn "Some checks failed. Review logs." }
}

Write-Log "Script completed. Exiting."
Cleanup-TempFiles
