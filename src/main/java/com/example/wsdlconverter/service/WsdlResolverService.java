package com.example.wsdlconverter.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

/**
 * WSDL解析服务
 * 
 * 处理复杂的WSDL结构，包括嵌套的imports和includes
 * 支持从动态URL获取WSDL并解析所有依赖
 */
@Service
@Slf4j
public class WsdlResolverService {

    private static final String WSDL_NAMESPACE = "http://schemas.xmlsoap.org/wsdl/";
    private static final String XSD_NAMESPACE = "http://www.w3.org/2001/XMLSchema";
    
    private final Set<String> processedUrls = new HashSet<>();
    private final Map<String, Document> documentCache = new HashMap<>();

    /**
     * 从URL获取并解析完整的WSDL文档（包括所有嵌套引用）
     * 
     * @param wsdlUrl 主WSDL文档的URL
     * @return 合并后的完整WSDL文档路径
     */
    public String resolveComplexWsdl(String wsdlUrl) throws Exception {
        log.info("开始解析复杂WSDL: {}", wsdlUrl);
        
        // 清理之前的状态
        processedUrls.clear();
        documentCache.clear();
        
        // 获取主WSDL文档
        Document mainDocument = fetchWsdlDocument(wsdlUrl);
        
        // 递归解析所有导入和包含
        resolveImportsAndIncludes(mainDocument, wsdlUrl);
        
        // 创建临时文件保存合并后的WSDL
        String tempWsdlPath = createTempWsdlFile(mainDocument);
        
        log.info("WSDL解析完成，临时文件: {}", tempWsdlPath);
        return tempWsdlPath;
    }

    /**
     * 从URL获取WSDL文档
     */
    private Document fetchWsdlDocument(String urlString) throws Exception {
        if (documentCache.containsKey(urlString)) {
            return documentCache.get(urlString);
        }

        log.info("获取WSDL文档: {}", urlString);
        
        URL url = new URL(urlString);
        URLConnection connection = url.openConnection();
        
        // 设置请求头
        connection.setRequestProperty("User-Agent", "Java WSDL Client");
        connection.setRequestProperty("Accept", "text/xml, application/xml");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(60000);
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document;
        
        try (InputStream inputStream = connection.getInputStream()) {
            document = builder.parse(inputStream);
        }
        
        documentCache.put(urlString, document);
        processedUrls.add(urlString);
        
        log.info("成功获取WSDL文档: {}", urlString);
        return document;
    }

    /**
     * 递归解析导入和包含的文档
     */
    private void resolveImportsAndIncludes(Document document, String baseUrl) throws Exception {
        // 处理 wsdl:import 元素
        processWsdlImports(document, baseUrl);
        
        // 处理 xsd:import 和 xsd:include 元素
        processSchemaImports(document, baseUrl);
    }

