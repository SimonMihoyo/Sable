#!/usr/bin/env bash
# ============================================================================
# 开发环境一键启动（macOS / Linux）
# ============================================================================
# 功能与 Windows 上的 scripts/dev-start.ps1 对等，两版请同步修改。
#
#   ./scripts/dev-start.sh                # 后端 + 前端（后端后台、前端前台）
#   ./scripts/dev-start.sh --backend      # 只起后端（前台运行）
#   ./scripts/dev-start.sh --frontend     # 只起前端
#   ./scripts/dev-start.sh --skip-build   # 跳过后端构建，直接跑现有 jar
#   ./scripts/dev-start.sh --stop         # 停止后端（清理残留进程）
#   ./scripts/dev-start.sh --help
#
# 可覆盖的环境变量：
#   SABLE_BACKEND_PORT   后端端口，默认 8080
#   SABLE_FRONTEND_PORT  前端端口，默认 8000
#   SABLE_START_TIMEOUT  等待后端就绪的上限秒数，默认 180
#   DB_HOST / DB_PORT    数据库地址，仅用于启动前的连通性提示
#   其余 DB_* / SABLE_* 见 Readme「环境变量」
#
# ── 为什么是 mvnw package + java -jar，而不是 spring-boot:run ───────────────
# `spring-boot:run` 这类**直接目标调用**会在 reactor 里的每个模块上都执行一遍。
# 而 -pl sable-app -am 必然把 9 个兄弟模块（以及父聚合模块）一起拉进 reactor ——
# 它们都没有主类，于是第一个模块就报 "Unable to find a suitable main class"。
# 实测：即便按路径把父聚合模块排除掉（-pl 'sable-app,!.' -am），reactor 里还有
# sable-common 等 9 个模块，照样失败。唯一能用 spring-boot:run 的前提是先把兄弟
# 模块 install 进本地仓库、再单独对 sable-app 执行 —— 两步，且要求先装一遍。
# 因此这里采用 AGENTS.md §5.5 已确立的方式：增量 package（无改动时约 12s）后
# 用 java -jar 起。附带两个好处：进程就是那个 java，没有 Maven fork 出来的子进程
# 需要额外清理（见 stop_backend）；跑过的 jar 与联调/演示形态完全一致。
#
# ── 为什么前端在前台、后端在后台 ─────────────────────────────────────────────
# 前端的价值在于热更新输出（哪个模块编译了、哪个报错），必须看得见；后端整天
# 不打一行日志，塞到后台并把控制台重定向到文件更合适。两者同属一个进程组，
# 于是 Ctrl+C 能一次性收干净：脚本的 EXIT trap 会停掉后端。
# ============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# ── 参数与默认值 ─────────────────────────────────────────────────────────────
MODE="both"
SKIP_BUILD="0"
BACKEND_PORT="${SABLE_BACKEND_PORT:-8080}"
FRONTEND_PORT="${SABLE_FRONTEND_PORT:-8000}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
HEALTH_TIMEOUT="${SABLE_START_TIMEOUT:-180}"

RUNTIME_DIR="${REPO_ROOT}/logs/dev-start"
BACKEND_PID_FILE="${RUNTIME_DIR}/backend.pid"
BACKEND_BUILD_LOG="${RUNTIME_DIR}/backend-build.log"
BACKEND_LOG="${RUNTIME_DIR}/backend-console.log"
FRONTEND_LOG="${RUNTIME_DIR}/frontend-console.log"

BACKEND_PID=""
APP_JAR=""

# 颜色：仅当输出到终端时启用，重定向到文件时不掺入 ANSI 转义
if [ -t 1 ]; then
  C_RESET=$'\033[0m'; C_DIM=$'\033[2m'; C_RED=$'\033[31m'
  C_GREEN=$'\033[32m'; C_YELLOW=$'\033[33m'; C_CYAN=$'\033[36m'
else
  C_RESET=''; C_DIM=''; C_RED=''; C_GREEN=''; C_YELLOW=''; C_CYAN=''
fi

