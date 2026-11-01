#!/usr/bin/env python3
"""ShowDoc -> jade-wiki 迁移脚本 v2: 抓取+导入一体, 幂等可重跑.

用法: python3 -I import_wiki.py <local_base> <showdoc_item_id>...
  local_base:      本地 wiki 服务地址, 如 http://localhost:8082 (admin/admin123)

流程(每个 showdoc item):
  抓 item_info -> 按 menu 顺序抓所有 page -> 解码 HTML 实体
  -> 下载页面引用的附件图(缓存到 cache/) -> 建/复用本地 item
  -> 按 ShowDoc 目录顺序建目录(sNumber) -> 传图 -> 生成/复用 API key
  -> 按顺序推送页面(目录页带 catName, 平铺页不带头衔)
  -> 回读本地校验标题与目录

幂等: item 按名称复用; key 按 remark 复用; 页面按标题 upsert;
     附件图按 md5 缓存本地(上传不判重, 重跑会产生新附件行, 内容不受影响)。
"""
import html
import json
import re
import sys
import urllib.error
import urllib.request
import uuid
from pathlib import Path

SHOWDOC = "http://wiki.globavend.com"
SD_API = SHOWDOC + "/server/index.php?s=/api/"
SD_ATT_PREFIX = SHOWDOC + "/server/index.php?s=/api/attachment/visitFile/sign/"
# 附件 URL 两种形态: ...visitFile/sign/<md5> 与 ...visitFile&sign=<md5>
ATT_RE = re.compile(r'https?://[^\s)\]"\'<>]*visitFile[&/]sign[=/]([0-9a-fA-F]{32})')
# 源文档里人写的跨页引用: web/#/<item>[/<page>]
LINK_RE = re.compile(re.escape(SHOWDOC) + r"/web/#/(\d+)(?:/\d+)?")
KEY_REMARK = "showdoc-migration"
CACHE = Path(__file__).parent / "cache"


def http_json(method, url, token=None, json_body=None, data=None, headers=None):
    req_headers = dict(headers or {})
    if token:
        req_headers["Authorization"] = f"Bearer {token}"
    if json_body is not None:
        data = json.dumps(json_body, ensure_ascii=False).encode()
        req_headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, method=method, headers=req_headers)
    try:
        with urllib.request.urlopen(req, timeout=90) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        raise SystemExit(f"{method} {url} -> HTTP {e.code}: {e.read().decode(errors='replace')[:500]}")


def sd_post(endpoint, params):
    form = "&".join(f"{k}={v}" for k, v in params.items()).encode()
    return http_json("POST", SD_API + endpoint, data=form,
                     headers={"Content-Type": "application/x-www-form-urlencoded"})


def sniff_ext(b):
    if b[:8] == b"\x89PNG\r\n\x1a\n":
        return "png"
    if b[:3] == b"\xff\xd8\xff":
        return "jpg"
    if b[:6] in (b"GIF87a", b"GIF89a"):
        return "gif"
    if b[:4] == b"RIFF" and b[8:12] == b"WEBP":
        return "webp"
    raise SystemExit(f"未知图片格式: {b[:12]!r}")


def crawl(sd_item_id):
    """抓取一个 ShowDoc item -> {name, cats:[{name,pages}], tops:[pages]}"""
    item_dir = CACHE / f"item_{sd_item_id}"
    img_dir = item_dir / "imgs"
    img_dir.mkdir(parents=True, exist_ok=True)

    menu = sd_post("item/info", {"item_id": sd_item_id})["data"]
    name = menu["item_name"].strip()
    pages = {}  # page_id -> {title, content}

    def fetch_page(pid):
        if pid not in pages:
            d = sd_post("page/info", {"page_id": pid})["data"]
            pages[pid] = {"title": d["page_title"].strip(), "content": d["page_content"]}
        return pages[pid]

    # 目录按 API 返回顺序; 嵌套目录平铺为 "父 / 子"（当前源全部一层, 仅为兜底）
    cats = []

    def walk(cat_list, prefix):
        for c in cat_list:
            label = (prefix + " / " + c["cat_name"]) if prefix else c["cat_name"]
            cats.append({"name": label.strip(),
                         "pages": [fetch_page(p["page_id"]) for p in c.get("pages", [])]})
            walk(c.get("catalogs") or [], label)

    walk(menu["menu"]["catalogs"], "")
    tops = [fetch_page(p["page_id"]) for p in menu["menu"]["pages"]]

    # 下载所有引用的附件图（302->OSS, urllib 自动跟随）
    # 注意: 源内容是 HTML 实体转义的(& -> &amp;), 必须解码后再扫 URL
    all_pages = tops + [pg for c in cats for pg in c["pages"]]
    mds = sorted({m.lower() for p in all_pages
                  for m in ATT_RE.findall(html.unescape(p["content"]))})
    have = {p.name.rsplit(".", 1)[0] for p in img_dir.iterdir()} if img_dir.exists() else set()
    for md in mds:
        if md in have:
            continue
        with urllib.request.urlopen(SD_ATT_PREFIX + md, timeout=120) as r:
            data = r.read()
        (img_dir / f"{md}.{sniff_ext(data)}").write_bytes(data)
        print(f"  img {md[:8]}.. {len(data)}B")
    print(f"item {sd_item_id} [{name}]: {len(tops)} 平铺页 + "
          f"{sum(len(c['pages']) for c in cats)} 目录页({len(cats)} 目录) + {len(mds)} 图")
    return {"sd_id": str(sd_item_id), "name": name, "cats": cats, "tops": tops, "img_dir": img_dir}


