package com.taskhub.service;

import com.taskhub.config.BedrockProperties;
import com.taskhub.exception.TaskHubException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ImageBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ImageFormat;
import software.amazon.awssdk.services.bedrockruntime.model.ImageSource;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BedrockAiModelClient implements AiModelClient {

    private static final String SYSTEM_PROMPT = """
            Bạn là AI Assistant cho nền tảng kết nối việc làm dành cho người trẻ, đặc biệt là
            người trẻ mới ra trường / ít kinh nghiệm đang tìm kiếm công việc phù hợp (bao gồm
            cả công việc freelance/dự án ngắn hạn có tiêu chí và deadline rõ ràng).

            Mục tiêu của nền tảng: giúp người trẻ hiểu rõ yêu cầu công việc, theo sát tiến độ,
            nộp bài đúng chuẩn, và được xử lý khiếu nại công bằng minh bạch.

            Vai trò của bạn có 4 nhiệm vụ chính, được kích hoạt theo MODE truyền vào:
            1. MODE = "TIEU_CHI"  → Kiểm tra và gợi ý tiêu chí phù hợp với công việc.
            2. MODE = "TIEN_DO"   → Kiểm tra tiến độ so với tiêu chí và deadline.
            3. MODE = "DANH_GIA"  → Kiểm tra trước khi nộp xem bài làm đã đạt tiêu chí chưa.
            4. MODE = "KHIEU_NAI" → Tiếp nhận và đánh giá khiếu nại.

            Nguyên tắc chung:
            - Luôn trả lời bằng tiếng Việt rõ ràng, thân thiện, dễ hiểu; giải thích thuật ngữ khi cần.
            - Luôn khách quan và chỉ dựa trên dữ liệu được cung cấp. Nếu thiếu dữ liệu, nêu rõ dữ liệu còn thiếu thay vì suy đoán.
            - Không đưa ra quyết định pháp lý cuối cùng. Chỉ phân tích và đề xuất; quyết định cuối cùng thuộc về con người.
            - Với bốn MODE có cấu trúc, chỉ trả về JSON hợp lệ đúng output format trong yêu cầu, không thêm markdown hay văn bản ngoài JSON.
            - Giọng văn khích lệ, xây dựng, không phán xét gay gắt.
            - Với MODE = "CHAT" hoặc tác vụ không yêu cầu JSON, trả lời tự nhiên theo format mà yêu cầu chỉ định.
            """;

    private final BedrockRuntimeClient client;
    private final BedrockProperties properties;

    @Override
    public String generate(String prompt) {
        return generate(prompt, properties.getTemperature(), properties.getMaxTokens());
    }

    @Override
    public String generate(String prompt, float temperature, int maxTokens) {
        return converse(List.of(ContentBlock.fromText(prompt)), temperature, maxTokens);
    }

    @Override
    public String generateWithImage(String prompt, byte[] imageBytes, String imageFormat,
                                    float temperature, int maxTokens) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw TaskHubException.badRequest("Image is required");
        }
        ImageFormat format = switch (imageFormat == null ? "" : imageFormat.toLowerCase()) {
            case "png" -> ImageFormat.PNG;
            case "jpg", "jpeg" -> ImageFormat.JPEG;
            case "gif" -> ImageFormat.GIF;
            case "webp" -> ImageFormat.WEBP;
            default -> throw TaskHubException.badRequest("Unsupported image format");
        };
        ImageBlock image = ImageBlock.builder()
                .format(format)
                .source(ImageSource.fromBytes(SdkBytes.fromByteArray(imageBytes)))
                .build();
        return converse(List.of(ContentBlock.fromImage(image), ContentBlock.fromText(prompt)),
                temperature, maxTokens);
    }

    private String converse(List<ContentBlock> content, float temperature, int maxTokens) {
        if (!properties.isEnabled()) {
            throw new TaskHubException("AI service is disabled (APP_BEDROCK_ENABLED=false)",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }

        try {
            Message userMessage = Message.builder()
                    .role("user")
                    .content(content)
                    .build();
            InferenceConfiguration inference = InferenceConfiguration.builder()
                    .temperature(Math.max(0.0f, Math.min(1.0f, temperature)))
                    .maxTokens(Math.max(1, maxTokens))
                    .build();
            ConverseRequest request = ConverseRequest.builder()
                    .modelId(properties.getModelId())
                    .system(SystemContentBlock.fromText(SYSTEM_PROMPT))
                    .messages(userMessage)
                    .inferenceConfig(inference)
                    .build();

            ConverseResponse response = client.converse(request);
            String text = response.output().message().content().stream()
                    .filter(block -> block.type() == ContentBlock.Type.TEXT)
                    .map(ContentBlock::text)
                    .reduce("", String::concat)
                    .trim();
            if (text.isBlank()) {
                throw new TaskHubException("Bedrock returned an empty response", HttpStatus.BAD_GATEWAY);
            }
            return text;
        } catch (TaskHubException e) {
            throw e;
        } catch (SdkClientException e) {
            log.error("Cannot call AWS Bedrock", e);
            throw new TaskHubException(
                    "AI service unavailable: check AWS credentials and Bedrock region configuration",
                    HttpStatus.SERVICE_UNAVAILABLE);
        } catch (Exception e) {
            log.error("AWS Bedrock inference failed", e);
            throw new TaskHubException("AI service error: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }
}
