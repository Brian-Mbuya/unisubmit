package com.chuka.irir.exception;

/**
 * Exception thrown when a file storage operation fails.
 *
 * This may occur during file upload, file reading, or when the
 * storage directory cannot be created or accessed.
 */
public class FileStorageException extends RuntimeException {

    public FileStorageException(String message) {
        super(message);
    }

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
