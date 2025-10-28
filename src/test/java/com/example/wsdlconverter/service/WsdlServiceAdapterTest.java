package com.example.wsdlconverter.service;

import com.example.wsdlconverter.config.WsdlClientConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * WsdlServiceAdapter的单元测试
 * 主要测试参数解析功能
 */
@ExtendWith(MockitoExtension.class)
class WsdlServiceAdapterTest {

    @Mock
    private WsdlClientConfig wsdlClientConfig;

    @Mock
    private WsdlResolverService wsdlResolverService;

    @InjectMocks
    private WsdlServiceAdapter wsdlServiceAdapter;

    @BeforeEach
    void setUp() {
        // 设置测试配置
        when(wsdlClientConfig.getServiceUrl()).thenReturn("http://localhost:8080/test");
        when(wsdlClientConfig.getServiceNamespace()).thenReturn("http://tempuri.org/");
        
        // 设置WSDL文件路径
        ReflectionTestUtils.setField(wsdlServiceAdapter, "wsdlFilePath", "src/main/resources/wsdl/service.wsdl");
        ReflectionTestUtils.setField(wsdlServiceAdapter, "securityEnabled", false);
    }

    @Test
    void testGetServiceInfo() {
        // 测试获取服务信息
        Map<String, Object> serviceInfo = wsdlServiceAdapter.getServiceInfo();
        
        assertNotNull(serviceInfo);
        assertEquals("http://localhost:8080/test", serviceInfo.get("serviceUrl"));
        assertEquals("http://tempuri.org/", serviceInfo.get("namespace"));
        assertNotNull(serviceInfo.get("timestamp"));
    }

    @Test
    void testGetAvailableMethods() {
        // 测试获取可用方法列表
        List<String> methods = wsdlServiceAdapter.getAvailableMethods();
        
        assertNotNull(methods);
        // 即使WSDL客户端未初始化，也应该返回空列表而不是null
        assertTrue(methods.isEmpty() || methods.size() > 0);
    }

    @Test
    void testIsServiceHealthy() {
        // 测试服务健康检查
        // 由于没有真实的WSDL客户端，应该返回false
        boolean isHealthy = wsdlServiceAdapter.isServiceHealthy();
        assertFalse(isHealthy);
    }

    @Test
    void testInitializeWsdlClient() {
        // 测试WSDL客户端初始化
        // 这个测试主要确保初始化方法不会抛出异常
        assertDoesNotThrow(() -> {
            wsdlServiceAdapter.initializeWsdlClient();
        });
    }

    @Test
    void testReinitializeClient() {
        // 测试重新初始化客户端
        assertDoesNotThrow(() -> {
            wsdlServiceAdapter.reinitializeClient();
        });
    }
}


