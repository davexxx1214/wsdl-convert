package com.example.wsdlconverter.config;

import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.headers.Header;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.List;

/**
 * 自定义WS-Security配置
 * 
 * 专门用于与C#自定义认证器PfsUserNameTokenAuthenticator兼容
 * 生成符合C#期望格式的WS-Security头
 */
@Component
public class CustomWsSecurityConfig {

    /**
     * 自定义WS-Security出站拦截器
     * 生成符合C#自定义认证器要求的安全头格式
     */
    public static class CustomWsSecurityOutInterceptor extends AbstractPhaseInterceptor<SoapMessage> {
        
        private final String username;
        private final String password;
        
        // WS-Security相关命名空间
        private static final String WSSE_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
        private static final String WSU_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd";
        
        public CustomWsSecurityOutInterceptor(String username, String password) {
            super(Phase.WRITE_ENDING);
            this.username = username;
            this.password = password;
        }

        @Override
        public void handleMessage(SoapMessage message) throws Fault {
            try {
                // 创建自定义的WS-Security头
                Element securityHeader = createCustomSecurityHeader();
                
                // 将安全头添加到SOAP消息中
                Header header = new Header(
                    new QName(WSSE_NS, "Security", "wsse"), 
                    securityHeader
                );
                
                List<Header> headers = message.getHeaders();
                headers.add(header);
                
            } catch (Exception e) {
                throw new Fault(e);
            }
        }
        
        /**
         * 创建符合C#自定义认证器要求的安全头
         */
        private Element createCustomSecurityHeader() throws Exception {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();
            
            // 创建Security元素
            Element security = doc.createElementNS(WSSE_NS, "wsse:Security");
            security.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:wsse", WSSE_NS);
            security.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:wsu", WSU_NS);
            security.setAttribute("soap:mustUnderstand", "1");
            
            // 创建UsernameToken元素
            Element usernameToken = doc.createElementNS(WSSE_NS, "wsse:UsernameToken");
            usernameToken.setAttributeNS(WSU_NS, "wsu:Id", "UsernameToken-" + System.currentTimeMillis());
            
            // 创建Username元素
            Element usernameElement = doc.createElementNS(WSSE_NS, "wsse:Username");
            usernameElement.setTextContent(username);
            usernameToken.appendChild(usernameElement);
            
            // 创建Password元素 - 关键：使用明文密码类型
            Element passwordElement = doc.createElementNS(WSSE_NS, "wsse:Password");
            passwordElement.setAttribute("Type", "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText");
            passwordElement.setTextContent(password);
            usernameToken.appendChild(passwordElement);
            
            // 添加Nonce（可选，但某些C#服务可能需要）
            Element nonceElement = doc.createElementNS(WSSE_NS, "wsse:Nonce");
            nonceElement.setAttribute("EncodingType", "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary");
            String nonce = java.util.Base64.getEncoder().encodeToString(
                String.valueOf(System.currentTimeMillis()).getBytes()
            );
            nonceElement.setTextContent(nonce);
            usernameToken.appendChild(nonceElement);
            
            // 添加Created时间戳（可选，但某些C#服务可能需要）
            Element createdElement = doc.createElementNS(WSU_NS, "wsu:Created");
            createdElement.setTextContent(getCurrentTimestamp());
            usernameToken.appendChild(createdElement);
            
            security.appendChild(usernameToken);
            
            return security;
        }
        
        /**
         * 获取当前时间戳（ISO 8601格式）
         */
        private String getCurrentTimestamp() {
            return java.time.Instant.now().toString();
        }
    }
    
    /**
     * 创建自定义WS-Security拦截器实例
     */
    public CustomWsSecurityOutInterceptor createCustomInterceptor(String username, String password) {
        return new CustomWsSecurityOutInterceptor(username, password);
    }
}