info()  { printf '%s[dev]%s %s\n'  "${C_CYAN}"   "${C_RESET}" "$*"; }
ok()    { printf '%s  ✓%s %s\n'    "${C_GREEN}"  "${C_RESET}" "$*"; }
warn()  { printf '%s  !%s %s\n'    "${C_YELLOW}" "${C_RESET}" "$*" >&2; }
err()   { printf '%s  ✗%s %s\n'    "${C_RED}"    "${C_RESET}" "$*" >&2; }
die()   { err "$*"; exit 1; }

usage() {
  cat <<'EOF'
用法：
  ./scripts/dev-start.sh               后端 + 前端（后端后台、前端前台）
  ./scripts/dev-start.sh --backend     只起后端（前台运行）
  ./scripts/dev-start.sh --frontend    只起前端
  ./scripts/dev-start.sh --skip-build  跳过后端构建，直接跑现有 jar（只改前端时用）
  ./scripts/dev-start.sh --stop        停止后端（清理残留进程）
  ./scripts/dev-start.sh --help        显示本帮助

说明：后端以「增量 package + java -jar」方式启动，日志写入
      logs/dev-start/；前端在前台运行以便看到热更新输出，Ctrl+C 会同时停止两者。

环境变量：SABLE_BACKEND_PORT(8080) SABLE_FRONTEND_PORT(8000)
          SABLE_START_TIMEOUT(180) DB_HOST DB_PORT
EOF
}

# ── 让本机请求绕开代理 ───────────────────────────────────────────────────────
# 开发机常配公司代理；若不排除 localhost，下面探测端口与等健康的请求会走代理
# 并失败（表现为"端口明明起着却连不上"）。这里做**追加**而非覆盖，不影响
# 用户原有的代理配置。curl 调用处另加 --noproxy '*' 兜底。
export NO_PROXY="localhost,127.0.0.1,::1${NO_PROXY:+,${NO_PROXY}}"
export no_proxy="${NO_PROXY}"

# ── 小工具 ───────────────────────────────────────────────────────────────────

# 端口是否可连。/dev/tcp 是 bash 内建，不依赖 nc；不可用时退回 nc。
port_open() {
  local host="$1" port="$2"
  if (exec 3<>"/dev/tcp/${host}/${port}") >/dev/null 2>&1; then
    return 0
  fi
  if command -v nc >/dev/null 2>&1; then
    nc -z "${host}" "${port}" >/dev/null 2>&1 && return 0
  fi
  return 1
}

listeners_on_port() {
  if ! command -v lsof >/dev/null 2>&1; then
    return 0
  fi
  lsof -ti "tcp:$1" -sTCP:LISTEN 2>/dev/null || true
}

# 先杀子孙再杀自己。java -jar 本没有子进程，这里留着是为了兜住"以后有人改回
# Maven 方式启动"的情况，避免父进程退出后子进程变成孤儿继续占着端口。
kill_tree() {
  local pid="$1" sig="${2:-TERM}"
  [ -n "${pid}" ] || return 0
  kill -0 "${pid}" 2>/dev/null || return 0
  local child
  for child in $(pgrep -P "${pid}" 2>/dev/null || true); do
    kill_tree "${child}" "${sig}"
  done
  kill -"${sig}" "${pid}" 2>/dev/null || true
}

kill_each() {
  local sig="$1"; shift
  local p
  for p in $@; do
    [ -n "${p}" ] && kill -"${sig}" "${p}" 2>/dev/null || true
  done
}

# ── 前置检查 ─────────────────────────────────────────────────────────────────

java_major_version() {
  local raw
  raw="$(java -version 2>&1 | head -n1 | sed -E 's/.*version "([^"]+)".*/\1/')"
  case "${raw}" in
    1.*) echo "${raw}" | cut -d. -f2 | tr -dc '0-9' ;;
    *)   echo "${raw}" | cut -d. -f1 | tr -dc '0-9' ;;
  esac
}

