#!/usr/bin/env python3
"""一次性数据修复: 给 wiki_page 里裸代码块(内容为合法 JSON)补 json 语言标记。

读 /tmp/wiki_pages.json (psql json_agg 导出), 生成 /tmp/wiki_update.sql。
只输出有变化的行; 幂等, 重跑无变化则无输出。
"""
import json
import re
import sys

BARE_FENCE_RE = re.compile(r"```[ \t]*\n(.*?)```", re.DOTALL)
# 宽松校验: 容忍尾随逗号 (人手写的接口示例很常见)
TRAILING_COMMA_RE = re.compile(r",\s*([}\]])")


def looks_like_json(body: str) -> bool:
    try:
        json.loads(TRAILING_COMMA_RE.sub(r"\1", body.strip()))
        return True
    except Exception:
        return False


def tag_json_fences(md: str) -> str:
    def repl(m: re.Match) -> str:
        body = m.group(1)
        if not looks_like_json(body):
            return m.group(0)
        return "```json\n" + body + "```"
    return BARE_FENCE_RE.sub(repl, md)


def main() -> None:
    rows = json.loads(open("/tmp/wiki_pages.json", encoding="utf-8").read())
    updates = []
    for r in rows:
        new = tag_json_fences(r["c"] or "")
        if new != (r["c"] or ""):
            updates.append((r["id"], new))
    with open("/tmp/wiki_update.sql", "w", encoding="utf-8") as f:
        for pid, content in updates:
            # dollar-quoting 防注入/转义问题; 标签足够独特
            assert "$JW$" not in content, f"page {r['id']} 内容含 $JW$, 需换 quoting 标签"
            f.write(f"UPDATE wiki_page SET page_content = $JW${content}$JW$ WHERE id = {pid};\n".replace("{pid}", str(pid)))
    print(f"total={len(rows)} changed={len(updates)} ids={[u[0] for u in updates]}")


if __name__ == "__main__":
    sys.exit(main())
