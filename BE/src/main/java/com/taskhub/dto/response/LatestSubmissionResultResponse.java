package com.taskhub.dto.response;

import com.taskhub.enums.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LatestSubmissionResultResponse {
    private Long taskId;
    private TaskStatus taskStatus;
    private SubmissionResponse latestSubmission;
    private SubmissionAIResult submissionAIResult;
    private int revisionCount;
    private RevisionRequestResponse latestRevision;
    private List<RevisionRequestResponse> revisionHistory;
}
