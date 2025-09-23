package com.example.wsdlconverter.service;

import com.example.wsdlconverter.config.WsdlClientConfig;
import com.example.wsdlconverter.exception.WsdlServiceException;
import lombok.extern.slf4j.Slf4j;
import org.apache.cxf.endpoint.dynamic.DynamicClientFactory;
import org.apache.cxf.jaxws.endpoint.dynamic.JaxWsDynamicClientFactory;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.apache.wss4j.dom.handler.WSHandlerConstants;
import org.apache.wss4j.common.ext.WSPasswordCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.wsdl.Definition;
import javax.wsdl.Operation;
import javax.wsdl.PortType;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * WSDL服务适配器
 * 
 * 负责与C#后台SOAP服务通信，并提供统一的调用接口
 * 支持动态调用WSDL服务方法
 */
@Service
@Slf4j
public class WsdlServiceAdapter {

    // 常量定义
    private static final String METHOD_GET_VERSION = "getVersion";
    private static final String METHOD_PING = "ping";
    private static final String RESULT_SUCCESS = "success";
    private static final String RESULT_DATA = "data";

    @Autowired
    private WsdlClientConfig wsdlClientConfig;


    @Value("${wsdl.file.url:}")
    private String wsdlFileUrl;

    @Value("${wsdl.file.path:src/main/resources/wsdl/service.wsdl}")
    private String wsdlFilePath;

    @Value("${wsdl.security.enabled:false}")
    private boolean securityEnabled;

    @Value("${wsdl.security.username:}")
    private String securityUsername;

    @Value("${wsdl.security.password:}")
    private String securityPassword;

    private org.apache.cxf.endpoint.Client dynamicClient;
    private Definition wsdlDefinition;
    private List<String> availableMethods;

    /**
     * 初始化WSDL客户端
     */
    @PostConstruct
    public void initializeWsdlClient() {
        try {
            log.info("正在初始化WSDL客户端...");
            
            // 确定WSDL源
            String wsdlSource = determineWsdlSource();
            log.info("使用WSDL源: {}", wsdlSource);
            
            // 尝试创建动态客户端
            boolean clientCreated = tryCreateDynamicClient(wsdlSource);
            
            if (clientCreated) {
                // 解析WSDL定义
                parseWsdlDefinition(wsdlSource);
                
                // 提取可用方法
                extractAvailableMethods();
                
                log.info("WSDL客户端初始化完成，可用方法数量: {}", availableMethods.size());
            } else {
                log.warn("WSDL客户端初始化失败，应用将以有限功能模式启动");
                // 设置默认方法以支持基本操作
                setDefaultMethods();
            }
            
        } catch (Exception e) {
            log.error("初始化WSDL客户端失败: {}", e.getMessage(), e);
            // 设置默认方法以支持基本操作
            setDefaultMethods();
        }
    }

    /**
     * 确定WSDL源（URL或文件路径）
     */
    private String determineWsdlSource() {
        // 优先级1：明确指定的WSDL URL
        if (wsdlFileUrl != null && !wsdlFileUrl.trim().isEmpty()) {
            log.info("使用配置的WSDL URL: {}", wsdlFileUrl);
            return wsdlFileUrl;
        }
        
        // 优先级2：本地WSDL文件
        java.io.File wsdlFile = new java.io.File(wsdlFilePath);
        if (wsdlFile.exists()) {
            log.info("使用本地WSDL文件: {}", wsdlFile.getAbsolutePath());
            return wsdlFile.getAbsolutePath();
        }
        
        // 优先级3：尝试从服务URL + ?wsdl获取
        String wsdlUrl = wsdlClientConfig.getServiceUrl() + "?wsdl";
        log.warn("本地WSDL文件不存在，尝试从服务URL获取: {}", wsdlUrl);
        log.warn("注意：如果服务不支持?wsdl查询，初始化可能失败");
        return wsdlUrl;
    }

