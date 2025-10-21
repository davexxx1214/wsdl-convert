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
 * 专门为C# PfsUserNameTokenAuthenticator设计的安全配置
 * 
 * 根据C#自定义认证器的要求，生成特定格式的WS-Security头
 */
@Component
public class PfsCompatibleSecurityConfig {

    /**
     * PFS兼容的WS-Security拦截器
     * 
     * 这个拦截器专门为了与C#的PfsUserNameTokenAuthenticator兼容而设计
     * 它会生成符合PFS期望格式的安全头
     */
    public static class PfsWsSecurityInterceptor extends AbstractPhaseInterceptor<SoapMessage> {
        
        private final String username;
        private final String password;
        private final String clientId;
        private final boolean windowsAuthentication;
        private final boolean changePassword;
        
        // PFS特定的命名空间和属性
        private static final String WSSE_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
        private static final String WSU_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd";
        private static final String PFS_NS = "http://pfs.com";
        private static final String XMLNS_URI = "http://www.w3.org/2000/xmlns/";
        
        public PfsWsSecurityInterceptor(String username, String password, String clientId, 
                                       boolean windowsAuthentication, boolean changePassword) {
            super(Phase.WRITE_ENDING);
            this.username = username;
            this.password = password;
            this.clientId = clientId;
            this.windowsAuthentication = windowsAuthentication;
            this.changePassword = changePassword;
        }

        @Override
        public void handleMessage(SoapMessage message) throws Fault {
            try {
                // 创建PFS兼容的安全头，并传入message以获取SOAP版本信息
                Element securityHeader = createPfsCompatibleSecurityHeader(message);
                
                // 添加到SOAP消息头
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
         * 创建PFS兼容的安全头
         * 
         * 根据C#自定义认证器PfsUserNameTokenAuthenticator的要求生成特定格式
         */
        private Element createPfsCompatibleSecurityHeader(SoapMessage message) throws Exception {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();
            
            // 创建Security根元素，包含所有必需的命名空间
            Element security = doc.createElementNS(WSSE_NS, "wsse:Security");
            security.setAttributeNS(XMLNS_URI, "xmlns:wsse", WSSE_NS);
            security.setAttributeNS(XMLNS_URI, "xmlns:wsu", WSU_NS);
            security.setAttributeNS(XMLNS_URI, "xmlns:pfs", PFS_NS);
            
            // 动态、正确地设置mustUnderstand属性
            String soapNs = message.getVersion().getNamespace();
            String prefix = message.getEnvelope().getPrefix();
            if (prefix == null || prefix.isEmpty()) {
                // 如果在信封上找不到前缀，则使用启发式方法
                prefix = soapNs.equals("http://www.w3.org/2003/05/soap-envelope") ? "s" : "soap";
            }
            security.setAttributeNS(soapNs, prefix + ":mustUnderstand", "1");
            
            // 创建PFS特定的安全令牌结构
            createPfsSecurityToken(doc, security);
            
            return security;
        }
        
        /**
         * 创建PFS特定的安全令牌结构
         * 
         * 根据图片中显示的正确格式生成PFS安全令牌
         */
        private void createPfsSecurityToken(Document doc, Element security) {
            // 创建PFS客户端ID
            Element pfsClientIdElem = doc.createElementNS(PFS_NS, "pfs:PfsClientID");
            pfsClientIdElem.setTextContent(clientId != null ? clientId : "DEFAULT");
            security.appendChild(pfsClientIdElem);
            
            // 创建PFS用户名
            Element pfsUserName = doc.createElementNS(PFS_NS, "pfs:PfsUserName");
            pfsUserName.setTextContent(username);
            security.appendChild(pfsUserName);
            
            // 创建PFS用户密码
            Element pfsUserPassword = doc.createElementNS(PFS_NS, "pfs:PfsUserPassword");
            pfsUserPassword.setTextContent(password);
            security.appendChild(pfsUserPassword);
            
            // 创建PFS用户新密码（根据changePassword标志决定）
            Element pfsUserNewPassword = doc.createElementNS(PFS_NS, "pfs:PfsUserNewPassword");
            pfsUserNewPassword.setTextContent(changePassword ? password : "");
            security.appendChild(pfsUserNewPassword);
            
            // 创建PFS密钥数据
            Element pfsKeyData = doc.createElementNS(PFS_NS, "pfs:PfsKeyData");
            pfsKeyData.setTextContent("");
            security.appendChild(pfsKeyData);
            
            // 创建PFS Windows认证标志
            Element pfsWindowsAuthElem = doc.createElementNS(PFS_NS, "pfs:PfsWindowsAuthentication");
            pfsWindowsAuthElem.setTextContent(String.valueOf(windowsAuthentication).toLowerCase());
            security.appendChild(pfsWindowsAuthElem);
            
            // 创建PFS更改密码标志
            Element pfsChangePasswordElem = doc.createElementNS(PFS_NS, "pfs:PfsChangePassword");
            pfsChangePasswordElem.setTextContent(String.valueOf(changePassword).toLowerCase());
            security.appendChild(pfsChangePasswordElem);
            
            // 创建PFS令牌
            Element pfsToken = doc.createElementNS(PFS_NS, "pfs:PfsToken");
            pfsToken.setTextContent("");
            security.appendChild(pfsToken);
        }
        
    }
    
    /**
     * 创建PFS兼容的安全拦截器
     */
    public PfsWsSecurityInterceptor createPfsInterceptor(String username, String password, 
                                                        String clientId, boolean windowsAuthentication, 
                                                        boolean changePassword) {
        return new PfsWsSecurityInterceptor(username, password, clientId, windowsAuthentication, changePassword);
    }
}
