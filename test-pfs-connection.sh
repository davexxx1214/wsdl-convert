#!/bin/bash

# PFS兼容性连接测试脚本

echo "=== PFS兼容性连接测试 ==="
echo

# 检查应用是否启动
echo "1. 检查应用状态..."
curl -s http://localhost:8080/wsdl-converter/api/wsdl/health > /dev/null
if [ $? -eq 0 ]; then
    echo "✓ 应用正在运行"
else
    echo "✗ 应用未启动，请先运行 ./start.sh"
    exit 1
fi

echo

# 获取服务信息
echo "2. 获取服务信息..."
SERVICE_INFO=$(curl -s http://localhost:8080/wsdl-converter/api/wsdl/info)
echo "$SERVICE_INFO" | jq . 2>/dev/null || echo "$SERVICE_INFO"

echo

# 获取可用方法
echo "3. 获取可用方法..."
METHODS=$(curl -s http://localhost:8080/wsdl-converter/api/wsdl/methods)
echo "$METHODS" | jq . 2>/dev/null || echo "$METHODS"

echo

# 测试连接（如果有GetVersion方法）
echo "4. 测试方法调用..."
echo "尝试调用 GetVersion 方法..."
VERSION_RESULT=$(curl -s -X POST http://localhost:8080/wsdl-converter/api/wsdl/invoke/GetVersion \
  -H "Content-Type: application/json" \
  -d "{}")

if echo "$VERSION_RESULT" | grep -q "success"; then
    echo "✓ GetVersion 调用成功"
    echo "$VERSION_RESULT" | jq . 2>/dev/null || echo "$VERSION_RESULT"
else
    echo "✗ GetVersion 调用失败"
    echo "$VERSION_RESULT"
fi

echo

# 测试Echo方法（如果存在）
echo "5. 测试 Echo 方法..."
ECHO_RESULT=$(curl -s -X POST http://localhost:8080/wsdl-converter/api/wsdl/invoke/Echo \
  -H "Content-Type: application/json" \
  -d '{"message": "PFS测试消息"}')

if echo "$ECHO_RESULT" | grep -q "success"; then
    echo "✓ Echo 调用成功"
    echo "$ECHO_RESULT" | jq . 2>/dev/null || echo "$ECHO_RESULT"
else
    echo "✗ Echo 调用失败或方法不存在"
    echo "$ECHO_RESULT"
fi

echo

# 检查日志中的安全配置信息
echo "6. 检查安全配置..."
if [ -f "logs/wsdl-converter.log" ]; then
    echo "查找PFS兼容配置日志..."
    grep -i "pfs兼容" logs/wsdl-converter.log | tail -5
    echo
    echo "查找安全配置日志..."
    grep -i "安全配置" logs/wsdl-converter.log | tail -5
else
    echo "日志文件不存在，请检查日志配置"
fi

echo
echo "=== 测试完成 ==="

# 提供故障排除建议
echo
echo "故障排除建议："
echo "1. 如果连接失败，请检查 application.yml 中的用户名和密码"
echo "2. 确保 C# 服务正在运行并可访问"
echo "3. 检查日志文件中的详细错误信息"
echo "4. 如果PFS兼容模式有问题，可以设置 use-pfs-compatible: false"
