@echo off
echo === PFS兼容性连接测试 ===
echo.

REM 检查应用是否启动
echo 1. 检查应用状态...
curl -s http://localhost:8080/wsdl-converter/api/wsdl/health >nul 2>&1
if %errorlevel% equ 0 (
    echo ✓ 应用正在运行
) else (
    echo ✗ 应用未启动，请先运行 start.bat
    pause
    exit /b 1
)

echo.

REM 获取服务信息
echo 2. 获取服务信息...
curl -s http://localhost:8080/wsdl-converter/api/wsdl/info

echo.
echo.

REM 获取可用方法
echo 3. 获取可用方法...
curl -s http://localhost:8080/wsdl-converter/api/wsdl/methods

echo.
echo.

REM 测试连接（如果有GetVersion方法）
echo 4. 测试方法调用...
echo 尝试调用 GetVersion 方法...
curl -s -X POST http://localhost:8080/wsdl-converter/api/wsdl/invoke/GetVersion -H "Content-Type: application/json" -d "{}"

echo.
echo.

REM 测试Echo方法（如果存在）
echo 5. 测试 Echo 方法...
curl -s -X POST http://localhost:8080/wsdl-converter/api/wsdl/invoke/Echo -H "Content-Type: application/json" -d "{\"message\": \"PFS测试消息\"}"

echo.
echo.

REM 检查日志中的安全配置信息
echo 6. 检查安全配置...
if exist "logs\wsdl-converter.log" (
    echo 查找PFS兼容配置日志...
    findstr /i "pfs兼容" logs\wsdl-converter.log 2>nul
    echo.
    echo 查找安全配置日志...
    findstr /i "安全配置" logs\wsdl-converter.log 2>nul
) else (
    echo 日志文件不存在，请检查日志配置
)

echo.
echo === 测试完成 ===
echo.
echo 故障排除建议：
echo 1. 如果连接失败，请检查 application.yml 中的用户名和密码
echo 2. 确保 C# 服务正在运行并可访问
echo 3. 检查日志文件中的详细错误信息
echo 4. 如果PFS兼容模式有问题，可以设置 use-pfs-compatible: false

pause
