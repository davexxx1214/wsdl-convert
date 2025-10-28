# SecureConversation + PFS 混合认证方案

## 架构说明

现在Java客户端支持**完整的WS-SecureConversation协议**，同时使用PFS自定义认证。

### 工作流程

```
阶段1：SecureConversation握手（RST/RSTR）
┌─────────────────────────────────────────────────────────────┐
│ 1. Java客户端 → C#服务器                                      │
│    发送RST (RequestSecurityToken)                            │
│    包含：Username Token (标准WS-Security格式)                │
│    认证方式：使用securityUsername和securityPassword         │
│                                                              │
│ 2. C#服务器 → Java客户端                                      │
│    返回RSTR (RequestSecurityTokenResponse)                   │
│    包含：                                                     │
│      - SecurityContextToken (SCT)                           │
│      - BinarySecret (共享密钥)                               │
│      - 会话令牌                                               │
└─────────────────────────────────────────────────────────────┘

阶段2：业务消息交换
┌─────────────────────────────────────────────────────────────┐
│ 3. Java客户端 → C#服务器                                      │
│    业务消息                                                   │
│    包含：                                                     │
│      - SecurityContextToken (引用握手时建立的会话)          │
│      - PFS自定义认证头（可选，如果PFS需要）                  │
│      - 使用BinarySecret加密的消息内容                        │
└─────────────────────────────────────────────────────────────┘
```

## 代码实现

### 1. 启用SecureConversation

```java
// 不禁用WS-Policy引擎
// CXF会自动处理SecureConversation

// 创建客户端时，Policy引擎自动激活
DynamicClientFactory factory = JaxWsDynamicClientFactory.newInstance(bus);
dynamicClient = factory.createClient(wsdlSource);
```

### 2. 配置RST认证

```java
// SecureConversation的RST请求使用标准Username Token
Map<String, Object> properties = new HashMap<>();
properties.put(WSHandlerConstants.ACTION, WSHandlerConstants.USERNAME_TOKEN);
properties.put(WSHandlerConstants.USER, securityUsername);
properties.put(WSHandlerConstants.PASSWORD_TYPE, "PasswordText");

WSS4JOutInterceptor wssOut = new WSS4JOutInterceptor(properties);
dynamicClient.getOutInterceptors().add(wssOut);
```

### 3. 添加PFS拦截器（可选）

```java
// 如果业务消息也需要PFS认证头
PfsWsSecurityInterceptor pfsInterceptor = createPfsInterceptor(...);
dynamicClient.getOutInterceptors().add(pfsInterceptor);
```

## 配置要点

### application.yml

```yaml
wsdl:
  security:
    enabled: true
    use-pfs-compatible: true
    username: your_username  # 用于RST握手
    password: your_password  # 用于RST握手
    
    pfs:
      client-id: "DEFAULT"
      windows-authentication: false
      change-password: false
```

### 关键参数

```java
// SecureConversation生命周期（5分钟）
requestContext.put("ws-security.sc.lifetime", "300000");

// 用户名和密码（用于RST）
requestContext.put("ws-security.username", securityUsername);
requestContext.put("ws-security.password", securityPassword);
requestContext.put("ws-security.password.type", "PasswordText");
```

## 拦截器顺序

CXF会按照Phase顺序执行拦截器：

```
1. PRE_LOGICAL       (预处理)
2. PRE_PROTOCOL      (协议前)
3. PRE_STREAM        (流前)
4. USER_LOGICAL      (用户逻辑)
5. PREPARE_SEND      (准备发送) ← PFS拦截器在这里
6. PRE_LOGICAL_ENDING (逻辑结束前)
7. WRITE            (写入)
8. WRITE_ENDING     (写入结束)
9. POST_LOGICAL     (后处理)
```

SecureConversation拦截器会在早期阶段处理：
- 检查是否需要建立会话
- 如果需要，发送RST请求
- 接收RSTR并缓存SecurityContextToken
- 后续请求自动使用已建立的会话

