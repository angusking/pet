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

  /**
   * backend 现在只负责通过 HTTP 调用 AIService。
   *
   * <p>旧的 Qwen 直连配置已经移除，避免让人误以为 backend 仍然支持直接连模型。
   */
  private final AiService aiService = new AiService();

  @Getter
  @Setter
  public static class AiService {
    private String baseUrl = "http://localhost:8001";
    private String modelName = "aiservice";
  }
}