    /**
     * 尝试创建动态客户端
     * @param wsdlSource WSDL源
     * @return 是否创建成功
     */
    private boolean tryCreateDynamicClient(String wsdlSource) {
        try {
            createDynamicClient(wsdlSource);
            return true;
        } catch (Exception e) {
            log.error("创建动态客户端失败: {}", e.getMessage());
            log.error("可能的原因: 1) WSDL URL无法访问 2) 本地WSDL文件不存在或格式错误 3) 网络连接问题");
            return false;
        }
    }

    /**
     * 创建动态客户端
     */
    private void createDynamicClient(String wsdlSource) throws Exception {
        DynamicClientFactory factory = JaxWsDynamicClientFactory.newInstance();
        dynamicClient = factory.createClient(wsdlSource);
        
        // 配置安全设置（如果启用）
        if (securityEnabled) {
            configureDynamicClientSecurity();
        }
        
        log.info("动态客户端创建成功");
    }

    /**
     * 解析WSDL定义
     */
    private void parseWsdlDefinition(String wsdlSource) {
        try {
            WSDLFactory wsdlFactory = WSDLFactory.newInstance();
            WSDLReader reader = wsdlFactory.newWSDLReader();
            
            if (wsdlSource.startsWith("http")) {
                wsdlDefinition = reader.readWSDL(wsdlSource);
            } else {
                wsdlDefinition = reader.readWSDL(new java.io.File(wsdlSource).toURI().toString());
            }
            
            log.info("WSDL定义解析完成");
        } catch (Exception e) {
            log.warn("解析WSDL定义失败，将使用反射方式获取方法: {}", e.getMessage());
        }
    }

    /**
     * 提取可用方法列表
     */
    private void extractAvailableMethods() {
        availableMethods = new ArrayList<>();
        
        try {
            if (wsdlDefinition != null) {
                // 从WSDL定义提取方法
                extractMethodsFromWsdl();
            } else {
                // 从动态客户端提取方法
                extractMethodsFromClient();
            }
        } catch (Exception e) {
            log.error("提取可用方法失败: {}", e.getMessage(), e);
            availableMethods = List.of(METHOD_GET_VERSION, METHOD_PING, "echo"); // 默认方法
        }
        
        log.info("提取到的方法: {}", availableMethods);
    }

    /**
     * 从WSDL定义提取方法
     */
    private void extractMethodsFromWsdl() {
        Map<?, ?> portTypes = wsdlDefinition.getPortTypes();
        for (Object portTypeObj : portTypes.values()) {
            PortType portType = (PortType) portTypeObj;
            List<?> operations = portType.getOperations();
            
            for (Object operationObj : operations) {
                Operation operation = (Operation) operationObj;
                availableMethods.add(operation.getName());
            }
        }
    }

    /**
     * 从动态客户端提取方法
     */
    private void extractMethodsFromClient() {
        if (dynamicClient != null && dynamicClient.getEndpoint() != null) {
            // 通过反射获取客户端方法
            Class<?> clientClass = dynamicClient.getClass();
            Method[] methods = clientClass.getMethods();
            
            availableMethods = Arrays.stream(methods)
                    .map(Method::getName)
                    .filter(name -> !name.startsWith("get") && !name.startsWith("set") 
                            && !name.equals("toString") && !name.equals("hashCode") 
                            && !name.equals("equals") && !name.equals("getClass"))
                    .distinct()
                    .collect(Collectors.toList());
        }
    }

    /**
     * 调用WSDL服务方法
     * 
     * @param methodName 方法名
     * @param parameters 参数
     * @return 调用结果
     */
    public Object invokeMethod(String methodName, Map<String, Object> parameters) throws Exception {
        if (dynamicClient == null) {
            throw new IllegalStateException("WSDL客户端未初始化");
        }

        try {
            log.info("调用WSDL方法: {}, 参数: {}", methodName, parameters);
            
            // 准备调用参数
            Object[] args = prepareMethodArguments(methodName, parameters);
            
            // 调用方法
            Object[] results = dynamicClient.invoke(methodName, args);
            
            // 处理返回结果
            Object result = processMethodResult(results);
            
            log.info("WSDL方法调用成功: {}", methodName);
            return result;
            
        } catch (Exception e) {
            log.error("调用WSDL方法失败: {}, 错误: {}", methodName, e.getMessage(), e);
            throw new WsdlServiceException("调用WSDL方法失败: " + e.getMessage(), e);
        }
    }

