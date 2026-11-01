package com.jade.admin;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * 认证接口集成测试
 *
 * 注意：本测试需要 PG + Redis 运行（docker-compose up -d）
 */
@QuarkusTest
class AuthControllerTest {

    @BeforeEach
    void resetFailureCount() {
        // 计数存进程内, 用例间会残留; 成功登录即重置 admin 的失败计数。
        // 若上个用例把失败次数留在阈值上(如 captcha_invalid 不重置), 本次登录会被要求
        // 验证码(1005), 此时解析 SVG 走一次成功登录完成重置
        var first = given().contentType(ContentType.JSON).body("""
                {"username":"admin","password":"admin123"}
                """)
                .when().post("/api/v1/auth/login")
                .then().statusCode(200).extract().jsonPath();
        if (first.getInt("code") == 1005) {
            var captcha = given().when().get("/api/v1/auth/captcha")
                    .then().statusCode(200).body("code", equalTo(0))
                    .extract().jsonPath();
            given().contentType(ContentType.JSON).body(
                            "{\"username\":\"admin\",\"password\":\"admin123\",\"uuid\":\"" + captcha.getString("data.uuid")
                                    + "\",\"code\":\"" + solveCaptcha(captcha.getString("data.image")) + "\"}")
                    .when().post("/api/v1/auth/login")
                    .then().statusCode(200).body("code", equalTo(0));
        }
    }