def upload_file(local_base, token, item_id, path):
    ext = path.suffix.lstrip(".")
    ctype = {"png": "image/png", "jpg": "image/jpeg", "gif": "image/gif", "webp": "image/webp"}[ext]
    boundary = uuid.uuid4().hex
    payload = bytearray()
    payload += (f"--{boundary}\r\nContent-Disposition: form-data; name=\"itemId\"\r\n\r\n{item_id}\r\n").encode()
    payload += (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; "
                f"filename=\"{path.name}\"\r\nContent-Type: {ctype}\r\n\r\n").encode()
    payload += path.read_bytes()
    payload += (f"\r\n--{boundary}--\r\n").encode()
    r = http_json("POST", f"{local_base}/api/v1/wiki/attachments", token=token,
                  data=bytes(payload),
                  headers={"Content-Type": f"multipart/form-data; boundary={boundary}"})
    return r["data"]


def runapi_to_md(j):
    """ShowDoc RunApi 接口定义 JSON -> 与 V1 文档同风格的 markdown 接口说明.

    结构: {page_title, info{title/description/method/url/remark...},
          request{params{mode/json/jsonDesc/formdata/urlencoded}, headers, query,
          pathVariable, cookies, auth}, response{responseExample,
          responseParamsDesc, responseFailExample, responseFailParamsDesc...}}
    """
    i = j.get("info") or {}
    req = j.get("request") or {}
    rp = req.get("params") or {}
    rs = j.get("response") or {}
    title = (i.get("title") or j.get("page_title") or "").strip()
    desc = (i.get("description") or "").strip()
    remark = (i.get("remark") or "").strip()
    method = (i.get("method") or "get").upper()
    url = (i.get("url") or "").strip()

    L = ["##### 简要描述", "", f"- {desc or title or '无'}", "",
         "##### 请求URL", "", f"- `{url}`", "",
         "##### 请求方式", "", f"- {method}", ""]

    def real(rows):
        if not isinstance(rows, list):
            return []
        return [x for x in rows if isinstance(x, dict) and (x.get("name") or "").strip()]

    def pipe(v):
        return str(v if v is not None else "").replace("|", "\\|").replace("\n", " ")

    def table(name, rows, with_require=True, with_value=False):
        entries = real(rows)
        if not entries:
            return
        cols = "|参数名|" + ("必选|" if with_require else "") + "类型|" + ("示例值|" if with_value else "") + "说明|"
        sep = "|:----|" + (":---|" if with_require else "") + ":-----|" + (":-----|" if with_value else "") + "-----|"
        L.append(f"##### {name}"); L.append(""); L.append(cols); L.append(sep)
        for x in entries:
            cells = [x.get("name", "")]
            if with_require:
                cells.append("是" if str(x.get("require")) == "1" else "否")
            cells.append(x.get("type") or "string")
            if with_value:
                cells.append(x.get("value", "") if x.get("value") is not None else "")
            cells.append(x.get("remark") or "")
            L.append("|" + "|".join(pipe(c) for c in cells) + "|")
        L.append("")

    table("请求头", req.get("headers"))
    table("Query参数", req.get("query"), with_value=True)
    table("路径参数", req.get("pathVariable"), with_value=True)
    mode = rp.get("mode")
    body_json = (rp.get("json") or "").strip()
    if (mode == "json" or (not mode and body_json)) and body_json:
        L.append("##### Body参数"); L.append("")
        L.append("```json"); L.append(body_json); L.append("```"); L.append("")
        jd = rp.get("jsonDesc")
        if isinstance(jd, dict) and jd:  # dict 形态: 字段 -> 示例值
            L.append("##### Body参数说明"); L.append("")
            L.append("|参数名|示例值|"); L.append("|:----|:-----|")
            for k, v in jd.items():
                vs = v if isinstance(v, str) else json.dumps(v, ensure_ascii=False)
                if len(vs) > 60:
                    vs = vs[:57] + "..."
                L.append(f"|{pipe(k)}|{pipe(vs)}|")
            L.append("")
        else:
            table("Body参数说明", jd)
    else:
        table("Body参数", rp.get("formdata"), with_value=True)
        table("Body参数", rp.get("urlencoded"), with_value=True)

    ex = (rs.get("responseExample") or "").strip()
    if ex:
        fence = "json" if ex[0] in "{[" else ""
        L.append("##### 返回示例"); L.append("")
        L.append(f"```{fence}".rstrip() if fence else "```")
        L.append(ex); L.append("```"); L.append("")
        table("返回参数说明", rs.get("responseParamsDesc"), with_require=False)
    fail = (rs.get("responseFailExample") or "").strip()
    if fail and fail != ex:
        fence = "json" if fail[0] in "{[" else ""
        L.append("##### 失败返回示例"); L.append("")
        L.append(f"```{fence}".rstrip() if fence else "```")
        L.append(fail); L.append("```"); L.append("")
        table("失败返回参数说明", rs.get("responseFailParamsDesc"), with_require=False)
    if remark:
        L.append("##### 备注"); L.append(""); L.append(f"- {remark}"); L.append("")
    return "\n".join(L).rstrip() + "\n"


