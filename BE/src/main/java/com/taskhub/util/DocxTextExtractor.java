package com.taskhub.util;

import com.taskhub.exception.TaskHubException;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts visible text from the XML parts of a DOCX package without opening
 * embedded fonts, images or other highly-compressed binary parts.
 *
 * <p>This avoids Apache POI's global zip-bomb ratio check rejecting otherwise
 * valid Word files that contain embedded fonts, while keeping strict limits on
 * the XML entries that are actually parsed.</p>
 */
public final class DocxTextExtractor {

    private static final int MAX_ENTRIES = 1000;
    private static final int MAX_XML_ENTRY_BYTES = 5 * 1024 * 1024;
    private static final int MAX_RELEVANT_XML_BYTES = 12 * 1024 * 1024;

    private DocxTextExtractor() {
    }

    public static String extract(byte[] bytes, int maxChars) {
        if (bytes == null || bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') {
            throw TaskHubException.badRequest("File content is not a valid DOCX document");
        }

        Map<String, byte[]> xmlParts = readRelevantParts(bytes);
        if (!xmlParts.containsKey("[Content_Types].xml") || !xmlParts.containsKey("word/document.xml")) {
            throw TaskHubException.badRequest("Invalid DOCX package");
        }

        StringBuilder text = new StringBuilder();
        appendPart(xmlParts.get("word/document.xml"), text, maxChars);
        xmlParts.entrySet().stream()
                .filter(entry -> entry.getKey().matches("word/(header|footer)\\d+\\.xml"))
                .forEach(entry -> appendPart(entry.getValue(), text, maxChars));

        String normalized = text.toString()
                .replace('\u00a0', ' ')
                .replaceAll("[ \\t]+\\n", "\\n")
                .replaceAll("\\n{3,}", "\\n\\n")
                .trim();
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars);
    }

    private static Map<String, byte[]> readRelevantParts(byte[] bytes) {
        Map<String, byte[]> parts = new LinkedHashMap<>();
        int entries = 0;
        int totalRelevantBytes = 0;

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    throw TaskHubException.badRequest("DOCX contains too many entries");
                }
                String name = entry.getName().replace('\\', '/');
                if (entry.isDirectory() || !isRelevantXml(name)) {
                    zip.closeEntry();
                    continue;
                }

                byte[] xml = readLimited(zip, MAX_XML_ENTRY_BYTES);
                totalRelevantBytes += xml.length;
                if (totalRelevantBytes > MAX_RELEVANT_XML_BYTES) {
                    throw TaskHubException.badRequest("DOCX XML content exceeds the safe limit");
                }
                parts.put(name, xml);
                zip.closeEntry();
            }
            return parts;
        } catch (TaskHubException e) {
            throw e;
        } catch (Exception e) {
            throw TaskHubException.badRequest("Invalid DOCX archive");
        }
    }

    private static boolean isRelevantXml(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return "[content_types].xml".equals(lower)
                || "word/document.xml".equals(lower)
                || lower.matches("word/(header|footer)\\d+\\.xml");
    }

    private static byte[] readLimited(ZipInputStream zip, int maxBytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        int total = 0;
        while ((read = zip.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw TaskHubException.badRequest("DOCX XML entry exceeds the safe limit");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static void appendPart(byte[] xml, StringBuilder out, int maxChars) {
        if (xml == null || out.length() >= maxChars) return;
        XMLInputFactory factory = XMLInputFactory.newFactory();
        setSafely(factory, XMLInputFactory.SUPPORT_DTD, false);
        setSafely(factory, "javax.xml.stream.isSupportingExternalEntities", false);

        try {
            XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(xml), "UTF-8");
            boolean inText = false;
            while (reader.hasNext() && out.length() < maxChars) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String local = reader.getLocalName();
                    if ("t".equals(local)) inText = true;
                    else if ("tab".equals(local)) out.append('\t');
                    else if ("br".equals(local) || "cr".equals(local)) out.append('\n');
                } else if ((event == XMLStreamConstants.CHARACTERS
                        || event == XMLStreamConstants.CDATA) && inText) {
                    out.append(reader.getText());
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String local = reader.getLocalName();
                    if ("t".equals(local)) inText = false;
                    else if ("p".equals(local) || "tr".equals(local)) out.append('\n');
                }
            }
            reader.close();
        } catch (TaskHubException e) {
            throw e;
        } catch (Exception e) {
            throw TaskHubException.badRequest("DOCX text extraction failed");
        }
    }

    private static void setSafely(XMLInputFactory factory, String property, Object value) {
        try {
            factory.setProperty(property, value);
        } catch (IllegalArgumentException ignored) {
            // The JDK StAX provider may not expose every optional property.
        }
    }
}
