package com.kisf.sqlquery.core.engine;

import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class MyBatisScriptEngine {

    private final Map<String, SqlSource> sqlSourceCache = new ConcurrentHashMap<>();

    public SqlSource parse(String sqlPath, String sqlTemplate, Configuration configuration) {
        return sqlSourceCache.computeIfAbsent(sqlPath, k -> {
            String innerSql = extractInnerSql(sqlTemplate);
            String script = "<script>" + innerSql + "</script>";
            XMLLanguageDriver driver = new XMLLanguageDriver();
            return driver.createSqlSource(configuration, script, Map.class);
        });
    }

    /**
     * Extract the inner content from a statement tag like <select>...</select>,
     * <insert>...</insert>, <update>...</update>, or <delete>...</delete>.
     * The outer tag may contain attributes (e.g., id="q").
     * The inner content is used as the body inside a <script> wrapper for dynamic SQL parsing.
     */
    static String extractInnerSql(String sqlTemplate) {
        try {
            DocumentBuilderFactory factory = secureDocumentBuilderFactory();
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler() {
                @Override
                public void error(SAXParseException e) throws SAXException {
                    throw e;
                }

                @Override
                public void fatalError(SAXParseException e) throws SAXException {
                    throw e;
                }
            });
            Document doc = builder.parse(
                    new ByteArrayInputStream(sqlTemplate.getBytes(StandardCharsets.UTF_8)));
            Node root = firstElement(doc.getChildNodes());
            if (root == null) {
                throw new SqlExecutor.ScriptParseException("SQL模板必须包含根元素节点");
            }
            StringBuilder sb = new StringBuilder();
            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.TEXT_NODE) {
                    sb.append(child.getTextContent());
                } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                    sb.append(serializeElement(child));
                }
            }
            return sb.toString();
        } catch (SqlExecutor.ScriptParseException e) {
            throw e;
        } catch (Exception e) {
            throw new SqlExecutor.ScriptParseException("Failed to parse SQL template: " + e.getMessage());
        }
    }

    private static DocumentBuilderFactory secureDocumentBuilderFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    private static Node firstElement(NodeList nodes) {
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i).getNodeType() == Node.ELEMENT_NODE) {
                return nodes.item(i);
            }
        }
        return null;
    }

    private static String serializeElement(Node node) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(node), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            throw new SqlExecutor.ScriptParseException(
                    "Failed to serialize dynamic SQL element: " + e.getMessage(), e);
        }
    }

    public SqlCommandType detectCommandType(String sqlTemplate) {
        String trimmed = sqlTemplate.trim().toLowerCase(Locale.ENGLISH);
        if (trimmed.startsWith("<select")) return SqlCommandType.SELECT;
        if (trimmed.startsWith("<insert")) return SqlCommandType.INSERT;
        if (trimmed.startsWith("<update")) return SqlCommandType.UPDATE;
        if (trimmed.startsWith("<delete")) return SqlCommandType.DELETE;
        throw new SqlExecutor.ScriptParseException("SQL模板必须以 <select>, <insert>, <update> 或 <delete> 开头");
    }

    public void invalidate(String sqlPath) {
        sqlSourceCache.remove(sqlPath);
    }

    public void invalidateAll() {
        sqlSourceCache.clear();
    }
}
