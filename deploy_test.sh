#!/bin/bash

# JSH ERP 自动化部署脚本
# 包含备份、打包和部署功能

set -e  # 遇到错误立即退出

# 配置变量
BACKEND_TARGET_DIR="/home/jshERP-test"
FRONTEND_TARGET_DIR="/home/jshERP-test"
BACKEND_NAME="jshERP-boot"
FRONTEND_NAME="jshERP-web"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 打印信息函数
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查命令是否存在
check_command() {
    if ! command -v $1 &> /dev/null; then
        print_error "$1 命令未找到，请先安装 $1"
        exit 1
    fi
}

# 备份函数
backup_directory() {
    local target_path=$1
    local dir_name=$2
    
    if [ -d "${target_path}/${dir_name}" ]; then
        print_info "开始备份 ${dir_name}..."
        
        # 删除旧的备份
        if [ -d "${target_path}/${dir_name}old" ]; then
            print_warning "删除旧的备份目录 ${target_path}/${dir_name}old"
            sudo rm -rf "${target_path}/${dir_name}old"
        fi
        
        # 创建新的备份
        print_info "将 ${target_path}/${dir_name} 重命名为 ${target_path}/${dir_name}old"
        sudo mv "${target_path}/${dir_name}" "${target_path}/${dir_name}old"
    else
        print_warning "目标目录 ${target_path}/${dir_name} 不存在，跳过备份"
    fi
}

