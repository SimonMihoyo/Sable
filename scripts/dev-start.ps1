#Requires -Version 5.1
<#
============================================================================
 开发环境一键启动（Windows / PowerShell）
============================================================================
 功能与 macOS / Linux 上的 scripts/dev-start.sh 对等，两版请同步修改。

   .\scripts\dev-start.ps1                # 后端 + 前端（后端后台、前端前台）
   .\scripts\dev-start.ps1 -Backend       # 只起后端（前台运行）
   .\scripts\dev-start.ps1 -Frontend      # 只起前端
   .\scripts\dev-start.ps1 -SkipBuild     # 跳过后端构建，直接跑现有 jar
   .\scripts\dev-start.ps1 -Stop          # 停止后端（清理残留进程）
   .\scripts\dev-start.ps1 -Help

 若提示"无法加载脚本，因为在此系统上禁止运行脚本"，给当前用户放开即可：
   Set-ExecutionPolicy -Scope CurrentUser RemoteSigned

 可覆盖的环境变量：
   SABLE_BACKEND_PORT   后端端口，默认 8080
   SABLE_FRONTEND_PORT  前端端口，默认 8000
   SABLE_START_TIMEOUT  等待后端就绪的上限秒数，默认 180
   DB_HOST / DB_PORT    数据库地址，仅用于启动前的连通性提示

 ── 本文件的编码要求（改它之前先看这条）──────────────────────────────────
 必须保存为 **UTF-8 with BOM**。Windows PowerShell 5.1 读取没有 BOM 的 .ps1 时
 会按系统 ANSI 代码页（简体中文即 GBK）解析，本文件里的中文注释与提示会变成
 乱码，严重时直接语法错误。用 VS Code 时注意右下角编码选 "UTF-8 with BOM"。

 ── 为什么是 mvnw package + java -jar，而不是 spring-boot:run ─────────────
 `spring-boot:run` 这类**直接目标调用**会在 reactor 里的每个模块上都执行一遍。
 而 -pl sable-app -am 必然把 9 个兄弟模块（以及父聚合模块）一起拉进 reactor ——
 它们都没有主类，于是第一个模块就报 "Unable to find a suitable main class"。
 实测：即便把父聚合模块排除掉，reactor 里还有 sable-common 等 9 个模块，照样失败。
 唯一能用 spring-boot:run 的前提是先把兄弟模块 install 进本地仓库、再单独对
 sable-app 执行 —— 两步，且要求先装一遍。因此这里采用 AGENTS.md §5.5 已确立的
 方式：增量 package（无改动时约 12s）后用 java -jar 起。附带的好处是进程就是
 那个 java，没有 Maven fork 出来的子进程需要额外清理（见 Stop-Backend）。
#>

[CmdletBinding()]
param(
    [switch]$Backend,
    [switch]$Frontend,
    [switch]$SkipBuild,
    [switch]$Stop,
    [switch]$Help
)

$ErrorActionPreference = 'Stop'

# 注意：$args / $PID / $Host 是 PowerShell 保留变量，本脚本刻意避开这些名字。
$RepoRoot = Split-Path -Parent $PSScriptRoot

$BackendPort   = if ($env:SABLE_BACKEND_PORT)  { [int]$env:SABLE_BACKEND_PORT }  else { 8080 }
$FrontendPort  = if ($env:SABLE_FRONTEND_PORT) { [int]$env:SABLE_FRONTEND_PORT } else { 8000 }
$DbHost        = if ($env:DB_HOST) { $env:DB_HOST } else { 'localhost' }
$DbPort        = if ($env:DB_PORT) { [int]$env:DB_PORT } else { 5432 }
$HealthTimeout = if ($env:SABLE_START_TIMEOUT) { [int]$env:SABLE_START_TIMEOUT } else { 180 }

$RuntimeDir         = Join-Path (Join-Path $RepoRoot 'logs') 'dev-start'
$BackendPidFile     = Join-Path $RuntimeDir 'backend.pid'
$BackendBuildLog    = Join-Path $RuntimeDir 'backend-build.log'
$BackendBuildErrLog = Join-Path $RuntimeDir 'backend-build.err.log'
$BackendLog         = Join-Path $RuntimeDir 'backend-console.log'
$BackendErrLog      = Join-Path $RuntimeDir 'backend-console.err.log'
$FrontendLog        = Join-Path $RuntimeDir 'frontend-console.log'

