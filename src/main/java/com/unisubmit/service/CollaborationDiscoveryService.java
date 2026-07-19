package com.unisubmit.service;

import com.unisubmit.config.CollaborationWeights;
import com.unisubmit.domain.AIInsight;
import com.unisubmit.domain.AIInsightStatus;
import com.unisubmit.domain.CollaborationMatch;
import com.unisubmit.domain.CollaborationValue;
import com.unisubmit.domain.ResearchArea;
import com.unisubmit.domain.Submission;
import com.unisubmit.domain.SubmissionStatus;
import com.unisubmit.domain.Technology;
import com.unisubmit.repository.CollaborationMatchRepository;
import com.unisubmit.repository.SubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Phase 8 — Stage 1: the mechanical collaboration pre-filter.
 * <p>
 * The opposite of the similarity engine. Instead of "how alike are these?" it
 * asks "would these two students gain from working together?". Concretely:
 * <ul>
 *   <li>candidate pool is the WHOLE corpus, not same-unit + recent;</li>
 *   <li>unit/title carry ZERO weight — a classmate doing the same assignment is
 *       not a collaborator;</li>
 *   <li>same-unit, same-student, same-group and opted-out students are excluded
 *       outright;</li>
 *   <li>score is semantic + technology + research-area + problem-domain overlap,
 *       plus a cross-department bonus and a mentorship bonus (senior / completed
 *       work);</li>
 *   <li>only the top ~15 candidates per submission survive to Stage 2 (the
 *       expensive LLM assessment).</li>
 * </ul>
 * Pairs are persisted once in canonical order (a.id &lt; b.id) as UNASSESSED
 * rows; {@link CollaborationAssessmentService} fills in the LLM verdict later.
 */
