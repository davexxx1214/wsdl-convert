package com.example.wsdlconverter.service;

import com.example.wsdlconverter.config.WsdlClientConfig;
import com.example.wsdlconverter.exception.WsdlServiceException;
import lombok.extern.slf4j.Slf4j;
import org.apache.cxf.endpoint.dynamic.DynamicClientFactory;
import org.apache.cxf.jaxws.endpoint.dynamic.JaxWsDynamicClientFactory;
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
            
            // 创建动态客户端
            createDynamicClient(wsdlSource);
            
            // 解析WSDL定义
            parseWsdlDefinition(wsdlSource);
            
            // 提取可用方法
            extractAvailableMethods();
            
            log.info("WSDL客户端初始化完成，可用方法数量: {}", availableMethods.size());
            
        } catch (Exception e) {
            log.error("初始化WSDL客户端失败: {}", e.getMessage(), e);
            // 不抛出异常，允许应用启动，但标记为不健康状态
        }
    }

    /**
     * 确定WSDL源（URL或文件路径）
     */
    private String determineWsdlSource() {
        if (wsdlFileUrl != null && !wsdlFileUrl.trim().isEmpty()) {
            return wsdlFileUrl;
        } else {
            // 检查本地文件是否存在
            java.io.File wsdlFile = new java.io.File(wsdlFilePath);
            if (wsdlFile.exists()) {
                return wsdlFile.getAbsolutePath();
            } else {
                // 使用服务URL + ?wsdl
                return wsdlClientConfig.getServiceUrl() + "?wsdl";
            }
        }
    }

    /**
     * 创建动态客户端
     */
    private void createDynamicClient(String wsdlSource) throws Exception {
        DynamicClientFactory factory = JaxWsDynamicClientFactory.newInstance();
        dynamicClient = factory.createClient(wsdlSource);
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
}
