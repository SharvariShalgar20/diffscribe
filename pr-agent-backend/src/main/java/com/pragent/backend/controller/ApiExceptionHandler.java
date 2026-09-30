package com.pragent.backend.controller;

import com.pragent.backend.ollama.OllamaUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

/**
 * One place mapping exceptions to HTTP statuses, instead of duplicating
 * try/catch blocks across controllers.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(OllamaUnavailableException.class)
    public ResponseEntity<String> handleOllamaUnavailable(OllamaUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }
}