$script:AppJar         = $null
$script:BackendProcess = $null
$script:BackendOwned   = $false   # 只有本脚本启动过后端，退出时才清理

# ── 输出 ────────────────────────────────────────────────────────────────────
function Write-Info  { param([string]$Message) Write-Host "[dev] $Message" -ForegroundColor Cyan }
function Write-Ok    { param([string]$Message) Write-Host "  OK  $Message" -ForegroundColor Green }
function Write-WarnLine { param([string]$Message) Write-Host "  !   $Message" -ForegroundColor Yellow }
function Write-ErrLine  { param([string]$Message) Write-Host "  X   $Message" -ForegroundColor Red }
function Stop-Script { param([string]$Message) Write-ErrLine $Message; exit 1 }

function Show-Usage {
    @'
用法：
  .\scripts\dev-start.ps1               后端 + 前端（后端后台、前端前台）
  .\scripts\dev-start.ps1 -Backend      只起后端（前台运行）
  .\scripts\dev-start.ps1 -Frontend     只起前端
  .\scripts\dev-start.ps1 -SkipBuild    跳过后端构建，直接跑现有 jar（只改前端时用）
  .\scripts\dev-start.ps1 -Stop         停止后端（清理残留进程）
  .\scripts\dev-start.ps1 -Help         显示本帮助

说明：后端以「增量 package + java -jar」方式启动，日志写入 logs\dev-start\；
      前端在前台运行以便看到热更新输出，Ctrl+C 会同时停止两者。

环境变量：SABLE_BACKEND_PORT(8080) SABLE_FRONTEND_PORT(8000)
          SABLE_START_TIMEOUT(180) DB_HOST DB_PORT
'@ | Write-Host
}

# ── 让本机请求绕开代理 ──────────────────────────────────────────────────────
# 开发机常配公司代理；不排除 localhost 的话，下面的探测与等健康都会走代理并失败。
# 这里做追加而非覆盖，不影响用户原有的代理设置。
$noProxy = 'localhost,127.0.0.1,::1'
if ($env:NO_PROXY) { $noProxy = "$noProxy,$($env:NO_PROXY)" }
$env:NO_PROXY = $noProxy
$env:no_proxy = $noProxy

# ── 小工具 ──────────────────────────────────────────────────────────────────

function Test-PortOpen {
    param([string]$TargetHost, [int]$Port, [int]$TimeoutMs = 1000)
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $task = $client.ConnectAsync($TargetHost, $Port)
        if ($task.Wait($TimeoutMs)) { return $client.Connected }
        return $false
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

# 列出监听某端口的进程号。用于兜底：进程不是本脚本起的、或上次异常退出时残留。
function Get-ListenersOnPort {
    param([int]$Port)
    try {
        return @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
                 Select-Object -ExpandProperty OwningProcess -Unique)
    } catch {
        return @()
    }
}

# 先杀子孙再杀自己。java -jar 本没有子进程，这里留着是为了兜住"以后有人改回
# Maven 方式启动"的情况，避免父进程退出后子进程变成孤儿继续占着端口。
function Stop-ProcessTree {
    param([int]$ProcessId, [switch]$Force)
    try {
        Get-CimInstance Win32_Process -Filter "ParentProcessId=$ProcessId" -ErrorAction SilentlyContinue |
            ForEach-Object { Stop-ProcessTree -ProcessId $_.ProcessId -Force:$Force }
    } catch { }
    Stop-Process -Id $ProcessId -Force:$Force -ErrorAction SilentlyContinue
}

function Get-JavaMajorVersion {
    # 用 cmd /c 合并 stderr：java -version 是往 stderr 写的，且直接 2>&1 在
    # $ErrorActionPreference='Stop' 下可能被当成错误记录抛出来
    $raw = (cmd /c "java -version 2>&1" | Out-String)
    if ($raw -notmatch 'version "([^"]+)"') { return 0 }
    $v = $Matches[1]
    if ($v.StartsWith('1.')) { return [int](($v -split '\.')[1]) }
    return [int](($v -split '[\.\-]')[0])
}

function Test-BackendHttpStatus {
    param([string]$Url)
    try {
        $resp = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
        return [int]$resp.StatusCode
    } catch {
        $webResp = $_.Exception.Response
        if ($webResp -ne $null) { return [int]$webResp.StatusCode }
        return 0
    }
}