preflight_backend() {
  command -v java >/dev/null 2>&1 || die "找不到 java，请先安装 JDK 17+"
  local major
  major="$(java_major_version)"
  if [ -n "${major}" ] && [ "${major}" -lt 17 ] 2>/dev/null; then
    die "检测到 Java ${major}，本项目要求 17+（见根 pom.xml 的 java.version）"
  fi
  if [ "${SKIP_BUILD}" != "1" ]; then
    [ -x "${REPO_ROOT}/mvnw" ] || die "mvnw 不存在或没有可执行位，试试：chmod +x ./mvnw"
  fi
  command -v curl >/dev/null 2>&1 || die "找不到 curl，等后端就绪需要它"

  # 数据库只做提示、不阻断：DB_HOST 可能是别的机器，或本地用容器暴露在不同端口
  if ! port_open "${DB_HOST}" "${DB_PORT}"; then
    warn "连不上数据库 ${DB_HOST}:${DB_PORT} —— 后端启动会在这里失败，先确认 PostgreSQL 已起、库已建。"
  fi
}

preflight_frontend() {
  command -v node >/dev/null 2>&1 || die "找不到 node，请先安装 Node.js 18+"
  command -v npm  >/dev/null 2>&1 || die "找不到 npm"
  if [ ! -d "${REPO_ROOT}/sable-admin/node_modules" ]; then
    warn "sable-admin/node_modules 不存在，先装依赖："
    printf '    cd sable-admin && npm install\n' >&2
    die "前端依赖未安装"
  fi
}

# ── 后端 ─────────────────────────────────────────────────────────────────────

build_backend() {
  info "构建后端（mvnw package，增量；日志：logs/dev-start/backend-build.log）"
  : > "${BACKEND_BUILD_LOG}"
  # 用 maven.test.skip 而不是 skipTests：前者连测试源码都不编译，启动更快。
  # 测试由 `./mvnw test` / CI 负责，不该阻塞开发环境的启动。
  if ! ./mvnw -pl sable-app -am -Dmaven.test.skip=true package >> "${BACKEND_BUILD_LOG}" 2>&1; then
    err "构建失败，最近的日志："
    tail -n 40 "${BACKEND_BUILD_LOG}" >&2 || true
    die "后端构建失败（完整日志：logs/dev-start/backend-build.log）"
  fi
  ok "构建完成"
}

resolve_app_jar() {
  APP_JAR="$(find "${REPO_ROOT}/sable-app/target" -maxdepth 1 -name 'sable-app-*.jar' \
              ! -name '*sources*' ! -name '*javadoc*' -print 2>/dev/null | head -n1 || true)"
}

start_backend() {
  mkdir -p "${RUNTIME_DIR}"

  if [ -f "${BACKEND_PID_FILE}" ]; then
    local stale
    stale="$(cat "${BACKEND_PID_FILE}" 2>/dev/null || true)"
    if [ -n "${stale}" ] && kill -0 "${stale}" 2>/dev/null; then
      die "后端似乎已在运行（PID ${stale}）。先执行：$0 --stop"
    fi
    rm -f "${BACKEND_PID_FILE}"
  fi

  if port_open 127.0.0.1 "${BACKEND_PORT}"; then
    warn "端口 ${BACKEND_PORT} 已被占用，可能是上次没退干净。"
    die "执行 '$0 --stop' 清理，或用 SABLE_BACKEND_PORT=8081 换端口（注意前端 config/proxy.ts 的 target 也要同步改）"
  fi

  if [ "${SKIP_BUILD}" = "1" ]; then
    info "跳过构建（--skip-build）"
  else
    build_backend
  fi

  resolve_app_jar
  if [ -z "${APP_JAR}" ]; then
    die "找不到 sable-app 的 jar：${REPO_ROOT}/sable-app/target/sable-app-*.jar。去掉 --skip-build 重新构建。"
  fi

  info "启动后端（dev profile，端口 ${BACKEND_PORT}，jar：${APP_JAR#${REPO_ROOT}/}）"
  : > "${BACKEND_LOG}"
  java -jar "${APP_JAR}" \
    --spring.profiles.active=dev \
    --server.port="${BACKEND_PORT}" \
    > "${BACKEND_LOG}" 2>&1 &

  BACKEND_PID=$!
  echo "${BACKEND_PID}" > "${BACKEND_PID_FILE}"
}

