#!/bin/bash

echo "======================================"
echo "WSDL to RESTful API Converter"
echo "======================================"
echo

# 检查Java环境
if ! command -v java &> /dev/null; then
    echo "错误: 请确保Java 17或更高版本已安装并配置在PATH中"
    exit 1
fi

echo "Java版本信息："
java -version
echo

# 检查Maven环境
if command -v mvn &> /dev/null; then
    echo "找到Maven，开始编译和启动应用..."
    echo
    
    echo "正在清理和编译项目..."
    mvn clean compile
    
    if [ $? -eq 0 ]; then
        echo "编译成功，正在启动应用..."
        echo "应用启动后访问地址："
        echo "http://localhost:8080/wsdl-converter/swagger-ui.html"
        echo
        mvn spring-boot:run
    else
        echo "编译失败，请检查错误信息"
        exit 1
    fi
else
    echo "未找到Maven，请使用以下命令启动应用："
    echo "mvn spring-boot:run"
    echo
    echo "或者，使用以下命令打包并运行："
    echo "mvn clean package"
    echo "java -jar target/wsdl-restful-converter-1.0.0.jar"
    echo
    echo "应用启动后访问地址："
    echo "http://localhost:8080/wsdl-converter/swagger-ui.html"
fi
