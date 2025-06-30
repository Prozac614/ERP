#!/bin/bash

# JSH ERP 后端部署脚本

set -e

# 配置变量
BACKEND_TARGET_DIR="/home/jshERP"
BACKEND_NAME="jshERP-boot"

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

# 备份后端
backup_backend() {
    if [ -d "${BACKEND_TARGET_DIR}/${BACKEND_NAME}" ]; then
        print_info "备份后端..."
        if [ -d "${BACKEND_TARGET_DIR}/${BACKEND_NAME}old" ]; then
            sudo rm -rf "${BACKEND_TARGET_DIR}/${BACKEND_NAME}old"
        fi
        sudo mv "${BACKEND_TARGET_DIR}/${BACKEND_NAME}" "${BACKEND_TARGET_DIR}/${BACKEND_NAME}old"
    fi
}

# 主要流程
print_info "开始后端部署..."

# 备份
backup_backend

# 打包
print_info "Maven打包后端..."
cd jshERP-boot
mvn clean package -DskipTests
cd ..

# 部署
print_info "部署后端..."
sudo cp -r "jshERP-boot/dist/jshERP-boot" "${BACKEND_TARGET_DIR}/"
sudo chmod -R 755 "${BACKEND_TARGET_DIR}/${BACKEND_NAME}"

print_info "后端部署完成！" 