@Service
public class CollaborationDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(CollaborationDiscoveryService.class);

    static final int SHORTLIST_SIZE = 15;
    private static final double MIN_MECHANICAL_SCORE = 0.08;

    private final SubmissionRepository submissionRepository;
    private final CollaborationMatchRepository matchRepository;
    private final CollaborationWeights weights;
    private final CollaborationAssessmentService assessmentService;
    private final CollaborationRequestService requestService;

    public CollaborationDiscoveryService(SubmissionRepository submissionRepository,
                                         CollaborationMatchRepository matchRepository,
                                         CollaborationWeights weights,
                                         CollaborationAssessmentService assessmentService,
                                         CollaborationRequestService requestService) {
        this.submissionRepository = submissionRepository;
        this.matchRepository = matchRepository;
        this.weights = weights;
        this.assessmentService = assessmentService;
        this.requestService = requestService;
    }

    /** ID entry point for callers outside an open session (startup runner, async). */
    @Transactional
    public void precomputeForSubmissionId(Long submissionId) {
        submissionRepository.findById(submissionId).ifPresent(this::precomputeForSubmission);
    }

    // ── Read side (Discover page) ────────────────────────────────────────────

    /**
     * The viewer's collaboration opportunities, oriented so "you" is always the
     * viewer. When an API key is configured only LLM-assessed HIGH/MEDIUM pairs
     * are shown; without a key the mechanical shortlist (UNASSESSED) is shown as
     * a lower-confidence tier so the page is still demonstrable. Partners are
     * re-checked for eligibility + discoverability at read time.
     */
    @Transactional(readOnly = true)
    public List<com.unisubmit.dto.CollaborationOpportunity> findOpportunitiesForStudent(
            com.unisubmit.domain.User viewer) {
        List<Submission> mine = submissionRepository.findByStudent(viewer);
        if (mine.isEmpty()) {
            return List.of();
        }
        Set<Long> myIds = new HashSet<>();
        mine.forEach(s -> myIds.add(s.getId()));

        List<CollaborationValue> values = assessmentService.isConfigured()
                ? List.of(CollaborationValue.HIGH, CollaborationValue.MEDIUM)
                : List.of(CollaborationValue.HIGH, CollaborationValue.MEDIUM, CollaborationValue.UNASSESSED);

        List<CollaborationMatch> matches = matchRepository.findForSubmissionsWithValues(mine, values);

        // Which partner submissions has the viewer already messaged?
        List<Submission> partners = new ArrayList<>();
        for (CollaborationMatch m : matches) {
            Submission partner = myIds.contains(m.getSubmissionA().getId())
                    ? m.getSubmissionB() : m.getSubmissionA();
            if (partner != null) {
                partners.add(partner);
            }
        }
        var requestStatuses = requestService.getRequestStatusesForSender(viewer, partners);

        List<com.unisubmit.dto.CollaborationOpportunity> out = new ArrayList<>();
        Set<Long> seenPartners = new HashSet<>();
        for (CollaborationMatch m : matches) {
            boolean mineIsA = myIds.contains(m.getSubmissionA().getId());
            Submission yours = mineIsA ? m.getSubmissionA() : m.getSubmissionB();
            Submission partner = mineIsA ? m.getSubmissionB() : m.getSubmissionA();
            if (partner == null || !isEligible(partner) || !isDiscoverable(partner)) {
                continue;
            }
            if (!seenPartners.add(partner.getId())) {
                continue; // keep the best (list is ordered value ASC, score DESC)
            }
            String youGain = mineIsA ? m.getWhatAGains() : m.getWhatBGains();
            String theyGain = mineIsA ? m.getWhatBGains() : m.getWhatAGains();

            out.add(new com.unisubmit.dto.CollaborationOpportunity(
                    m.getId(), yours, partner, m.getCollaborationValue(), m.getCollaborationType(),
                    youGain, theyGain, m.getPitch(), m.getComplementaryGaps(),
                    m.isAssessed(),
                    requestStatuses.containsKey(partner.getId()),
                    partner.getUnit() != null && partner.getUnit().getDepartment() != null
                            ? partner.getUnit().getDepartment().getName() : null,
                    studentYear(partner)));
        }
        return out;
    }

    @Transactional
    public void precomputeForSubmission(Submission current) {
        if (!isEligible(current) || !isDiscoverable(current)) {
            // Opted out or not analysable → this submission takes part in no
            // collaboration matching; drop any UNASSESSED rows it drove.
            removeUnassessedRowsFor(current);
            return;
        }

        AIInsight currentInsight = current.getAiInsight();
        Set<String> curTechs = techNames(current);
        Set<String> curAreas = areaNames(current);
        Set<String> curDomains = domainNames(currentInsight);
        Long curDeptId = departmentId(current);

        List<Submission> corpus = submissionRepository.findAll();

        record Scored(Submission candidate, double score) {}
        List<Scored> scored = new ArrayList<>();
        for (Submission candidate : corpus) {
            if (!isValidPartner(current, candidate, curDeptId)) {
                continue;
            }
            double score = mechanicalScore(current, currentInsight, curTechs, curAreas, curDomains,
                    curDeptId, candidate);
            if (score >= MIN_MECHANICAL_SCORE) {
                scored.add(new Scored(candidate, score));
            }
        }

        List<Scored> shortlist = scored.stream()
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(SHORTLIST_SIZE)
                .toList();

        Set<Long> keptPartnerIds = new HashSet<>();
        String currentHash = insightHash(current);
        for (Scored s : shortlist) {
            keptPartnerIds.add(s.candidate().getId());
            upsertPair(current, currentHash, s.candidate(), s.score());
        }

        // Remove UNASSESSED rows for `current` whose partner dropped off the
        // shortlist (assessed rows are preserved — they cost an LLM call).
        for (CollaborationMatch existing : matchRepository.findBySubmission(current)) {
            if (existing.isAssessed()) {
                continue;
            }
            Submission partner = partnerOf(existing, current);
            if (partner != null && !keptPartnerIds.contains(partner.getId())) {
                matchRepository.delete(existing);
            }
        }
    }

    // ── Eligibility & exclusions ─────────────────────────────────────────────

    private boolean isEligible(Submission s) {
        return s.getCurriculum() != null
                && s.getCurriculum().getUnit() != null
                && s.getAiInsight() != null
                && s.getAiInsight().getStatus().hasContent()
                && s.getStatus() != SubmissionStatus.DRAFT;
    }

    private boolean isDiscoverable(Submission s) {
        return s.getStudent() != null
                && s.getStudent().getStudentProfile() != null
                && s.getStudent().getStudentProfile().isDiscoverableForCollaboration();
    }

    /** All the reasons a candidate is not a collaboration partner for `current`. */
    private boolean isValidPartner(Submission current, Submission candidate, Long curDeptId) {
        if (candidate.getId().equals(current.getId())) {
            return false;
        }
        if (!isEligible(candidate) || !isDiscoverable(candidate)) {
            return false;
        }
        // Same student → not a collaboration
        if (candidate.getStudent() != null && current.getStudent() != null
                && candidate.getStudent().getId().equals(current.getStudent().getId())) {
            return false;
        }
        // Same unit → that's your classmate on the same assignment, excluded
        if (current.getUnit() != null && candidate.getUnit() != null
                && current.getUnit().getId().equals(candidate.getUnit().getId())) {
            return false;
        }
        // Already in the same project group → already collaborating
        if (current.getProjectGroup() != null && candidate.getProjectGroup() != null
                && current.getProjectGroup().getId().equals(candidate.getProjectGroup().getId())) {
            return false;
        }
        return true;
    }

    // ── Scoring ──────────────────────────────────────────────────────────────

    private double mechanicalScore(Submission current, AIInsight currentInsight,
                                   Set<String> curTechs, Set<String> curAreas, Set<String> curDomains,
                                   Long curDeptId, Submission candidate) {
        double semantic = cosine(current.getEmbedding(), candidate.getEmbedding());
        double tech = jaccard(curTechs, techNames(candidate));
        double area = jaccard(curAreas, areaNames(candidate));
        double domain = jaccard(curDomains, domainNames(candidate.getAiInsight()));

        double weighted = semantic * weights.getSemantic()
                + tech * weights.getTechnology()
                + area * weights.getResearchArea()
                + domain * weights.getProblemDomain();
        double base = weighted / weights.signalWeightTotal();

        // Cross-department pairings are the whole point — reward them.
        Long candDeptId = departmentId(candidate);
        if (curDeptId != null && candDeptId != null && !curDeptId.equals(candDeptId)) {
            base += weights.getCrossDepartmentBonus();
        }
        // Mentorship shape: the candidate is more mature (finished / senior).
        if (looksLikeMentor(current, candidate)) {
            base += weights.getMentorshipBonus();
        }
        return Math.min(base, 1.0);
    }

    /** Candidate reads as a mentor when its work is completed or its author is more senior. */
    private boolean looksLikeMentor(Submission current, Submission candidate) {
        SubmissionStatus cs = candidate.getStatus();
        boolean mature = cs == SubmissionStatus.APPROVED || cs == SubmissionStatus.FINAL
                || cs == SubmissionStatus.ARCHIVED;
        Integer currentYear = studentYear(current);
        Integer candidateYear = studentYear(candidate);
        boolean senior = currentYear != null && candidateYear != null && candidateYear > currentYear;
        return mature || senior;
    }

    // ── Persistence (canonical pair upsert) ──────────────────────────────────

    private void upsertPair(Submission current, String currentHash, Submission partner, double score) {
        Submission a = current.getId() < partner.getId() ? current : partner;
        Submission b = current.getId() < partner.getId() ? partner : current;
        String hashA = a.getId().equals(current.getId()) ? currentHash : insightHash(a);
        String hashB = b.getId().equals(current.getId()) ? currentHash : insightHash(b);

        CollaborationMatch match = matchRepository.findByPair(a, b).orElse(null);
        if (match == null) {
            match = new CollaborationMatch();
            match.setSubmissionA(a);
            match.setSubmissionB(b);
            match.setCollaborationValue(CollaborationValue.UNASSESSED);
        } else if (hashChanged(match, hashA, hashB)) {
            // Either side's analysis changed → re-queue for Stage 2.
            match.setCollaborationValue(CollaborationValue.UNASSESSED);
            match.setComputedAt(null);
        }
        match.setMechanicalScore(score);
        match.setHashA(hashA);
        match.setHashB(hashB);
        matchRepository.save(match);
    }

    private static boolean hashChanged(CollaborationMatch match, String hashA, String hashB) {
        return !safeEquals(match.getHashA(), hashA) || !safeEquals(match.getHashB(), hashB);
    }

    private void removeUnassessedRowsFor(Submission current) {
        for (CollaborationMatch existing : matchRepository.findBySubmission(current)) {
            if (!existing.isAssessed()) {
                matchRepository.delete(existing);
            }
        }
    }

    private static Submission partnerOf(CollaborationMatch match, Submission self) {
        if (match.getSubmissionA() != null && match.getSubmissionA().getId().equals(self.getId())) {
            return match.getSubmissionB();
        }
        return match.getSubmissionA();
    }

    // ── Vector / set helpers ─────────────────────────────────────────────────

    static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0.0;
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0.0;
        }
        double cos = dot / (Math.sqrt(na) * Math.sqrt(nb));
        return Math.max(0.0, cos); // clamp negatives — anti-correlation is not overlap
    }

    static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) inter.size() / union.size();
    }

    private static Set<String> techNames(Submission s) {
        Set<String> out = new LinkedHashSet<>();
        s.getTechnologies().stream().map(Technology::getName).forEach(n -> out.add(norm(n)));
        out.remove("");
        return out;
    }

    private static Set<String> areaNames(Submission s) {
        Set<String> out = new LinkedHashSet<>();
        s.getResearchAreas().stream().map(ResearchArea::getName).forEach(n -> out.add(norm(n)));
        out.remove("");
        return out;
    }

    private static Set<String> domainNames(AIInsight insight) {
        Set<String> out = new LinkedHashSet<>();
        if (insight != null && insight.getProblemDomains() != null) {
            insight.getProblemDomains().forEach(d -> out.add(norm(d)));
        }
        out.remove("");
        return out;
    }

    private static Long departmentId(Submission s) {
        return s.getUnit() != null && s.getUnit().getDepartment() != null
                ? s.getUnit().getDepartment().getId() : null;
    }

    private static Integer studentYear(Submission s) {
        return s.getStudent() != null && s.getStudent().getStudentProfile() != null
                ? s.getStudent().getStudentProfile().getCurrentYear() : null;
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean safeEquals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    /**
     * Fingerprint of a submission's analysable content — Stage 2 only re-runs
     * when this changes, so the expensive LLM call is not repeated needlessly.
     */
    static String insightHash(Submission s) {
        AIInsight insight = s.getAiInsight();
        StringBuilder sb = new StringBuilder();
        sb.append(norm(s.getTitle())).append('|');
        if (insight != null) {
            sb.append(norm(insight.getSummary())).append('|');
            sb.append(norm(insight.getProblemStatement())).append('|');
            sb.append(String.join(",", new TreeSet<>(nz(insight.getKeywords())))).append('|');
            sb.append(String.join(",", new TreeSet<>(nz(insight.getObjectives())))).append('|');
            sb.append(String.join(",", new TreeSet<>(nz(insight.getProblemDomains())))).append('|');
        }
        sb.append(String.join(",", new TreeSet<>(techNames(s)))).append('|');
        sb.append(String.join(",", new TreeSet<>(areaNames(s))));
        return sha256(sb.toString());
    }

    private static Set<String> nz(Set<String> set) {
        return set == null ? Set.of() : set;
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            return Integer.toHexString(text.hashCode());
        }
    }
}
