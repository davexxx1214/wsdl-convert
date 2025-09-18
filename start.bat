@echo off
echo ======================================
echo WSDL to RESTful API Converter
echo ======================================
echo.

REM 检查Java环境
java -version
if %ERRORLEVEL% neq 0 (
    echo 错误: 请确保Java 17或更高版本已安装并配置在PATH中
    pause
    exit /b 1
)

echo.
echo 如果您已安装Maven，请使用以下命令启动应用：
echo mvn spring-boot:run
echo.
echo 或者，使用以下命令打包并运行：
echo mvn clean package
echo java -jar target\wsdl-restful-converter-1.0.0.jar
echo.
echo 应用启动后访问地址：
echo http://localhost:8080/wsdl-converter/swagger-ui.html
echo.
pause
