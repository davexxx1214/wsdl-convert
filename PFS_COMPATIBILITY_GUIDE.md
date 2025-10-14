# PFS兼容性配置指南

## 问题背景

当Java CXF客户端连接到使用自定义认证器`PfsUserNameTokenAuthenticator`的C# WCF服务时，标准的WS-Security Username Token格式可能不被C#自定义认证器接受。

## 解决方案概述

我们创建了一个专门的PFS兼容安全配置，它会生成符合C#自定义认证器期望格式的WS-Security头。

## 配置步骤

### 1. 启用PFS兼容模式

在`application.yml`中设置：

```yaml
wsdl:
  security:
    enabled: true                    # 启用WS-Security
    username: your_actual_username   # 替换为实际用户名
    password: your_actual_password   # 替换为实际密码
    use-pfs-compatible: true         # 启用PFS兼容模式
```

### 2. 消息格式对比

#### Java标准格式（可能被C#拒绝）：
```xml
<soap:Header>
  <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
    <wsse:UsernameToken wsu:Id="UsernameToken-1">
      <wsse:Username>username</wsse:Username>
      <wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText">password</wsse:Password>
    </wsse:UsernameToken>
  </wsse:Security>
</soap:Header>
```

#### PFS兼容格式（应该被C#接受）：
```xml
<soap:Header>
  <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" 
                 xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd"
                 soap:mustUnderstand="1">
    <wsse:UsernameToken wsu:Id="UsernameToken-{timestamp}">
      <wsse:Username>username</wsse:Username>
      <wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText">password</wsse:Password>
      <wsse:Nonce EncodingType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary">{base64_nonce}</wsse:Nonce>
      <wsu:Created>{iso8601_timestamp}</wsu:Created>
    </wsse:UsernameToken>
  </wsse:Security>
</soap:Header>
```

### 3. 关键差异

PFS兼容格式包含以下额外元素：
- `soap:mustUnderstand="1"` 属性
- `wsse:Nonce` 元素（防重放攻击）
- `wsu:Created` 时间戳元素
- 更完整的命名空间声明

## 配置选项

### 开发环境配置
```yaml
wsdl:
  service:
    url: https://localhost:6666/pamservicelayer/service
  security:
    enabled: true
    username: dev_user
    password: dev_password
    use-pfs-compatible: true
```

### 生产环境配置
```yaml
wsdl:
  service:
    url: https://prod-server:6666/pamservicelayer/service
  security:
    enabled: true
    username: ${PFS_USERNAME}      # 从环境变量读取
    password: ${PFS_PASSWORD}      # 从环境变量读取
    use-pfs-compatible: true
```

### 回退到标准模式
如果PFS兼容模式有问题，可以禁用它：
```yaml
wsdl:
  security:
    use-pfs-compatible: false      # 使用标准WS-Security格式
```

## 测试连接

### 1. 启动应用
```bash
./start.sh
# 或者在Windows上
start.bat
```

### 2. 检查健康状态
```bash
curl http://localhost:8080/wsdl-converter/api/wsdl/health
```

### 3. 获取服务信息
```bash
curl http://localhost:8080/wsdl-converter/api/wsdl/info
```

### 4. 调用具体方法
```bash
curl -X POST http://localhost:8080/wsdl-converter/api/wsdl/invoke/GetVersion \
  -H "Content-Type: application/json" \
  -d "{}"
```

## 故障排除

### 1. 启用详细日志
在`application.yml`中添加：
```yaml
logging:
  level:
    com.example.wsdlconverter: DEBUG
    org.apache.cxf: DEBUG
    org.apache.wss4j: DEBUG
```

### 2. 常见错误及解决方案

#### 错误：认证失败
- **原因**：用户名/密码不正确
- **解决**：验证凭据是否与C#服务配置匹配

#### 错误：安全令牌格式不正确
- **原因**：C#自定义认证器不接受标准格式
- **解决**：确保`use-pfs-compatible: true`

#### 错误：SSL/TLS握手失败
- **原因**：HTTPS证书问题
- **解决**：检查证书配置或在测试环境中暂时使用HTTP

### 3. 调试步骤

1. 检查配置是否正确加载：
   ```bash
   curl http://localhost:8080/wsdl-converter/api/wsdl/info
   ```

2. 查看日志中的安全配置信息：
   ```
   已配置PFS兼容的WS-Security设置
   ```

3. 如果仍有问题，可以暂时禁用PFS兼容模式进行对比测试

## 自定义调整

如果默认的PFS兼容格式仍不满足要求，可以修改`PfsCompatibleSecurityConfig.java`中的以下方法：

1. `createPfsCompatibleSecurityHeader()` - 调整安全头结构
2. `addPfsSpecificElements()` - 添加C#特定的元素
3. 命名空间和属性设置

## 安全注意事项

1. **密码保护**：生产环境中使用环境变量存储敏感信息
2. **网络安全**：确保使用HTTPS传输
3. **时间同步**：确保客户端和服务器时间同步（用于时间戳验证）
4. **Nonce唯一性**：系统会自动生成唯一的Nonce值

## 联系支持

如果遇到问题，请提供以下信息：
1. 完整的错误日志
2. C#服务的具体配置
3. 期望的消息格式示例
4. 当前的配置文件内容
