package com.taskhub.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.dto.request.PortfolioItemRequest;
import com.taskhub.dto.response.PortfolioItemResponse;
import com.taskhub.entity.PortfolioItem;
import com.taskhub.entity.Task;
import com.taskhub.entity.User;
import com.taskhub.enums.ReviewType;
import com.taskhub.enums.TaskStatus;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.PortfolioItemRepository;
import com.taskhub.repository.ReviewRepository;
import com.taskhub.repository.TaskRepository;
import com.taskhub.repository.UserRepository;
import com.taskhub.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioService {
    private final PortfolioItemRepository portfolioRepo;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final ReviewRepository reviewRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<PortfolioItemResponse> getMyPortfolio() {
        User current = AuthUtil.getCurrentUser();
        return portfolioRepo.findByUserIdOrderByDisplayOrder(current.getId()).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PortfolioItemResponse> getPublicPortfolio(Long userId) {
        userRepository.findById(userId).orElseThrow(() -> TaskHubException.notFound("User not found"));
        return portfolioRepo.findByUserIdAndIsPublicTrueOrderByDisplayOrder(userId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public PortfolioItemResponse createPortfolioItem(PortfolioItemRequest req) {
        User current = AuthUtil.getCurrentUser();
        final int order = portfolioRepo.findByUserIdOrderByDisplayOrder(current.getId()).size();
        final int displayOrderVal = req.getDisplayOrder() != null ? req.getDisplayOrder() : order;

        PortfolioItem item = PortfolioItem.builder()
                .user(current)
                .title(req.getTitle().trim())
                .description(req.getDescription())
                .projectUrl(req.getProjectUrl())
                .imageUrlsJson(toJson(req.getImageUrls()))
                .fileUrl(req.getFileUrl())
                .fileName(req.getFileName())
                .displayOrder(displayOrderVal)
                .isPublic(req.getIsPublic() != null ? req.getIsPublic() : true)
                .build();
        item.setCreatedBy(current.getId());
        item = portfolioRepo.save(item);
        log.info("Portfolio item created: id={}, userId={}", item.getId(), current.getId());
        return toResponse(item);
    }

    @Transactional
    public PortfolioItemResponse updatePortfolioItem(Long itemId, PortfolioItemRequest req) {
        User current = AuthUtil.getCurrentUser();
        PortfolioItem item = portfolioRepo.findById(itemId)
                .orElseThrow(() -> TaskHubException.notFound("Portfolio item not found"));

        if (!item.getUser().getId().equals(current.getId())) {
            throw TaskHubException.forbidden("Not your portfolio item");
        }

        if (req.getTitle() != null) item.setTitle(req.getTitle().trim());
        if (req.getDescription() != null) item.setDescription(req.getDescription());
        if (req.getProjectUrl() != null) item.setProjectUrl(req.getProjectUrl());
        if (req.getImageUrls() != null) item.setImageUrlsJson(toJson(req.getImageUrls()));
        if (req.getFileUrl() != null) item.setFileUrl(req.getFileUrl());
        if (req.getFileName() != null) item.setFileName(req.getFileName());
        if (req.getDisplayOrder() != null) item.setDisplayOrder(req.getDisplayOrder());
        if (req.getIsPublic() != null) item.setIsPublic(req.getIsPublic());

        item.setUpdatedBy(current.getId());
        item = portfolioRepo.save(item);
        return toResponse(item);
    }

    @Transactional
    public void deletePortfolioItem(Long itemId) {
        User current = AuthUtil.getCurrentUser();
        PortfolioItem item = portfolioRepo.findById(itemId)
                .orElseThrow(() -> TaskHubException.notFound("Portfolio item not found"));

        if (!item.getUser().getId().equals(current.getId())) {
            throw TaskHubException.forbidden("Not your portfolio item");
        }

        portfolioRepo.delete(item);
        log.info("Portfolio item deleted: id={}", itemId);
    }

    @Transactional
    public void reorderPortfolioItems(List<Long> itemIds) {
        User current = AuthUtil.getCurrentUser();
        int idx = 0;
        for (Long itemId : itemIds) {
            final int order = idx;
            PortfolioItem item = portfolioRepo.findById(itemId)
                    .orElseThrow(() -> TaskHubException.notFound("Portfolio item not found: " + itemId));
            if (!item.getUser().getId().equals(current.getId())) {
                throw TaskHubException.forbidden("Not your portfolio item");
            }
            item.setDisplayOrder(order);
            portfolioRepo.save(item);
            idx++;
        }
    }

    private PortfolioItemResponse toResponse(PortfolioItem item) {
        Long userId = item.getUser().getId();

        Double avgRating = reviewRepository.getAverageRating(userId, ReviewType.FREELANCER_TO_HIRER);
        long totalReviews = reviewRepository.countByRevieweeIdAndType(userId, ReviewType.FREELANCER_TO_HIRER);
        BigDecimal totalEarnings = taskRepository.findByAssignedToIdAndStatus(userId, TaskStatus.COMPLETED).stream()
                .map(Task::getBudget).reduce(BigDecimal.ZERO, BigDecimal::add);
        long completedTasks = taskRepository.findByAssignedToIdAndStatus(userId, TaskStatus.COMPLETED).size();

        return PortfolioItemResponse.builder()
                .id(item.getId())
                .userId(userId)
                .userFullName(item.getUser().getFullName())
                .title(item.getTitle())
                .description(item.getDescription())
                .projectUrl(item.getProjectUrl())
                .imageUrls(fromJson(item.getImageUrlsJson()))
                .fileUrl(item.getFileUrl())
                .fileName(item.getFileName())
                .displayOrder(item.getDisplayOrder())
                .isPublic(item.getIsPublic())
                .averageRating(avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : null)
                .totalReviews(totalReviews)
                .totalEarnings(totalEarnings)
                .completedTasks(completedTasks)
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        try { return objectMapper.writeValueAsString(list); }
        catch (JsonProcessingException e) { return null; }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, new TypeReference<List<String>>() {}); }
        catch (JsonProcessingException e) { return List.of(); }
    }
}