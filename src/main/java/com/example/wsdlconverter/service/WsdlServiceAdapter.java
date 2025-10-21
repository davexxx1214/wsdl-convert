package com.example.wsdlconverter.service;

import com.example.wsdlconverter.config.WsdlClientConfig;
import com.example.wsdlconverter.config.PfsCompatibleSecurityConfig;
import com.example.wsdlconverter.exception.WsdlServiceException;
import lombok.extern.slf4j.Slf4j;
import org.apache.cxf.endpoint.dynamic.DynamicClientFactory;
import org.apache.cxf.ext.logging.LoggingInInterceptor;
import org.apache.cxf.ext.logging.LoggingOutInterceptor;
import org.apache.cxf.jaxws.endpoint.dynamic.JaxWsDynamicClientFactory;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.apache.wss4j.dom.handler.WSHandlerConstants;
import org.apache.wss4j.common.ext.WSPasswordCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.wsdl.Definition;
import javax.wsdl.Message;
import javax.wsdl.Operation;
import javax.wsdl.Part;
import javax.wsdl.PortType;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;
import javax.xml.namespace.QName;
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

    @Autowired
    private WsdlResolverService wsdlResolverService;

    @Autowired
    private PfsCompatibleSecurityConfig pfsSecurityConfig;

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

    @Value("${wsdl.security.use-pfs-compatible:true}")
    private boolean usePfsCompatible;

    @Value("${wsdl.security.pfs.client-id:DEFAULT}")
    private String pfsClientId;

    @Value("${wsdl.security.pfs.windows-authentication:false}")
    private boolean pfsWindowsAuthentication;

    @Value("${wsdl.security.pfs.change-password:false}")
    private boolean pfsChangePassword;

    private org.apache.cxf.endpoint.Client dynamicClient;
    private Definition wsdlDefinition;
    private List<String> availableMethods;
    private Map<String, OperationInfo> operationInfoMap;

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
                
                // 解析操作信息
                parseOperationInfos();
                
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
            
            // 检查是否为复杂WSDL（需要解析嵌套引用）
            if (isComplexWsdlUrl(wsdlFileUrl)) {
                log.info("检测到复杂WSDL，开始解析嵌套引用...");
                try {
                    return wsdlResolverService.resolveComplexWsdl(wsdlFileUrl);
                } catch (Exception e) {
                    log.error("解析复杂WSDL失败: {}", e.getMessage());
                    return wsdlFileUrl; // 回退到原始URL
                }
            }
            
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
        
        // 检查URL是否可访问
        if (wsdlResolverService.isUrlAccessible(wsdlUrl)) {
            // 检查是否为复杂WSDL
            if (isComplexWsdlUrl(wsdlUrl)) {
                log.info("检测到复杂WSDL，开始解析嵌套引用...");
                try {
                    return wsdlResolverService.resolveComplexWsdl(wsdlUrl);
                } catch (Exception e) {
                    log.error("解析复杂WSDL失败: {}", e.getMessage());
                    return wsdlUrl; // 回退到原始URL
                }
            }
            return wsdlUrl;
        } else {
            log.warn("WSDL URL不可访问: {}", wsdlUrl);
            log.warn("无法获取WSDL文档，将使用默认方法启动");
            return null; // 返回null表示无法获取WSDL
        }
    }

    /**
     * 判断是否为复杂WSDL（包含嵌套引用）
     */
    private boolean isComplexWsdlUrl(String url) {
        // 简单启发式判断：如果URL包含特定模式，认为可能是复杂WSDL
        return url.contains("?") || url.contains("wsdl") || url.toLowerCase().contains("service");
    }

    /**
     * 尝试创建动态客户端
     * @param wsdlSource WSDL源
     * @return 是否创建成功
     */
    private boolean tryCreateDynamicClient(String wsdlSource) {
        if (wsdlSource == null) {
            log.warn("WSDL源为空，无法创建动态客户端");
            return false;
        }
        
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
        
        // 先创建客户端
        dynamicClient = factory.createClient(wsdlSource);
        
        // 如果使用PFS兼容模式，禁用WS-Policy处理
        if (usePfsCompatible) {
            // 在Bus级别禁用WS-Policy引擎（通过客户端的Bus）
            dynamicClient.getBus().setProperty("org.apache.cxf.ws.policy.PolicyEngine.enabled", Boolean.FALSE);
            dynamicClient.getBus().setProperty("ws-security.validate.token", Boolean.FALSE);
            log.info("已在Bus级别禁用WS-Policy引擎");
            
            // 禁用端点级别的WS-Policy拦截器
            disableWsPolicyProcessing();
        }
        
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

        try {
            // 根据WSDL定义和方法名来确定参数顺序和类型
            OperationInfo operationInfo = getOperationInfo(methodName);
            if (operationInfo != null && !operationInfo.getInputParameters().isEmpty()) {
                return prepareOrderedArguments(operationInfo, parameters);
            }
            
            // 回退到原始方式
            log.debug("未找到方法 {} 的WSDL定义，使用默认参数顺序", methodName);
            return parameters.values().toArray();
            
        } catch (Exception e) {
            log.warn("解析方法 {} 的参数定义失败，使用默认参数顺序: {}", methodName, e.getMessage());
            return parameters.values().toArray();
        }
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
     * 解析WSDL操作信息
     */
    private void parseOperationInfos() {
        operationInfoMap = new HashMap<>();
        
        if (wsdlDefinition == null) {
            log.warn("WSDL定义为空，无法解析操作信息");
            return;
        }
        
        try {
            Map<?, ?> portTypes = wsdlDefinition.getPortTypes();
            for (Object portTypeObj : portTypes.values()) {
                PortType portType = (PortType) portTypeObj;
                List<?> operations = portType.getOperations();
                
                for (Object operationObj : operations) {
                    Operation operation = (Operation) operationObj;
                    OperationInfo operationInfo = parseOperationInfo(operation);
                    if (operationInfo != null) {
                        operationInfoMap.put(operation.getName(), operationInfo);
                        log.debug("解析操作信息: {} - 输入参数数量: {}", 
                                operation.getName(), operationInfo.getInputParameters().size());
                    }
                }
            }
            
            log.info("成功解析 {} 个操作的参数信息", operationInfoMap.size());
            
        } catch (Exception e) {
            log.error("解析WSDL操作信息失败: {}", e.getMessage(), e);
            operationInfoMap = new HashMap<>();
        }
    }

    /**
     * 解析单个操作的信息
     */
    private OperationInfo parseOperationInfo(Operation operation) {
        try {
            OperationInfo operationInfo = new OperationInfo(operation.getName());
            
            // 解析输入参数
            if (operation.getInput() != null && operation.getInput().getMessage() != null) {
                Message inputMessage = operation.getInput().getMessage();
                parseMessageParameters(inputMessage, operationInfo, true);
            }
            
            // 解析输出参数
            if (operation.getOutput() != null && operation.getOutput().getMessage() != null) {
                Message outputMessage = operation.getOutput().getMessage();
                parseMessageParameters(outputMessage, operationInfo, false);
            }
            
            return operationInfo;
            
        } catch (Exception e) {
            log.warn("解析操作 {} 的信息失败: {}", operation.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * 解析消息参数
     */
    private void parseMessageParameters(Message message, OperationInfo operationInfo, boolean isInput) {
        try {
            Map<?, ?> parts = message.getParts();
            int order = 0;
            
            for (Object partObj : parts.values()) {
                Part part = (Part) partObj;
                
                // 获取参数名称
                String paramName = part.getName();
                
                // 获取参数类型
                QName paramType = null;
                if (part.getTypeName() != null) {
                    paramType = part.getTypeName();
                } else if (part.getElementName() != null) {
                    paramType = part.getElementName();
                }
                
                // 创建参数信息
                ParameterInfo paramInfo = new ParameterInfo(
                    paramName, 
                    paramType, 
                    true, // 默认为必需参数
                    order++
                );
                
                if (isInput) {
                    operationInfo.addInputParameter(paramInfo);
                } else {
                    operationInfo.addOutputParameter(paramInfo);
                }
                
                log.debug("解析参数: {} - 类型: {} - 顺序: {}", 
                        paramName, paramType, order - 1);
            }
            
        } catch (Exception e) {
            log.warn("解析消息 {} 的参数失败: {}", message.getQName(), e.getMessage());
        }
    }

    /**
     * 获取操作信息
     */
    private OperationInfo getOperationInfo(String methodName) {
        if (operationInfoMap == null) {
            return null;
        }
        
        // 直接匹配
        OperationInfo operationInfo = operationInfoMap.get(methodName);
        if (operationInfo != null) {
            return operationInfo;
        }
        
        // 尝试不区分大小写匹配
        for (Map.Entry<String, OperationInfo> entry : operationInfoMap.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(methodName)) {
                return entry.getValue();
            }
        }
        
        return null;
    }

    /**
     * 根据WSDL定义准备有序参数
     */
    private Object[] prepareOrderedArguments(OperationInfo operationInfo, Map<String, Object> parameters) {
        List<ParameterInfo> inputParams = operationInfo.getInputParameters();
        
        // 如果没有定义输入参数，返回空数组
        if (inputParams.isEmpty()) {
            return new Object[0];
        }
        
        // 按照WSDL定义的顺序准备参数
        Object[] orderedArgs = new Object[inputParams.size()];
        
        for (int i = 0; i < inputParams.size(); i++) {
            ParameterInfo paramInfo = inputParams.get(i);
            String paramName = paramInfo.getName();
            
            // 尝试多种方式匹配参数名
            Object paramValue = findParameterValue(parameters, paramName);
            
            if (paramValue != null) {
                // 尝试类型转换
                orderedArgs[i] = convertParameterType(paramValue, paramInfo.getType());
            } else if (paramInfo.isRequired()) {
                log.warn("缺少必需参数: {}", paramName);
                orderedArgs[i] = null;
            } else {
                orderedArgs[i] = null;
            }
        }
        
        log.debug("准备有序参数完成，参数数量: {}", orderedArgs.length);
        return orderedArgs;
    }

    /**
     * 查找参数值（支持多种匹配方式）
     */
    private Object findParameterValue(Map<String, Object> parameters, String paramName) {
        // 直接匹配
        if (parameters.containsKey(paramName)) {
            return parameters.get(paramName);
        }
        
        // 不区分大小写匹配
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(paramName)) {
                return entry.getValue();
            }
        }
        
        // 去掉命名空间前缀匹配
        String simpleParamName = paramName.contains(":") ? 
            paramName.substring(paramName.lastIndexOf(":") + 1) : paramName;
        
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String simpleKey = entry.getKey().contains(":") ? 
                entry.getKey().substring(entry.getKey().lastIndexOf(":") + 1) : entry.getKey();
            
            if (simpleKey.equalsIgnoreCase(simpleParamName)) {
                return entry.getValue();
            }
        }
        
        return null;
    }

    /**
     * 转换参数类型
     */
    private Object convertParameterType(Object value, QName targetType) {
        if (value == null || targetType == null) {
            return value;
        }
        
        try {
            String localPart = targetType.getLocalPart().toLowerCase();
            
            // 基本类型转换
            switch (localPart) {
                case "string":
                    return value.toString();
                case "int":
                case "integer":
                    if (value instanceof Number) {
                        return ((Number) value).intValue();
                    }
                    return Integer.parseInt(value.toString());
                case "long":
                    if (value instanceof Number) {
                        return ((Number) value).longValue();
                    }
                    return Long.parseLong(value.toString());
                case "double":
                    if (value instanceof Number) {
                        return ((Number) value).doubleValue();
                    }
                    return Double.parseDouble(value.toString());
                case "float":
                    if (value instanceof Number) {
                        return ((Number) value).floatValue();
                    }
                    return Float.parseFloat(value.toString());
                case "boolean":
                    if (value instanceof Boolean) {
                        return value;
                    }
                    return Boolean.parseBoolean(value.toString());
                default:
                    // 对于复杂类型，保持原值
                    return value;
            }
            
        } catch (Exception e) {
            log.warn("类型转换失败，参数值: {}, 目标类型: {}, 错误: {}", 
                    value, targetType, e.getMessage());
            return value;
        }
    }

    /**
     * 禁用WS-Policy处理（用于自定义认证）
     */
    private void disableWsPolicyProcessing() {
        try {
            // 移除所有WS-Policy相关的拦截器
            dynamicClient.getEndpoint().getOutInterceptors().removeIf(
                interceptor -> interceptor.getClass().getName().contains("policy") ||
                              interceptor.getClass().getName().contains("Policy") ||
                              interceptor.getClass().getName().contains("SecureConversation")
            );
            
            dynamicClient.getEndpoint().getInInterceptors().removeIf(
                interceptor -> interceptor.getClass().getName().contains("policy") ||
                              interceptor.getClass().getName().contains("Policy") ||
                              interceptor.getClass().getName().contains("SecureConversation")
            );
            
            log.info("已禁用WS-Policy和WS-SecureConversation处理，使用自定义PFS认证");
            
        } catch (Exception e) {
            log.warn("禁用WS-Policy处理时出错: {}", e.getMessage());
        }
    }
    
    /**
     * 配置动态客户端的安全设置
     */
    private void configureDynamicClientSecurity() {
        try {
            if (usePfsCompatible) {
                // 使用PFS兼容的安全配置
                configurePfsCompatibleDynamicSecurity();
            } else {
                // 使用标准的WS-Security配置
                configureStandardDynamicSecurity();
            }
            
        } catch (Exception e) {
            log.error("配置动态客户端安全设置失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 配置PFS兼容的动态客户端安全设置
     */
    private void configurePfsCompatibleDynamicSecurity() {
        try {
            // 添加日志拦截器（用于调试）
            dynamicClient.getOutInterceptors().add(new LoggingOutInterceptor());
            dynamicClient.getInInterceptors().add(new LoggingInInterceptor());
            
            // 使用我们自定义的PFS兼容拦截器，传递所有PFS参数
            PfsCompatibleSecurityConfig.PfsWsSecurityInterceptor pfsInterceptor = 
                pfsSecurityConfig.createPfsInterceptor(
                    securityUsername, 
                    securityPassword, 
                    pfsClientId, 
                    pfsWindowsAuthentication, 
                    pfsChangePassword
                );
            
            dynamicClient.getOutInterceptors().add(pfsInterceptor);
            
            log.info("动态客户端PFS兼容安全配置完成 - ClientID: {}, WindowsAuth: {}, ChangePassword: {}", 
                    pfsClientId, pfsWindowsAuthentication, pfsChangePassword);
            
        } catch (Exception e) {
            log.error("配置PFS兼容安全设置失败，回退到标准配置: {}", e.getMessage());
            configureStandardDynamicSecurity();
        }
    }
    
    /**
     * 配置标准的动态客户端安全设置
     */
    private void configureStandardDynamicSecurity() {
        Map<String, Object> properties = new HashMap<>();
        
        // 配置WSS4J出站安全
        properties.put(WSHandlerConstants.ACTION, WSHandlerConstants.USERNAME_TOKEN);
        properties.put(WSHandlerConstants.USER, securityUsername);
        properties.put(WSHandlerConstants.PASSWORD_TYPE, "PasswordText");
        properties.put(WSHandlerConstants.PW_CALLBACK_REF, new ClientPasswordCallback(securityPassword));
        
        // 创建WSS4J出站拦截器
        WSS4JOutInterceptor wssOut = new WSS4JOutInterceptor(properties);
        dynamicClient.getOutInterceptors().add(wssOut);
        
        log.info("动态客户端标准安全配置完成");
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

    /**
     * 操作信息类，用于存储WSDL操作的参数信息
     */
    private static class OperationInfo {
        private final String operationName;
        private final List<ParameterInfo> inputParameters;
        private final List<ParameterInfo> outputParameters;

        public OperationInfo(String operationName) {
            this.operationName = operationName;
            this.inputParameters = new ArrayList<>();
            this.outputParameters = new ArrayList<>();
        }

        public String getOperationName() {
            return operationName;
        }

        public List<ParameterInfo> getInputParameters() {
            return inputParameters;
        }

        public List<ParameterInfo> getOutputParameters() {
            return outputParameters;
        }

        public void addInputParameter(ParameterInfo parameter) {
            this.inputParameters.add(parameter);
        }

        public void addOutputParameter(ParameterInfo parameter) {
            this.outputParameters.add(parameter);
        }
    }

    /**
     * 参数信息类，用于存储参数的名称、类型等信息
     */
    private static class ParameterInfo {
        private final String name;
        private final QName type;
        private final boolean required;
        private final int order;

        public ParameterInfo(String name, QName type, boolean required, int order) {
            this.name = name;
            this.type = type;
            this.required = required;
            this.order = order;
        }

        public String getName() {
            return name;
        }

        public QName getType() {
            return type;
        }

        public boolean isRequired() {
            return required;
        }

        public int getOrder() {
            return order;
        }
    }
}
