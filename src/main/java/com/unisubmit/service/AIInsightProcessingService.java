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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.web.multipart.MultipartFile;
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
    private final OcrService ocrService;
    private final CollaborationDiscoveryService collaborationDiscoveryService;
    private final CollaborationAssessmentService collaborationAssessmentService;
    private final SubmissionRepository submissionRepository;
    private final TransactionTemplate transactionTemplate;
    private final Path uploadRoot;

    @Value("${spring.ai.openai.api-key:NO_KEY}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:https://openrouter.ai/api/v1}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.model:openai/gpt-4o-mini}")
    private String model;

    /** Upper bound for one full pipeline run — GROBID + LLM regularly exceeds 30s. */
    @Value("${unisubmit.ai.timeout-seconds:120}")
    private long timeoutSeconds;

    public AIInsightProcessingService(AIInsightRepository aiInsightRepository,
                                      RecommendationService recommendationService,
                                      TechnologyRepository technologyRepository,
                                      ResearchAreaRepository researchAreaRepository,
                                      GrobidService grobidService,
                                      ReferenceRepository referenceRepository,
                                      SpecterService specterService,
                                      OcrService ocrService,
                                      CollaborationDiscoveryService collaborationDiscoveryService,
                                      CollaborationAssessmentService collaborationAssessmentService,
                                      SubmissionRepository submissionRepository,
                                      PlatformTransactionManager transactionManager,
                                      @Value("${app.storage.upload-dir:uploads}") String uploadDir) {
        this.aiInsightRepository = aiInsightRepository;
        this.recommendationService = recommendationService;
        this.technologyRepository = technologyRepository;
        this.researchAreaRepository = researchAreaRepository;
        this.grobidService = grobidService;
        this.referenceRepository = referenceRepository;
        this.specterService = specterService;
        this.ocrService = ocrService;
        this.collaborationDiscoveryService = collaborationDiscoveryService;
        this.collaborationAssessmentService = collaborationAssessmentService;
        this.submissionRepository = submissionRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public static class LlmResult {
        public String summary;
        public List<String> keywords = new ArrayList<>();
        public List<String> objectives = new ArrayList<>();
        public List<String> technologies = new ArrayList<>();
        public List<String> researchAreas = new ArrayList<>();
        public List<String> problemDomains = new ArrayList<>();
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
              "problemDomains": ["1-3 broad real-world application domains this project touches, chosen to be cross-disciplinary (e.g. transportation, healthcare, agriculture, energy, education, manufacturing, finance, environment, security, urban planning)"],
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
              "max_tokens": 1500,
              "temperature": 0.2,
              "messages": [
                {
                  "role": "user",
                  "content": %s
                }
              ]
            }
            """.formatted(model, escapedPrompt);

        // Bounded timeouts so a slow / rate-limited provider fails fast into the
        // local fallback instead of blocking the pipeline up to its 120s limit.
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(getCompletionsUrl()))
                .timeout(java.time.Duration.ofSeconds(45))
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

    /**
     * Deliberately NOT {@code @Transactional}: holding one outer transaction
     * (and its DB connection) open while blocking on the pipeline future caused
     * connection starvation and let the outer entity save race the inner
     * committed state. Instead: mark PROCESSING in a short standalone tx, run
     * the pipeline (which manages its own tx), then record failure in another
     * short tx if needed.
     */
    @org.springframework.scheduling.annotation.Async
    public void performAnalysisAsync(Long insightId) {
        // Captured inside the pipeline tx so Stage 2 collaboration assessment can
        // be triggered AFTER commit (the async reader must see the UNASSESSED rows).
        final java.util.concurrent.atomic.AtomicReference<Long> analysedSubmissionId =
                new java.util.concurrent.atomic.AtomicReference<>();
        final Path filePath;
        try {
            filePath = transactionTemplate.execute(status -> {
                AIInsight insight = aiInsightRepository.findById(insightId).orElse(null);
                if (insight == null) {
                    return null;
                }
                insight.setStatus(AIInsightStatus.PROCESSING);
                aiInsightRepository.save(insight);

                Submission submission = insight.getSubmission();
                if (submission.getVersions().isEmpty()) {
                    throw new IllegalStateException("Submission has no file versions to analyse.");
                }
                String fileName = submission.getVersions()
                        .get(submission.getVersions().size() - 1)
                        .getFilePath();
                Path resolved = uploadRoot.resolve(fileName);
                if (!resolved.toFile().exists()) {
                    throw new IllegalStateException("Uploaded file could not be found on storage.");
                }
                return resolved;
            });
        } catch (Exception ex) {
            markFailed(insightId, ex);
            return;
        }
        if (filePath == null) {
            return;
        }

        try {
            // Run the text extraction and summary generation in a timeout-bounded execution block
            java.util.concurrent.CompletableFuture<Void> future = java.util.concurrent.CompletableFuture.runAsync(() -> {
                transactionTemplate.execute(status -> {
                    try {
                        // Reload entities inside this transaction thread
                        AIInsight txInsight = aiInsightRepository.findById(insightId)
                                .orElseThrow(() -> new IllegalStateException("Insight not found: " + insightId));
                        Submission txSubmission = txInsight.getSubmission();

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
                                List<Reference> existingRefs = referenceRepository.findBySubmissionIdOrderByTitle(txSubmission.getId());
                                referenceRepository.deleteAll(existingRefs);
                                for (GrobidService.GrobidReference gRef : gr.references()) {
                                    Reference ref = new Reference();
                                    ref.setSubmission(txSubmission);
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
                            // Scanned-document case: Tika sees (almost) nothing.
                            // Try the OCR sidecar before giving up.
                            if (rawText == null || rawText.strip().length() < 200) {
                                String ocrText = ocrService.extractText(filePath.toFile()).orElse(null);
                                if (ocrText != null && ocrText.strip().length() >= 200) {
                                    rawText = ocrText;
                                }
                            }
                            if (rawText == null || rawText.isBlank()) {
                                throw new IllegalStateException(ocrService.isEnabled()
                                        ? "Document appears to be empty or unreadable, and OCR found no text either."
                                        : "Document appears to be empty or scanned. OCR fallback is disabled "
                                          + "(enable it with UNISUBMIT OCR settings) — upload a text-based file instead.");
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
                            // Fallback keeps only what was genuinely derived from the
                            // document (TF keywords + extractive summary). Structured
                            // fields stay empty — fabricated tags would pollute the
                            // knowledge model and recommendation signals.
                            result = new LlmResult();
                            result.summary = "(Automated fallback — AI analysis unavailable.) "
                                    + extractSummary(rawText != null ? rawText : promptInputText, keywords, 3);
                            result.keywords = keywords;
                            result.objectives = List.of();
                            result.problemDomains = List.of();
                            result.problemStatement = null;
                            // null (not empty) = leave any existing submission tags untouched
                            result.technologies = null;
                            result.researchAreas = null;
                        }

                        // Deduplicate lists before saving/mapping (null = skip tag mapping)
                        result.keywords = dedupeCaseInsensitive(result.keywords, 10);
                        result.objectives = dedupeCaseInsensitive(result.objectives, 6);
                        result.problemDomains = dedupeCaseInsensitive(result.problemDomains, 5);
                        result.technologies = result.technologies == null ? null
                                : dedupeCaseInsensitive(result.technologies, Integer.MAX_VALUE);
                        result.researchAreas = result.researchAreas == null ? null
                                : dedupeCaseInsensitive(result.researchAreas, Integer.MAX_VALUE);

                        // Save structured results on insight
                        txInsight.setSummary(result.summary);
                        
                        txInsight.getKeywords().clear();
                        if (result.keywords != null) {
                            txInsight.getKeywords().addAll(result.keywords);
                        }
                        
                        txInsight.getObjectives().clear();
                        if (result.objectives != null) {
                            txInsight.getObjectives().addAll(result.objectives);
                        }

                        // Phase 8 — broad application domains for cross-disciplinary matching
                        txInsight.getProblemDomains().clear();
                        if (result.problemDomains != null) {
                            txInsight.getProblemDomains().addAll(result.problemDomains);
                        }

                        txInsight.setProblemStatement(result.problemStatement);
                        txInsight.setStatus(AIInsightStatus.COMPLETED);
                        aiInsightRepository.save(txInsight);

                        // Generate document embedding via SPECTER service
                        try {
                            String combinedText = txSubmission.getTitle() + " " + result.summary;
                            Optional<float[]> embeddingOpt = specterService.embed(combinedText);
                            if (embeddingOpt.isPresent()) {
                                txSubmission.setEmbedding(embeddingOpt.get());
                            }
                        } catch (Exception ex) {
                            log.warn("Failed to generate or save embedding for submission {}: {}", txSubmission.getId(), ex.getMessage());
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
                            txSubmission.setTechnologies(mappedTechs);
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
                            txSubmission.setResearchAreas(mappedAreas);
                        }

                        submissionRepository.save(txSubmission);

                        log.info("AI analysis COMPLETED for insight {} (submission: {})",
                                insightId, txSubmission.getId());

                        recommendationService.precomputeForSubmission(txSubmission);
                        // Phase 8 Stage 1 — refresh this submission's collaboration
                        // shortlist (whole-corpus, cross-disciplinary, unit excluded).
                        collaborationDiscoveryService.precomputeForSubmission(txSubmission);
                        analysedSubmissionId.set(txSubmission.getId());
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                    return null;
                });
            });

            try {
                future.get(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException ex) {
                future.cancel(true);
                throw new java.util.concurrent.TimeoutException(
                        "AI analysis execution timed out after " + timeoutSeconds + " seconds.");
            }

            // Phase 8 Stage 2 — assess the shortlisted pairs with the LLM, AFTER
            // the Stage 1 rows are committed and visible. Async + no-op without a key.
            Long submissionId = analysedSubmissionId.get();
            if (submissionId != null) {
                collaborationAssessmentService.assessForSubmission(submissionId);
            }
        } catch (Exception ex) {
            markFailed(insightId, ex);
        }
    }

    /** Records FAILED + error message in its own short transaction. */
    private void markFailed(Long insightId, Exception ex) {
        log.warn("AI analysis FAILED for insight {}: {}", insightId, ex.getMessage());
        String raw = ex.getMessage() != null ? ex.getMessage() : "Unknown error";
        if (ex instanceof java.util.concurrent.ExecutionException && ex.getCause() != null
                && ex.getCause().getMessage() != null) {
            raw = ex.getCause().getMessage();
        }
        final String msg = raw.length() > 1000 ? raw.substring(0, 1000) : raw;
        try {
            transactionTemplate.executeWithoutResult(status ->
                    aiInsightRepository.findById(insightId).ifPresent(insight -> {
                        insight.setStatus(AIInsightStatus.FAILED);
                        insight.setErrorMessage(msg);
                        aiInsightRepository.save(insight);
                    }));
        } catch (Exception persistEx) {
            log.error("Could not persist FAILED state for insight {}: {}", insightId, persistEx.getMessage());
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

    /**
     * Uses the LLM to suggest 3 creative, professional project titles based on
     * the AI insight (summary, keywords, objectives, problem statement).
     * Returns an empty list if the API key is not configured or the call fails.
     */
    public List<String> suggestTitles(Long submissionId) {
        if (apiKey == null || apiKey.isBlank() || "NO_KEY".equals(apiKey)) {
            return List.of();
        }

        Optional<com.unisubmit.domain.Submission> optSub = submissionRepository.findById(submissionId);
        if (optSub.isEmpty() || optSub.get().getAiInsight() == null) {
            return List.of();
        }

        AIInsight insight = optSub.get().getAiInsight();
        String currentTitle = optSub.get().getTitle();

        String prompt = """
            You are an academic project naming expert.
            Based on the following project analysis, suggest exactly 3 creative, professional,
            and concise project titles. Each title should be clear, academic in tone, and
            capture the essence of the project.

            Current title: %s
            Summary: %s
            Problem statement: %s
            Keywords: %s
            Objectives: %s

            Return ONLY a JSON array of 3 strings, no markdown fences.
            Example: ["Title One", "Title Two", "Title Three"]
            """.formatted(
                currentTitle != null ? currentTitle : "Untitled",
                insight.getSummary() != null ? insight.getSummary() : "No summary",
                insight.getProblemStatement() != null ? insight.getProblemStatement() : "No problem statement",
                insight.getKeywords() != null ? String.join(", ", insight.getKeywords()) : "none",
                insight.getObjectives() != null ? String.join("; ", insight.getObjectives()) : "none"
        );

        try {
            ObjectMapper mapper = new ObjectMapper();
            String escapedPrompt = mapper.writeValueAsString(prompt);

            String requestBody = """
                {
                  "model": "%s",
                  "max_tokens": 300,
                  "temperature": 0.7,
                  "messages": [
                    {
                      "role": "user",
                      "content": %s
                    }
                  ]
                }
                """.formatted(model, escapedPrompt);

            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .build();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(getCompletionsUrl()))
                    .timeout(java.time.Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestBody, java.nio.charset.StandardCharsets.UTF_8))
                    .build();

            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Title suggestion LLM call failed with status {}", response.statusCode());
                return List.of();
            }

            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(response.body());
            com.fasterxml.jackson.databind.JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                return List.of();
            }
            String content = choices.get(0).get("message").get("content").asText("").trim();

            // Strip markdown fences if present
            if (content.contains("```")) {
                int start = content.indexOf('[');
                int end = content.lastIndexOf(']');
                if (start >= 0 && end > start) {
                    content = content.substring(start, end + 1);
                }
            }

            com.fasterxml.jackson.databind.JsonNode array = mapper.readTree(content);
            if (!array.isArray()) {
                return List.of();
            }
            List<String> titles = new ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode n : array) {
                titles.add(n.asText());
            }
            return titles;

        } catch (Exception ex) {
            log.warn("Title suggestion failed: {}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * Stateless title suggestion: parses a raw uploaded MultipartFile and calls the LLM
     * to suggest 3 titles based on the extracted text. Does not write to database.
     */
    public List<String> suggestTitlesForDraft(MultipartFile file) {
        if (apiKey == null || apiKey.isBlank() || "NO_KEY".equals(apiKey)) {
            return List.of();
        }
        try {
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            
            try (InputStream stream = file.getInputStream()) {
                parser.parse(stream, handler, metadata, context);
            }
            String rawText = handler.toString();
            if (rawText == null || rawText.isBlank()) {
                return List.of();
            }
            
            String trimmedText = trimFrontMatter(rawText);
            
            // Now call LLM to generate 3 suggested titles
            String prompt = """
                You are an academic project naming expert.
                Based on the following extracted text snippet from a student's project draft document,
                suggest exactly 3 creative, professional, and academic project titles.
                The titles should capture the key technical keywords, objectives, and domain of the project.

                Extracted text:
                %s

                Return ONLY a JSON array of 3 strings, no markdown fences.
                Example: ["Title One", "Title Two", "Title Three"]
                """.formatted(trimmedText.substring(0, Math.min(trimmedText.length(), 2000)));

            ObjectMapper mapper = new ObjectMapper();
            String escapedPrompt = mapper.writeValueAsString(prompt);

            String requestBody = """
                {
                  "model": "%s",
                  "max_tokens": 300,
                  "temperature": 0.7,
                  "messages": [
                    {
                      "role": "user",
                      "content": %s
                    }
                  ]
                }
                """.formatted(model, escapedPrompt);

            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .build();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(getCompletionsUrl()))
                    .timeout(java.time.Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestBody, java.nio.charset.StandardCharsets.UTF_8))
                    .build();

            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Draft title suggestion LLM call failed with status {}", response.statusCode());
                return List.of();
            }

            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(response.body());
            com.fasterxml.jackson.databind.JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                return List.of();
            }
            String content = choices.get(0).get("message").get("content").asText("").trim();

            if (content.contains("```")) {
                int start = content.indexOf('[');
                int end = content.lastIndexOf(']');
                if (start >= 0 && end > start) {
                    content = content.substring(start, end + 1);
                }
            }

            com.fasterxml.jackson.databind.JsonNode array = mapper.readTree(content);
            if (!array.isArray()) {
                return List.of();
            }
            List<String> titles = new ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode n : array) {
                titles.add(n.asText());
            }
            return titles;
        } catch (Exception e) {
            log.warn("Draft title suggestion failed: {}", e.getMessage());
            return List.of();
        }
    }
}
