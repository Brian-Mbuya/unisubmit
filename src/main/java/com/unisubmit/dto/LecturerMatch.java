package com.unisubmit.dto;

import com.unisubmit.domain.User;

import java.util.List;

/**
 * A lecturer recommended for a submission, computed by pure SQL/Java
 * aggregation over the lecturer's past review history — no LLM involved.
 *
 * @param lecturer            the recommended lecturer
 * @param sharedTechnologies  technologies from the current submission this
 *                            lecturer has reviewed before
 * @param sharedResearchAreas research areas from the current submission this
 *                            lecturer has reviewed before
 * @param reviewedCount       number of distinct submissions they have reviewed
 * @param overlapCount        distinct shared tags backing this recommendation
 * @param sameDepartment      lecturer's department matches the submission's
 * @param score               internal ranking score (higher = better match)
 */
public record LecturerMatch(
        User lecturer,
        List<String> sharedTechnologies,
        List<String> sharedResearchAreas,
        long reviewedCount,
        int overlapCount,
        boolean sameDepartment,
        double score
) {}
