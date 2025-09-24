# WSDL 获取和配置指南

## 问题描述
当Java CXF客户端无法从C#服务URL直接获取WSDL文档时，需要手动配置WSDL源。

## WSDL获取策略（按优先级）

### 策略1：明确指定WSDL URL（推荐）
如果您有独立的WSDL文件URL，在`application.yml`中配置：

```yaml
wsdl:
  file:
    url: "https://your-server.com/path/to/service.wsdl"
```

### 策略2：使用本地WSDL文件（推荐）
1. **从C#服务获取WSDL文件**：
   ```bash
   # 方法1：如果服务支持?wsdl查询
   curl "https://localhost:6666/pamservicelayer/service?wsdl" -o service.wsdl
   
   # 方法2：从Visual Studio项目中复制
   # 通常位于: C#项目/bin/Debug 或 bin/Release 目录
   
   # 方法3：使用svcutil工具生成
   svcutil.exe https://localhost:6666/pamservicelayer/service?wsdl
   ```

2. **将WSDL文件放置到指定位置**：
   ```
   src/main/resources/wsdl/service.wsdl
   ```

3. **验证WSDL文件内容**：
   确保文件包含正确的服务端点和方法定义。

### 策略3：自动从服务URL获取（兜底策略）
如果前两种方法都不可用，系统会尝试：
```
https://localhost:6666/pamservicelayer/service?wsdl
```

## 获取真实WSDL的方法

### 方法1：通过Visual Studio
1. 在Visual Studio中右键点击C#服务项目
2. 选择"发布" → "创建配置文件"
3. 发布后访问 `http://服务地址/?wsdl`

### 方法2：通过WCF服务配置
如果是WCF服务，检查web.config中的`<serviceMetadata>`配置：

```xml
<serviceMetadata httpsGetEnabled="true" />
```

然后访问：`https://localhost:6666/pamservicelayer/service?wsdl`

### 方法3：通过开发工具
```bash
# 使用PowerShell
Invoke-WebRequest -Uri "https://localhost:6666/pamservicelayer/service?wsdl" -OutFile "service.wsdl"

# 使用curl
curl -k "https://localhost:6666/pamservicelayer/service?wsdl" > service.wsdl
```

### 方法4：从现有文档获取
- 查看C#项目的文档
- 联系后端开发人员获取WSDL文件
- 查看API文档或Swagger文档

## 配置示例

### 完整配置示例
```yaml
wsdl:
  service:
    url: https://localhost:6666/pamservicelayer/service
    namespace: http://tempuri.org/
  
  file:
    # 优先级1：指定WSDL URL
    url: "https://localhost:6666/pamservicelayer/service?wsdl"
    
    # 优先级2：本地WSDL文件
    path: src/main/resources/wsdl/service.wsdl
  
  security:
    enabled: true
    username: your_username
    password: your_password
```

### 开发环境配置
```yaml
# application-dev.yml
wsdl:
  file:
    url: "http://dev-server:8080/service?wsdl"
```

### 生产环境配置
```yaml
# application-prod.yml
wsdl:
  file:
    url: "${WSDL_URL}"  # 从环境变量读取
```

## 验证WSDL配置

### 1. 检查应用启动日志
```
[INFO] 正在初始化WSDL客户端...
[INFO] 使用本地WSDL文件: /path/to/service.wsdl
[INFO] 动态客户端创建成功
[INFO] WSDL客户端初始化完成，可用方法数量: 5
```

### 2. 调用健康检查端点
```bash
curl http://localhost:8080/wsdl-converter/api/wsdl/health
```

### 3. 获取服务信息
```bash
curl http://localhost:8080/wsdl-converter/api/wsdl/info
```

### 4. 获取可用方法
```bash
curl http://localhost:8080/wsdl-converter/api/wsdl/methods
```

## 故障排除

### 问题1：WSDL URL无法访问
**错误信息**: `创建动态客户端失败: Connection refused`

**解决方案**:
1. 检查C#服务是否运行
2. 验证URL是否正确
3. 检查防火墙设置
4. 使用本地WSDL文件作为替代

### 问题2：SSL证书问题
**错误信息**: `SSL certificate validation failed`

**解决方案**:
```yaml
# 添加SSL配置（仅开发环境）
server:
  ssl:
    enabled: false
```

### 问题3：WSDL格式错误
**错误信息**: `Could not create client from WSDL`

**解决方案**:
1. 验证WSDL文件语法
2. 检查命名空间配置
3. 确认服务端点URL正确

### 问题4：无WSDL启动模式
如果完全无法获取WSDL，应用会以"有限功能模式"启动：

```
[WARN] WSDL客户端初始化失败，应用将以有限功能模式启动
[INFO] 设置默认可用方法: [GetVersion, Echo, Ping]
```

此模式下可以尝试调用基本方法，但功能受限。

## 最佳实践

1. **本地文件优先**: 将WSDL文件保存在项目中，避免运行时网络依赖
2. **版本控制**: 将WSDL文件纳入版本控制，确保一致性
3. **环境隔离**: 不同环境使用不同的WSDL配置
4. **监控告警**: 监控WSDL获取失败的情况
5. **文档更新**: 当C#服务更新时，及时更新WSDL文件

## 联系支持

如果仍然无法解决WSDL获取问题，请：
1. 检查C#服务的元数据配置
2. 联系后端服务开发团队
3. 获取完整的错误日志进行分析
