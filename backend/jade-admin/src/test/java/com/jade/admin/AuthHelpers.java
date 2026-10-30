package com.jade.admin;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.parsing.Parser;
import io.restassured.response.Response;

import static io.restassured.RestAssured.given;

/**
 * 测试用认证 helper
 * 登录拿 token，给后续请求加 Authorization header
 */
public class AuthHelpers {

    static {
        // 默认 JSON 解析器（response 无 Content-Type 时不报错）
        if (RestAssured.defaultParser == null) {
            RestAssured.defaultParser = Parser.JSON;
        }
    }

    public static String loginAsAdmin() {
        return login("admin", "admin123");
    }

    /**
     * 测试应用端口: @BeforeAll 阶段框架尚未接管 RestAssured 默认端口(8080),
     * 本机 8080 被其他服务占用时必须显式指定, 与 application.properties 的 test-port 一致
     */
    private static int testPort() {
        String p = System.getProperty("quarkus.http.port");
        if (p == null || p.isBlank()) {
            p = System.getProperty("quarkus.http.test-port");
        }
        return Integer.parseInt(p == null || p.isBlank() ? "8085" : p.trim());
    }

    public static String login(String username, String password) {
        Response r = given()
            .port(testPort())
            .contentType(ContentType.JSON)
            .body("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
            .when().post("/api/v1/auth/login");
        if (r.statusCode() == 200 && r.jsonPath().get("data.accessToken") != null) {
            return r.jsonPath().getString("data.accessToken");
        }
        throw new RuntimeException("login failed: " + r.statusCode() + " " + r.body().asString());
    }

    /** 给 RestAssured 设默认 Authorization header */
    public static void authAll(String token) {
        RestAssured.port = testPort();
        RestAssured.requestSpecification = given()
            .header("Authorization", "Bearer " + token)
            .contentType(ContentType.JSON)
            .log().all(false);
    }

    public static void clearAuth() {
        RestAssured.requestSpecification = null;
    }
}
