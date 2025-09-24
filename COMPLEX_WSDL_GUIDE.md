# 复杂WSDL解析指南

## 功能概述
该系统现在支持解析包含嵌套引用的复杂WSDL文档，自动处理以下类型的引用：
- `wsdl:import` - WSDL导入
- `xsd:import` - XML Schema导入
- `xsd:include` - XML Schema包含

## 配置方式

### 方式1：在配置文件中指定WSDL URL
```yaml
wsdl:
  file:
    url: "https://pr1marisap03.pjz.pt:8443/pms/wsdl"  # 您的复杂WSDL URL
  
  resolver:
    enabled: true                # 启用复杂WSDL解析
    timeout: 30000              # 单个文件下载超时
    max-depth: 10               # 最大嵌套深度
    cache-enabled: true         # 启用文档缓存
```

### 方式2：通过API动态解析
```bash
# 验证WSDL URL是否可访问
curl "http://localhost:8080/wsdl-converter/api/wsdl/validate-wsdl-url?wsdlUrl=https://pr1marisap03.pjz.pt:8443/pms/wsdl"

# 手动解析复杂WSDL
curl -X POST "http://localhost:8080/wsdl-converter/api/wsdl/resolve-wsdl?wsdlUrl=https://pr1marisap03.pjz.pt:8443/pms/wsdl"
```

## 工作原理

### 1. WSDL解析流程
```
1. 获取主WSDL文档
   ↓
2. 解析所有 wsdl:import 引用
   ↓
3. 解析所有 xsd:import 和 xsd:include 引用
   ↓
4. 递归处理嵌套引用
   ↓
5. 合并所有文档到单一WSDL文件
   ↓
6. 生成临时合并文件供CXF使用
```

### 2. 自动处理的引用类型

**WSDL导入示例**：
```xml
<wsdl:import namespace="http://example.org/types" 
             location="./types.wsdl"/>
```

**Schema导入示例**：
```xml
<xsd:import namespace="http://example.org/common" 
            schemaLocation="./common.xsd"/>
```

**Schema包含示例**：
```xml
<xsd:include schemaLocation="./utilities.xsd"/>
```

### 3. URL解析策略
- **绝对URL**: 直接使用
- **相对URL**: 基于主WSDL的URL解析
- **本地路径**: 转换为绝对URL

## API 端点

### 1. 验证WSDL URL
```http
GET /api/wsdl/validate-wsdl-url?wsdlUrl={url}
```

**响应示例**：
```json
{
  "url": "https://pr1marisap03.pjz.pt:8443/pms/wsdl",
  "accessible": true,
  "message": "URL可访问",
  "timestamp": 1703123456789
}
```

### 2. 手动解析WSDL
```http
POST /api/wsdl/resolve-wsdl?wsdlUrl={url}
```

**响应示例**：
```json
{
  "success": true,
  "originalUrl": "https://pr1marisap03.pjz.pt:8443/pms/wsdl",
  "resolvedPath": "/tmp/wsdl-resolver/resolved-wsdl-1703123456789.wsdl",
  "message": "WSDL解析成功",
  "timestamp": 1703123456789
}
```

### 3. 重新初始化客户端
```http
POST /api/wsdl/reinitialize
```

### 4. 清理临时文件
```http
POST /api/wsdl/cleanup
```

## 使用示例

### 场景1：启动时自动解析
1. 在 `application.yml` 中配置WSDL URL
2. 启动应用，系统自动检测并解析复杂WSDL
3. 查看日志确认解析状态

### 场景2：运行时手动解析
```bash
# 1. 验证URL
curl "http://localhost:8080/wsdl-converter/api/wsdl/validate-wsdl-url?wsdlUrl=https://your-wsdl-server.com/service?wsdl"

# 2. 解析WSDL
curl -X POST "http://localhost:8080/wsdl-converter/api/wsdl/resolve-wsdl?wsdlUrl=https://your-wsdl-server.com/service?wsdl"

# 3. 重新初始化客户端
curl -X POST "http://localhost:8080/wsdl-converter/api/wsdl/reinitialize"

# 4. 检查服务状态
curl "http://localhost:8080/wsdl-converter/api/wsdl/health"
```

### 场景3：多环境配置
```yaml
# application-dev.yml
wsdl:
  file:
    url: "https://dev-server.com/service?wsdl"

# application-prod.yml
wsdl:
  file:
    url: "https://prod-server.com/service?wsdl"
```

## 日志监控

### 关键日志信息
```
[INFO] 开始解析复杂WSDL: https://example.com/service?wsdl
[INFO] 获取WSDL文档: https://example.com/service?wsdl
[INFO] 处理WSDL导入: https://example.com/types.wsdl
[INFO] 处理Schema import: https://example.com/common.xsd
[INFO] WSDL解析完成，临时文件: /tmp/wsdl-resolver/resolved-wsdl-xxx.wsdl
[INFO] WSDL客户端初始化完成，可用方法数量: 15
```

### 错误日志处理
```
[ERROR] 创建动态客户端失败: Connection refused
[WARN] 无法导入WSDL: https://example.com/missing.wsdl - 404 Not Found
[ERROR] 可能的原因: 1) WSDL URL无法访问 2) 本地WSDL文件不存在或格式错误 3) 网络连接问题
```

## 性能优化

### 1. 文档缓存
- 启用缓存避免重复下载相同文档
- 缓存在内存中，应用重启后清空

### 2. 超时设置
```yaml
wsdl:
  resolver:
    timeout: 30000  # 调整单个文件下载超时
```

### 3. 深度限制
```yaml
wsdl:
  resolver:
    max-depth: 10   # 防止循环引用导致无限递归
```

## 故障排除

### 问题1：URL不可访问
**症状**: `WSDL URL不可访问`
**解决方案**:
1. 检查网络连接
2. 验证URL格式
3. 检查防火墙设置
4. 确认服务器是否运行

### 问题2：SSL证书问题
**症状**: `SSL certificate validation failed`
**解决方案**:
```java
// 在开发环境中可以添加SSL忽略配置
System.setProperty("com.sun.net.ssl.checkRevocation", "false");
```

### 问题3：循环引用
**症状**: `Maximum nesting depth exceeded`
**解决方案**:
1. 检查WSDL文档中的循环引用
2. 调整 `max-depth` 设置
3. 手动修复WSDL文档

### 问题4：内存不足
**症状**: `OutOfMemoryError`
**解决方案**:
1. 增加JVM堆内存: `-Xmx2g`
2. 禁用文档缓存: `cache-enabled: false`
3. 分批处理大型WSDL

## 最佳实践

1. **URL规范化**: 确保WSDL URL格式正确
2. **网络稳定性**: 在稳定的网络环境中进行解析
3. **错误处理**: 监控解析过程中的错误日志
4. **资源清理**: 定期清理临时文件
5. **版本控制**: 保存解析后的WSDL文件用于离线使用

## 高级配置

### 自定义HTTP头
```java
// 在WsdlResolverService中可以添加自定义请求头
connection.setRequestProperty("Authorization", "Bearer your-token");
connection.setRequestProperty("Custom-Header", "value");
```

### 代理设置
```yaml
# 如果需要通过代理访问WSDL
wsdl:
  proxy:
    enabled: true
    host: proxy.company.com
    port: 8080
    username: user
    password: pass
```

这个增强版本现在可以完美处理您提到的复杂WSDL结构，自动解析和合并所有嵌套的引用文档。