    /**
     * 准备方法调用参数
     */
    private Object[] prepareMethodArguments(String methodName, Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return new Object[0];
        }

        // 将参数按顺序转换为数组
        // TODO: 根据WSDL定义和方法名来确定参数顺序和类型
        log.debug("为方法 {} 准备参数: {}", methodName, parameters);
        return parameters.values().toArray();
    }

    /**
     * 处理方法调用结果
     */
    private Object processMethodResult(Object[] results) {
        if (results == null || results.length == 0) {
            return Map.of(RESULT_SUCCESS, true, RESULT_DATA, null);
        }
        
        if (results.length == 1) {
            return Map.of(RESULT_SUCCESS, true, RESULT_DATA, results[0]);
        }
        
        // 多个返回值
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put(RESULT_SUCCESS, true);
        
        for (int i = 0; i < results.length; i++) {
            resultMap.put("result" + i, results[i]);
        }
        
        return resultMap;
    }

    /**
     * 获取服务信息
     */
    public Map<String, Object> getServiceInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("serviceUrl", wsdlClientConfig.getServiceUrl());
        info.put("namespace", wsdlClientConfig.getServiceNamespace());
        info.put("wsdlSource", determineWsdlSource());
        info.put("clientInitialized", dynamicClient != null);
        info.put("availableMethodsCount", availableMethods != null ? availableMethods.size() : 0);
        info.put("timestamp", System.currentTimeMillis());
        
        return info;
    }

    /**
     * 获取可用方法列表
     */
    public List<String> getAvailableMethods() {
        return availableMethods != null ? new ArrayList<>(availableMethods) : new ArrayList<>();
    }

    /**
     * 检查服务健康状态
     */
    public boolean isServiceHealthy() {
        try {
            if (dynamicClient == null) {
                return false;
            }
            
            // 尝试调用一个简单的方法来测试连接
            // 这里可以调用一个已知的轻量级方法，如ping或getVersion
            if (availableMethods.contains(METHOD_PING)) {
                invokeMethod(METHOD_PING, null);
            } else if (availableMethods.contains(METHOD_GET_VERSION)) {
                invokeMethod(METHOD_GET_VERSION, null);
            }
            
            return true;
            
        } catch (Exception e) {
            log.warn("服务健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 重新初始化客户端
     */
    public void reinitializeClient() {
        log.info("重新初始化WSDL客户端...");
        initializeWsdlClient();
    }

    /**
     * 设置默认方法（当WSDL无法获取时）
     */
    private void setDefaultMethods() {
        availableMethods = new ArrayList<>();
        availableMethods.add("GetVersion");
        availableMethods.add("Echo");
        availableMethods.add("Ping");
        log.info("设置默认可用方法: {}", availableMethods);
    }

    /**
     * 配置动态客户端的安全设置
     */
    private void configureDynamicClientSecurity() {
        try {
            Map<String, Object> properties = new HashMap<>();
            
            // 配置WSS4J出站安全
            properties.put(WSHandlerConstants.ACTION, WSHandlerConstants.USERNAME_TOKEN);
            properties.put(WSHandlerConstants.USER, securityUsername);
            properties.put(WSHandlerConstants.PASSWORD_TYPE, "PasswordText");
            properties.put(WSHandlerConstants.PW_CALLBACK_REF, new ClientPasswordCallback(securityPassword));
            
            // 创建WSS4J出站拦截器
            WSS4JOutInterceptor wssOut = new WSS4JOutInterceptor(properties);
            dynamicClient.getOutInterceptors().add(wssOut);
            
            log.info("动态客户端安全配置完成");
            
        } catch (Exception e) {
            log.error("配置动态客户端安全设置失败: {}", e.getMessage(), e);
        }
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
