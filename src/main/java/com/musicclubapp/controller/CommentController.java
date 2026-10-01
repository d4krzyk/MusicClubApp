package com.musicclubapp.controller;

import com.musicclubapp.dto.CommentResponse;
import com.musicclubapp.dto.CommentsPageResponse;
import com.musicclubapp.dto.CreateCommentRequest;
import com.musicclubapp.dto.MentionHint;
import com.musicclubapp.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/** Komentarze pod postami, odpowiedzi i oznaczanie osob. */
@RestController
@RequestMapping("/api")
@Tag(name = "Komentarze", description = "Komentarze pod postami, odpowiedzi i oznaczenia (@login)")
public class CommentController {

    private static final int MAX_SIZE = 30;

    private final CommentService comments;

    public CommentController(CommentService comments) {
        this.comments = comments;
    }

    @GetMapping("/posts/{postId}/comments")
    @Operation(summary = "Komentarze pierwszego poziomu pod postem, od najnowszych")
    public ResponseEntity<CommentsPageResponse> list(@PathVariable Long postId,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "10") int size,
                                                     Authentication authentication) {
        return ResponseEntity.ok(comments.list(authentication.getName(), postId, pageOf(page, size)));
    }

    @PostMapping("/posts/{postId}/comments")
    @Operation(summary = "Dodaje komentarz albo odpowiedz (parentId)",
        description = "@login w tresci oznacza osobe, ktora widzi ten post; dostaje powiadomienie.")
    public ResponseEntity<CommentResponse> create(@PathVariable Long postId,
                                                  @Valid @RequestBody CreateCommentRequest request,
                                                  Authentication authentication) {
        CommentResponse created = comments.create(authentication.getName(), postId, request);
        return ResponseEntity.created(URI.create("/api/comments/" + created.id())).body(created);
    }

    @GetMapping("/posts/{postId}/mentionable")
    @Operation(summary = "Kogo mozna oznaczyc pod tym postem (po poczatku loginu)")
    public ResponseEntity<List<MentionHint>> mentionable(@PathVariable Long postId,
                                                         @RequestParam(name = "q", defaultValue = "") String q,
                                                         Authentication authentication) {
        return ResponseEntity.ok(comments.hints(authentication.getName(), postId, q.length() > 50 ? q.substring(0, 50) : q));
    }

    @GetMapping("/comments/{id}")
    @Operation(summary = "Jeden komentarz")
    public ResponseEntity<CommentResponse> get(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(comments.get(authentication.getName(), id));
    }

    @GetMapping("/comments/{id}/replies")
    @Operation(summary = "Odpowiedzi pod komentarzem, od najstarszej")
    public ResponseEntity<Page<CommentResponse>> replies(@PathVariable Long id,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size,
                                                         Authentication authentication) {
        return ResponseEntity.ok(comments.replies(authentication.getName(), id, pageOf(page, size)));
    }

    @DeleteMapping("/comments/{id}")
    @Operation(summary = "Usuwa komentarz (autor, autor posta, administrator, zarzad klanu)")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        comments.delete(authentication.getName(), id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private static PageRequest pageOf(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE));
    }
}