wait_for_backend() {
  local url="http://127.0.0.1:${BACKEND_PORT}/v3/api-docs"
  local waited=0 code
  info "等待后端就绪（上限 ${HEALTH_TIMEOUT}s，日志：logs/dev-start/backend-console.log）"

  while [ "${waited}" -lt "${HEALTH_TIMEOUT}" ]; do
    # 进程都不在了就不必再等，直接把日志尾巴打出来
    if [ -n "${BACKEND_PID}" ] && ! kill -0 "${BACKEND_PID}" 2>/dev/null; then
      printf '\n' >&2
      err "后端进程已退出，最近的日志："
      tail -n 30 "${BACKEND_LOG}" >&2 || true
      die "启动失败"
    fi

    # 判据是"能拿到 HTTP 状态码"而不是"必须是 200"：
    # /v3/api-docs 在白名单里、正常返回 200；但若有人设了 SABLE_OPENAPI_ENABLED=false，
    # 它会变成 401/404。那时 Tomcat 其实已经起来了，HTTP 有响应就说明 Flyway 也跑完了
    # （Tomcat 在单例 bean 全部实例化之后才 onRefresh 开始监听）。000 才是"没起来"。
    code="$(curl -s --noproxy '*' -o /dev/null -w '%{http_code}' --max-time 3 "${url}" 2>/dev/null || true)"
    if [ -n "${code}" ] && [ "${code}" != "000" ]; then
      printf '\n'
      ok "后端已就绪（GET /v3/api-docs → HTTP ${code}）"
      return 0
    fi

    printf '.'
    sleep 2
    waited=$((waited + 2))
  done

  printf '\n' >&2
  err "等待 ${HEALTH_TIMEOUT}s 后后端仍未就绪，最近的日志："
  tail -n 30 "${BACKEND_LOG}" >&2 || true
  return 1
}

stop_backend() {
  local stopped=0

  if [ -f "${BACKEND_PID_FILE}" ]; then
    local pid
    pid="$(cat "${BACKEND_PID_FILE}" 2>/dev/null || true)"
    if [ -n "${pid}" ] && kill -0 "${pid}" 2>/dev/null; then
      info "停止后端（PID ${pid}）"
      kill_tree "${pid}" TERM

      # 给它 15s 体面退出（Spring 要跑完关闭钩子），超时再强杀
      local i=0
      while kill -0 "${pid}" 2>/dev/null && [ "${i}" -lt 30 ]; do
        sleep 0.5
        i=$((i + 1))
      done
      if kill -0 "${pid}" 2>/dev/null; then
        warn "后端未在 15s 内退出，强制终止"
        kill_tree "${pid}" KILL
      fi
      stopped=1
    else
      warn "pid 文件里的进程（${pid:-空}）已经不在了，按端口兜底清理"
    fi
    rm -f "${BACKEND_PID_FILE}"
  else
    warn "没有找到 ${BACKEND_PID_FILE}（不是本脚本启动的？），按端口兜底清理"
  fi

  # 兜底：进程不是本脚本起的、或上次异常退出时残留，端口上还有监听就一并停掉
  local pids
  pids="$(listeners_on_port "${BACKEND_PORT}")"
  if [ -n "${pids}" ]; then
    warn "端口 ${BACKEND_PORT} 仍有进程在监听，一并停止：$(echo "${pids}" | tr '\n' ' ')"
    # shellcheck disable=SC2086
    kill_each TERM ${pids}
    sleep 2
    pids="$(listeners_on_port "${BACKEND_PORT}")"
    if [ -n "${pids}" ]; then
      # shellcheck disable=SC2086
      kill_each KILL ${pids}
    fi
    stopped=1
  fi

  rm -f "${BACKEND_PID_FILE}"

  if [ "${stopped}" = "1" ]; then
    ok "后端已停止"
  else
    ok "没有发现需要停止的后端进程"
  fi
}

