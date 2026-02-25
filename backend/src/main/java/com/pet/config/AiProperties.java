package com.pet.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {
  @Setter
  private String provider = "mock";
  @Setter
  private Integer sessionMaxUserMessages = 5;

  private final Qwen qwen = new Qwen();
  private final DebugLog debugLog = new DebugLog();

  @Getter
  @Setter
  public static class Qwen {
    private String apiKey;
    private String model = "qwen-plus";
    private String baseUrl;
    private String systemPromptFile;
    private Integer maxTokens = 450;
    private Float temperature = 0.4f;
    private Float presencePenalty = 0.0f;
    private Float frequencyPenalty = 0.0f;
    private String systemPrompt = "你是一位宠物健康与养护助手。回答时先给结论，再给原因和建议。";
  }

  @Getter
  @Setter
  public static class DebugLog {
    private boolean enabled = true;
    private String dir = "./AIlog";
    private int previewLength = 200;
    private boolean includeFullRequest = false;
    private boolean includeFullResponse = true;
    private boolean writeOnSuccess = true;
    private boolean writeOnError = true;
  }
}
