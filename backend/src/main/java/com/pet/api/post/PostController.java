package com.pet.api.post;

import com.pet.api.post.dto.PostCommentCreateRequest;
import com.pet.api.post.dto.PostCommentResponse;
import com.pet.api.post.dto.PostCreateRequest;
import com.pet.api.post.dto.PostDetailResponse;
import com.pet.api.post.dto.PostLikeResponse;
import com.pet.api.post.dto.PostResponse;
import com.pet.service.PostService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PostController {
  private final PostService postService;

  public PostController(PostService postService) {
    this.postService = postService;
  }

  @PostMapping("/posts")
  public PostResponse create(@Valid @RequestBody PostCreateRequest request, Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    return postService.createPost(userId, request);
  }

  @GetMapping("/posts")
  public Page<PostResponse> list(
      @RequestParam(value = "petId", required = false) Long petId,
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "10") int size) {
    return postService.listPosts(petId, page, size);
  }

  @GetMapping("/posts/{id}")
  public PostDetailResponse detail(@PathVariable("id") Long id, Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    return postService.getPostDetail(userId, id);
  }

  @PostMapping("/posts/{id}/like")
  public PostLikeResponse like(@PathVariable("id") Long id, Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    return postService.likePost(userId, id);
  }

  @DeleteMapping("/posts/{id}/like")
  public PostLikeResponse unlike(@PathVariable("id") Long id, Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    return postService.unlikePost(userId, id);
  }

  @GetMapping("/posts/{id}/comments")
  public List<PostCommentResponse> comments(@PathVariable("id") Long id) {
    return postService.listComments(id);
  }

  @GetMapping("/posts/{id}/likes")
  public List<com.pet.api.post.dto.PostAuthorSummary> likes(@PathVariable("id") Long id) {
    return postService.listLikes(id);
  }

  @PostMapping("/posts/{id}/comments")
  public PostCommentResponse createComment(
      @PathVariable("id") Long id,
      @Valid @RequestBody PostCommentCreateRequest request,
      Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    return postService.createComment(userId, id, request);
  }

  @DeleteMapping("/posts/{postId}/comments/{commentId}")
  public void deleteComment(
      @PathVariable("postId") Long postId,
      @PathVariable("commentId") Long commentId,
      Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    postService.deleteComment(userId, postId, commentId);
  }

  @DeleteMapping("/posts/{id}")
  public void delete(@PathVariable("id") Long id, Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    postService.deletePost(userId, id);
  }

  @GetMapping("/me/posts")
  public Page<PostResponse> listMine(
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "10") int size,
      Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    return postService.listMyPosts(userId, page, size);
  }
}
