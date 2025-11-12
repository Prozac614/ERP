# 关于本项目

本项目是基于 [管伊佳ERP（jshERP）](http://www.gyjerp.com) 开源项目的定制版本，针对客户特定需求进行了定制开发。

**原项目信息：**
* 原项目名称：管伊佳ERP（原名：华夏ERP，英文名：jshERP）
* 原项目官网：http://www.gyjerp.com
* 原项目协议：GPL-3.0
* 更多原项目信息请访问：http://www.gyjerp.com

---

# 部署说明

本项目提供了自动化部署脚本，简化部署流程。

## 一键部署脚本（生产环境）

`deploy.sh` 是生产环境的一键部署脚本，包含以下功能：

* **自动备份**：部署前自动备份现有版本到 `*old` 目录
* **后端打包**：使用 Maven 自动打包后端项目
* **前端打包**：使用 Yarn 自动打包前端项目
* **自动部署**：将打包好的文件部署到 `/home/jshERP` 目录
* **配置修改**：自动修改数据库配置文件

### 使用方法

```bash
# 确保脚本有执行权限
chmod +x deploy.sh

# 执行部署
./deploy.sh
```

### 部署路径

* 后端部署路径：`/home/jshERP/jshERP-boot`
* 前端部署路径：`/home/jshERP/jshERP-web`

### 前置要求

* 已安装 Maven、Yarn、sudo
* 目标目录 `/home/jshERP` 已存在
* 具有 sudo 权限

## 测试环境部署脚本

`deploy_test.sh` 是测试环境的部署脚本，功能与生产环境脚本类似，但有以下区别：

* **部署路径**：部署到 `/home/jshERP-test` 目录
* **端口配置**：自动修改为 9998 端口
* **数据库配置**：使用测试数据库配置
* **文件路径**：使用测试环境的文件上传路径和临时路径

### 使用方法

```bash
# 确保脚本有执行权限
chmod +x deploy_test.sh

# 执行测试环境部署
./deploy_test.sh
```

### 部署路径

* 后端部署路径：`/home/jshERP-test/jshERP-boot`
* 前端部署路径：`/home/jshERP-test/jshERP-web`

## 单独部署脚本

如果需要单独部署前端或后端，可以使用以下脚本：

* `deploy_frontend.sh` - 仅部署前端
* `deploy_backend.sh` - 仅部署后端

---

# 技术框架

* 核心框架：SpringBoot 2.0.0
* 持久层框架：Mybatis 1.3.2
* 日志管理：SLF4J 1.7
* 前端框架：Vue 2.7.16
* UI框架: Ant-Design-Vue 1.5.2
* 模板框架: Jeecg-Boot 2.2.0
* 项目管理框架: Maven 3.2.3

# 开发环境

建议开发者使用以下环境，可以避免版本带来的问题：

* IDE: IntelliJ IDEA 2019.2+和JetBrains WebStorm 2019.3+
* DB: Mysql 5.7.33
* JDK: JDK 1.8
* Node: Node 20.17.0
* Maven: Maven 3.2.3+
* Redis: 6.2.1
* Nginx: 1.12.2

# 服务器环境

* 数据库：Mysql5.7.33
* JAVA平台：JRE1.8
* Redis库：redis6.2.1
* Nginx代理：nginx1.12.2
* 操作系统：Windows、Linux等

# 默认登录信息

部署后登录系统的默认租户账号：`jsh`，默认超管账户：`admin`，默认密码均为：`123456`

# 开源说明

* 本系统100%开源，遵守GPL-3.0协议
* 支持全球73种语言，在登录后右上角"界面设置"页面进行切换

---

# 更多信息

关于原项目的更多信息，包括：
* 项目介绍和功能说明
* 用户手册和接口文档
* 视频教程和部署教程
* 系统截图和功能介绍
* 网络版和插件信息

请访问原项目官网：**http://www.gyjerp.com**
