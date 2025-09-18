package com.example.wsdlconverter.config;

import org.apache.cxf.Bus;
import org.apache.cxf.bus.spring.SpringBus;
// LoggingFeature import removed - will be handled differently
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.xml.namespace.QName;

/**
 * WSDL客户端配置类
 * 
 * 配置Apache CXF客户端，用于与C#后台SOAP服务通信
 */
@Configuration
public class WsdlClientConfig {

    @Value("${wsdl.service.url:http://localhost:8080/Service.asmx}")
    private String serviceUrl;

    @Value("${wsdl.service.namespace:http://tempuri.org/}")
    private String serviceNamespace;

    @Value("${wsdl.connection.timeout:30000}")
    private long connectionTimeout;

    @Value("${wsdl.receive.timeout:60000}")
    private long receiveTimeout;

    /**
     * 配置CXF Bus
     */
    @Bean
    @Primary
    public Bus cxfBus() {
        SpringBus bus = new SpringBus();
        // Logging feature can be configured via properties
        return bus;
    }

    /**
     * 创建WSDL服务客户端代理工厂
     * 这是一个通用的方法，可以为任何WSDL服务接口创建客户端代理
     */
    public <T> T createWsdlClient(Class<T> serviceInterface, String serviceName) {
        JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
        factory.setServiceClass(serviceInterface);
        factory.setAddress(serviceUrl);
        
        // 设置服务名称和命名空间
        QName qname = new QName(serviceNamespace, serviceName);
        factory.setServiceName(qname);
        
        // 设置特性 - 日志记录通过配置启用
        
        // 创建客户端代理
        T client = (T) factory.create();
        
        // 配置HTTP传输
        configureHttpTransport(client);
        
        return client;
    }

    /**
     * 配置HTTP传输参数
     */
    private void configureHttpTransport(Object client) {
        org.apache.cxf.endpoint.Client cxfClient = ClientProxy.getClient(client);
        HTTPConduit httpConduit = (HTTPConduit) cxfClient.getConduit();
        
        HTTPClientPolicy httpClientPolicy = new HTTPClientPolicy();
        httpClientPolicy.setConnectionTimeout(connectionTimeout);
        httpClientPolicy.setReceiveTimeout(receiveTimeout);
        httpClientPolicy.setAllowChunking(false);
        
        httpConduit.setClient(httpClientPolicy);
    }

    /**
     * 获取服务URL
     */
    public String getServiceUrl() {
        return serviceUrl;
    }

    /**
     * 获取服务命名空间
     */
    public String getServiceNamespace() {
        return serviceNamespace;
    }
}
