# 更新的PFS兼容性配置指南

## 问题解决

根据您提供的正确消息格式，我已经完全重新设计了PFS兼容配置，现在Java客户端将生成C#自定义认证器`PfsUserNameTokenAuthenticator`期望的确切格式。

## 关键修改

### 1. 正确的消息格式

**现在Java将生成的格式（符合C#期望）：**
```xml
<wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" 
               xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd"
               xmlns:pfs="http://pfs.com" 
               s:mustUnderstand="1">
  <pfs:PfsClientID>DEFAULT</pfs:PfsClientID>
  <pfs:PfsUserName>your_username</pfs:PfsUserName>
  <pfs:PfsUserPassword>your_password</pfs:PfsUserPassword>
  <pfs:PfsUserNewPassword></pfs:PfsUserNewPassword>
  <pfs:PfsKeyData></pfs:PfsKeyData>
  <pfs:PfsWindowsAuthentication>false</pfs:PfsWindowsAuthentication>
  <pfs:PfsChangePassword>false</pfs:PfsChangePassword>
  <pfs:PfsToken></pfs:PfsToken>
</wsse:Security>
```

### 2. 配置选项

在`application.yml`中的完整配置：

```yaml
wsdl:
  service:
    url: https://localhost:6666/pamservicelayer/service
  security:
    enabled: true
    username: your_actual_username
    password: your_actual_password
    use-pfs-compatible: true
    
    # PFS特定配置
    pfs:
      client-id: "DEFAULT"           # PFS客户端ID
      windows-authentication: false  # 是否使用Windows认证
      change-password: false         # 是否更改密码
```

### 3. 核心差异

**错误格式（之前Java发送的）：**
- 使用标准的`wsse:UsernameToken`结构
- 缺少PFS特定的命名空间和元素

**正确格式（现在Java发送的）：**
- 使用PFS特定的命名空间：`xmlns:pfs="http://pfs.com"`
- 包含所有PFS必需的元素：
  - `pfs:PfsClientID`
  - `pfs:PfsUserName`
  - `pfs:PfsUserPassword`
  - `pfs:PfsUserNewPassword`
  - `pfs:PfsKeyData`
  - `pfs:PfsWindowsAuthentication`
  - `pfs:PfsChangePassword`
  - `pfs:PfsToken`

## 测试步骤

### 1. 更新配置
确保`application.yml`中的用户名和密码正确：
```yaml
wsdl:
  security:
    username: your_real_username
    password: your_real_password
```

### 2. 启动应用
```bash
# Windows
start.bat

# Linux/Mac
./start.sh
```

### 3. 验证配置
```bash
# Windows
test-pfs-connection.bat

# Linux/Mac
./test-pfs-connection.sh
```

### 4. 检查日志
查找以下日志消息：
```
已配置PFS兼容的WS-Security设置 - ClientID: DEFAULT, WindowsAuth: false, ChangePassword: false
```

## 高级配置

### Windows认证模式
如果需要Windows认证：
```yaml
wsdl:
  security:
    pfs:
      windows-authentication: true
```

### 密码更改模式
如果需要更改密码：
```yaml
wsdl:
  security:
    pfs:
      change-password: true
```

### 自定义客户端ID
如果需要特定的客户端ID：
```yaml
wsdl:
  security:
    pfs:
      client-id: "YOUR_CLIENT_ID"
```

## 故障排除

### 1. 如果仍然认证失败
- 验证用户名和密码是否正确
- 检查C#服务是否正在运行
- 确认`use-pfs-compatible: true`已设置

### 2. 如果需要回退到标准模式
```yaml
wsdl:
  security:
    use-pfs-compatible: false
```

### 3. 启用详细日志
```yaml
logging:
  level:
    com.example.wsdlconverter: DEBUG
    org.apache.cxf: DEBUG
```

## 技术细节

### 自定义拦截器
- `PfsWsSecurityInterceptor`：专门为PFS格式设计的拦截器
- 在CXF的`WRITE_ENDING`阶段添加PFS安全头
- 完全替代标准的`WSS4JOutInterceptor`

### 消息结构
- 不再使用标准的`UsernameToken`
- 直接在`Security`元素下添加PFS特定元素
- 保持与C#`PfsUserNameTokenAuthenticator`的完全兼容

现在的配置应该能够完全匹配C#期望的消息格式，解决认证问题。
