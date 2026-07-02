package com.unisubmit.controller;

import com.unisubmit.domain.SubmissionVersion;
import com.unisubmit.security.CustomUserDetails;
import com.unisubmit.service.SubmissionAccessService;
import com.unisubmit.service.FileStorageService;
import com.unisubmit.repository.SubmissionVersionRepository;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class FileController {

    private final FileStorageService fileStorageService;
    private final SubmissionVersionRepository submissionVersionRepository;
    private final SubmissionAccessService submissionAccessService;

    public FileController(FileStorageService fileStorageService,
                          SubmissionVersionRepository submissionVersionRepository,
                          SubmissionAccessService submissionAccessService) {
        this.fileStorageService = fileStorageService;
        this.submissionVersionRepository = submissionVersionRepository;
        this.submissionAccessService = submissionAccessService;
    }

    @GetMapping("/files/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(@AuthenticationPrincipal CustomUserDetails userDetails,
                                             @PathVariable String filename) {
        SubmissionVersion version = submissionVersionRepository.findByFilePath(filename)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));

        if (userDetails == null || !submissionAccessService.canAccessSubmissionFile(userDetails.getUser(), version.getSubmission())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to access this file");
        }

        Resource file;
        try {
            file = fileStorageService.loadFileAsResource(filename);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Uploaded file is no longer available");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + version.getOriginalFileName() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, version.getFileType())
                .body(file);
    }
}