    /**
     * 处理WSDL导入
     */
    private void processWsdlImports(Document document, String baseUrl) throws Exception {
        NodeList importNodes = document.getElementsByTagNameNS(WSDL_NAMESPACE, "import");
        
        for (int i = 0; i < importNodes.getLength(); i++) {
            Element importElement = (Element) importNodes.item(i);
            String location = importElement.getAttribute("location");
            
            if (location != null && !location.trim().isEmpty()) {
                String absoluteUrl = resolveRelativeUrl(baseUrl, location);
                
                if (!processedUrls.contains(absoluteUrl)) {
                    log.info("处理WSDL导入: {}", absoluteUrl);
                    
                    try {
                        Document importedDoc = fetchWsdlDocument(absoluteUrl);
                        resolveImportsAndIncludes(importedDoc, absoluteUrl);
                        
                        // 将导入的内容合并到主文档中
                        mergeWsdlDocument(document, importedDoc);
                        
                    } catch (Exception e) {
                        log.warn("无法导入WSDL: {} - {}", absoluteUrl, e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * 处理Schema导入和包含
     */
    private void processSchemaImports(Document document, String baseUrl) throws Exception {
        // 处理 xsd:import
        NodeList importNodes = document.getElementsByTagNameNS(XSD_NAMESPACE, "import");
        processSchemaReferences(importNodes, baseUrl, "import");
        
        // 处理 xsd:include
        NodeList includeNodes = document.getElementsByTagNameNS(XSD_NAMESPACE, "include");
        processSchemaReferences(includeNodes, baseUrl, "include");
    }

    /**
     * 处理Schema引用
     */
    private void processSchemaReferences(NodeList nodes, String baseUrl, String type) throws Exception {
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String schemaLocation = element.getAttribute("schemaLocation");
            
            if (schemaLocation != null && !schemaLocation.trim().isEmpty()) {
                String absoluteUrl = resolveRelativeUrl(baseUrl, schemaLocation);
                
                if (!processedUrls.contains(absoluteUrl)) {
                    log.info("处理Schema {}: {}", type, absoluteUrl);
                    
                    try {
                        Document schemaDoc = fetchWsdlDocument(absoluteUrl);
                        resolveImportsAndIncludes(schemaDoc, absoluteUrl);
                        
                        // 将Schema内容内联到主文档中
                        inlineSchemaContent(element, schemaDoc);
                        
                    } catch (Exception e) {
                        log.warn("无法{}Schema: {} - {}", type, absoluteUrl, e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * 解析相对URL为绝对URL
     */
    private String resolveRelativeUrl(String baseUrl, String relativeUrl) throws Exception {
        if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
            return relativeUrl;
        }
        
        URI baseUri = new URI(baseUrl);
        URI resolvedUri = baseUri.resolve(relativeUrl);
        return resolvedUri.toString();
    }

    /**
     * 合并WSDL文档
     */
    private void mergeWsdlDocument(Document mainDoc, Document importedDoc) {
        Element mainRoot = mainDoc.getDocumentElement();
        Element importedRoot = importedDoc.getDocumentElement();
        
        // 合并types
        mergeElements(mainDoc, mainRoot, importedRoot, "types");
        
        // 合并message
        mergeElements(mainDoc, mainRoot, importedRoot, "message");
        
        // 合并portType
        mergeElements(mainDoc, mainRoot, importedRoot, "portType");
        
        // 合并binding
        mergeElements(mainDoc, mainRoot, importedRoot, "binding");
        
        // 合并service
        mergeElements(mainDoc, mainRoot, importedRoot, "service");
    }

    /**
     * 合并指定类型的元素
     */
    private void mergeElements(Document mainDoc, Element mainRoot, Element importedRoot, String elementName) {
        NodeList importedElements = importedRoot.getElementsByTagNameNS(WSDL_NAMESPACE, elementName);
        
        for (int i = 0; i < importedElements.getLength(); i++) {
            Node importedNode = importedElements.item(i);
            Node adoptedNode = mainDoc.adoptNode(importedNode.cloneNode(true));
            mainRoot.appendChild(adoptedNode);
        }
    }

    /**
     * 内联Schema内容
     */
    private void inlineSchemaContent(Element importElement, Document schemaDoc) {
        Element schemaRoot = schemaDoc.getDocumentElement();
        Document mainDoc = importElement.getOwnerDocument();
        
        // 找到包含import/include的schema元素
        Element parentSchema = findParentSchema(importElement);
        if (parentSchema != null) {
            // 将Schema内容添加到父Schema中
            NodeList schemaChildren = schemaRoot.getChildNodes();
            for (int i = 0; i < schemaChildren.getLength(); i++) {
                Node childNode = schemaChildren.item(i);
                if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                    Node adoptedNode = mainDoc.adoptNode(childNode.cloneNode(true));
                    parentSchema.appendChild(adoptedNode);
                }
            }
            
            // 移除原始的import/include元素
            importElement.getParentNode().removeChild(importElement);
        }
    }

    /**
     * 查找父级Schema元素
     */
    private Element findParentSchema(Element element) {
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            if ("schema".equals(parentElement.getLocalName()) && 
                XSD_NAMESPACE.equals(parentElement.getNamespaceURI())) {
                return parentElement;
            }
            parent = parent.getParentNode();
        }
        return null;
    }

    /**
     * 创建临时WSDL文件
     */
    private String createTempWsdlFile(Document document) throws Exception {
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "wsdl-resolver");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        
        File tempFile = new File(tempDir, "resolved-wsdl-" + System.currentTimeMillis() + ".wsdl");
        
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        
        DOMSource source = new DOMSource(document);
        StreamResult result = new StreamResult(tempFile);
        
        transformer.transform(source, result);
        
        return tempFile.getAbsolutePath();
    }

    /**
     * 清理临时文件
     */
    public void cleanupTempFiles() {
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "wsdl-resolver");
        if (tempDir.exists()) {
            File[] files = tempDir.listFiles((dir, name) -> name.startsWith("resolved-wsdl-"));
            if (files != null) {
                for (File file : files) {
                    if (file.delete()) {
                        log.debug("删除临时WSDL文件: {}", file.getName());
                    }
                }
            }
        }
    }

    /**
     * 验证URL是否可访问
     */
    public boolean isUrlAccessible(String urlString) {
        try {
            URL url = new URL(urlString);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.connect();
            return true;
        } catch (Exception e) {
            log.debug("URL不可访问: {} - {}", urlString, e.getMessage());
            return false;
        }
    }
}
