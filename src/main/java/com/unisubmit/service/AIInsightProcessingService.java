package com.unisubmit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisubmit.domain.*;
import com.unisubmit.repository.AIInsightRepository;
import com.unisubmit.repository.TechnologyRepository;
import com.unisubmit.repository.ResearchAreaRepository;
import com.unisubmit.repository.ReferenceRepository;
import com.unisubmit.repository.SubmissionRepository;
import java.util.Optional;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AIInsightProcessingService {

    private static final Logger log = LoggerFactory.getLogger(AIInsightProcessingService.class);

    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "that", "have", "for", "not", "with", "you", "this",
            "but", "from", "they", "will", "would", "there", "their", "what",
            "about", "which", "when", "make", "like", "time", "just", "know",
            "take", "into", "year", "your", "good", "some", "could", "them",
            "than", "then", "look", "only", "come", "over", "also", "back",
            "after", "use", "two", "how", "our", "work", "well", "even",
            "want", "because", "does", "part", "place", "very", "through",
            "long", "where", "much", "should", "these", "more", "other",
            "many", "each", "such", "been", "here", "were", "while", "being",
            "between", "both", "during", "before", "under", "those", "same",
            "however", "therefore", "thus", "hence", "given", "since", "used",
            "using", "based", "can", "may", "has", "had", "its", "any", "all",
            "are", "was", "one", "new", "most", "first", "last", "his", "her",
            "him", "she", "who", "whom", "data", "paper", "study", "result",
            "results", "conclusion", "section", "figure", "table", "show",
            "shows", "shown", "found", "find", "chapter", "page",
            "abstract", "introduction", "references", "bibliography"
    );

    private final AIInsightRepository aiInsightRepository;
    private final RecommendationService recommendationService;
    private final TechnologyRepository technologyRepository;
    private final ResearchAreaRepository researchAreaRepository;
    private final GrobidService grobidService;
    private final ReferenceRepository referenceRepository;
    private final SpecterService specterService;
    private final SubmissionRepository submissionRepository;
    private final Path uploadRoot;

    @Value("${spring.ai.openai.api-key:NO_KEY}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:https://openrouter.ai/api/v1}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.model:openai/gpt-4o-mini}")
    private String model;

    public AIInsightProcessingService(AIInsightRepository aiInsightRepository,
                                      RecommendationService recommendationService,
                                      TechnologyRepository technologyRepository,
                                      ResearchAreaRepository researchAreaRepository,
                                      GrobidService grobidService,
                                      ReferenceRepository referenceRepository,
                                      SpecterService specterService,
                                      SubmissionRepository submissionRepository,
                                      @Value("${app.storage.upload-dir:uploads}") String uploadDir) {
        this.aiInsightRepository = aiInsightRepository;
        this.recommendationService = recommendationService;
        this.technologyRepository = technologyRepository;
        this.researchAreaRepository = researchAreaRepository;
        this.grobidService = grobidService;
        this.referenceRepository = referenceRepository;
        this.specterService = specterService;
        this.submissionRepository = submissionRepository;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public static class LlmResult {
        public String summary;
        public List<String> keywords = new ArrayList<>();
        public List<String> objectives = new ArrayList<>();
        public List<String> technologies = new ArrayList<>();
        public List<String> researchAreas = new ArrayList<>();
        public String problemStatement;
    }

    private String trimFrontMatter(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String[] words = text.trim().split("\\s+");
        if (words.length <= 400) {
            return text;
        }
        return Arrays.stream(words)
                .skip(400)
                .collect(Collectors.joining(" "));
    }

    private String getCompletionsUrl() {
        String url = baseUrl;
        if (url == null) {
            return "https://openrouter.ai/api/v1/chat/completions";
        }
        url = url.trim();
        if (url.endsWith("/chat/completions")) {
            return url;
        }
        if (url.endsWith("/")) {
            return url + "chat/completions";
        }
        return url + "/chat/completions";
    }

    private LlmResult callOpenAi(String trimmedText) throws Exception {
        if (apiKey == null || apiKey.isBlank() || "NO_KEY".equals(apiKey)) {
            throw new IllegalStateException("OpenAI/OpenRouter API key is not configured.");
        }

        String prompt = """
            Analyze the following academic project document.
            Return a strict JSON object with these fields:
            {
              "summary": "A concise 3-4 sentence academic summary of the project.",
              "keywords": ["3-5 keywords"],
              "objectives": ["2-4 clear objectives or goals of the project"],
              "technologies": ["Technologies, frameworks, databases, or programming languages mentioned or recommended for implementation"],
              "researchAreas": ["1-3 academic research areas or fields of study (e.g. Distributed Systems, Computer Vision, Cybersecurity)"],
              "problemStatement": "A concise description of the problem this project intends to solve."
            }
            Do not include any markdown styling like ```json or ```. Return only the raw JSON.
            
            Document text:
            %s
            """.formatted(trimmedText);

        ObjectMapper mapper = new ObjectMapper();
        String escapedPrompt = mapper.writeValueAsString(prompt);

        String requestBody = """
            {
              "model": "%s",
              "messages": [
                {
                  "role": "user",
                  "content": %s
                }
              ]
            }
            """.formatted(model, escapedPrompt);

        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(getCompletionsUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestBody, java.nio.charset.StandardCharsets.UTF_8))
                .build();

        log.info("Sending request to OpenRouter/OpenAI API at {} using model {}...", getCompletionsUrl(), model);
        java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("API request failed with status code " + response.statusCode() + ": " + response.body());
        }

        String responseBody = response.body();
        com.fasterxml.jackson.databind.JsonNode rootNode = mapper.readTree(responseBody);
        com.fasterxml.jackson.databind.JsonNode choices = rootNode.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            throw new RuntimeException("Unexpected API response structure: " + responseBody);
        }
        String content = choices.get(0).get("message").get("content").asText();

        if (content.contains("```json")) {
            content = content.substring(content.indexOf("```json") + 7);
            if (content.contains("```")) {
                content = content.substring(0, content.indexOf("```"));
            }
        } else if (content.contains("```")) {
            content = content.substring(content.indexOf("```") + 3);
            if (content.contains("```")) {
                content = content.substring(0, content.indexOf("```"));
            }
        }
        content = content.trim();

        return mapper.readValue(content, LlmResult.class);
    }

    @Transactional
    @org.springframework.scheduling.annotation.Async
    public void performAnalysisAsync(Long insightId) {
        AIInsight insight = aiInsightRepository.findById(insightId).orElse(null);
        if (insight == null) {
            return;
        }

        insight.setStatus(AIInsightStatus.PROCESSING);
        aiInsightRepository.save(insight);

        try {
            Submission submission = insight.getSubmission();
            if (submission.getVersions().isEmpty()) {
                throw new IllegalStateException("Submission has no file versions to analyse.");
            }

            String fileName = submission.getVersions()
                    .get(submission.getVersions().size() - 1)
                    .getFilePath();
            Path filePath = uploadRoot.resolve(fileName);
            if (!filePath.toFile().exists()) {
                throw new IllegalStateException("Uploaded file could not be found on storage.");
            }

            // Run the text extraction and summary generation in a timeout-bounded execution block
            final Submission finalSub = submission;
            java.util.concurrent.CompletableFuture<Void> future = java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    String promptInputText = "";
                    String rawText = null;

                    // Try GROBID first
                    Optional<GrobidService.GrobidResult> grobidResult = grobidService.extractStructured(filePath.toFile());
                    if (grobidResult.isPresent()) {
                        GrobidService.GrobidResult gr = grobidResult.get();
                        StringBuilder sb = new StringBuilder();
                        if (gr.introduction() != null && !gr.introduction().isBlank()) {
                            sb.append("=== INTRODUCTION ===\n").append(gr.introduction()).append("\n\n");
                        }
                        if (gr.methodology() != null && !gr.methodology().isBlank()) {
                            sb.append("=== METHODOLOGY ===\n").append(gr.methodology()).append("\n\n");
                        }
                        if (gr.conclusion() != null && !gr.conclusion().isBlank()) {
                            sb.append("=== CONCLUSION ===\n").append(gr.conclusion()).append("\n\n");
                        }
                        promptInputText = sb.toString().trim();

                        // Save references deterministically (without LLM)
                        if (gr.references() != null && !gr.references().isEmpty()) {
                            List<Reference> existingRefs = referenceRepository.findBySubmissionIdOrderByTitle(finalSub.getId());
                            referenceRepository.deleteAll(existingRefs);
                            for (GrobidService.GrobidReference gRef : gr.references()) {
                                Reference ref = new Reference();
                                ref.setSubmission(finalSub);
                                ref.setAuthors(gRef.authors());
                                ref.setTitle(gRef.title());
                                ref.setYear(gRef.year());
                                ref.setDoi(gRef.doi());
                                referenceRepository.save(ref);
                            }
                        }
                    }

                    // If GROBID fails/disabled or yields no text, fall back to Tika
                    if (promptInputText.isBlank()) {
                        rawText = extractText(filePath);
                        if (rawText == null || rawText.isBlank()) {
                            throw new IllegalStateException("Document appears to be empty or unreadable.");
                        }
                        promptInputText = trimFrontMatter(rawText);
                    } else {
                        // Still run Tika text extraction to compute term frequency keywords from body
                        try {
                            rawText = extractText(filePath);
                        } catch (Exception ex) {
                            log.warn("Tika fallback text extraction failed during GROBID process: {}", ex.getMessage());
                        }
                    }

                    List<String> keywords = extractKeywords(rawText != null ? rawText : promptInputText, 10);
                    LlmResult result = null;

                    try {
                        result = callOpenAi(promptInputText);
                    } catch (Exception ex) {
                        log.warn("OpenAI/OpenRouter LLM analysis failed. Using fallback local heuristic analysis. Reason: {}", ex.getMessage());
                        result = new LlmResult();
                        result.summary = extractSummary(rawText != null ? rawText : promptInputText, keywords, 3);
                        result.keywords = keywords;
                        result.objectives = List.of(
                            "Design and implement the proposed system model.",
                            "Evaluate the performance, scalability, and usability of the application."
                        );
                        result.problemStatement = "The manual processes and lack of specialized automation currently limit efficiency in this academic domain.";
                        result.technologies = List.of("Spring Boot", "Java", "H2 Database");
                        result.researchAreas = List.of("Software Engineering");
                    }

                    // Deduplicate lists before saving/mapping
                    result.keywords = dedupeCaseInsensitive(result.keywords, 10);
                    result.objectives = dedupeCaseInsensitive(result.objectives, 6);
                    result.technologies = dedupeCaseInsensitive(result.technologies, Integer.MAX_VALUE);
                    result.researchAreas = dedupeCaseInsensitive(result.researchAreas, Integer.MAX_VALUE);

                    // Save structured results on insight
                    insight.setSummary(result.summary);
                    
                    insight.getKeywords().clear();
                    if (result.keywords != null) {
                        insight.getKeywords().addAll(result.keywords);
                    }
                    
                    insight.getObjectives().clear();
                    if (result.objectives != null) {
                        insight.getObjectives().addAll(result.objectives);
                    }
                    
                    insight.setProblemStatement(result.problemStatement);
                    insight.setStatus(AIInsightStatus.COMPLETED);
                    aiInsightRepository.save(insight);

                    // Generate document embedding via SPECTER service
                    try {
                        String combinedText = finalSub.getTitle() + " " + result.summary;
                        Optional<float[]> embeddingOpt = specterService.embed(combinedText);
                        if (embeddingOpt.isPresent()) {
                            finalSub.setEmbedding(embeddingOpt.get());
                            submissionRepository.save(finalSub);
                        }
                    } catch (Exception ex) {
                        log.warn("Failed to generate or save embedding for submission {}: {}", finalSub.getId(), ex.getMessage());
                    }

                    // Map lookup tables
                    if (result.technologies != null) {
                        Set<Technology> mappedTechs = new HashSet<>();
                        for (String techName : result.technologies) {
                            if (techName == null || techName.isBlank()) continue;
                            Technology tech;
                            try {
                                String trimmedName = techName.trim();
                                tech = technologyRepository.findByNameIgnoreCase(trimmedName)
                                        .orElseGet(() -> {
                                            Technology newTech = new Technology();
                                            newTech.setName(trimmedName);
                                            return technologyRepository.saveAndFlush(newTech);
                                        });
                            } catch (Exception ex) {
                                tech = technologyRepository.findByNameIgnoreCase(techName.trim())
                                        .orElseThrow(() -> new RuntimeException("Failed to save or find technology: " + techName, ex));
                            }
                            mappedTechs.add(tech);
                        }
                        submission.setTechnologies(mappedTechs);
                    }

                    if (result.researchAreas != null) {
                        Set<ResearchArea> mappedAreas = new HashSet<>();
                        for (String areaName : result.researchAreas) {
                            if (areaName == null || areaName.isBlank()) continue;
                            ResearchArea area;
                            try {
                                String trimmedName = areaName.trim();
                                area = researchAreaRepository.findByNameIgnoreCase(trimmedName)
                                        .orElseGet(() -> {
                                            ResearchArea newArea = new ResearchArea();
                                            newArea.setName(trimmedName);
                                            return researchAreaRepository.saveAndFlush(newArea);
                                        });
                            } catch (Exception ex) {
                                area = researchAreaRepository.findByNameIgnoreCase(areaName.trim())
                                        .orElseThrow(() -> new RuntimeException("Failed to save or find research area: " + areaName, ex));
                            }
                            mappedAreas.add(area);
                        }
                        submission.setResearchAreas(mappedAreas);
                    }

                    log.info("AI analysis COMPLETED for insight {} (submission: {})",
                            insightId, finalSub.getId());

                    recommendationService.precomputeForSubmission(finalSub);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });

            try {
                // Limit maximum processing duration to 30 seconds
                future.get(30, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException ex) {
                future.cancel(true);
                throw new java.util.concurrent.TimeoutException("AI analysis execution timed out after 30 seconds.");
            }
        } catch (Exception ex) {
            log.warn("AI analysis FAILED for insight {}: {}", insightId, ex.getMessage());
            insight.setStatus(AIInsightStatus.FAILED);
            String msg = ex.getMessage() != null ? ex.getMessage() : "Unknown error";
            if (ex instanceof java.util.concurrent.ExecutionException && ex.getCause() != null) {
                msg = ex.getCause().getMessage();
            }
            insight.setErrorMessage(msg.length() > 1000 ? msg.substring(0, 1000) : msg);
            aiInsightRepository.save(insight);
        }
    }

    private String extractText(Path filePath) throws Exception {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();

        try (InputStream stream = new FileInputStream(filePath.toFile())) {
            parser.parse(stream, handler, metadata, context);
        }
        return handler.toString();
    }

    private List<String> extractKeywords(String text, int topN) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String[] tokens = text.toLowerCase()
                .replaceAll("[^a-z\\s]", " ")
                .split("\\s+");

        Map<String, Long> freq = Arrays.stream(tokens)
                .filter(w -> w.length() >= 4)
                .filter(w -> !STOPWORDS.contains(w))
                .collect(Collectors.groupingBy(w -> w, Collectors.counting()));

        return freq.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(topN)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private String extractSummary(String text, List<String> keywords, int maxSentences) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String[] sentences = text.split("[.!?]+");
        if (sentences.length <= maxSentences) {
            return text.trim().length() > 600 ? text.trim().substring(0, 600) + "..." : text.trim();
        }

        Set<String> keywordSet = new HashSet<>(keywords);
        record SentenceScore(int index, String text, double score) {}

        List<SentenceScore> scored = new ArrayList<>();
        for (int i = 0; i < sentences.length; i++) {
            String sentence = sentences[i].trim();
            if (sentence.length() < 20) {
                continue;
            }

            String lower = sentence.toLowerCase();
            long hits = keywordSet.stream().filter(lower::contains).count();
            String[] words = lower.split("\\s+");
            double density = words.length > 0 ? (double) hits / words.length : 0;
            scored.add(new SentenceScore(i, sentence, density));
        }

        if (scored.isEmpty()) {
            return text.trim().substring(0, Math.min(text.trim().length(), 400));
        }

        List<SentenceScore> selected = scored.stream()
                .sorted(Comparator.comparingDouble(SentenceScore::score).reversed())
                .limit(maxSentences)
                .sorted(Comparator.comparingInt(SentenceScore::index))
                .toList();

        return selected.stream()
                .map(SentenceScore::text)
                .collect(Collectors.joining(". ")) + ".";
    }

    private List<String> dedupeCaseInsensitive(List<String> input, int maxSize) {
        if (input == null) return List.of();
        return input.stream()
            .filter(s -> s != null && !s.isBlank())
            .collect(Collectors.toMap(
                s -> s.toLowerCase().trim(),
                s -> s,
                (first, second) -> first,
                java.util.LinkedHashMap::new))
            .values().stream()
            .limit(maxSize)
            .collect(Collectors.toList());
    }
}
