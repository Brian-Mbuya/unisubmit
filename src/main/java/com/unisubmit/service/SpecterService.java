package com.unisubmit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

@Service
public class SpecterService {

    private static final Logger log = LoggerFactory.getLogger(SpecterService.class);

    private final RestTemplate restTemplate;
    private final boolean enabled;
    private final String specterUrl;

    public SpecterService(RestTemplate restTemplate,
                          @Value("${unisubmit.specter.enabled:false}") boolean enabled,
                          @Value("${unisubmit.specter.url:http://localhost:5001}") String specterUrl) {
        this.restTemplate = restTemplate;
        this.enabled = enabled;
        this.specterUrl = specterUrl;
    }

    public Optional<float[]> embed(String text) {
        if (!enabled) {
            log.debug("SPECTER embedding service is disabled.");
            return Optional.empty();
        }

        try {
            log.info("Requesting embedding from SPECTER service at {}...", specterUrl);

            // Configure request factory locally for a 10 second timeout on this call
            RestTemplate specTemplate = restTemplate;
            if (restTemplate.getRequestFactory() instanceof SimpleClientHttpRequestFactory) {
                SimpleClientHttpRequestFactory specFactory = new SimpleClientHttpRequestFactory();
                specFactory.setConnectTimeout(10000); // 10 seconds
                specFactory.setReadTimeout(10000);    // 10 seconds
                specTemplate = new RestTemplate(specFactory);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> requestBody = Map.of("text", text);
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = specTemplate.postForEntity(
                    specterUrl + "/embed",
                    requestEntity,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<?, ?> body = response.getBody();
                Object embObj = body.get("embedding");
                if (embObj instanceof java.util.List) {
                    java.util.List<?> list = (java.util.List<?>) embObj;
                    float[] embedding = new float[list.size()];
                    for (int i = 0; i < list.size(); i++) {
                        embedding[i] = ((Number) list.get(i)).floatValue();
                    }
                    return Optional.of(embedding);
                }
            }
            log.warn("SPECTER response did not contain a valid embedding array.");
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Error calling SPECTER embedding service: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