def is_runapi(decoded):
    try:
        j = json.loads(decoded)
    except Exception:
        return None
    if isinstance(j, dict) and (j.get("info") or {}).get("from") == "runapi":
        return j
    return None


# 裸代码块 (无语言标记), 内容为合法 JSON 的自动补 json 标记 -> 前端有语法高亮
BARE_FENCE_RE = re.compile(r"```[ \t]*\n(.*?)```", re.DOTALL)
# 宽松校验: 容忍尾随逗号 (人手写的接口示例很常见)
TRAILING_COMMA_RE = re.compile(r",\s*([}\]])")


def looks_like_json(body):
    try:
        json.loads(TRAILING_COMMA_RE.sub(r"\1", body.strip()))
        return True
    except Exception:
        return False


def tag_json_fences(md):
    def repl(m):
        body = m.group(1)
        if not looks_like_json(body):
            return m.group(0)
        return "```json\n" + body + "```"
    return BARE_FENCE_RE.sub(repl, md)


def transform(content, img_map, link_map):
    """HTML 实体解码 + RunApi 接口页转 markdown + 附件/跨页链接改写"""
    out = html.unescape(content)
    j = is_runapi(out)
    if j:
        out = runapi_to_md(j)
    out = tag_json_fences(out)
    def repl(m):
        url = img_map.get(m.group(1).lower())
        if url is None:
            raise SystemExit(f"未上传的图片: {m.group(1)}")
        return url
    out = ATT_RE.sub(repl, out)
    out = LINK_RE.sub(lambda m: f"/wiki/view/{link_map[m.group(1)]}"
                      if m.group(1) in link_map else m.group(0), out)
    return out