    @Test
    void login_success() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"username":"admin","password":"admin123"}
                        """)
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("message", equalTo("success"))
                .body("data.accessToken", notNullValue())
                .body("data.tokenType", equalTo("Bearer"))
                .body("data.expiresIn", equalTo(7200))
                .body("data.user.username", equalTo("admin"));
    }

    @Test
    void login_wrong_password_returns_bad_credentials() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"username":"admin","password":"wrong"}
                        """)
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .body("code", equalTo(1004))
                .body("message", equalTo("用户名或密码错误"));
    }

    @Test
    void login_empty_username_validation_fails() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"username":"","password":"admin123"}
                        """)
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(400);
    }

    @Test
    void me_without_token_returns_401() {
        given()
                .when()
                .get("/api/v1/auth/me")
                .then()
                .statusCode(401);
    }

    @Test
    void unauthorized_still_carries_cors_headers() {
        // 回归: 无效/过期 token 在安全层被拒的 401 也必须带 CORS 头,
        // 否则浏览器拦截成 Network Error, 前端"登录已过期"提示失效
        given()
                .header("Origin", "http://localhost:5173")
                .header("Authorization", "Bearer expired-or-invalid-token")
                .when()
                .get("/api/v1/auth/me")
                .then()
                .statusCode(401)
                .header("Access-Control-Allow-Origin", equalTo("http://localhost:5173"));
    }

    @Test
    void preflight_is_answered_without_auth() {
        given()
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .when()
                .options("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .header("Access-Control-Allow-Origin", equalTo("http://localhost:5173"));
    }

    @Test
    void garbage_captcha_rejected_even_below_threshold() {
        // 页面残留验证码行 + 进程内计数被清: 带验证码字段就必须认真校验
        given().contentType(ContentType.JSON).body("""
                {"username":"admin","password":"admin123","uuid":"stale-uuid","code":"ZZZZ"}
                """)
                .when().post("/api/v1/auth/login")
                .then().statusCode(200).body("code", equalTo(1006));
    }

    @Test
    void captcha_endpoint_returns_svg() {
        given()
                .when()
                .get("/api/v1/auth/captcha")
                .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.uuid", notNullValue())
                .body("data.image", containsString("data:image/svg+xml;base64,"));
    }

    @Test
    void login_captcha_required_after_retry_count_exceeded() {
        String wrong = """
                {"username":"admin","password":"wrong"}
                """;
        // 前 5 次失败（阈值 passwordRetryCount=5）只返回 1004，不要求验证码
        for (int i = 0; i < 5; i++) {
            given().contentType(ContentType.JSON).body(wrong)
                    .when().post("/api/v1/auth/login")
                    .then().statusCode(200).body("code", equalTo(1004));
        }
        // 失败已达标，此后即使密码正确也要求验证码（缺验证码 → 1005）
        given().contentType(ContentType.JSON).body("""
                {"username":"admin","password":"admin123"}
                """)
                .when().post("/api/v1/auth/login")
                .then().statusCode(200).body("code", equalTo(1005));

        // 拉一张验证码（进程内存储），带正确验证码 → 登录成功
        var captcha = given().when().get("/api/v1/auth/captcha")
                .then().statusCode(200).body("code", equalTo(0))
                .extract().jsonPath();
        given().contentType(ContentType.JSON).body(
                        "{\"username\":\"admin\",\"password\":\"admin123\",\"uuid\":\"" + captcha.getString("data.uuid")
                                + "\",\"code\":\"" + solveCaptcha(captcha.getString("data.image")) + "\"}")
                .when().post("/api/v1/auth/login")
                .then().statusCode(200).body("code", equalTo(0));

        // 成功后计数重置：再次登录不再要求验证码
        given().contentType(ContentType.JSON).body("""
                {"username":"admin","password":"admin123"}
                """)
                .when().post("/api/v1/auth/login")
                .then().statusCode(200).body("code", equalTo(0));
    }

    @Test
    void login_captcha_invalid_returns_1006() {
        String wrong = """
                {"username":"admin","password":"wrong"}
                """;
        for (int i = 0; i < 5; i++) {
            given().contentType(ContentType.JSON).body(wrong)
                    .when().post("/api/v1/auth/login")
                    .then().statusCode(200).body("code", equalTo(1004));
        }
        // 答案错误
        var captcha = given().when().get("/api/v1/auth/captcha")
                .then().statusCode(200).body("code", equalTo(0))
                .extract().jsonPath();
        given().contentType(ContentType.JSON).body(
                        "{\"username\":\"admin\",\"password\":\"admin123\",\"uuid\":\"" + captcha.getString("data.uuid")
                                + "\",\"code\":\"XXXX\"}")
                .when().post("/api/v1/auth/login")
                .then().statusCode(200).body("code", equalTo(1006));

        // uuid 不存在（已过期/伪造）同样 1006
        given().contentType(ContentType.JSON).body("""
                {"username":"admin","password":"admin123","uuid":"no-such-uuid","code":"AB12"}
                """)
                .when().post("/api/v1/auth/login")
                .then().statusCode(200).body("code", equalTo(1006));
    }

    @Test
    void login_below_threshold_never_requires_captcha() {
        String wrong = """
                {"username":"admin","password":"wrong"}
                """;
        for (int i = 0; i < 4; i++) {
            given().contentType(ContentType.JSON).body(wrong)
                    .when().post("/api/v1/auth/login")
                    .then().statusCode(200).body("code", equalTo(1004));
        }
        // 阈值内失败未触发验证码：换正确密码直接登录成功
        given().contentType(ContentType.JSON).body("""
                {"username":"admin","password":"admin123"}
                """)
                .when().post("/api/v1/auth/login")
                .then().statusCode(200).body("code", equalTo(0));
    }

    /** 从生成的 SVG（base64 data URI）里按顺序抠出验证码字符 */
    private String solveCaptcha(String dataUri) {
        String svg = new String(java.util.Base64.getDecoder().decode(
                dataUri.substring("data:image/svg+xml;base64,".length())), java.nio.charset.StandardCharsets.UTF_8);
        StringBuilder code = new StringBuilder();
        var m = java.util.regex.Pattern.compile("<text[^>]*>([^<]+)</text>").matcher(svg);
        while (m.find()) {
            code.append(m.group(1));
        }
        return code.toString();
    }
}
