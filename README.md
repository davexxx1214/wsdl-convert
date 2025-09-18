# WSDL to RESTful API 转换器

这是一个基于Spring Boot Cloud和Apache CXF的Java应用程序，用于将WSDL SOAP服务转换为RESTful API接口。

## 功能特性

- ✅ 自动读取和解析WSDL文件
- ✅ 使用Apache CXF与C# SOAP服务通信
- ✅ 暴露RESTful API接口
- ✅ 支持JSON格式的请求和响应
- ✅ 动态方法调用支持
- ✅ 健康检查和监控
- ✅ Swagger API文档
- ✅ 多环境配置支持

## 技术栈

- **Spring Boot 3.1.5** - 主框架
- **Spring Cloud 2022.0.4** - 微服务支持
- **Apache CXF 4.0.3** - WSDL/SOAP客户端
- **Maven** - 构建工具
- **Java 17** - 运行环境
- **Swagger/OpenAPI 3** - API文档

## 项目结构

```
wsdl-restful-converter/
├── src/
│   ├── main/
│   │   ├── java/com/example/wsdlconverter/
│   │   │   ├── WsdlConverterApplication.java      # 主启动类
│   │   │   ├── config/
│   │   │   │   └── WsdlClientConfig.java          # WSDL客户端配置
│   │   │   ├── controller/
│   │   │   │   └── WsdlRestController.java        # REST控制器
│   │   │   └── service/
│   │   │       └── WsdlServiceAdapter.java        # WSDL服务适配器
│   │   └── resources/
│   │       ├── application.yml                    # 应用配置
│   │       └── wsdl/
│   │           └── service.wsdl                   # WSDL文件
│   └── test/                                      # 测试代码
├── pom.xml                                        # Maven配置
└── README.md                                      # 项目说明
```

## 快速开始

### 1. 环境要求

- Java 17 或更高版本
- Maven 3.6 或更高版本
- 运行中的C# SOAP服务

### 2. 配置WSDL服务

#### 方式一：使用本地WSDL文件

1. 将您的C#服务生成的WSDL文件保存为 `src/main/resources/wsdl/service.wsdl`
2. 修改 `application.yml` 中的配置：

```yaml
wsdl:
  service:
    url: http://your-csharp-service:port/Service.asmx  # 您的C#服务地址
    namespace: http://tempuri.org/                     # WSDL命名空间
  file:
    path: src/main/resources/wsdl/service.wsdl
```

#### 方式二：使用WSDL URL

```yaml
wsdl:
  service:
    url: http://your-csharp-service:port/Service.asmx
  file:
    url: http://your-csharp-service:port/Service.asmx?wsdl
```

### 3. 编译和运行

```bash
# 编译项目
mvn clean compile

# 运行应用
mvn spring-boot:run

# 或者打包后运行
mvn clean package
java -jar target/wsdl-restful-converter-1.0.0.jar
```

### 4. 访问应用

- **应用主页**: http://localhost:8080/wsdl-converter
- **Swagger文档**: http://localhost:8080/wsdl-converter/swagger-ui.html
- **健康检查**: http://localhost:8080/wsdl-converter/api/wsdl/health
- **服务信息**: http://localhost:8080/wsdl-converter/api/wsdl/info

## API 使用说明

### 1. 获取服务信息

```bash
GET /api/wsdl/info
```

返回WSDL服务的基本信息。

### 2. 获取可用方法列表

```bash
GET /api/wsdl/methods
```

返回WSDL服务中所有可用的方法列表。

### 3. 调用WSDL方法（POST）

```bash
POST /api/wsdl/invoke/{methodName}
Content-Type: application/json

{
  "param1": "value1",
  "param2": "value2"
}
```

### 4. 调用WSDL方法（GET）

```bash
GET /api/wsdl/invoke/{methodName}?param1=value1&param2=value2
```

### 5. 健康检查

```bash
GET /api/wsdl/health
```

## 使用示例

假设您的C#服务有一个名为 `GetUserInfo` 的方法：

### POST 调用示例

```bash
curl -X POST http://localhost:8080/wsdl-converter/api/wsdl/invoke/GetUserInfo \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "123",
    "includeDetails": true
  }'
```

### GET 调用示例

```bash
curl "http://localhost:8080/wsdl-converter/api/wsdl/invoke/GetUserInfo?userId=123&includeDetails=true"
```

### 响应格式

```json
{
  "success": true,
  "data": {
    "userId": "123",
    "userName": "张三",
    "email": "zhangsan@example.com"
  }
}
```

## 配置说明

### 主要配置项

| 配置项 | 描述 | 默认值 |
|--------|------|--------|
| `wsdl.service.url` | C#服务地址 | `http://localhost:8080/Service.asmx` |
| `wsdl.service.namespace` | WSDL命名空间 | `http://tempuri.org/` |
| `wsdl.file.url` | WSDL文件URL | 空 |
| `wsdl.file.path` | 本地WSDL文件路径 | `src/main/resources/wsdl/service.wsdl` |
| `wsdl.connection.timeout` | 连接超时时间 | `30000`毫秒 |
| `wsdl.receive.timeout` | 接收超时时间 | `60000`毫秒 |

### 环境配置

支持多环境配置：

- **开发环境**: `--spring.profiles.active=dev`
- **测试环境**: `--spring.profiles.active=test`
- **生产环境**: `--spring.profiles.active=prod`

## 监控和日志

### 监控端点

- `/actuator/health` - 健康状态
- `/actuator/info` - 应用信息
- `/actuator/metrics` - 性能指标

### 日志配置

日志文件位置：`logs/wsdl-converter.log`

可以通过修改 `application.yml` 中的 `logging` 配置来调整日志级别和输出格式。

## 故障排除

### 常见问题

1. **WSDL文件解析失败**
   - 检查WSDL文件格式是否正确
   - 确认WSDL文件路径配置正确
   - 检查网络连接（如果使用WSDL URL）

2. **连接C#服务失败**
   - 确认C#服务正在运行
   - 检查服务地址和端口配置
   - 确认防火墙设置

3. **方法调用失败**
   - 检查方法名是否正确
   - 确认参数格式和类型
   - 查看应用日志获取详细错误信息

### 调试模式

启用调试日志：

```bash
java -jar target/wsdl-restful-converter-1.0.0.jar --logging.level.com.example.wsdlconverter=DEBUG
```

## 开发和贡献

### 构建项目

```bash
# 清理并编译
mvn clean compile

# 运行测试
mvn test

# 打包
mvn package

# 跳过测试打包
mvn package -DskipTests
```

### 代码生成

项目使用Apache CXF插件从WSDL文件生成Java客户端代码：

```bash
mvn cxf:wsdl2java
```

生成的代码位于：`target/generated-sources/cxf/`

## 许可证

本项目采用 MIT 许可证。

## 支持

如果您遇到问题或有建议，请创建 Issue 或联系开发团队。
