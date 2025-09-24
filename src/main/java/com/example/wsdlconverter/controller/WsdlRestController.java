package com.example.wsdlconverter.controller;

import com.example.wsdlconverter.service.WsdlServiceAdapter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * WSDL转RESTful API控制器
 * 
 * 这个控制器提供RESTful接口，内部调用WSDL服务
 * 支持动态方法调用和参数传递
 */
@RestController
@RequestMapping("/api/wsdl")
@Tag(name = "WSDL Service API", description = "WSDL服务的RESTful API接口")
@Slf4j
public class WsdlRestController {

    @Autowired
    private WsdlServiceAdapter wsdlServiceAdapter;

    @Autowired
    private com.example.wsdlconverter.service.WsdlResolverService wsdlResolverService;


    /**
     * 调用WSDL服务的通用接口
     * 
     * @param methodName WSDL服务方法名
     * @param requestBody 请求参数（JSON格式）
     * @return WSDL服务响应（JSON格式）
     */
    @PostMapping(value = "/invoke/{methodName}", 
                 consumes = MediaType.APPLICATION_JSON_VALUE, 
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "调用WSDL服务方法", 
               description = "通过RESTful接口调用后台WSDL服务的指定方法")
    public ResponseEntity<Object> invokeWsdlMethod(
            @Parameter(description = "WSDL服务方法名", required = true)
            @PathVariable String methodName,
            @Parameter(description = "请求参数（JSON格式）", required = false)
            @RequestBody(required = false) Map<String, Object> requestBody) {
        
        try {
            log.info("调用WSDL方法: {}, 参数: {}", methodName, requestBody);
            
            // 调用WSDL服务适配器
            Object result = wsdlServiceAdapter.invokeMethod(methodName, requestBody);
            
            log.info("WSDL方法调用成功: {}", methodName);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("调用WSDL方法失败: {}, 错误: {}", methodName, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "调用WSDL服务失败", "message", e.getMessage()));
        }
    }

    /**
     * GET方式调用WSDL服务（用于简单查询）
     * 
     * @param methodName WSDL服务方法名
     * @param params 查询参数
     * @return WSDL服务响应（JSON格式）
     */
    @GetMapping("/invoke/{methodName}")
    @Operation(summary = "GET方式调用WSDL服务", 
               description = "通过GET请求调用WSDL服务方法，适用于简单查询")
    public ResponseEntity<Object> invokeWsdlMethodGet(
            @Parameter(description = "WSDL服务方法名", required = true)
            @PathVariable String methodName,
            @Parameter(description = "查询参数")
            @RequestParam(required = false) Map<String, String> params) {
        
        try {
            log.info("GET调用WSDL方法: {}, 参数: {}", methodName, params);
            
            // 将String参数转换为Object参数
            Map<String, Object> objectParams = params != null ? 
                    Map.copyOf(params) : null;
            
            Object result = wsdlServiceAdapter.invokeMethod(methodName, objectParams);
            
            log.info("WSDL方法调用成功: {}", methodName);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("GET调用WSDL方法失败: {}, 错误: {}", methodName, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "调用WSDL服务失败", "message", e.getMessage()));
        }
    }

    /**
     * 获取WSDL服务信息
     * 
     * @return WSDL服务的基本信息
     */
    @GetMapping("/info")
    @Operation(summary = "获取WSDL服务信息", 
               description = "获取当前配置的WSDL服务的基本信息")
    public ResponseEntity<Object> getWsdlServiceInfo() {
        try {
            Map<String, Object> serviceInfo = wsdlServiceAdapter.getServiceInfo();
            return ResponseEntity.ok(serviceInfo);
        } catch (Exception e) {
            log.error("获取WSDL服务信息失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "获取服务信息失败", "message", e.getMessage()));
        }
    }

    /**
     * 获取WSDL服务可用方法列表
     * 
     * @return 可用方法列表
     */
    @GetMapping("/methods")
    @Operation(summary = "获取WSDL服务方法列表", 
               description = "获取WSDL服务中所有可用的方法列表")
    public ResponseEntity<Object> getAvailableMethods() {
        try {
            Object methods = wsdlServiceAdapter.getAvailableMethods();
            return ResponseEntity.ok(methods);
        } catch (Exception e) {
            log.error("获取WSDL方法列表失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "获取方法列表失败", "message", e.getMessage()));
        }
    }

    /**
     * 健康检查接口
     * 
     * @return 服务状态
     */
    @GetMapping("/health")
    @Operation(summary = "健康检查", 
               description = "检查WSDL服务连接状态")
    public ResponseEntity<Object> healthCheck() {
        try {
            boolean isHealthy = wsdlServiceAdapter.isServiceHealthy();
            if (isHealthy) {
                return ResponseEntity.ok(Map.of("status", "健康", "timestamp", System.currentTimeMillis()));
            } else {
                return ResponseEntity.status(503)
                        .body(Map.of("status", "不健康", "timestamp", System.currentTimeMillis()));
            }
        } catch (Exception e) {
            log.error("健康检查失败: {}", e.getMessage(), e);
            return ResponseEntity.status(503)
                    .body(Map.of("status", "错误", "message", e.getMessage(), "timestamp", System.currentTimeMillis()));
        }
    }

    /**
     * 手动解析复杂WSDL
     * 
     * @param wsdlUrl WSDL URL
     * @return 解析结果
     */
    @PostMapping("/resolve-wsdl")
    @Operation(summary = "解析复杂WSDL", 
               description = "手动解析包含嵌套引用的WSDL文档")
    public ResponseEntity<Object> resolveComplexWsdl(
            @Parameter(description = "WSDL URL", required = true)
            @RequestParam String wsdlUrl) {
        
        try {
            log.info("手动解析WSDL: {}", wsdlUrl);
            
            if (wsdlUrl == null || wsdlUrl.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "WSDL URL不能为空"));
            }
            
            // 检查URL是否可访问
            if (!wsdlResolverService.isUrlAccessible(wsdlUrl)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "WSDL URL不可访问", "url", wsdlUrl));
            }
            
            // 解析WSDL
            String resolvedWsdlPath = wsdlResolverService.resolveComplexWsdl(wsdlUrl);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("originalUrl", wsdlUrl);
            result.put("resolvedPath", resolvedWsdlPath);
            result.put("message", "WSDL解析成功");
            result.put("timestamp", System.currentTimeMillis());
            
            log.info("WSDL解析完成: {}", wsdlUrl);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("WSDL解析失败: {}, 错误: {}", wsdlUrl, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "WSDL解析失败", "message", e.getMessage(), "url", wsdlUrl));
        }
    }

    /**
     * 验证WSDL URL可访问性
     * 
     * @param wsdlUrl WSDL URL
     * @return 验证结果
     */
    @GetMapping("/validate-wsdl-url")
    @Operation(summary = "验证WSDL URL", 
               description = "检查WSDL URL是否可访问")
    public ResponseEntity<Object> validateWsdlUrl(
            @Parameter(description = "WSDL URL", required = true)
            @RequestParam String wsdlUrl) {
        
        try {
            if (wsdlUrl == null || wsdlUrl.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "WSDL URL不能为空"));
            }
            
            boolean isAccessible = wsdlResolverService.isUrlAccessible(wsdlUrl);
            
            Map<String, Object> result = new HashMap<>();
            result.put("url", wsdlUrl);
            result.put("accessible", isAccessible);
            result.put("message", isAccessible ? "URL可访问" : "URL不可访问");
            result.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("验证WSDL URL失败: {}, 错误: {}", wsdlUrl, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "验证失败", "message", e.getMessage(), "url", wsdlUrl));
        }
    }

    /**
     * 清理临时文件
     * 
     * @return 清理结果
     */
    @PostMapping("/cleanup")
    @Operation(summary = "清理临时文件", 
               description = "清理WSDL解析过程中生成的临时文件")
    public ResponseEntity<Object> cleanupTempFiles() {
        try {
            wsdlResolverService.cleanupTempFiles();
            return ResponseEntity.ok(Map.of("message", "临时文件清理完成", "timestamp", System.currentTimeMillis()));
        } catch (Exception e) {
            log.error("清理临时文件失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "清理失败", "message", e.getMessage()));
        }
    }

    /**
     * 重新初始化WSDL客户端
     * 
     * @return 初始化结果
     */
    @PostMapping("/reinitialize")
    @Operation(summary = "重新初始化客户端", 
               description = "重新初始化WSDL客户端")
    public ResponseEntity<Object> reinitializeClient() {
        try {
            wsdlServiceAdapter.reinitializeClient();
            return ResponseEntity.ok(Map.of("message", "客户端重新初始化完成", "timestamp", System.currentTimeMillis()));
        } catch (Exception e) {
            log.error("重新初始化客户端失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "重新初始化失败", "message", e.getMessage()));
        }
    }
}
