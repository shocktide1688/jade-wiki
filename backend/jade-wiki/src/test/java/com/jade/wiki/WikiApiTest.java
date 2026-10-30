package com.jade.wiki;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.restassured.RestAssured;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Jade Wiki 全流程回归测试
 * 覆盖: 项目/目录/页面/乐观锁/历史/回收站/搜索/成员权限/模板/分享/导出/开放API
 *
 * 需要 PG 运行: 本地 5434 (jade_wiki_test), CI 由 WIKI_TEST_DB_URL 覆盖
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WikiApiTest {

    static String token;
    static long itemId;
    static long catId;
    static long pageId;

    private Response auth(String method, String path, Object body) {
        var spec = given().contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token);
        var r = (body == null) ? spec : spec.body(body);
        return r.when().request(method, path);
    }

    private Long id(Response r, String jsonPath) {
        return r.jsonPath().getLong(jsonPath);
    }

    @Test
    @Order(1)
    void login() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        token = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"username":"admin","password":"admin123"}
                        """)
                .when().post("/api/v1/auth/login")
                .then().statusCode(200)
                .body("code", equalTo(0))
                .body("data.accessToken", notNullValue())
                .extract().jsonPath().getString("data.accessToken");
    }

    @Test
    @Order(2)
    void item_crud_and_members() {
        // 创建
        Response r = auth("POST", "/api/v1/wiki/items",
                new ItemReq("回归测试项目", "E2E", "private", null));
        r.then().statusCode(200).body("code", equalTo(0))
                .body("data.myRole", equalTo(0));
        itemId = id(r, "data.id");

        // 成员
        auth("POST", "/api/v1/wiki/items/" + itemId + "/members",
                new MemberReq("user", 2))
                .then().body("code", equalTo(0));
        auth("GET", "/api/v1/wiki/items/" + itemId + "/members", null)
                .then().body("data", hasSize(2));
    }

    @Test
    @Order(3)
    void catalog_tree() {
        Response r = auth("POST", "/api/v1/wiki/catalogs",
                new CatalogReq(itemId, "模块A", 0L, null));
        r.then().body("code", equalTo(0));
        catId = id(r, "data.id");

        // 二级目录 + 排序
        Response child = auth("POST", "/api/v1/wiki/catalogs",
                new CatalogReq(itemId, "模块B", 0L, null));
        long catB = id(child, "data.id");
        auth("PUT", "/api/v1/wiki/catalogs/" + catB + "/move", new MoveReq("up"))
                .then().body("code", equalTo(0));

        auth("GET", "/api/v1/wiki/catalogs?itemId=" + itemId, null)
                .then().body("data", hasSize(2));
    }

    @Test
    @Order(4)
    void page_optimistic_lock_history() {
        // 模板创建页面
        Response r = auth("POST", "/api/v1/wiki/pages",
                new PageSaveReq(itemId, "接口文档", "# 接口\n内容v1", catId));
        r.then().statusCode(200).body("code", equalTo(0))
                .body("data.version", equalTo(0));
        pageId = id(r, "data.id");

        // v0 -> v1
        auth("PUT", "/api/v1/wiki/pages/" + pageId,
                new PageUpdateReq("接口文档", "# 接口\n内容v2", catId, 0L))
                .then().body("data.version", equalTo(1));

        // 旧版本冲突 4009
        auth("PUT", "/api/v1/wiki/pages/" + pageId,
                new PageUpdateReq("旧", "x", catId, 0L))
                .then().body("code", equalTo(4009));

        // 历史快照
        auth("GET", "/api/v1/wiki/pages/" + pageId + "/history", null)
                .then().body("data", hasSize(greaterThan(0)));

        // 页内搜索
        auth("GET", "/api/v1/wiki/pages?itemId=" + itemId + "&keyword=内容v2", null)
                .then().body("data", hasSize(1));
    }

    @Test
    @Order(5)
    void copy_move_export() {
        // 复制
        Response copy = auth("POST", "/api/v1/wiki/pages/" + pageId + "/copy", null);
        copy.then().body("code", equalTo(0)).body("data.pageTitle", equalTo("接口文档 - 副本"));
        long copyId = id(copy, "data.id");

        // 排序: 副本上移
        auth("PUT", "/api/v1/wiki/pages/" + copyId + "/move", new MoveReq("up"))
                .then().body("code", equalTo(0));

        // 单页导出
        auth("GET", "/api/v1/wiki/pages/" + pageId + "/export", null)
                .then().statusCode(200);

        // 项目 zip 导出
        auth("GET", "/api/v1/wiki/items/" + itemId + "/export", null)
                .then().statusCode(200)
                .header("Content-Disposition", org.hamcrest.Matchers.containsString(".zip"));

        // 清理副本
        auth("DELETE", "/api/v1/wiki/pages/" + copyId, null).then().body("code", equalTo(0));
    }

    @Test
    @Order(6)
    void trash_and_restore() {
        auth("DELETE", "/api/v1/wiki/pages/" + pageId, null).then().body("code", equalTo(0));
        // 回收站里应有 pageId (测试5删除的副本可能也在, 按 id 断言更稳)
        java.util.List<java.util.Map<String, Object>> trash =
                auth("GET", "/api/v1/wiki/pages?itemId=" + itemId + "&trash=true", null)
                        .jsonPath().getList("data");
        org.junit.jupiter.api.Assertions.assertTrue(
                trash.stream().anyMatch(pg -> ((Number) pg.get("id")).longValue() == pageId),
                "回收站应包含被删页面");
        auth("POST", "/api/v1/wiki/pages/" + pageId + "/restore", null).then().body("code", equalTo(0));
        java.util.List<java.util.Map<String, Object>> active =
                auth("GET", "/api/v1/wiki/pages?itemId=" + itemId, null)
                        .jsonPath().getList("data");
        org.junit.jupiter.api.Assertions.assertTrue(
                active.stream().anyMatch(pg -> ((Number) pg.get("id")).longValue() == pageId),
                "恢复后应回到活跃列表");
    }

    @Test
    @Order(7)
    void templates() {
        // 内置模板 2 个
        auth("GET", "/api/v1/wiki/templates", null)
                .then().body("data", hasSize(greaterThan(1)));

        // 自建 + 删除
        Response created = auth("POST", "/api/v1/wiki/templates",
                new TemplateReq("我的模板", "# t"));
        created.then().body("code", equalTo(0));
        long tid = id(created, "data.id");
        auth("DELETE", "/api/v1/wiki/templates/" + tid, null).then().body("code", equalTo(0));
    }

    @Test
    @Order(8)
    void share_password_and_anonymous_view() {
        // 给项目加密码
        auth("PUT", "/api/v1/wiki/items/" + itemId,
                new ItemReq("回归测试项目", "E2E", "private", "abc123"))
                .then().body("code", equalTo(0));

        // 无 token 匿名访问 → 1003
        given().when().get("/api/v1/wiki/view/items/" + itemId)
                .then().body("code", equalTo(1003));

        // 错误密码 → 1003
        given().contentType(ContentType.JSON)
                .body("{\"itemId\":" + itemId + ",\"password\":\"wrong\"}")
                .when().post("/api/v1/wiki/share/verify")
                .then().body("code", equalTo(1003));

        // 正确密码 → token, 匿名访问通过
        String viewToken = given().contentType(ContentType.JSON)
                .body("{\"itemId\":" + itemId + ",\"password\":\"abc123\"}")
                .when().post("/api/v1/wiki/share/verify")
                .then().body("code", equalTo(0))
                .extract().jsonPath().getString("data.viewToken");

        given().header("X-Share-Token", viewToken)
                .when().get("/api/v1/wiki/view/items/" + itemId)
                .then().body("code", equalTo(0))
                .body("data.itemName", equalTo("回归测试项目"));

        given().header("X-Share-Token", viewToken)
                .when().get("/api/v1/wiki/view/pages/" + pageId)
                .then().body("code", equalTo(0));

        // 还原为无密码
        auth("PUT", "/api/v1/wiki/items/" + itemId,
                new ItemReq("回归测试项目", "E2E", "private", ""))
                .then().body("code", equalTo(0));
    }

    @Test
    @Order(9)
    void open_api_key_and_push() {
        // 生成 key
        Response key = auth("POST", "/api/v1/wiki/items/" + itemId + "/api-keys",
                new ApiKeyReq("CI 机器人"));
        key.then().body("code", equalTo(0));
        String apiKey = key.jsonPath().getString("data.apiKey");
        String apiSecret = key.jsonPath().getString("data.apiSecret");

        // 创建
        authNoLogin(apiKey, apiSecret, "CI 推送接口", "# CI 内容", "CI 目录")
                .then().body("code", equalTo(0)).body("data.action", equalTo("created"));

        // 覆盖
        authNoLogin(apiKey, apiSecret, "CI 推送接口", "# CI 内容v2", null)
                .then().body("data.action", equalTo("updated"));

        // 错误 secret
        authNoLogin(apiKey, "bad-secret", "x", "y", null)
                .then().body("code", equalTo(1003));
    }

    private Response authNoLogin(String apiKey, String apiSecret, String title, String content, String catName) {
        return given().contentType(ContentType.JSON)
                .body(new OpenSaveReq(apiKey, apiSecret, title, content, catName))
                .when().post("/api/open/wiki/page/save");
    }

    @Test
    @Order(10)
    void global_search() {
        auth("GET", "/api/v1/wiki/pages/global-search?keyword=接口", null)
                .then().body("code", equalTo(0))
                .body("data", hasSize(greaterThan(0)));
    }

    @Test
    @Order(11)
    void readonly_member_cannot_edit() {
        // user 对本项目是只读成员(测试 2 加入 memberGroup=2)
        String userToken = given().contentType(ContentType.JSON)
                .body("{\"username\":\"user\",\"password\":\"admin123\"}")
                .when().post("/api/v1/auth/login")
                .then().extract().jsonPath().getString("data.accessToken");
        given().contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + userToken)
                .body(new PageUpdateReq("越权修改", "x", catId, 1L))
                .when().put("/api/v1/wiki/pages/" + pageId)
                .then().body("code", equalTo(1003));
    }

    // ---- 请求体 record ----
    record ItemReq(String itemName, String itemDescription, String visibility, String password) {}
    record MemberReq(String username, Integer memberGroup) {}
    record CatalogReq(Long itemId, String catName, Long parentId, Integer sNumber) {}
    record PageSaveReq(Long itemId, String pageTitle, String pageContent, Long catId) {}
    record PageUpdateReq(String pageTitle, String pageContent, Long catId, Long version) {}
    record MoveReq(String direction) {}
    record TemplateReq(String templateName, String templateContent) {}
    record ApiKeyReq(String remark) {}
    record OpenSaveReq(String apiKey, String apiSecret, String pageTitle, String pageContent, String catName) {}
}
