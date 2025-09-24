# WS-Security 配置指南

## 问题描述
当Java CXF客户端连接到使用WS-Security的C#服务时，可能会遇到"The security context token is expired"错误。

## 解决方案

### 1. 配置修改

#### application.yml 配置更新
```yaml
wsdl:
  service:
    url: https://localhost:6666/pamservicelayer/service  # 使用HTTPS协议
    namespace: http://tempuri.org/
  
  security:
    enabled: true                    # 启用WS-Security
    username: your_actual_username   # 替换为实际的用户名
    password: your_actual_password   # 替换为实际的密码
```

#### 重要说明
1. **URL协议**: 由于C#服务使用HTTPS，确保Java客户端也使用HTTPS
2. **用户名和密码**: 必须使用有效的凭据，这些凭据需要与C#服务中配置的用户认证信息匹配
3. **安全模式**: C#服务使用`TransportWithMessageCredential`模式，Java客户端使用对应的WS-Security配置

### 2. C#服务配置分析

您的C#服务配置显示：
- 安全模式: `TransportWithMessageCredential`
- 客户端凭据类型: `UserName`
- 传输协议: HTTPS (端口6666)

### 3. Java客户端安全配置

代码已自动配置以下WS-Security设置：
- **Username Token**: 在SOAP消息头中添加用户名令牌
- **密码类型**: 使用明文密码 (PasswordText)
- **回调处理**: 自动处理密码回调

### 4. 测试连接

更新配置后，可以使用以下端点测试连接：

```bash
# 健康检查
GET http://localhost:8080/wsdl-converter/api/wsdl/health

# 获取服务信息
GET http://localhost:8080/wsdl-converter/api/wsdl/info

# 调用具体方法（示例）
POST http://localhost:8080/wsdl-converter/api/wsdl/invoke/GetVersion
Content-Type: application/json

{}
```

### 5. 常见问题排查

#### 5.1 令牌过期错误
- **原因**: 安全上下文令牌超时
- **解决**: 确保系统时间同步，检查凭据是否正确

#### 5.2 SSL/TLS 错误
- **原因**: HTTPS证书问题
- **解决**: 如果是自签名证书，可能需要额外的SSL配置

#### 5.3 认证失败
- **原因**: 用户名/密码不正确
- **解决**: 验证凭据是否与C#服务配置匹配

### 6. 配置示例

#### 开发环境
```yaml
wsdl:
  security:
    enabled: true
    username: dev_user
    password: dev_password
```

#### 生产环境  
```yaml
wsdl:
  security:
    enabled: true
    username: ${WSDL_USERNAME}  # 从环境变量读取
    password: ${WSDL_PASSWORD}  # 从环境变量读取
```

### 7. 安全注意事项

1. **密码保护**: 生产环境中不要将密码硬编码在配置文件中
2. **环境变量**: 使用环境变量或密钥管理系统存储敏感信息
3. **网络安全**: 确保网络连接使用HTTPS加密
4. **权限控制**: 确保使用的账户只具有必要的最小权限

### 8. 调试信息

如果仍有问题，可以启用详细日志：

```yaml
logging:
  level:
    com.example.wsdlconverter: DEBUG
    org.apache.cxf: DEBUG
    org.apache.wss4j: DEBUG
```

这将显示详细的安全协商过程和可能的错误信息。
