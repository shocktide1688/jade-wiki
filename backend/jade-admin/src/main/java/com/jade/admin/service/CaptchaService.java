package com.jade.admin.service;

import com.jade.admin.dto.CaptchaResponse;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * 图形验证码服务（SVG 生成，无 AWT 依赖，JVM / Native 均可用）
 *
 * 本项目未接入 Redis：答案存进程内（TTL 120s、一次性）。
 * 单机语义：重启后已下发验证码失效、多实例需会话粘性；校验不过即拒绝，不放行。
 */
@ApplicationScoped
public class CaptchaService {

    public static final String KEY_PREFIX = "captcha:";
    /** 验证码有效期（秒） */
    private static final long TTL_SECONDS = 120;
    /** 排除易混淆字符 0O1I */
    private static final String CHAR_POOL = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 4;

    private final SecureRandom random = new SecureRandom();
    private final InMemoryTtlStore memory = new InMemoryTtlStore();

    /** 生成验证码：返回 uuid + SVG 图片（data URI） */
    public CaptchaResponse generate() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String code = randomCode();
        memory.setex(KEY_PREFIX + uuid, TTL_SECONDS, code);
        return new CaptchaResponse(uuid, render(code));
    }

    /** 校验验证码（忽略大小写，一次性：无论对错立即作废，防同一 uuid 爆破） */
    public boolean verify(String uuid, String code) {
        if (uuid == null || uuid.isBlank() || code == null || code.isBlank()) {
            return false;
        }
        String expected = memory.get(KEY_PREFIX + uuid);
        memory.del(KEY_PREFIX + uuid);
        return expected != null && expected.equalsIgnoreCase(code.trim());
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHAR_POOL.charAt(random.nextInt(CHAR_POOL.length())));
        }
        return sb.toString();
    }

    private String render(String code) {
        int width = 130;
        int height = 42;
        StringBuilder sb = new StringBuilder(512);
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(width)
                .append("\" height=\"").append(height).append("\" viewBox=\"0 0 ")
                .append(width).append(' ').append(height).append("\">");
        sb.append("<rect width=\"100%\" height=\"100%\" fill=\"#f2f4f7\"/>");
        for (int i = 0; i < 4; i++) {
            sb.append("<line x1=\"").append(rand(width)).append("\" y1=\"").append(rand(height))
                    .append("\" x2=\"").append(rand(width)).append("\" y2=\"").append(rand(height))
                    .append("\" stroke=\"").append(randomColor(150, 220)).append("\" stroke-width=\"1\"/>");
        }
        for (int i = 0; i < 20; i++) {
            sb.append("<circle cx=\"").append(rand(width)).append("\" cy=\"").append(rand(height))
                    .append("\" r=\"1\" fill=\"").append(randomColor(120, 200)).append("\"/>");
        }
        int x = 24;
        for (char c : code.toCharArray()) {
            double rotate = (random.nextDouble() - 0.5) * 40; // ±20°
            sb.append("<text x=\"").append(x).append("\" y=\"30\" font-size=\"28\" font-weight=\"bold\"")
                    .append(" font-family=\"Georgia, 'Times New Roman', sans-serif\" fill=\"")
                    .append(randomColor(20, 110))
                    .append("\" transform=\"rotate(").append(String.format("%.1f", rotate))
                    .append(' ').append(x + 8).append(" 22)\">").append(c).append("</text>");
            x += 24;
        }
        sb.append("</svg>");
        return "data:image/svg+xml;base64,"
                + Base64.getEncoder().encodeToString(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private int rand(int bound) {
        return random.nextInt(bound);
    }

    private String randomColor(int min, int max) {
        int r = min + random.nextInt(max - min);
        int g = min + random.nextInt(max - min);
        int b = min + random.nextInt(max - min);
        return String.format("#%02x%02x%02x", r, g, b);
    }
}