# ── 前端 ─────────────────────────────────────────────────────────────────────

start_frontend() {
  if port_open 127.0.0.1 "${FRONTEND_PORT}"; then
    die "端口 ${FRONTEND_PORT} 已被占用（另一个 dev server？）。可用 SABLE_FRONTEND_PORT=8001 换端口"
  fi

  info "启动前端 dev server（端口 ${FRONTEND_PORT}）"
  info "提示：Ctrl+C 会同时停止前端与后端"
  printf '\n'

  cd "${REPO_ROOT}/sable-admin"
  # 用 tee 落盘一份，方便事后翻编译错误；前台输出保持实时
  PORT="${FRONTEND_PORT}" npm run dev 2>&1 | tee -a "${FRONTEND_LOG}"
}

# ── 主流程 ───────────────────────────────────────────────────────────────────

print_summary() {
  printf '\n'
  printf '%s────────────────────────────────────────────────────────────%s\n' "${C_DIM}" "${C_RESET}"
  printf '  后端      http://localhost:%s\n' "${BACKEND_PORT}"
  printf '  接口文档  http://localhost:%s/swagger-ui.html\n' "${BACKEND_PORT}"
  if [ "${MODE}" = "both" ]; then
    printf '  前端      http://localhost:%s\n' "${FRONTEND_PORT}"
  fi
  printf '  日志      logs/dev-start/（另有 logs/sable.log）\n'
  printf '  开发账号  alice / bob / carol，密码均为 Sable@2026\n'
  printf '             alice 全权；bob 只读；carol 读写但无 system:manage\n'
  printf '%s────────────────────────────────────────────────────────────%s\n' "${C_DIM}" "${C_RESET}"
  printf '\n'
}

on_exit() {
  local code=$?
  # 只在本脚本确实启动过后端时清理；--stop 与前检查失败不该动别人的进程
  if [ -n "${BACKEND_PID}" ]; then
    printf '\n'
    info "退出中，停止后端…"
    stop_backend || true
  fi
  return "${code}"
}

# 只挂 EXIT，不要再挂 INT/TERM。
# ⚠️ 若写成 `trap on_exit EXIT INT TERM`，一次 Ctrl+C 会让清理跑**两遍**：
# SIGINT 命中 INT 分支先跑一遍，随后脚本退出时 EXIT 分支再跑一遍 ——
# 第二遍会因为 pid 文件已被删掉而打出"不是本脚本启动的？"这条误导性提示。
# 实测踩到过，别改回去。
on_backend_started() {
  trap on_exit EXIT
}

parse_args() {
  while [ $# -gt 0 ]; do
    case "$1" in
      --backend|--backend-only)   MODE="backend" ;;
      --frontend|--frontend-only) MODE="frontend" ;;
      --skip-build|--no-build)    SKIP_BUILD="1" ;;
      --stop)                     MODE="stop" ;;
      -h|--help)                  usage; exit 0 ;;
      *)                          err "未知参数：$1"; printf '\n' >&2; usage >&2; exit 2 ;;
    esac
    shift
  done
}

main() {
  parse_args "$@"

  if [ "${MODE}" = "stop" ]; then
    stop_backend
    exit 0
  fi

  info "仓库根目录：${REPO_ROOT}"

  if [ "${MODE}" = "frontend" ]; then
    preflight_frontend
    start_frontend
    exit 0
  fi

  preflight_backend

  if [ "${MODE}" = "backend" ]; then
    start_backend
    on_backend_started
    wait_for_backend
    print_summary
    info "后端正在前台运行，Ctrl+C 停止"
    # wait 会阻塞到 java 退出；被 Ctrl+C 打断时返回非 0，交给 trap 收尾
    wait "${BACKEND_PID}" || true
    exit 0
  fi

  # both
  preflight_frontend
  start_backend
  on_backend_started
  wait_for_backend
  print_summary
  start_frontend
}

main "$@"
