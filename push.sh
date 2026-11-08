#!/system/bin/sh
# 模块热拔插 - GitHub 推送脚本
# 用途：在 Termux 或 root 终端里运行，token 只在终端输入，不进任何对话。

# 1. 补全 Termux 环境（su/root 下跑也能找到 git/curl）
export PATH=/data/data/com.termux/files/usr/bin:/system/bin:$PATH
export LD_LIBRARY_PATH=/data/data/com.termux/files/usr/lib
export LD_PRELOAD=/data/data/com.termux/files/usr/lib/libtermux-exec.so
export HOME=/data/data/com.termux/files/home

GITHUB_USER="ksp-glitch"
REPO_NAME="ModuleHotApply"

# 进入脚本所在目录（项目根目录）
cd "$(dirname "$0")"

# 2. 读取 token（只进终端，不回显）
printf "请粘贴 GitHub token（输入后回车，不回显）："
stty -echo 2>/dev/null
read TOKEN
stty echo 2>/dev/null
printf "\n"
if [ -z "$TOKEN" ]; then echo "token 为空，退出"; exit 1; fi

# 3. 校验 token
echo "校验 token ..."
HTTP=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: token $TOKEN" https://api.github.com/user)
if [ "$HTTP" = "401" ]; then echo "token 无效（401），请重新生成后粘贴"; exit 1; fi
if [ "$HTTP" != "200" ]; then echo "校验返回 HTTP $HTTP，请检查网络后重试"; fi
echo "token 有效"

# 4. git 初始化与提交
git init 2>/dev/null
git config user.name "$GITHUB_USER"
git config user.email "${GITHUB_USER}@users.noreply.github.com"
git add -A
git commit -m "ModuleHotApply v1.6.1" 2>/dev/null || echo "无变更或已提交"

# 5. 分支改 main
git branch -M main

# 6. 设置 remote 并推送
REMOTE="https://${GITHUB_USER}:${TOKEN}@github.com/${GITHUB_USER}/${REPO_NAME}.git"
git remote remove origin 2>/dev/null
git remote add origin "$REMOTE"
git push -u origin main

echo ""
echo "推送完成。"
echo "⚠️ 请立即到 GitHub 删除这个 token："
echo "   头像 → 设置 → 开发者设置 → 个人访问令牌 → 删除"
