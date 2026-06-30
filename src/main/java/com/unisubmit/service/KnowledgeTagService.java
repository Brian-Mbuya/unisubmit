package com.unisubmit.service;

import com.unisubmit.domain.*;
import com.unisubmit.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class KnowledgeTagService {

    private final TechnologyRepository technologyRepository;
    private final ResearchAreaRepository researchAreaRepository;
    private final FrameworkRepository frameworkRepository;
    private final DatabaseRepository databaseRepository;
    private final ProgrammingLanguageRepository programmingLanguageRepository;
    private final SkillRepository skillRepository;
    private final SubmissionRepository submissionRepository;

    public KnowledgeTagService(TechnologyRepository technologyRepository,
                               ResearchAreaRepository researchAreaRepository,
                               FrameworkRepository frameworkRepository,
                               DatabaseRepository databaseRepository,
                               ProgrammingLanguageRepository programmingLanguageRepository,
                               SkillRepository skillRepository,
                               SubmissionRepository submissionRepository) {
        this.technologyRepository = technologyRepository;
        this.researchAreaRepository = researchAreaRepository;
        this.frameworkRepository = frameworkRepository;
        this.databaseRepository = databaseRepository;
        this.programmingLanguageRepository = programmingLanguageRepository;
        this.skillRepository = skillRepository;
        this.submissionRepository = submissionRepository;
    }

    // ── Find Or Create ───────────────────────────────────────────────────────

    @Transactional
    public Technology findOrCreateTechnology(String name) {
        return technologyRepository.findByNameIgnoreCase(name.trim())
                .orElseGet(() -> {
                    Technology t = new Technology();
                    t.setName(name.trim());
                    return technologyRepository.save(t);
                });
    }

    @Transactional
    public ResearchArea findOrCreateResearchArea(String name) {
        return researchAreaRepository.findByNameIgnoreCase(name.trim())
                .orElseGet(() -> {
                    ResearchArea r = new ResearchArea();
                    r.setName(name.trim());
                    return researchAreaRepository.save(r);
                });
    }

    @Transactional
    public Framework findOrCreateFramework(String name) {
        return frameworkRepository.findByNameIgnoreCase(name.trim())
                .orElseGet(() -> {
                    Framework f = new Framework();
                    f.setName(name.trim());
                    return frameworkRepository.save(f);
                });
    }

    @Transactional
    public Database findOrCreateDatabase(String name) {
        return databaseRepository.findByNameIgnoreCase(name.trim())
                .orElseGet(() -> {
                    Database d = new Database();
                    d.setName(name.trim());
                    return databaseRepository.save(d);
                });
    }

    @Transactional
    public ProgrammingLanguage findOrCreateProgrammingLanguage(String name) {
        return programmingLanguageRepository.findByNameIgnoreCase(name.trim())
                .orElseGet(() -> {
                    ProgrammingLanguage p = new ProgrammingLanguage();
                    p.setName(name.trim());
                    return programmingLanguageRepository.save(p);
                });
    }

    @Transactional
    public Skill findOrCreateSkill(String name) {
        return skillRepository.findByNameIgnoreCase(name.trim())
                .orElseGet(() -> {
                    Skill s = new Skill();
                    s.setName(name.trim());
                    return skillRepository.save(s);
                });
    }

    // ── Get All ──────────────────────────────────────────────────────────────

    public List<Technology> getAllTechnologies() {
        return technologyRepository.findAllByOrderByNameAsc();
    }

    public List<ResearchArea> getAllResearchAreas() {
        return researchAreaRepository.findAllByOrderByNameAsc();
    }

    public List<Framework> getAllFrameworks() {
        return frameworkRepository.findAllByOrderByNameAsc();
    }

    public List<Database> getAllDatabases() {
        return databaseRepository.findAllByOrderByNameAsc();
    }

    public List<ProgrammingLanguage> getAllProgrammingLanguages() {
        return programmingLanguageRepository.findAllByOrderByNameAsc();
    }

    public List<Skill> getAllSkills() {
        return skillRepository.findAllByOrderByNameAsc();
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @Transactional
    public void deleteTechnology(Long id) {
        technologyRepository.deleteById(id);
    }

    @Transactional
    public void deleteResearchArea(Long id) {
        researchAreaRepository.deleteById(id);
    }

    @Transactional
    public void deleteFramework(Long id) {
        frameworkRepository.deleteById(id);
    }

    @Transactional
    public void deleteDatabase(Long id) {
        databaseRepository.deleteById(id);
    }

    @Transactional
    public void deleteProgrammingLanguage(Long id) {
        programmingLanguageRepository.deleteById(id);
    }

    @Transactional
    public void deleteSkill(Long id) {
        skillRepository.deleteById(id);
    }

    // ── Rename ───────────────────────────────────────────────────────────────

    @Transactional
    public void renameTechnology(Long id, String newName) {
        technologyRepository.findById(id).ifPresent(t -> {
            t.setName(newName.trim());
            technologyRepository.save(t);
        });
    }

    @Transactional
    public void renameResearchArea(Long id, String newName) {
        researchAreaRepository.findById(id).ifPresent(r -> {
            r.setName(newName.trim());
            researchAreaRepository.save(r);
        });
    }

    @Transactional
    public void renameFramework(Long id, String newName) {
        frameworkRepository.findById(id).ifPresent(f -> {
            f.setName(newName.trim());
            frameworkRepository.save(f);
        });
    }

    @Transactional
    public void renameDatabase(Long id, String newName) {
        databaseRepository.findById(id).ifPresent(d -> {
            d.setName(newName.trim());
            databaseRepository.save(d);
        });
    }

    @Transactional
    public void renameProgrammingLanguage(Long id, String newName) {
        programmingLanguageRepository.findById(id).ifPresent(p -> {
            p.setName(newName.trim());
            programmingLanguageRepository.save(p);
        });
    }

    @Transactional
    public void renameSkill(Long id, String newName) {
        skillRepository.findById(id).ifPresent(s -> {
            s.setName(newName.trim());
            skillRepository.save(s);
        });
    }

    // ── Merge ────────────────────────────────────────────────────────────────

    @Transactional
    public void mergeTechnologies(Long sourceId, Long targetId) {
        if (sourceId.equals(targetId)) return;
        Technology source = technologyRepository.findById(sourceId).orElse(null);
        Technology target = technologyRepository.findById(targetId).orElse(null);
        if (source == null || target == null) return;

        List<Submission> submissions = submissionRepository.findByTechnologyId(sourceId);
        for (Submission sub : submissions) {
            sub.getTechnologies().remove(source);
            sub.getTechnologies().add(target);
            submissionRepository.save(sub);
        }
        technologyRepository.delete(source);
    }

    @Transactional
    public void mergeResearchAreas(Long sourceId, Long targetId) {
        if (sourceId.equals(targetId)) return;
        ResearchArea source = researchAreaRepository.findById(sourceId).orElse(null);
        ResearchArea target = researchAreaRepository.findById(targetId).orElse(null);
        if (source == null || target == null) return;

        List<Submission> submissions = submissionRepository.findByResearchAreaId(sourceId);
        for (Submission sub : submissions) {
            sub.getResearchAreas().remove(source);
            sub.getResearchAreas().add(target);
            submissionRepository.save(sub);
        }
        researchAreaRepository.delete(source);
    }

    @Transactional
    public void mergeFrameworks(Long sourceId, Long targetId) {
        if (sourceId.equals(targetId)) return;
        Framework source = frameworkRepository.findById(sourceId).orElse(null);
        Framework target = frameworkRepository.findById(targetId).orElse(null);
        if (source == null || target == null) return;

        List<Submission> submissions = submissionRepository.findByFrameworkId(sourceId);
        for (Submission sub : submissions) {
            sub.getFrameworks().remove(source);
            sub.getFrameworks().add(target);
            submissionRepository.save(sub);
        }
        frameworkRepository.delete(source);
    }

    @Transactional
    public void mergeDatabases(Long sourceId, Long targetId) {
        if (sourceId.equals(targetId)) return;
        Database source = databaseRepository.findById(sourceId).orElse(null);
        Database target = databaseRepository.findById(targetId).orElse(null);
        if (source == null || target == null) return;

        List<Submission> submissions = submissionRepository.findByDatabaseId(sourceId);
        for (Submission sub : submissions) {
            sub.getDatabases().remove(source);
            sub.getDatabases().add(target);
            submissionRepository.save(sub);
        }
        databaseRepository.delete(source);
    }

    @Transactional
    public void mergeProgrammingLanguages(Long sourceId, Long targetId) {
        if (sourceId.equals(targetId)) return;
        ProgrammingLanguage source = programmingLanguageRepository.findById(sourceId).orElse(null);
        ProgrammingLanguage target = programmingLanguageRepository.findById(targetId).orElse(null);
        if (source == null || target == null) return;

        List<Submission> submissions = submissionRepository.findByProgrammingLanguageId(sourceId);
        for (Submission sub : submissions) {
            sub.getProgrammingLanguages().remove(source);
            sub.getProgrammingLanguages().add(target);
            submissionRepository.save(sub);
        }
        programmingLanguageRepository.delete(source);
    }

    @Transactional
    public void mergeSkills(Long sourceId, Long targetId) {
        if (sourceId.equals(targetId)) return;
        Skill source = skillRepository.findById(sourceId).orElse(null);
        Skill target = skillRepository.findById(targetId).orElse(null);
        if (source == null || target == null) return;

        List<Submission> submissions = submissionRepository.findBySkillId(sourceId);
        for (Submission sub : submissions) {
            sub.getSkills().remove(source);
            sub.getSkills().add(target);
            submissionRepository.save(sub);
        }
        skillRepository.delete(source);
    }

    @Transactional
    public void updateSubmissionTags(Long submissionId,
                                     List<Long> technologyIds,
                                     List<Long> researchAreaIds,
                                     List<Long> frameworkIds,
                                     List<Long> databaseIds,
                                     List<Long> programmingLanguageIds,
                                     List<Long> skillIds) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Submission not found: " + submissionId));

        submission.getTechnologies().clear();
        if (technologyIds != null) {
            submission.getTechnologies().addAll(technologyRepository.findAllById(technologyIds));
        }

        submission.getResearchAreas().clear();
        if (researchAreaIds != null) {
            submission.getResearchAreas().addAll(researchAreaRepository.findAllById(researchAreaIds));
        }

        submission.getFrameworks().clear();
        if (frameworkIds != null) {
            submission.getFrameworks().addAll(frameworkRepository.findAllById(frameworkIds));
        }

        submission.getDatabases().clear();
        if (databaseIds != null) {
            submission.getDatabases().addAll(databaseRepository.findAllById(databaseIds));
        }

        submission.getProgrammingLanguages().clear();
        if (programmingLanguageIds != null) {
            submission.getProgrammingLanguages().addAll(programmingLanguageRepository.findAllById(programmingLanguageIds));
        }

        submission.getSkills().clear();
        if (skillIds != null) {
            submission.getSkills().addAll(skillRepository.findAllById(skillIds));
        }

        submissionRepository.save(submission);
    }
}