# ── 前置检查 ────────────────────────────────────────────────────────────────

function Invoke-PreflightBackend {
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) { Stop-Script '找不到 java，请先安装 JDK 17+' }
    $major = Get-JavaMajorVersion
    if ($major -gt 0 -and $major -lt 17) {
        Stop-Script "检测到 Java $major，本项目要求 17+（见根 pom.xml 的 java.version）"
    }
    if (-not $SkipBuild) {
        $mvnw = Join-Path $RepoRoot 'mvnw.cmd'
        if (-not (Test-Path -LiteralPath $mvnw)) { Stop-Script "找不到 $mvnw" }
    }

    if (-not (Test-PortOpen -TargetHost $DbHost -Port $DbPort)) {
        Write-WarnLine "连不上数据库 ${DbHost}:${DbPort} —— 后端启动会在这里失败，先确认 PostgreSQL 已起、库已建。"
    }
}

function Invoke-PreflightFrontend {
    if (-not (Get-Command node -ErrorAction SilentlyContinue)) { Stop-Script '找不到 node，请先安装 Node.js 18+' }
    if (-not (Get-Command npm  -ErrorAction SilentlyContinue)) { Stop-Script '找不到 npm' }
    $modules = Join-Path (Join-Path $RepoRoot 'sable-admin') 'node_modules'
    if (-not (Test-Path -LiteralPath $modules)) {
        Write-WarnLine 'sable-admin\node_modules 不存在，先装依赖：'
        Write-Host '    cd sable-admin; npm install' -ForegroundColor Yellow
        Stop-Script '前端依赖未安装'
    }
}

# ── 后端 ────────────────────────────────────────────────────────────────────

function Invoke-BackendBuild {
    Write-Info '构建后端（mvnw package，增量；日志：logs\dev-start\backend-build.log）'
    $mvnw = Join-Path $RepoRoot 'mvnw.cmd'
    # 用 maven.test.skip 而不是 skipTests：前者连测试源码都不编译，启动更快。
    # 测试由 mvnw test / CI 负责，不该阻塞开发环境的启动。
    $mvnArgs = @('-pl', 'sable-app', '-am', '-Dmaven.test.skip=true', 'package')

    # Start-Process 不允许 stdout 与 stderr 重定向到同一个文件，故分成两个
    $proc = Start-Process -FilePath $mvnw -ArgumentList $mvnArgs `
        -WorkingDirectory $RepoRoot `
        -RedirectStandardOutput $BackendBuildLog `
        -RedirectStandardError $BackendBuildErrLog `
        -NoNewWindow -PassThru -Wait

    if ($proc.ExitCode -ne 0) {
        Write-ErrLine '构建失败，最近的日志：'
        if (Test-Path -LiteralPath $BackendBuildLog) { Get-Content -LiteralPath $BackendBuildLog -Tail 40 | Write-Host }
        Stop-Script '后端构建失败（完整日志：logs\dev-start\backend-build.log）'
    }
    Write-Ok '构建完成'
}

function Resolve-AppJar {
    $target = Join-Path (Join-Path $RepoRoot 'sable-app') 'target'
    $jar = Get-ChildItem -LiteralPath $target -Filter 'sable-app-*.jar' -ErrorAction SilentlyContinue |
           Where-Object { $_.Name -notmatch 'sources|javadoc' } |
           Select-Object -First 1
    $script:AppJar = if ($jar) { $jar.FullName } else { $null }
}

