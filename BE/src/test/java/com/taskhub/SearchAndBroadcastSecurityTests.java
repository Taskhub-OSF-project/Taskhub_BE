package com.taskhub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.dto.PageRequestDto;
import com.taskhub.dto.request.BroadcastNotificationRequest;
import com.taskhub.dto.response.FreelancerSearchResponse;
import com.taskhub.entity.Task;
import com.taskhub.entity.User;
import com.taskhub.enums.ReviewType;
import com.taskhub.enums.Role;
import com.taskhub.enums.TaskStatus;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.*;
import com.taskhub.service.AnalyticsService;
import com.taskhub.service.NotificationService;
import com.taskhub.service.SearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchAndBroadcastSecurityTests {
    @Mock UserRepository userRepository;
    @Mock TaskRepository taskRepository;
    @Mock ReviewRepository reviewRepository;
    @Mock NotificationRepository notificationRepository;
    @Mock DailyMetricRepository dailyMetricRepository;
    @Mock WalletTransactionRepository walletTransactionRepository;

    @Test
    void pageRequestClampsInvalidValuesAndRejectsUnsafeSortProperty() {
        PageRequestDto request = PageRequestDto.builder()
                .page(-5).size(100_000).sortBy("hirer.password").sortDir("sideways")
                .build();

        assertThat(request.getPage()).isZero();
        assertThat(request.getSize()).isEqualTo(100);
        assertThat(request.getSortBy()).isEqualTo("id");
        assertThat(request.getSortDir()).isEqualTo("desc");
        assertThat(request.toSpringPageRequest().getSort().getOrderFor("id")).isNotNull();
    }

    @Test
    void taskSearchFiltersInDatabaseBeforePaginationAndKeepsCorrectTotals() {
        SearchService service = new SearchService(userRepository, taskRepository, reviewRepository);
        User hirer = User.builder().id(7L).fullName("Hirer").role(Role.HIRER).build();
        Task task = Task.builder().id(3L).title("Java API").description("Backend")
                .category("Web").budget(BigDecimal.TEN)
                .deadline(LocalDateTime.now().plusDays(2)).status(TaskStatus.ACTIVE)
                .hirer(hirer).build();
        var requestedPage = org.springframework.data.domain.PageRequest.of(0, 2);
        when(taskRepository.searchPublicTasks(eq(TaskStatus.ACTIVE), eq("java"), eq("Web"),
                any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(task), requestedPage, 12));

        var response = service.searchTasks("  java ", " Web ", null,
                PageRequestDto.builder().page(-2).size(2).build());

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getTotalElements()).isEqualTo(12);
        assertThat(response.getTotalPages()).isEqualTo(6);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(taskRepository).searchPublicTasks(eq(TaskStatus.ACTIVE), eq("java"), eq("Web"),
                any(LocalDateTime.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
    }

    @Test
    void freelancerSearchUsesCorrectReviewDirectionAndNeverSerializesEmail() throws Exception {
        SearchService service = new SearchService(userRepository, taskRepository, reviewRepository);
        User freelancer = User.builder().id(9L).email("private@example.com")
                .fullName("Student").role(Role.STUDENT).build();
        when(userRepository.findByRole(eq(Role.STUDENT), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(freelancer)));
        when(reviewRepository.getPublicStatsForUsers(List.of(9L), ReviewType.HIRER_TO_FREELANCER))
                .thenReturn(List.<Object[]>of(new Object[]{9L, 4.5d, 2L}));
        when(taskRepository.getAssigneeTaskStats(List.of(9L), TaskStatus.COMPLETED))
                .thenReturn(List.of());

        FreelancerSearchResponse result = service.searchFreelancers(null, new PageRequestDto())
                .getContent().get(0);

        assertThat(result.getAverageRating()).isEqualTo(4.5d);
        assertThat(new ObjectMapper().writeValueAsString(result)).doesNotContain("email");
    }

    @Test
    void broadcastCannotTargetAdministrators() {
        NotificationService service = new NotificationService(notificationRepository, userRepository);
        BroadcastNotificationRequest request = new BroadcastNotificationRequest();
        request.setTitle("Notice");
        request.setBody("Body");
        request.setTargetRole("ADMIN");

        assertThatThrownBy(() -> service.broadcast(request))
                .isInstanceOf(TaskHubException.class)
                .hasMessageContaining("ADMIN");
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void analyticsRejectsUnboundedDateRangesBeforeQueryingDatabase() {
        AnalyticsService service = new AnalyticsService(dailyMetricRepository, userRepository,
                taskRepository, reviewRepository, walletTransactionRepository);

        assertThatThrownBy(() -> service.getAnalyticsDashboard(0))
                .isInstanceOf(TaskHubException.class)
                .hasMessageContaining("between 1 and 365");
        assertThatThrownBy(() -> service.getAnalyticsDashboard(366))
                .isInstanceOf(TaskHubException.class);
        verifyNoInteractions(dailyMetricRepository, userRepository, taskRepository,
                reviewRepository, walletTransactionRepository);
    }
}
