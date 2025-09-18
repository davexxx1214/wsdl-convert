package com.example.wsdlconverter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * WSDL to RESTful API转换器主启动类
 * 
 * 这个应用程序提供以下功能：
 * 1. 读取WSDL文件并解析服务接口
 * 2. 使用Apache CXF与SOAP服务通信
 * 3. 暴露RESTful API接口供前端调用
 * 4. 自动进行数据格式转换（SOAP <-> JSON）
 */
@SpringBootApplication
@EnableFeignClients
public class WsdlConverterApplication {

    public static void main(String[] args) {
        SpringApplication.run(WsdlConverterApplication.class, args);
    }
}
