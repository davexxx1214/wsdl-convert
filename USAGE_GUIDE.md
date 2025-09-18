# WSDL转RESTful API转换器使用指南

## 快速入门

### 1. 准备您的C#服务WSDL文件

首先，您需要获取C#服务的WSDL文件。有以下几种方式：

#### 方式A：从C#服务URL获取
```bash
# 假设您的C#服务地址是 http://localhost:8080/Service.asmx
# 访问以下URL获取WSDL文件：
http://localhost:8080/Service.asmx?wsdl
```

#### 方式B：使用本地WSDL文件
将WSDL文件保存为 `src/main/resources/wsdl/service.wsdl`

### 2. 配置应用程序

编辑 `src/main/resources/application.yml` 文件：

```yaml
wsdl:
  service:
    url: http://your-csharp-service:port/Service.asmx  # 您的C#服务实际地址
    namespace: http://tempuri.org/                     # 根据您的WSDL调整
  file:
    url: "http://your-csharp-service:port/Service.asmx?wsdl"  # 或使用WSDL URL
    # path: src/main/resources/wsdl/service.wsdl      # 或使用本地文件
```

### 3. 启动应用程序

#### 使用Maven启动：
```bash
mvn spring-boot:run
```

#### 或者打包后启动：
```bash
mvn clean package
java -jar target/wsdl-restful-converter-1.0.0.jar
```

#### 使用启动脚本：
```bash
# Windows
start.bat

# Linux/Mac
chmod +x start.sh
./start.sh
```

### 4. 访问应用程序

应用启动后，您可以访问：

- **Swagger API文档**: http://localhost:8080/wsdl-converter/swagger-ui.html
- **服务信息**: http://localhost:8080/wsdl-converter/api/wsdl/info
- **健康检查**: http://localhost:8080/wsdl-converter/api/wsdl/health

## API使用示例

### 获取服务信息
```bash
curl http://localhost:8080/wsdl-converter/api/wsdl/info
```

### 获取可用方法列表
```bash
curl http://localhost:8080/wsdl-converter/api/wsdl/methods
```

### 调用WSDL方法（POST方式）
```bash
curl -X POST http://localhost:8080/wsdl-converter/api/wsdl/invoke/YourMethodName \
  -H "Content-Type: application/json" \
  -d '{
    "param1": "value1",
    "param2": "value2"
  }'
```

### 调用WSDL方法（GET方式）
```bash
curl "http://localhost:8080/wsdl-converter/api/wsdl/invoke/YourMethodName?param1=value1&param2=value2"
```

## C#服务示例

如果您需要创建一个测试用的C#服务，以下是一个简单的示例：

```csharp
using System;
using System.Web.Services;

[WebService(Namespace = "http://tempuri.org/")]
[WebServiceBinding(ConformsTo = WsiProfiles.BasicProfile1_1)]
public class Service : System.Web.Services.WebService
{
    [WebMethod]
    public string GetVersion()
    {
        return "1.0.0";
    }

    [WebMethod]
    public string Echo(string message)
    {
        return $"Echo: {message}";
    }

    [WebMethod]
    public UserInfo GetUserInfo(string userId)
    {
        return new UserInfo
        {
            UserId = userId,
            UserName = $"用户{userId}",
            Email = $"user{userId}@example.com"
        };
    }
}

public class UserInfo
{
    public string UserId { get; set; }
    public string UserName { get; set; }
    public string Email { get; set; }
}
```

## 常见问题排除

### 问题1：无法连接到C#服务
**解决方案**：
1. 确保C#服务正在运行
2. 检查服务地址和端口配置
3. 确认防火墙设置
4. 使用浏览器测试WSDL URL是否可访问

### 问题2：WSDL解析失败
**解决方案**：
1. 检查WSDL文件格式是否正确
2. 确认WSDL文件路径配置
3. 检查网络连接（如果使用WSDL URL）
4. 查看应用日志获取详细错误信息

### 问题3：方法调用失败
**解决方案**：
1. 检查方法名是否正确（区分大小写）
2. 确认参数格式和类型
3. 查看应用日志获取详细错误信息
4. 使用Swagger UI测试API调用

### 问题4：编译错误
**解决方案**：
1. 确保Java 17或更高版本已安装
2. 确保Maven正确安装并配置
3. 检查网络连接（下载依赖需要）
4. 清理Maven缓存：`mvn clean`

## 高级配置

### 环境配置
应用支持多环境配置，可以通过以下方式指定环境：

```bash
# 开发环境
java -jar app.jar --spring.profiles.active=dev

# 测试环境
java -jar app.jar --spring.profiles.active=test

# 生产环境
java -jar app.jar --spring.profiles.active=prod
```

### 日志配置
可以通过修改 `application.yml` 中的 `logging` 配置来调整日志级别：

```yaml
logging:
  level:
    com.example.wsdlconverter: DEBUG
    org.apache.cxf: INFO
```

### 性能优化
- 调整连接超时时间
- 配置连接池
- 启用缓存机制

## 技术支持

如果您在使用过程中遇到问题，可以：

1. 查看应用日志文件：`logs/wsdl-converter.log`
2. 访问健康检查端点：`/api/wsdl/health`
3. 使用Swagger UI进行API测试
4. 检查Maven依赖是否正确下载

---

**注意**：请确保您的C#服务已正确配置CORS设置（如果需要跨域访问）。
