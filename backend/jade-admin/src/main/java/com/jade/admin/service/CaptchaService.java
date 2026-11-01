package com.jade.admin.service;

import com.jade.admin.dto.CaptchaResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.Deflater;
import java.util.zip.CRC32;

/**
 * 图形验证码服务（PNG 位图渲染，无 AWT 依赖，JVM / Native 均可用）
 *
 * 防识别设计（不依赖 OCR 难度以外的任何假设）：
 *  - 本项目未接 Redis：答案存进程内（TTL 120s、一次性、GETDEL 原子消费）
 *  - 答案不出现在任何标记里：5x7 位图字形逐像素渲染为 PNG（SVG text 明文方案已废弃，
 *    机器人解码标记即可零成本识别）
 *  - 干扰：字符随机旋转 ±18°、正弦波行扰动、噪点 + 干扰线、随机配色
 *  - TTL 120s、忽略大小写；调用方负责对获取接口做 IP 限流
 *  - 单机语义：重启归零、多实例需会话粘性；校验不过即拒绝，不放行
 */
@ApplicationScoped
public class CaptchaService {

    private static final Logger LOG = Logger.getLogger(CaptchaService.class);

    public static final String KEY_PREFIX = "captcha:";
    /** 验证码有效期（秒） */
    private static final long TTL_SECONDS = 120;
    /** 排除易混淆字符 0O1I */
    private static final String CHAR_POOL = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 4;

    // ---- 5x7 位图字体（行序自上而下，'1'=着色；仅覆盖 CHAR_POOL） ----
    private static final String FONT =
            "A=01110,10001,10001,11111,10001,10001,10001;B=11110,10001,10001,11110,10001,10001,11110;" +
            "C=01110,10001,10000,10000,10000,10001,01110;D=11110,10001,10001,10001,10001,10001,11110;" +
            "E=11111,10000,10000,11110,10000,10000,11111;F=11111,10000,10000,11110,10000,10000,10000;" +
            "G=01110,10001,10000,10111,10001,10001,01111;H=10001,10001,10001,11111,10001,10001,10001;" +
            "J=00111,00010,00010,00010,00010,10010,01100;K=10001,10010,10100,11000,10100,10010,10001;" +
            "L=10000,10000,10000,10000,10000,10000,11111;M=10001,11011,10101,10101,10001,10001,10001;" +
            "N=10001,11001,10101,10011,10001,10001,10001;P=11110,10001,10001,11110,10000,10000,10000;" +
            "Q=01110,10001,10001,10001,10101,10010,01101;R=11110,10001,10001,11110,10100,10010,10001;" +
            "S=01111,10000,10000,01110,00001,00001,11110;T=11111,00100,00100,00100,00100,00100,00100;" +
            "U=10001,10001,10001,10001,10001,10001,01110;V=10001,10001,10001,10001,10001,01010,00100;" +
            "W=10001,10001,10001,10101,10101,11011,10001;X=10001,10001,01010,00100,01010,10001,10001;" +
            "Y=10001,10001,01010,00100,00100,00100,00100;Z=11111,00001,00010,00100,01000,10000,11111;" +
            "2=01110,10001,00001,00010,00100,01000,11111;3=11111,00010,00100,00010,00001,10001,01110;" +
            "4=00010,00110,01010,10010,11111,00010,00010;5=11111,10000,11110,00001,00001,10001,01110;" +
            "6=00110,01000,10000,11110,10001,10001,01110;7=11111,00001,00010,00100,01000,01000,01000;" +
            "8=01110,10001,10001,01110,10001,10001,01110;9=01110,10001,10001,01111,00001,00010,01100";

    private static final java.util.Map<Character, boolean[][]> GLYPHS = new java.util.HashMap<>();
    static {
        for (String entry : FONT.split(";")) {
            int eq = entry.indexOf('=');
            char ch = entry.charAt(0);
            String[] rows = entry.substring(eq + 1).split(",");
            boolean[][] g = new boolean[7][5];
            for (int r = 0; r < 7; r++) {
                for (int c = 0; c < 5; c++) {
                    g[r][c] = rows[r].charAt(c) == '1';
                }
            }
            GLYPHS.put(ch, g);
        }
    }

    private final InMemoryTtlStore memory = new InMemoryTtlStore();

    /** IP 限流（进程内固定窗口）：30 次/分钟，防刷图/资源滥用 */
    public boolean allowIssue(String ip) {
        return memory.increment("captchalimit:" + (ip == null ? "" : ip), 60) <= 30;
    }

