package com.pet.service.ai;

import com.pet.config.AiProperties;
import com.pet.entity.PetEntity;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AiDebugLogWriter {
  private static final Logger log = LoggerFactory.getLogger(AiDebugLogWriter.class);
  private static final DateTimeFormatter DATE_DIR_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final DateTimeFormatter FILE_TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

  private final AiProperties aiProperties;

  public AiDebugLogWriter(AiProperties aiProperties) {
    this.aiProperties = aiProperties;
  }

  public void writeSuccess(
      String traceId,
      Long userId,
      Long sessionId,
      PetEntity pet,
      String provider,
      String model,
      String requestContent,
      String systemPrompt,
      AiProvider.AiReply reply,
      long latencyMs) {
    AiProperties.DebugLog cfg = aiProperties.getDebugLog();
    if (!cfg.isEnabled() || !cfg.isWriteOnSuccess()) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    StringBuilder sb = new StringBuilder(1024);
    sb.append("=== AI CALL DEBUG LOG ===\n");
    sb.append("time=").append(now).append('\n');
    sb.append("traceId=").append(traceId).append('\n');
    sb.append("status=success\n");
    sb.append("provider=").append(nullSafe(provider)).append('\n');
    sb.append("model=").append(nullSafe(model)).append('\n');
    sb.append("userId=").append(userId).append('\n');
    sb.append("sessionId=").append(sessionId).append('\n');
    sb.append("petId=").append(pet == null ? "null" : pet.getId()).append('\n');
    sb.append('\n');
    appendRequestSection(sb, requestContent, systemPrompt, cfg);
    appendPetSection(sb, pet);
    sb.append("[PROVIDER_RESULT]\n");
    sb.append("success=true\n");
    sb.append("latencyMs=").append(latencyMs).append('\n');
    sb.append("tokens=").append(reply == null ? "null" : reply.tokens()).append('\n');
    sb.append("requestId=").append(reply == null ? "null" : nullSafe(reply.requestId())).append('\n');
    sb.append("reply.length=").append(reply == null || reply.content() == null ? 0 : reply.content().length()).append('\n');
    sb.append("reply.preview=").append(preview(reply == null ? null : reply.content(), cfg.getPreviewLength())).append('\n');
    if (cfg.isIncludeFullResponse()) {
      sb.append("reply.full=").append(nullSafe(reply == null ? null : reply.content())).append('\n');
    }
    sb.append('\n');
    sb.append("[ERROR]\nnone\n");
    writeFile(now, traceId, "send", sb.toString());
  }

  public void writeError(
      String traceId,
      Long userId,
      Long sessionId,
      PetEntity pet,
      String provider,
      String model,
      String requestContent,
      String systemPrompt,
      long latencyMs,
      Throwable error) {
    AiProperties.DebugLog cfg = aiProperties.getDebugLog();
    if (!cfg.isEnabled() || !cfg.isWriteOnError()) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    StringBuilder sb = new StringBuilder(1024);
    sb.append("=== AI CALL DEBUG LOG ===\n");
    sb.append("time=").append(now).append('\n');
    sb.append("traceId=").append(traceId).append('\n');
    sb.append("status=error\n");
    sb.append("provider=").append(nullSafe(provider)).append('\n');
    sb.append("model=").append(nullSafe(model)).append('\n');
    sb.append("userId=").append(userId).append('\n');
    sb.append("sessionId=").append(sessionId).append('\n');
    sb.append("petId=").append(pet == null ? "null" : pet.getId()).append('\n');
    sb.append('\n');
    appendRequestSection(sb, requestContent, systemPrompt, cfg);
    appendPetSection(sb, pet);
    sb.append("[PROVIDER_RESULT]\n");
    sb.append("success=false\n");
    sb.append("latencyMs=").append(latencyMs).append('\n');
    sb.append('\n');
    sb.append("[ERROR]\n");
    sb.append("type=").append(error == null ? "null" : error.getClass().getName()).append('\n');
    sb.append("message=").append(preview(error == null ? null : error.getMessage(), cfg.getPreviewLength())).append('\n');
    sb.append("message.full=").append(nullSafe(error == null ? null : error.getMessage())).append('\n');
    Throwable root = rootCause(error);
    sb.append("root.type=").append(root == null ? "null" : root.getClass().getName()).append('\n');
    sb.append("root.message=").append(preview(root == null ? null : root.getMessage(), cfg.getPreviewLength())).append('\n');
    sb.append("root.message.full=").append(nullSafe(root == null ? null : root.getMessage())).append('\n');
    appendReflectiveErrorField(sb, error, "requestId");
    appendReflectiveErrorField(sb, root, "requestId");
    appendReflectiveErrorField(sb, error, "statusCode");
    appendReflectiveErrorField(sb, root, "statusCode");
    appendReflectiveErrorField(sb, error, "code");
    appendReflectiveErrorField(sb, root, "code");
    sb.append("cause.chain=").append(buildCauseChain(error, cfg.getPreviewLength())).append('\n');
    sb.append("stackTrace=\n").append(stackTrace(error)).append('\n');
    writeFile(now, traceId, "send_error", sb.toString());
  }

  private void appendRequestSection(StringBuilder sb, String requestContent, String systemPrompt, AiProperties.DebugLog cfg) {
    sb.append("[REQUEST]\n");
    sb.append("message.length=").append(requestContent == null ? 0 : requestContent.length()).append('\n');
    sb.append("message.preview=").append(preview(requestContent, cfg.getPreviewLength())).append('\n');
    sb.append("systemPrompt.preview=").append(preview(systemPrompt, cfg.getPreviewLength())).append('\n');
    if (cfg.isIncludeFullRequest()) {
      sb.append("message.full=").append(nullSafe(requestContent)).append('\n');
      sb.append("systemPrompt.full=").append(nullSafe(systemPrompt)).append('\n');
    }
    sb.append('\n');
  }

  private void appendPetSection(StringBuilder sb, PetEntity pet) {
    sb.append("[PET_CONTEXT]\n");
    if (pet == null) {
      sb.append("none\n\n");
      return;
    }
    sb.append("id=").append(pet.getId()).append('\n');
    sb.append("name=").append(nullSafe(pet.getName())).append('\n');
    sb.append("breed=").append(nullSafe(pet.getBreed())).append('\n');
    sb.append("gender=").append(pet.getGender()).append('\n');
    sb.append("birthday=").append(pet.getBirthday()).append('\n');
    sb.append("weightKg=").append(pet.getWeightKg()).append('\n');
    sb.append('\n');
  }

  private void writeFile(LocalDateTime now, String traceId, String stage, String content) {
    AiProperties.DebugLog cfg = aiProperties.getDebugLog();
    try {
      Path baseDir = Path.of(cfg.getDir());
      Path dateDir = baseDir.resolve(now.format(DATE_DIR_FMT));
      Files.createDirectories(dateDir);
      String fileName = now.format(FILE_TS_FMT) + "_" + sanitize(traceId) + "_" + stage + ".txt";
      Files.writeString(dateDir.resolve(fileName), content, StandardCharsets.UTF_8);
    } catch (IOException e) {
      log.warn("Failed to write AI debug log file: {}", e.getMessage());
    }
  }

  private String preview(String text, int maxLen) {
    if (text == null) {
      return "null";
    }
    String normalized = text.replace("\r", "\\r").replace("\n", "\\n");
    if (maxLen <= 0 || normalized.length() <= maxLen) {
      return normalized;
    }
    return normalized.substring(0, maxLen) + "...";
  }

  private String sanitize(String value) {
    if (value == null || value.isBlank()) {
      return "no-trace";
    }
    return value.replaceAll("[^a-zA-Z0-9_-]", "_");
  }

  private String nullSafe(String value) {
    return value == null ? "null" : value;
  }

  private Throwable rootCause(Throwable error) {
    if (error == null) {
      return null;
    }
    Throwable current = error;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    return current;
  }

  private String buildCauseChain(Throwable error, int previewLength) {
    if (error == null) {
      return "null";
    }
    StringBuilder sb = new StringBuilder();
    Throwable current = error;
    int depth = 0;
    while (current != null && depth < 8) {
      if (depth > 0) {
        sb.append(" <- ");
      }
      sb.append(current.getClass().getSimpleName())
          .append('(')
          .append(preview(current.getMessage(), previewLength))
          .append(')');
      current = current.getCause();
      depth++;
    }
    if (current != null) {
      sb.append(" <- ...");
    }
    return sb.toString();
  }

  private String stackTrace(Throwable error) {
    if (error == null) {
      return "null";
    }
    StringWriter sw = new StringWriter(1024);
    PrintWriter pw = new PrintWriter(sw);
    error.printStackTrace(pw);
    pw.flush();
    return sw.toString();
  }

  private void appendReflectiveErrorField(StringBuilder sb, Throwable error, String fieldName) {
    Object value = invokeNoArgGetter(error, fieldName);
    if (value == null) {
      return;
    }
    sb.append("error.").append(fieldName).append('=').append(String.valueOf(value)).append('\n');
  }

  private Object invokeNoArgGetter(Object target, String fieldName) {
    if (target == null || fieldName == null || fieldName.isBlank()) {
      return null;
    }
    String suffix = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    String[] methodNames = new String[] { "get" + suffix, fieldName };
    for (String methodName : methodNames) {
      try {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
      } catch (Exception ignored) {
        // Best-effort reflective extraction for third-party exceptions.
      }
    }
    return null;
  }
}