# 主要流程
main() {
    print_info "开始 JSH ERP 自动化部署..."
    
    # 检查必要的命令
    print_info "检查必要的命令..."
    check_command "mvn"
    check_command "yarn"
    check_command "sudo"
    
    # 检查目标目录
    if [ ! -d "$BACKEND_TARGET_DIR" ]; then
        print_error "后端目标目录 $BACKEND_TARGET_DIR 不存在"
        exit 1
    fi
    
    if [ ! -d "$FRONTEND_TARGET_DIR" ]; then
        print_error "前端目标目录 $FRONTEND_TARGET_DIR 不存在"
        exit 1
    fi
    
    # 步骤1: 备份原有文件夹
    print_info "========== 步骤1: 备份原有文件夹 =========="
    backup_directory "$BACKEND_TARGET_DIR" "$BACKEND_NAME"
    backup_directory "$FRONTEND_TARGET_DIR" "$FRONTEND_NAME"
    
    # 步骤2: Maven打包后端
    print_info "========== 步骤2: Maven打包后端 =========="
    cd jshERP-boot
    print_info "清理之前的构建..."
    mvn clean
    print_info "开始Maven打包..."
    mvn package -DskipTests
    
    if [ ! -f "dist/jshERP-bin.zip" ]; then
        print_error "后端打包失败，未找到 dist/jshERP-bin.zip"
        exit 1
    fi
    
    print_info "后端打包完成"

    # 解压后端打包文件
    print_info "解压后端打包文件..."
    cd dist
    if [ -f "jshERP-bin.zip" ]; then
        # 如果已存在解压目录，先删除
        if [ -d "jshERPt" ]; then
            rm -rf "jshERP"
        fi
        # 解压文件
        unzip -q "jshERP-bin.zip"
        if [ ! -d "jshERP" ]; then
            print_error "解压失败，未找到 jshERP 目录"
            exit 1
        fi
        print_info "后端文件解压完成"
    else
        print_error "未找到 jshERP-bin.zip 文件"
        exit 1
    fi
    cd ../..
    
    # 步骤3: 复制打包好的后端到指定路径
    print_info "========== 步骤3: 部署后端 =========="
    if [ -d "jshERP-boot/dist/jshERP" ]; then
        print_info "复制后端文件到 ${BACKEND_TARGET_DIR}/${BACKEND_NAME}"
        sudo cp -r "jshERP-boot/dist/jshERP" "${BACKEND_TARGET_DIR}/${BACKEND_NAME}"
        
        # 设置权限
        print_info "设置后端目录权限为 755"
        sudo chmod -R 755 "${BACKEND_TARGET_DIR}/${BACKEND_NAME}"
        
        print_info "后端部署完成"
    else
        print_error "未找到后端打包文件 jshERP-boot/dist/jshERP"
        exit 1
    fi
    
    # 修改配置文件
    print_info "修改测试环境配置..."
    config_file="/home/jshERP-test/jshERP-boot/config/application.properties"
    if [ -f "$config_file" ]; then
        # 备份原配置文件
        sudo cp "$config_file" "${config_file}.backup.$(date +%Y%m%d_%H%M%S)"
        
        # 修改端口为9998
        sudo sed -i 's/^server\.port=.*/server.port=9998/' "$config_file"
        
        # 修改数据库为测试数据库
        sudo sed -i 's/jsh_erp?/jsh_ert_test?/g' "$config_file"
        sudo sed -i 's/^spring\.datasource\.username=.*/spring.datasource.username=jsh_ert_test/' "$config_file"
        sudo sed -i 's/^spring\.datasource\.password=.*/spring.datasource.password=8Me2kckmDDH8E3TX/' "$config_file"
        
        # 修改文件上传路径为测试环境
        sudo sed -i 's|^file\.path=.*|file.path=/opt/jshERP-test/upload|' "$config_file"
        
        # 修改临时路径为测试环境
        sudo sed -i 's|^server\.tomcat\.basedir=.*|server.tomcat.basedir=/opt/tmp/tomcat-test|' "$config_file"
        
        print_info "测试环境配置修改完成"
    else
        print_warning "配置文件 $config_file 不存在，跳过配置修改"
    fi
    
    # 修改启动脚本以支持路径区分
    print_info "修改启动脚本以支持路径区分..."
    run_script="/home/jshERP-test/jshERP-boot/bin/run-manage.sh"
    if [ -f "$run_script" ]; then
        # 备份原启动脚本
        sudo cp "$run_script" "${run_script}.backup.$(date +%Y%m%d_%H%M%S)"
        
        # 修改checkpid函数，增加路径过滤
        sudo sed -i 's/grep \$APP_MAIN_CLASS | grep -v/grep \$APP_MAIN_CLASS | grep "\$APP_HOME" | grep -v/' "$run_script"
        
        print_info "启动脚本修改完成"
    else
        print_warning "启动脚本 $run_script 不存在，跳过修改"
    fi
    
    
    # 步骤4: Yarn打包前端
    print_info "========== 步骤4: Yarn打包前端 =========="
    cd jshERP-web
    
    # 检查是否有node_modules，如果没有则安装依赖
    if [ ! -d "node_modules" ]; then
        print_info "安装前端依赖..."
        yarn install
    fi
    
    print_info "开始Yarn打包..."
    yarn build
    
    if [ ! -d "dist" ]; then
        print_error "前端打包失败，未找到 dist 目录"
        exit 1
    fi
    
    print_info "前端打包完成"
    cd ..
    
    # 步骤5: 复制打包好的前端到指定路径
    print_info "========== 步骤5: 部署前端 =========="
    print_info "复制前端文件到 ${FRONTEND_TARGET_DIR}/${FRONTEND_NAME}"
    sudo cp -r "jshERP-web/dist" "${FRONTEND_TARGET_DIR}/${FRONTEND_NAME}"
    
    print_info "前端部署完成"
    
    # 完成
    print_info "========== 部署完成 =========="
    print_info "后端部署路径: ${BACKEND_TARGET_DIR}/${BACKEND_NAME}"
    print_info "前端部署路径: ${FRONTEND_TARGET_DIR}/${FRONTEND_NAME}"
    
    print_info "所有步骤执行完成！"
}

# 捕获中断信号
trap 'print_error "部署过程被中断"; exit 1' INT TERM

# 执行主函数
main "$@" 