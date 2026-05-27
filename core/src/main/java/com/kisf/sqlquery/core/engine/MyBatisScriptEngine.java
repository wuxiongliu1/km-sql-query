package com.kisf.sqlquery.core.engine;

import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MyBatisScriptEngine {

    private static final String[] STATEMENT_TAGS = {"select", "insert", "update", "delete"};

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
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            Document doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(sqlTemplate.getBytes("UTF-8")));
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
            javax.xml.transform.TransformerFactory tf = javax.xml.transform.TransformerFactory.newInstance();
            javax.xml.transform.Transformer t = tf.newTransformer();
            t.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
            java.io.StringWriter sw = new java.io.StringWriter();
            t.transform(new javax.xml.transform.dom.DOMSource(node),
                    new javax.xml.transform.stream.StreamResult(sw));
            return sw.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public SqlCommandType detectCommandType(String sqlTemplate) {
        String trimmed = sqlTemplate.trim().toLowerCase();
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
