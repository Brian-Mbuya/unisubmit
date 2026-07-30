package com.unisubmit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class GrobidService {

    private static final Logger log = LoggerFactory.getLogger(GrobidService.class);

    private final RestTemplate restTemplate;
    private final boolean grobidEnabled;
    private final String grobidUrl;

    public GrobidService(RestTemplate restTemplate,
                         @Value("${unisubmit.grobid.enabled:false}") boolean grobidEnabled,
                         @Value("${unisubmit.grobid.url:http://localhost:8070}") String grobidUrl) {
        this.restTemplate = restTemplate;
        this.grobidEnabled = grobidEnabled;
        this.grobidUrl = grobidUrl;
    }

    public record GrobidReference(String authors, String title, String year, String doi) {}

    public record GrobidResult(String introduction, String methodology, String conclusion, List<GrobidReference> references) {}

    public Optional<GrobidResult> extractStructured(File pdfFile) {
        if (!grobidEnabled) {
            log.debug("GROBID is disabled. Skipping structured extraction.");
            return Optional.empty();
        }

        try {
            log.info("Sending document {} to GROBID service at {}...", pdfFile.getName(), grobidUrl);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("input", new FileSystemResource(pdfFile));

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    grobidUrl + "/api/processFulltextDocument",
                    requestEntity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseTeiXml(response.getBody());
            } else {
                log.warn("GROBID returned non-success code: {}", response.getStatusCode());
                return Optional.empty();
            }
        } catch (Exception ex) {
            log.warn("Error calling GROBID service or parsing response: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<GrobidResult> parseTeiXml(String xmlContent) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Prevent XXE attacks
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));

            String introduction = null;
            String methodology = null;
            String conclusion = null;
            List<GrobidReference> references = new ArrayList<>();

            // Find first div under body if no explicit type is found
            NodeList bodyList = doc.getElementsByTagName("body");
            Element bodyEl = (bodyList.getLength() > 0) ? (Element) bodyList.item(0) : null;
            Element firstBodyDiv = null;
            if (bodyEl != null) {
                NodeList bodyChildren = bodyEl.getChildNodes();
                for (int i = 0; i < bodyChildren.getLength(); i++) {
                    Node n = bodyChildren.item(i);
                    if (n instanceof Element && "div".equals(n.getNodeName())) {
                        firstBodyDiv = (Element) n;
                        break;
                    }
                }
            }

            NodeList divs = doc.getElementsByTagName("div");
            for (int i = 0; i < divs.getLength(); i++) {
                Element div = (Element) divs.item(i);
                String type = div.getAttribute("type");
                
                String headText = "";
                NodeList heads = div.getElementsByTagName("head");
                if (heads.getLength() > 0) {
                    headText = heads.item(0).getTextContent().toLowerCase();
                }

                if ("introduction".equalsIgnoreCase(type)) {
                    introduction = div.getTextContent().trim();
                } else if ("methods".equalsIgnoreCase(type) || "methodology".equalsIgnoreCase(type) || headText.contains("method")) {
                    methodology = div.getTextContent().trim();
                } else if ("conclusion".equalsIgnoreCase(type) || headText.contains("conclusion")) {
                    conclusion = div.getTextContent().trim();
                }
            }

            if (introduction == null && firstBodyDiv != null) {
                introduction = firstBodyDiv.getTextContent().trim();
            }

            // Extract references
            NodeList bibls = doc.getElementsByTagName("biblStruct");
            for (int i = 0; i < bibls.getLength(); i++) {
                Element bibl = (Element) bibls.item(i);

                // Authors
                List<String> authorNames = new ArrayList<>();
                NodeList authors = bibl.getElementsByTagName("author");
                for (int j = 0; j < authors.getLength(); j++) {
                    Element author = (Element) authors.item(j);
                    NodeList forenames = author.getElementsByTagName("forename");
                    NodeList surnames = author.getElementsByTagName("surname");
                    StringBuilder nameBuilder = new StringBuilder();
                    for (int k = 0; k < forenames.getLength(); k++) {
                        nameBuilder.append(forenames.item(k).getTextContent().trim()).append(" ");
                    }
                    for (int k = 0; k < surnames.getLength(); k++) {
                        nameBuilder.append(surnames.item(k).getTextContent().trim()).append(" ");
                    }
                    String name = nameBuilder.toString().trim();
                    if (!name.isEmpty()) {
                        authorNames.add(name);
                    }
                }
                String authorsStr = String.join(", ", authorNames);

                // Title
                String title = "";
                NodeList titles = bibl.getElementsByTagName("title");
                for (int j = 0; j < titles.getLength(); j++) {
                    Element titleEl = (Element) titles.item(j);
                    if (j == 0 || "a".equals(titleEl.getAttribute("level"))) {
                        title = titleEl.getTextContent().trim();
                    }
                }

                // Year
                String year = "";
                NodeList dates = bibl.getElementsByTagName("date");
                if (dates.getLength() > 0) {
                    Element dateEl = (Element) dates.item(0);
                    year = dateEl.getAttribute("when");
                    if (year.isEmpty()) {
                        year = dateEl.getTextContent().trim();
                    }
                    if (year.length() > 4) {
                        year = year.substring(0, 4);
                    }
                }

                // DOI
                String doi = "";
                NodeList idnos = bibl.getElementsByTagName("idno");
                for (int j = 0; j < idnos.getLength(); j++) {
                    Element idno = (Element) idnos.item(j);
                    if ("doi".equalsIgnoreCase(idno.getAttribute("type"))) {
                        doi = idno.getTextContent().trim();
                        break;
                    }
                }

                if (!title.isEmpty()) {
                    references.add(new GrobidReference(authorsStr, title, year, doi));
                }
            }

            return Optional.of(new GrobidResult(introduction, methodology, conclusion, references));
        } catch (Exception ex) {
            log.warn("Failed to parse GROBID TEI-XML response: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
