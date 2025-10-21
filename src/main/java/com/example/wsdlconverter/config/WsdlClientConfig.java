package com.example.wsdlconverter.config;

import org.apache.cxf.Bus;
import org.apache.cxf.bus.spring.SpringBus;
import org.apache.cxf.ext.logging.LoggingInInterceptor;
import org.apache.cxf.ext.logging.LoggingOutInterceptor;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.apache.wss4j.dom.handler.WSHandlerConstants;
import org.apache.wss4j.common.ext.WSPasswordCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import lombok.extern.slf4j.Slf4j;

import javax.xml.namespace.QName;
import java.util.HashMap;
import java.util.Map;

/**
 * WSDL客户端配置类
 * 
 * 配置Apache CXF客户端，用于与C#后台SOAP服务通信
 */
@Configuration
@Slf4j
public class WsdlClientConfig {

    @Autowired
    private PfsCompatibleSecurityConfig pfsSecurityConfig;

    @Value("${wsdl.service.url:http://localhost:8080/Service.asmx}")
    private String serviceUrl;

    @Value("${wsdl.service.namespace:http://tempuri.org/}")
    private String serviceNamespace;

    @Value("${wsdl.connection.timeout:30000}")
    private long connectionTimeout;

    @Value("${wsdl.receive.timeout:60000}")
    private long receiveTimeout;

    @Value("${wsdl.security.username:}")
    private String securityUsername;

    @Value("${wsdl.security.password:}")
    private String securityPassword;

    @Value("${wsdl.security.enabled:false}")
    private boolean securityEnabled;

    @Value("${wsdl.security.use-pfs-compatible:true}")
    private boolean usePfsCompatible;

    @Value("${wsdl.security.pfs.client-id:DEFAULT}")
    private String pfsClientId;

    @Value("${wsdl.security.pfs.windows-authentication:false}")
    private boolean pfsWindowsAuthentication;

    @Value("${wsdl.security.pfs.change-password:false}")
    private boolean pfsChangePassword;

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
        
        // 配置安全设置（如果启用）
        if (securityEnabled) {
            configureSecurity(factory);
        }
        
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

    /**
     * 配置WS-Security设置
     */
    private void configureSecurity(JaxWsProxyFactoryBean factory) {
        if (usePfsCompatible) {
            // 使用PFS兼容的安全配置
            configurePfsCompatibleSecurity(factory);
        } else {
            // 使用标准的WS-Security配置
            configureStandardSecurity(factory);
        }
    }
    
    /**
     * 配置PFS兼容的安全设置
     */
    private void configurePfsCompatibleSecurity(JaxWsProxyFactoryBean factory) {
        try {
            // 添加日志拦截器（用于调试）
            factory.getOutInterceptors().add(new LoggingOutInterceptor());
            factory.getInInterceptors().add(new LoggingInInterceptor());
            
            // 使用我们自定义的PFS兼容拦截器，传递所有PFS参数
            PfsCompatibleSecurityConfig.PfsWsSecurityInterceptor pfsInterceptor = 
                pfsSecurityConfig.createPfsInterceptor(
                    securityUsername, 
                    securityPassword, 
                    pfsClientId, 
                    pfsWindowsAuthentication, 
                    pfsChangePassword
                );
            
            factory.getOutInterceptors().add(pfsInterceptor);
            
            log.info("已配置PFS兼容的WS-Security设置 - ClientID: {}, WindowsAuth: {}, ChangePassword: {}", 
                    pfsClientId, pfsWindowsAuthentication, pfsChangePassword);
            
        } catch (Exception e) {
            log.error("配置PFS兼容安全设置失败，回退到标准配置: {}", e.getMessage());
            configureStandardSecurity(factory);
        }
    }
    
    /**
     * 配置标准的WS-Security设置
     */
    private void configureStandardSecurity(JaxWsProxyFactoryBean factory) {
        Map<String, Object> properties = new HashMap<>();
        
        // 配置WSS4J出站安全
        properties.put(WSHandlerConstants.ACTION, WSHandlerConstants.USERNAME_TOKEN);
        properties.put(WSHandlerConstants.USER, securityUsername);
        properties.put(WSHandlerConstants.PASSWORD_TYPE, "PasswordText");
        properties.put(WSHandlerConstants.PW_CALLBACK_REF, new ClientPasswordCallback(securityPassword));
        
        // 创建WSS4J出站拦截器
        WSS4JOutInterceptor wssOut = new WSS4JOutInterceptor(properties);
        factory.getOutInterceptors().add(wssOut);
        
        log.info("已配置标准WS-Security设置");
    }

    /**
     * 密码回调处理器
     */
    private static class ClientPasswordCallback implements javax.security.auth.callback.CallbackHandler {
        private final String password;

        public ClientPasswordCallback(String password) {
            this.password = password;
        }

        @Override
        public void handle(javax.security.auth.callback.Callback[] callbacks) 
                throws java.io.IOException, javax.security.auth.callback.UnsupportedCallbackException {
            for (javax.security.auth.callback.Callback callback : callbacks) {
                if (callback instanceof WSPasswordCallback) {
                    WSPasswordCallback pc = (WSPasswordCallback) callback;
                    pc.setPassword(password);
                }
            }
        }
    }
}