    /** 生成验证码：返回 uuid + PNG 图片（data URI） */
    public CaptchaResponse generate() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String code = randomCode();
        memory.setex(KEY_PREFIX + uuid, TTL_SECONDS, code);
        return new CaptchaResponse(uuid, render(code));
    }

    /**
     * 校验验证码（忽略大小写）。GETDEL 原子消费：无论对错立即作废，并发提交同一
     * uuid 只能通过一次；Redis 查不到（含降级期）再看进程内副本。
     */
    public boolean verify(String uuid, String code) {
        if (uuid == null || uuid.isBlank() || code == null || code.isBlank()) {
            return false;
        }
        String expected = memory.getdel(KEY_PREFIX + uuid); // 原子消费, 并发不可复用
        return expected != null && expected.equalsIgnoreCase(code.trim());
    }

    /**
     * 仅供自动化测试：读取未消费验证码的答案（不删除/不作废）。
     * 生产代码禁止调用。
     */
    public String peekAnswer(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return null;
        }
        return memory.get(KEY_PREFIX + uuid);
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHAR_POOL.charAt(ThreadLocalRandom.current().nextInt(CHAR_POOL.length())));
        }
        return sb.toString();
    }

    // ==================== PNG 渲染 ====================

    private static final int W = 132;
    private static final int H = 42;
    private static final int SCALE = 4;           // 字形放大倍数 → 20x28
    private static final int BG = 0xFFF2F4F7;

    /** 渲染验证码为 PNG data URI：位图字形 + 随机旋转 + 波形扰动 + 噪声 */
    private String render(String code) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int[] px = new int[W * H];
        java.util.Arrays.fill(px, BG);

        // 干扰线（画在字符下层）
        for (int i = 0; i < 3; i++) {
            drawLine(px, rnd.nextInt(W), rnd.nextInt(H), rnd.nextInt(W), rnd.nextInt(H),
                    rgb(rnd.nextInt(150, 210), rnd.nextInt(150, 210), rnd.nextInt(150, 210)));
        }

        // 字符：随机暗色、±18° 旋转、正弦波扰动
        int cellW = (W - 16) / CODE_LENGTH;
        for (int i = 0; i < CODE_LENGTH; i++) {
            boolean[][] g = GLYPHS.get(code.charAt(i));
            int gw = 5 * SCALE, gh = 7 * SCALE;
            int x0 = 8 + i * cellW + (cellW - gw) / 2;
            int y0 = (H - gh) / 2;
            double angle = Math.toRadians(rnd.nextDouble(-18, 18));
            double sin = Math.sin(angle), cos = Math.cos(angle);
            int color = rgb(rnd.nextInt(20, 110), rnd.nextInt(20, 110), rnd.nextInt(20, 110));
            double waveAmp = 1.5 + rnd.nextDouble(1.5);
            double wavePhase = rnd.nextDouble(Math.PI * 2);

            int cx = x0 + gw / 2, cy = y0 + gh / 2;
            for (int py = Math.max(0, y0 - 6); py < Math.min(H, y0 + gh + 6); py++) {
                int shift = (int) Math.round(waveAmp * Math.sin(py / 5.0 + wavePhase));
                for (int pxx = Math.max(0, x0 - 6); pxx < Math.min(W, x0 + gw + 6); pxx++) {
                    // 目标像素 → 逆旋转 → 波形位移 → 字形坐标
                    int dx = pxx - cx, dy = py - cy;
                    int sx = (int) Math.round(dx * cos + dy * sin) + shift;
                    int sy = (int) Math.round(-dx * sin + dy * cos);
                    int gx = sx / SCALE + 2, gy = sy / SCALE + 3; // +2/+3: 字形在 7x5 网格中的起点
                    if (gx >= 0 && gx < 5 && gy >= 0 && gy < 7 && g[gy][gx]) {
                        px[py * W + pxx] = color;
                    }
                }
            }
        }

        // 噪点（画在字符上层）
        for (int i = 0; i < 40; i++) {
            px[rnd.nextInt(H) * W + rnd.nextInt(W)] =
                    rgb(rnd.nextInt(100, 200), rnd.nextInt(100, 200), rnd.nextInt(100, 200));
        }

        return "data:image/png;base64," + Base64.getEncoder().encodeToString(encodePng(px, W, H));
    }

    private static int rgb(int r, int g, int b) {
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void drawLine(int[] px, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            if (x1 >= 0 && x1 < W && y1 >= 0 && y1 < H) px[y1 * W + x1] = color;
            if (x1 == x2 && y1 == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x1 += sx; }
            if (e2 < dx) { err += dx; y1 += sy; }
        }
    }

    /** 最小 PNG 编码（8-bit RGB，filter 0），零第三方依赖 */
    private static byte[] encodePng(int[] px, int w, int h) {
        byte[] raw = new byte[h * (w * 3 + 1)];
        int p = 0;
        for (int y = 0; y < h; y++) {
            raw[p++] = 0; // filter: none
            for (int x = 0; x < w; x++) {
                int c = px[y * w + x];
                raw[p++] = (byte) (c >> 16);
                raw[p++] = (byte) (c >> 8);
                raw[p++] = (byte) c;
            }
        }
        Deflater def = new Deflater();
        try {
            def.setInput(raw);
            def.finish();
            byte[] idat = new byte[Math.max(64, raw.length)];
            int len = 0;
            while (!def.finished() && len < idat.length) {
                len += def.deflate(idat, len, idat.length - len);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(64 + len);
            byte[] sig = {(byte) 137, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
            out.write(sig, 0, sig.length);
            writeChunk(out, "IHDR", ihdr(w, h));
            writeChunk(out, "IDAT", java.util.Arrays.copyOf(idat, len));
            writeChunk(out, "IEND", new byte[0]);
            return out.toByteArray();
        } finally {
            def.end();
        }
    }

    private static byte[] ihdr(int w, int h) {
        return new byte[]{
                (byte) (w >>> 24), (byte) (w >>> 16), (byte) (w >>> 8), (byte) w,
                (byte) (h >>> 24), (byte) (h >>> 16), (byte) (h >>> 8), (byte) h,
                8, 2, 0, 0, 0};
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) {
        out.write((data.length >>> 24) & 0xFF);
        out.write((data.length >>> 16) & 0xFF);
        out.write((data.length >>> 8) & 0xFF);
        out.write(data.length & 0xFF);
        CRC32 crc = new CRC32();
        crc.update(type.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        crc.update(data);
        out.write(type.getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, 4);
        out.write(data, 0, data.length);
        long v = crc.getValue();
        out.write((int) (v >>> 24) & 0xFF);
        out.write((int) (v >>> 16) & 0xFF);
        out.write((int) (v >>> 8) & 0xFF);
        out.write((int) v & 0xFF);
    }
}
