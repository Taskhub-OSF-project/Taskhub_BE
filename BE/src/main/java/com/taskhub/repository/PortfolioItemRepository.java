package com.taskhub.repository;

import com.taskhub.entity.PortfolioItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PortfolioItemRepository extends JpaRepository<PortfolioItem, Long> {
    List<PortfolioItem> findByUserIdOrderByDisplayOrder(Long userId);
    List<PortfolioItem> findByUserIdAndIsPublicTrueOrderByDisplayOrder(Long userId);
    long countByUserId(Long userId);
}
