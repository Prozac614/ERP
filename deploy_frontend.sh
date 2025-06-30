#!/bin/bash

# JSH ERP 前端部署脚本

set -e

# 配置变量
FRONTEND_TARGET_DIR="/home/jshERP"
FRONTEND_NAME="jshERP-web"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 备份前端
backup_frontend() {
    if [ -d "${FRONTEND_TARGET_DIR}/${FRONTEND_NAME}" ]; then
        print_info "备份前端..."
        if [ -d "${FRONTEND_TARGET_DIR}/${FRONTEND_NAME}old" ]; then
            sudo rm -rf "${FRONTEND_TARGET_DIR}/${FRONTEND_NAME}old"
        fi
        sudo mv "${FRONTEND_TARGET_DIR}/${FRONTEND_NAME}" "${FRONTEND_TARGET_DIR}/${FRONTEND_NAME}old"
    fi
}

# 主要流程
print_info "开始前端部署..."

# 备份
backup_frontend

# 打包
print_info "Yarn打包前端..."
cd jshERP-web
if [ ! -d "node_modules" ]; then
    print_info "安装前端依赖..."
    yarn install
fi
yarn build
cd ..

# 部署
print_info "部署前端..."
sudo cp -r "jshERP-web/dist" "${FRONTEND_TARGET_DIR}/${FRONTEND_NAME}"

print_info "前端部署完成！" 