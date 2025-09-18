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
}
