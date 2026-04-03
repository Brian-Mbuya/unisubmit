package com.chuka.irir.service;

import com.chuka.irir.exception.FileStorageException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Handles storage of uploaded project files.
 */
@Service
public class FileStorageService {

    private static final Logger logger = LoggerFactory.getLogger(FileStorageService.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "zip");

    private final Path uploadRoot;

    public FileStorageService(@Value("${app.upload.dir}") String uploadDir) {
        this.uploadRoot = Path.of(Objects.requireNonNull(uploadDir, "Upload directory must be configured"))
                .toAbsolutePath()
                .normalize();
        initStorage();
    }

    private void initStorage() {
        try {
            Files.createDirectories(uploadRoot);
        } catch (IOException ex) {
            throw new FileStorageException("Failed to create upload directory: " + uploadRoot, ex);
        }
    }

    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("Uploaded file is empty.");
        }

        String originalFilename = file.getOriginalFilename();
        String originalName = Objects.requireNonNull(StringUtils.cleanPath(originalFilename == null ? "" : originalFilename));
        if (originalName.isBlank()) {
            throw new FileStorageException("Uploaded file name is invalid.");
        }

        String extension = getExtension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new FileStorageException("Unsupported file type. Allowed: PDF, DOCX, ZIP.");
        }

        String storedFileName = UUID.randomUUID() + "." + extension;
        Path targetPath = uploadRoot.resolve(storedFileName).normalize();

        if (!targetPath.startsWith(uploadRoot)) {
            throw new FileStorageException("Invalid file path.");
        }

        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new FileStorageException("Failed to store file: " + originalName, ex);
        }

        String extractedText = extractTextSafely(targetPath);

        return new StoredFile(
                originalName,
                file.getContentType() == null ? "application/octet-stream" : file.getContentType(),
                file.getSize(),
                storedFileName,
                extractedText
        );
    }

    public Resource loadAsResource(String storagePath) {
        try {
            Path filePath = uploadRoot.resolve(storagePath).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new FileStorageException("File not found: " + storagePath);
        } catch (MalformedURLException ex) {
            throw new FileStorageException("File path is invalid: " + storagePath, ex);
        }
    }

    public String extractTextFromStoredFile(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return "";
        }
        Path filePath = uploadRoot.resolve(storagePath).normalize();
        if (!filePath.startsWith(uploadRoot)) {
            throw new FileStorageException("Invalid file path.");
        }
        return extractTextSafely(filePath);
    }

    private String extractTextSafely(Path filePath) {
        try {
            if (!Files.exists(filePath)) {
                return "";
            }

            String extension = getExtension(filePath.getFileName().toString());
            if ("zip".equals(extension)) {
                return "Archive submission: " + filePath.getFileName();
            }
            if ("docx".equals(extension)) {
                return extractDocx(filePath);
            }
            if ("pdf".equals(extension)) {
                return extractPdf(filePath);
            }
            return "";
        } catch (IOException | RuntimeException ex) {
            logger.warn("Could not extract text from {}", filePath.getFileName(), ex);
            return "";
        }
    }

    private String extractDocx(Path filePath) throws IOException {
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(filePath));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return normalizeExtractedText(extractor.getText());
        }
    }

    private String extractPdf(Path filePath) throws IOException {
        try (PDDocument document = PDDocument.load(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return normalizeExtractedText(stripper.getText(document));
        }
    }

    private String normalizeExtractedText(String extracted) {
        if (extracted == null) {
            return "";
        }
        return extracted.replaceAll("\\s+", " ").trim();
    }

    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ENGLISH);
    }

    public record StoredFile(
            String originalName,
            String contentType,
            long size,
            String storagePath,
            String extractedText) {
    }
}