function Start-Backend {
    if (-not (Test-Path -LiteralPath $RuntimeDir)) {
        New-Item -ItemType Directory -Path $RuntimeDir -Force | Out-Null
    }

    if (Test-Path -LiteralPath $BackendPidFile) {
        $stale = (Get-Content -LiteralPath $BackendPidFile -Raw).Trim()
        if ($stale -and (Get-Process -Id ([int]$stale) -ErrorAction SilentlyContinue)) {
            Stop-Script "后端似乎已在运行（PID $stale）。先执行：.\scripts\dev-start.ps1 -Stop"
        }
        Remove-Item -LiteralPath $BackendPidFile -Force -ErrorAction SilentlyContinue
    }

    if (Test-PortOpen -TargetHost '127.0.0.1' -Port $BackendPort) {
        Write-WarnLine "端口 $BackendPort 已被占用，可能是上次没退干净。"
        Stop-Script "执行 '.\scripts\dev-start.ps1 -Stop' 清理，或用 `$env:SABLE_BACKEND_PORT=8081 换端口（前端 config/proxy.ts 的 target 也要同步改）"
    }

    if ($SkipBuild) {
        Write-Info '跳过构建（-SkipBuild）'
    } else {
        Invoke-BackendBuild
    }

    Resolve-AppJar
    if (-not $script:AppJar) {
        Stop-Script "找不到 sable-app 的 jar：$RepoRoot\sable-app\target\sable-app-*.jar。去掉 -SkipBuild 重新构建。"
    }

    Write-Info "启动后端（dev profile，端口 $BackendPort，jar：sable-app\target\$(Split-Path -Leaf $script:AppJar)）"

    $javaArgs = @(
        '-jar', $script:AppJar,
        '--spring.profiles.active=dev',
        "--server.port=$BackendPort"
    )
    $script:BackendProcess = Start-Process -FilePath 'java' -ArgumentList $javaArgs `
        -WorkingDirectory $RepoRoot `
        -RedirectStandardOutput $BackendLog `
        -RedirectStandardError $BackendErrLog `
        -NoNewWindow -PassThru

    $script:BackendProcess.Id | Out-File -LiteralPath $BackendPidFile -Encoding ascii -Force
    $script:BackendOwned = $true
}

function Wait-BackendReady {
    $url = "http://127.0.0.1:$BackendPort/v3/api-docs"
    $waited = 0
    Write-Info "等待后端就绪（上限 ${HealthTimeout}s，日志：logs\dev-start\backend-console.log）"

    while ($waited -lt $HealthTimeout) {
        if ($script:BackendProcess -and $script:BackendProcess.HasExited) {
            Write-Host ''
            Write-ErrLine '后端进程已退出，最近的日志：'
            if (Test-Path -LiteralPath $BackendLog) { Get-Content -LiteralPath $BackendLog -Tail 30 | Write-Host }
            Stop-Script '启动失败'
        }

        # 判据是"能拿到 HTTP 状态码"而不是"必须是 200"：/v3/api-docs 在白名单里、
        # 正常返回 200；但若设了 SABLE_OPENAPI_ENABLED=false 它会变成 401/404。
        # 那时 Tomcat 其实已经起来了（它在单例 bean 全部实例化之后才 onRefresh 监听），
        # HTTP 有响应就说明 Flyway 也跑完了；0 才是"没起来"。
        $code = Test-BackendHttpStatus -Url $url
        if ($code -gt 0) {
            Write-Host ''
            Write-Ok "后端已就绪（GET /v3/api-docs -> HTTP $code）"
            return
        }

        Write-Host '.' -NoNewline
        Start-Sleep -Seconds 2
        $waited += 2
    }

    Write-Host ''
    Write-ErrLine "等待 ${HealthTimeout}s 后后端仍未就绪，最近的日志："
    if (Test-Path -LiteralPath $BackendLog) { Get-Content -LiteralPath $BackendLog -Tail 30 | Write-Host }
    throw '后端未在超时时间内就绪'
}

function Stop-Backend {
    $stopped = $false

    if (Test-Path -LiteralPath $BackendPidFile) {
        $stale = (Get-Content -LiteralPath $BackendPidFile -Raw).Trim()
        if ($stale -and (Get-Process -Id ([int]$stale) -ErrorAction SilentlyContinue)) {
            Write-Info "停止后端（PID $stale）"
            Stop-ProcessTree -ProcessId ([int]$stale)

            # 给它 15s 体面退出（Spring 要跑完关闭钩子），超时再强杀
            $i = 0
            while ((Get-Process -Id ([int]$stale) -ErrorAction SilentlyContinue) -and $i -lt 30) {
                Start-Sleep -Milliseconds 500
                $i++
            }
            if (Get-Process -Id ([int]$stale) -ErrorAction SilentlyContinue) {
                Write-WarnLine '后端未在 15s 内退出，强制终止'
                Stop-ProcessTree -ProcessId ([int]$stale) -Force
            }
            $stopped = $true
        } else {
            Write-WarnLine "pid 文件里的进程（$stale）已经不在了，按端口兜底清理"
        }
        Remove-Item -LiteralPath $BackendPidFile -Force -ErrorAction SilentlyContinue
    } else {
        Write-WarnLine "没有找到 $BackendPidFile（不是本脚本启动的？），按端口兜底清理"
    }

    $pids = Get-ListenersOnPort -Port $BackendPort
    if ($pids -and $pids.Count -gt 0) {
        Write-WarnLine "端口 $BackendPort 仍有进程在监听，一并停止：$($pids -join ' ')"
        foreach ($p in $pids) { Stop-Process -Id ([int]$p) -ErrorAction SilentlyContinue }
        Start-Sleep -Seconds 2
        $pids = Get-ListenersOnPort -Port $BackendPort
        foreach ($p in $pids) { Stop-Process -Id ([int]$p) -Force -ErrorAction SilentlyContinue }
        $stopped = $true
    }

    Remove-Item -LiteralPath $BackendPidFile -Force -ErrorAction SilentlyContinue

    if ($stopped) { Write-Ok '后端已停止' } else { Write-Ok '没有发现需要停止的后端进程' }
}

