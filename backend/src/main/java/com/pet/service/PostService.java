package com.pet.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pet.api.post.dto.PostCreateRequest;
import com.pet.api.post.dto.PostAuthorSummary;
import com.pet.api.post.dto.PostCommentCreateRequest;
import com.pet.api.post.dto.PostCommentResponse;
import com.pet.api.post.dto.PostDetailResponse;
import com.pet.api.post.dto.PostLikeResponse;
import com.pet.api.post.dto.PostPetSummary;
import com.pet.api.post.dto.PostResponse;
import com.pet.api.error.ApiError;
import com.pet.api.error.BusinessException;
import com.pet.entity.PetEntity;
import com.pet.entity.PostCommentEntity;
import com.pet.entity.PostEntity;
import com.pet.entity.PostLikeEntity;
import com.pet.entity.PostMediaEntity;
import com.pet.entity.UserEntity;
import com.pet.repository.PetRepository;
import com.pet.repository.PostCommentRepository;
import com.pet.repository.PostLikeRepository;
import com.pet.repository.PostMediaRepository;
import com.pet.repository.PostRepository;
import com.pet.repository.UserRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {
  private final PostRepository postRepository;
  private final PostMediaRepository postMediaRepository;
  private final PostLikeRepository postLikeRepository;
  private final PostCommentRepository postCommentRepository;
  private final PetRepository petRepository;
  private final UserRepository userRepository;
  private final ObjectMapper objectMapper;

  public PostService(PostRepository postRepository, PostMediaRepository postMediaRepository,
      PostLikeRepository postLikeRepository, PostCommentRepository postCommentRepository,
      PetRepository petRepository, UserRepository userRepository, ObjectMapper objectMapper) {
    this.postRepository = postRepository;
    this.postMediaRepository = postMediaRepository;
    this.postLikeRepository = postLikeRepository;
    this.postCommentRepository = postCommentRepository;
    this.petRepository = petRepository;
    this.userRepository = userRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public PostResponse createPost(Long userId, PostCreateRequest request) {
    if (request.petId() != null) {
      petRepository.findByIdAndUserId(request.petId(), userId)
          .orElseThrow(() -> new BusinessException(ApiError.PET_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    PostEntity post = new PostEntity();
    post.setUserId(userId);
    post.setPetId(request.petId());
    post.setContent(request.content());
    post.setCity(request.city());
    post.setTagsJson(toJson(request.tags()));
    post.setStatus("PUBLISHED");
    post.setLikeCount(0);
    post.setCommentCount(0);
    PostEntity saved = postRepository.save(post);

    List<String> urls = request.mediaUrls() == null ? Collections.emptyList() : request.mediaUrls();
    if (!urls.isEmpty()) {
      List<PostMediaEntity> mediaEntities = new ArrayList<>();
      for (int i = 0; i < urls.size(); i++) {
        String url = urls.get(i);
        if (url == null || url.isBlank()) {
          continue;
        }
        PostMediaEntity media = new PostMediaEntity();
        media.setPostId(saved.getId());
        media.setMediaType("IMAGE");
        media.setUrl(url);
        media.setSortOrder(i);
        mediaEntities.add(media);
      }
      if (!mediaEntities.isEmpty()) {
        postMediaRepository.saveAll(mediaEntities);
      }
    }

    return toResponse(saved, urls);
  }

  @Transactional(readOnly = true)
  public Page<PostResponse> listPosts(Long petId, int page, int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<PostEntity> postPage = petId == null
        ? postRepository.findAll(pageable)
        : postRepository.findByPetIdOrderByCreatedAtDesc(petId, pageable);

    List<Long> postIds = postPage.getContent().stream().map(PostEntity::getId).toList();
    Map<Long, List<String>> mediaMap = loadMediaMap(postIds);

    return postPage.map(post -> toResponse(post, mediaMap.getOrDefault(post.getId(), Collections.emptyList())));
  }

  @Transactional(readOnly = true)
  public Page<PostResponse> listMyPosts(Long userId, int page, int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<PostEntity> postPage = postRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

    List<Long> postIds = postPage.getContent().stream().map(PostEntity::getId).toList();
    Map<Long, List<String>> mediaMap = loadMediaMap(postIds);

    return postPage.map(post -> toResponse(post, mediaMap.getOrDefault(post.getId(), Collections.emptyList())));
  }

  @Transactional(readOnly = true)
  public PostResponse getPost(Long postId) {
    PostEntity post = requirePost(postId);
    Map<Long, List<String>> mediaMap = loadMediaMap(List.of(post.getId()));
    return toResponse(post, mediaMap.getOrDefault(post.getId(), Collections.emptyList()));
  }

  @Transactional(readOnly = true)
  public PostDetailResponse getPostDetail(Long userId, Long postId) {
    PostEntity post = requirePost(postId);
    Map<Long, List<String>> mediaMap = loadMediaMap(List.of(post.getId()));
    UserEntity author = userRepository.findById(post.getUserId()).orElse(null);
    PetEntity pet = post.getPetId() == null ? null : petRepository.findById(post.getPetId()).orElse(null);
    boolean likedByMe = userId != null && postLikeRepository.existsByPostIdAndUserId(postId, userId);
    boolean canDelete = userId != null && userId.equals(post.getUserId());
    return new PostDetailResponse(
        post.getId(),
        post.getUserId(),
        post.getPetId(),
        post.getContent(),
        post.getCity(),
        fromJson(post.getTagsJson()),
        post.getLikeCount(),
        post.getCommentCount(),
        post.getCreatedAt(),
        mediaMap.getOrDefault(post.getId(), Collections.emptyList()),
        likedByMe,
        canDelete,
        toAuthor(author),
        toPet(pet)
    );
  }

  @Transactional
  public PostLikeResponse likePost(Long userId, Long postId) {
    PostEntity post = requirePost(postId);
    if (!postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
      PostLikeEntity like = new PostLikeEntity();
      like.setPostId(postId);
      like.setUserId(userId);
      postLikeRepository.save(like);
      refreshPostCounters(post);
    }
    return new PostLikeResponse(true, post.getLikeCount() == null ? 0 : post.getLikeCount());
  }

  @Transactional
  public PostLikeResponse unlikePost(Long userId, Long postId) {
    PostEntity post = requirePost(postId);
    long deleted = postLikeRepository.deleteByPostIdAndUserId(postId, userId);
    if (deleted > 0) {
      refreshPostCounters(post);
    }
    return new PostLikeResponse(false, post.getLikeCount() == null ? 0 : post.getLikeCount());
  }

  @Transactional(readOnly = true)
  public List<PostCommentResponse> listComments(Long postId) {
    requirePost(postId);
    List<PostCommentEntity> comments = postCommentRepository.findByPostIdOrderByCreatedAtAsc(postId);
    if (comments.isEmpty()) {
      return Collections.emptyList();
    }
    List<Long> userIds = comments.stream().map(PostCommentEntity::getUserId).distinct().toList();
    Map<Long, UserEntity> users = new HashMap<>();
    for (UserEntity user : userRepository.findAllById(userIds)) {
      users.put(user.getId(), user);
    }
    List<PostCommentResponse> result = new ArrayList<>();
    for (PostCommentEntity comment : comments) {
      result.add(new PostCommentResponse(
          comment.getId(),
          comment.getPostId(),
          comment.getUserId(),
          comment.getContent(),
          comment.getCreatedAt(),
          toAuthor(users.get(comment.getUserId()))));
    }
    return result;
  }

  @Transactional(readOnly = true)
  public List<PostAuthorSummary> listLikes(Long postId) {
    requirePost(postId);
    List<PostLikeEntity> likes = postLikeRepository.findByPostIdOrderByCreatedAtDesc(postId);
    if (likes.isEmpty()) {
      return Collections.emptyList();
    }
    List<Long> userIds = likes.stream().map(PostLikeEntity::getUserId).distinct().toList();
    Map<Long, UserEntity> users = new HashMap<>();
    for (UserEntity user : userRepository.findAllById(userIds)) {
      users.put(user.getId(), user);
    }
    List<PostAuthorSummary> result = new ArrayList<>();
    for (PostLikeEntity like : likes) {
      UserEntity user = users.get(like.getUserId());
      if (user != null) {
        result.add(toAuthor(user));
      }
    }
    return result;
  }

  @Transactional
  public PostCommentResponse createComment(Long userId, Long postId, PostCommentCreateRequest request) {
    PostEntity post = requirePost(postId);
    PostCommentEntity comment = new PostCommentEntity();
    comment.setPostId(postId);
    comment.setUserId(userId);
    comment.setContent(request.content().trim());
    PostCommentEntity saved = postCommentRepository.save(comment);
    refreshPostCounters(post);
    UserEntity author = userRepository.findById(userId).orElse(null);
    return new PostCommentResponse(
        saved.getId(),
        saved.getPostId(),
        saved.getUserId(),
        saved.getContent(),
        saved.getCreatedAt(),
        toAuthor(author));
  }

  @Transactional
  public void deleteComment(Long userId, Long postId, Long commentId) {
    PostEntity post = requirePost(postId);
    PostCommentEntity comment = postCommentRepository.findByIdAndPostId(commentId, postId)
        .orElseThrow(() -> new BusinessException(ApiError.VALIDATION_FAILED, HttpStatus.NOT_FOUND));
    if (!userId.equals(comment.getUserId())) {
      throw new BusinessException(ApiError.FORBIDDEN, HttpStatus.FORBIDDEN);
    }
    postCommentRepository.delete(comment);
    refreshPostCounters(post);
  }

  @Transactional
  public void deletePost(Long userId, Long postId) {
    PostEntity post = requirePost(postId);
    if (!userId.equals(post.getUserId())) {
      throw new BusinessException(ApiError.FORBIDDEN, HttpStatus.FORBIDDEN);
    }
    postMediaRepository.deleteByPostId(postId);
    postLikeRepository.deleteByPostId(postId);
    postCommentRepository.deleteByPostId(postId);
    postRepository.delete(post);
  }

  private PostEntity requirePost(Long postId) {
    return postRepository.findById(postId)
        .orElseThrow(() -> new BusinessException(ApiError.VALIDATION_FAILED, HttpStatus.NOT_FOUND));
  }

  private void refreshPostCounters(PostEntity post) {
    post.setLikeCount((int) postLikeRepository.countByPostId(post.getId()));
    post.setCommentCount((int) postCommentRepository.countByPostId(post.getId()));
    postRepository.save(post);
  }

  private Map<Long, List<String>> loadMediaMap(List<Long> postIds) {
    if (postIds.isEmpty()) {
      return Collections.emptyMap();
    }
    List<PostMediaEntity> media = postMediaRepository.findByPostIdInOrderByPostIdAscSortOrderAsc(postIds);
    Map<Long, List<String>> map = new HashMap<>();
    for (PostMediaEntity m : media) {
      map.computeIfAbsent(m.getPostId(), k -> new ArrayList<>()).add(m.getUrl());
    }
    return map;
  }

  private PostResponse toResponse(PostEntity post, List<String> mediaUrls) {
    return new PostResponse(
        post.getId(),
        post.getUserId(),
        post.getPetId(),
        post.getContent(),
        post.getCity(),
        fromJson(post.getTagsJson()),
        post.getLikeCount(),
        post.getCommentCount(),
        post.getCreatedAt(),
        mediaUrls
    );
  }

  private PostAuthorSummary toAuthor(UserEntity user) {
    if (user == null) {
      return null;
    }
    return new PostAuthorSummary(user.getId(), user.getUsername(), user.getNickname(), user.getAvatarUrl());
  }

  private PostPetSummary toPet(PetEntity pet) {
    if (pet == null) {
      return null;
    }
    return new PostPetSummary(pet.getId(), pet.getName(), pet.getBreed(), pet.getAvatarUrl());
  }

  private String toJson(List<String> tags) {
    if (tags == null || tags.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(tags);
    } catch (Exception ex) {
      throw new BusinessException(ApiError.VALIDATION_FAILED, HttpStatus.BAD_REQUEST);
    }
  }

  private List<String> fromJson(String tagsJson) {
    if (tagsJson == null || tagsJson.isBlank()) {
      return Collections.emptyList();
    }
    try {
      return objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {});
    } catch (Exception ex) {
      return Collections.emptyList();
    }
  }
}
