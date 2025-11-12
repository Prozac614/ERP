# 清除缓存命令

## Redis缓存清除

### 方法1：清除所有库存相关缓存
```bash
# 连接到Redis
redis-cli

# 查看所有缓存key
KEYS *stock*

# 删除所有库存相关缓存
DEL $(redis-cli KEYS "*stock*" | tr '\n' ' ')

# 或者删除所有缓存（谨慎使用）
FLUSHALL
```

### 方法2：通过Redis客户端工具
如果您使用Redis Desktop Manager或其他GUI工具：
1. 连接到Redis服务器
2. 查找包含"stock"关键词的key
3. 删除这些缓存

### 方法3：重启Redis服务
```bash
# 重启Redis服务（会清除所有缓存）
sudo systemctl restart redis
# 或
sudo service redis restart
```

## 验证缓存清除
```bash
redis-cli
KEYS *stock*
# 应该返回空结果或很少的结果
```