# ── 前端 ────────────────────────────────────────────────────────────────────

function Start-Frontend {
    if (Test-PortOpen -TargetHost '127.0.0.1' -Port $FrontendPort) {
        Stop-Script "端口 $FrontendPort 已被占用（另一个 dev server？）。可用 `$env:SABLE_FRONTEND_PORT=8001 换端口"
    }

    Write-Info "启动前端 dev server（端口 $FrontendPort）"
    Write-Info '提示：Ctrl+C 会同时停止前端与后端'
    Write-Host ''

    Push-Location (Join-Path $RepoRoot 'sable-admin')
    try {
        $env:PORT = "$FrontendPort"
        # 用 Tee-Object 落盘一份，方便事后翻编译错误；控制台输出保持实时
        & npm run dev 2>&1 | Tee-Object -FilePath $FrontendLog -Append
    } finally {
        Pop-Location
    }
}

# ── 摘要 / 收尾 ─────────────────────────────────────────────────────────────

function Show-Summary {
    Write-Host ''
    Write-Host '────────────────────────────────────────────────────────────' -ForegroundColor DarkGray
    Write-Host "  后端      http://localhost:$BackendPort"
    Write-Host "  接口文档  http://localhost:$BackendPort/swagger-ui.html"
    if ($Mode -eq 'both') { Write-Host "  前端      http://localhost:$FrontendPort" }
    Write-Host '  日志      logs\dev-start\（另有 logs\sable.log）'
    Write-Host '  开发账号  alice / bob / carol，密码均为 Sable@2026'
    Write-Host '             alice 全权；bob 只读；carol 读写但无 system:manage'
    Write-Host '────────────────────────────────────────────────────────────' -ForegroundColor DarkGray
    Write-Host ''
}

# ── 主流程 ──────────────────────────────────────────────────────────────────

if ($Help) { Show-Usage; exit 0 }

$Mode = 'both'
if ($Backend -and $Frontend) { Stop-Script '-Backend 与 -Frontend 不能同时使用；两个都要就直接不带参数运行' }
elseif ($Backend)  { $Mode = 'backend' }
elseif ($Frontend) { $Mode = 'frontend' }
elseif ($Stop)     { $Mode = 'stop' }

try {
    if ($Mode -eq 'stop') {
        Stop-Backend
        exit 0
    }

    Write-Info "仓库根目录：$RepoRoot"

    if ($Mode -eq 'frontend') {
        Invoke-PreflightFrontend
        Start-Frontend
        exit 0
    }

    Invoke-PreflightBackend
    if ($Mode -eq 'both') { Invoke-PreflightFrontend }

    Start-Backend
    Wait-BackendReady
    Show-Summary

    if ($Mode -eq 'backend') {
        Write-Info '后端正在前台运行，Ctrl+C 停止'
        $script:BackendProcess | Wait-Process
    } else {
        Start-Frontend
    }
} finally {
    # 只清理本脚本启动的后端；-Stop 与前置检查失败都不会走到有意义的清理。
    # 先清标记再清理，保证本块即使被重复进入也只真正执行一次。
    if ($script:BackendOwned) {
        $script:BackendOwned = $false
        Write-Host ''
        Write-Info '退出中，停止后端…'
        Stop-Backend
    }
}
