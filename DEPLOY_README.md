# JSH ERP 自动化部署脚本使用说明

## 脚本文件

1. **deploy.sh** - 完整的自动化部署脚本（推荐使用）
2. **deploy_backend.sh** - 只部署后端的脚本
3. **deploy_frontend.sh** - 只部署前端的脚本

## 使用方法

### 完整部署
```bash
./deploy.sh
```

### 只部署后端
```bash
./deploy_backend.sh
```

### 只部署前端
```bash
./deploy_frontend.sh
```

## 脚本功能

### 完整部署脚本 (deploy.sh) 包含以下步骤：

1. **环境检查**
   - 检查 Maven、Yarn、sudo 命令是否可用
   - 检查目标目录是否存在

2. **备份原有文件夹**
   - 备份 `/home/jshERP/jshERP-boot` → `/home/jshERP/jshERP-bootold`
   - 备份 `/home/jshERP/jshERP-web` → `/home/jshERP/jshERP-webold`
   - 自动删除之前的 old 备份文件夹

3. **后端打包部署**
   - 使用 Maven 清理和打包：`mvn clean package -DskipTests`
   - 复制 `jshERP-boot/dist/jshERP-boot` 到 `/home/jshERP/jshERP-boot`
   - 设置目录权限为 755

4. **前端打包部署**
   - 检查并安装依赖（如果需要）：`yarn install`
   - 使用 Yarn 打包：`yarn build`
   - 复制 `jshERP-web/dist` 到 `/home/jshERP/jshERP-web`

## 注意事项

1. **权限要求**
   - 脚本需要 sudo 权限来操作 `/home/jshERP/` 目录
   - 执行前确保当前用户可以使用 sudo

2. **环境要求**
   - 系统已安装 Maven
   - 系统已安装 Node.js 和 Yarn
   - 确保 `/home/jshERP/` 目录存在

3. **备份策略**
   - 每次部署前会自动备份现有版本
   - 备份文件夹命名规则：原文件夹名 + "old"
   - 如果之前有 old 备份，会被删除

4. **错误处理**
   - 脚本使用 `set -e`，遇到错误会立即退出
   - 支持 Ctrl+C 中断操作
   - 错误信息会以红色显示

## 自定义配置

如需修改部署路径，请编辑脚本中的以下变量：

```bash
BACKEND_TARGET_DIR="/home/jshERP"    # 后端目标目录
FRONTEND_TARGET_DIR="/home/jshERP"   # 前端目标目录
BACKEND_NAME="jshERP-boot"           # 后端文件夹名
FRONTEND_NAME="jshERP-web"           # 前端文件夹名
```

## 故障排除

1. **Maven 打包失败**
   - 检查 Java 环境是否正确配置
   - 检查 Maven 依赖是否能正常下载

2. **Yarn 打包失败**
   - 检查 Node.js 版本是否兼容
   - 尝试删除 `node_modules` 重新安装依赖

3. **权限问题**
   - 确保当前用户有 sudo 权限
   - 检查目标目录的权限设置

4. **路径问题**
   - 确保在项目根目录执行脚本
   - 检查目标部署路径是否存在 