def main():
    local_base = sys.argv[1].rstrip("/")
    sd_ids = sys.argv[2:]
    if not sd_ids:
        raise SystemExit("至少给一个 showdoc item_id")

    # 1. 抓取全部源 item
    items = [crawl(i) for i in sd_ids]

    # 2. 本地登录
    token = http_json("POST", f"{local_base}/api/v1/auth/login",
                      json_body={"username": "admin", "password": "admin123"})["data"]["accessToken"]

    # 3. 先建/复用全部 item（跨页链接映射需要所有本地 id）
    records = http_json("GET", f"{local_base}/api/v1/wiki/items?page=1&size=100",
                        token=token)["data"]["records"]
    local_ids = {}
    for it in items:
        item = next((x for x in records if x["itemName"] == it["name"]), None)
        if item:
            local_ids[it["sd_id"]] = str(item["id"])
            print(f"item 复用 id={item['id']} [{it['name']}]")
        else:
            r = http_json("POST", f"{local_base}/api/v1/wiki/items", token=token,
                          json_body={"itemName": it["name"],
                                     "itemDescription": f"自 {SHOWDOC} ShowDoc 迁移 (item {it['sd_id']})",
                                     "visibility": "public"})
            local_ids[it["sd_id"]] = str(r["data"]["id"])
            print(f"item 新建 id={r['data']['id']} [{it['name']}]")

    # 4. 跨页链接映射: showdoc item id -> 本地 item id（未迁移的引用原样保留）
    link_map = dict(local_ids)
    referenced = {m for it in items for p in it["tops"] + [pg for c in it["cats"] for pg in c["pages"]]
                  for m in LINK_RE.findall(p["content"])}
    for ref in referenced - set(link_map):
        name = sd_post("item/info", {"item_id": ref})["data"]["item_name"].strip()
        found = next((x for x in records if x["itemName"] == name), None)
        if found:
            link_map[ref] = str(found["id"])
    print(f"跨页链接映射: {link_map}")

    for it in items:
        item_id = local_ids[it["sd_id"]]
        print(f"=== {it['name']} (id={item_id}) ===")

        # 5. 上传图片 -> md5 -> 本地 URL
        img_map = {}
        for f in sorted(it["img_dir"].iterdir()):
            if f.is_file():
                img_map[f.stem.lower()] = upload_file(local_base, token, item_id, f)["url"]
        if img_map:
            print(f"  附件 {len(img_map)} 张")

        # 6. 复用/生成 API key
        r = http_json("GET", f"{local_base}/api/v1/wiki/items/{item_id}/api-keys", token=token)
        key = next((k for k in r["data"] if k["remark"] == KEY_REMARK), None)
        if not key:
            key = http_json("POST", f"{local_base}/api/v1/wiki/items/{item_id}/api-keys",
                            token=token, json_body={"remark": KEY_REMARK})["data"]

        # 7. 按 ShowDoc 顺序建目录（同名略过——开放 API catName 也按名匹配）
        existing = {c["catName"] for c in http_json(
            "GET", f"{local_base}/api/v1/wiki/catalogs?itemId={item_id}", token=token)["data"]}
        for idx, c in enumerate(it["cats"]):
            if c["name"] not in existing:
                http_json("POST", f"{local_base}/api/v1/wiki/catalogs", token=token,
                          json_body={"itemId": item_id, "catName": c["name"], "sNumber": idx + 1})
                print(f"  目录新建 [{c['name']}]")

        # 8. 顺序推送: 目录页在前, 平铺页在后
        for c in it["cats"]:
            for p in c["pages"]:
                r = http_json("POST", f"{local_base}/api/open/wiki/page/save",
                              json_body={"apiKey": key["apiKey"], "apiSecret": key["apiSecret"],
                                         "pageTitle": p["title"], "catName": c["name"],
                                         "pageContent": transform(p["content"], img_map, link_map)})
                print(f"  page {r['data']['pageId']:>3} [{p['title']}] @{c['name']} {r['data']['action']}")
        for p in it["tops"]:
            r = http_json("POST", f"{local_base}/api/open/wiki/page/save",
                          json_body={"apiKey": key["apiKey"], "apiSecret": key["apiSecret"],
                                     "pageTitle": p["title"], "catName": None,
                                     "pageContent": transform(p["content"], img_map, link_map)})
            print(f"  page {r['data']['pageId']:>3} [{p['title']}] (平铺) {r['data']['action']}")

        # 9. 回读校验: 标题集合 + 目录集合
        vpages = http_json("GET", f"{local_base}/api/v1/wiki/view/pages?itemId={item_id}",
                           token=token)["data"]
        vcats = http_json("GET", f"{local_base}/api/v1/wiki/view/catalogs?itemId={item_id}",
                          token=token)["data"]
        want_titles = [p["title"] for p in it["tops"]] + \
                      [p["title"] for c in it["cats"] for p in c["pages"]]
        got_titles = [p["pageTitle"] for p in vpages]
        want_cats = [c["name"] for c in it["cats"]]
        got_cats = [c["catName"] for c in vcats]
        print(f"  校验: 页面 {len(got_titles)}/{len(want_titles)} "
              f"{'OK' if sorted(got_titles) == sorted(want_titles) else 'MISMATCH ' + str(got_titles)}"
              f"; 目录 {len(got_cats)}/{len(want_cats)} "
              f"{'OK' if got_cats == want_cats else 'MISMATCH ' + str(got_cats)}")


if __name__ == "__main__":
    main()