## 调试信息

### 成功的日志应该显示：

```
启用SecureConversation支持，使用PFS认证
配置SecureConversation使用PFS认证...
SecureConversation配置完成
动态客户端SecureConversation+PFS配置完成 - ClientID: DEFAULT, WindowsAuth: false, ChangePassword: false
```

### SOAP消息示例

**RST请求（握手）**：
```xml
<s:Envelope>
  <s:Header>
    <a:Action>http://schemas.xmlsoap.org/ws/2005/02/trust/RST/Issue</a:Action>
    <wsse:Security>
      <wsse:UsernameToken>
        <wsse:Username>your_username</wsse:Username>
        <wsse:Password Type="...#PasswordText">your_password</wsse:Password>
      </wsse:UsernameToken>
    </wsse:Security>
  </s:Header>
  <s:Body>
    <trust:RequestSecurityToken>
      <trust:TokenType>http://schemas.xmlsoap.org/ws/2005/02/sc/sct</trust:TokenType>
      <trust:RequestType>http://schemas.xmlsoap.org/ws/2005/02/trust/Issue</trust:RequestType>
    </trust:RequestSecurityToken>
  </s:Body>
</s:Envelope>
```

**RSTR响应（包含BinarySecret）**：
```xml
<s:Envelope>
  <s:Body>
    <trust:RequestSecurityTokenResponse>
      <trust:TokenType>http://schemas.xmlsoap.org/ws/2005/02/sc/sct</trust:TokenType>
      <trust:RequestedSecurityToken>
        <c:SecurityContextToken>
          <c:Identifier>uuid:xxxxx</c:Identifier>
        </c:SecurityContextToken>
      </trust:RequestedSecurityToken>
      <trust:RequestedProofToken>
        <trust:BinarySecret>base64_encoded_key</trust:BinarySecret>  ← 这就是您看到的
      </trust:RequestedProofToken>
    </trust:RequestSecurityTokenResponse>
  </s:Body>
</s:Envelope>
```

**业务消息（使用SCT）**：
```xml
<s:Envelope>
  <s:Header>
    <wsse:Security>
      <c:SecurityContextToken>
        <c:Identifier>uuid:xxxxx</c:Identifier>  ← 引用握手时的会话
      </c:SecurityContextToken>
      <!-- 可选：如果PFS也需要 -->
      <pfs:PfsClientID>DEFAULT</pfs:PfsClientID>
      <pfs:PfsUserName>username</pfs:PfsUserName>
      ...
    </wsse:Security>
  </s:Header>
  <s:Body>
    <!-- 使用BinarySecret加密的业务数据 -->
  </s:Body>
</s:Envelope>
```

## 可能的问题

### 1. RST认证失败

**症状**：`SecureConversation failed: Authentication failed`

**原因**：C#的PFS认证器可能不接受标准Username Token

**解决**：需要确认PFS认证器是否在RST阶段生效。如果是，需要修改RST消息格式。

### 2. 业务消息仍需要PFS头

**症状**：SecureConversation成功，但业务调用失败

**原因**：C#可能在两个地方都需要PFS认证：
- RST握手时
- 业务消息时

**解决**：两个地方都添加PFS头（当前实现已包含）

### 3. BinarySecret解密失败

**症状**：`Cannot decrypt message`

**原因**：密钥协商问题

**解决**：检查算法套件是否匹配

## 下一步测试

1. **重启Java应用**
2. **查看日志**，确认：
   - "启用SecureConversation支持"
   - "SecureConversation配置完成"
3. **调用服务**
4. **查看SOAP消息**，确认：
   - 第一个请求是RST
   - 响应包含BinarySecret
   - 后续请求使用SecurityContextToken

如果RST认证失败，说明C#的PFS认证器也在RST阶段检查，需要进一步调整RST消息格式。
