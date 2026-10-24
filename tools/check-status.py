#!/usr/bin/env python3
"""检查 mmSupernaturals 在各平台的审核/上线状态。

用法：
    python tools/check-status.py            # 打印表格
    python tools/check-status.py --write    # 同时写入 .workbuddy/out/status-report.md

判定原则：一律按「匿名访问」的结果判定。作者登录后能看到 ≠ 已上线。
只用标准库，不需要 pip install。
退出码永远是 0（避免定时任务误报失败），判定结果看输出。
"""

import argparse
import datetime as dt
import os
import re
import ssl
import sys
import urllib.error
import urllib.request

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/124.0 Safari/537.36")

PENDING_PHRASES = [
    "指定的主题不存在或已被删除或正在被审核",   # Discuz 未过审/已删
    "抱歉，指定的主题不存在",
    "审核中", "待审核",
]
ERROR_PHRASES = [
    "我们遇到了一些问题",                      # MineBBS 错误页
    "加载时间比预期的要久",                    # 红石 SPA 外壳（未渲染出内容）
    "Project not found",                        # Modrinth 未公开
]

# name, probe url, kind
TARGETS = [
    ("Modrinth", "https://api.modrinth.com/v2/project/mmsupernaturals", "modrinth"),
    ("Hangar", "https://hangar.papermc.io/api/v1/projects/mmSupernaturals", "hangar"),
    ("MineBBS", "https://www.minebbs.com/resources/mmsupernaturals-paper.18704/", "discuz"),
    ("红石中继站", "https://www.mczwlt.net/resource/skkshwk7", "spa"),
    ("CurseForge", "https://www.curseforge.com/minecraft/bukkit-plugins/mmsupernaturals", "generic"),
    ("MCBBS 纪念版", "https://www.mcbbs.co/thread-6667-1-1.html", "discuz"),
    ("苦力怕论坛", "https://klpbbs.com/thread-174737-1-1.html", "discuz"),
    ("BuiltByBit", "https://builtbybit.com/resources/mmsupernaturals.134029/", "generic"),
]

TIMEOUT = 25
MAX_BYTES = 400_000


def fetch(url):
    """返回 (status, body)。网络异常返回 (0, error_text)。"""
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    req = urllib.request.Request(url, headers={
        "User-Agent": UA,
        "Accept": "text/html,application/json;q=0.9,*/*;q=0.8",
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
    })
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT, context=ctx) as r:
            raw = r.read(MAX_BYTES)
            charset = r.headers.get_content_charset() or "utf-8"
            return r.status, raw.decode(charset, "replace")
    except urllib.error.HTTPError as e:
        return e.code, ""
    except Exception as e:                     # noqa: BLE001
        return 0, f"{type(e).__name__}: {e}"


def page_title(body):
    m = re.search(r"<title>(.*?)</title>", body, re.S | re.I)
    return re.sub(r"\s+", " ", m.group(1)).strip() if m else ""


def judge(kind, status, body):
    """返回 (verdict, evidence)。verdict ∈ {LIVE, PENDING, MISSING, BLOCKED, UNKNOWN}"""
    if status == 0:
        return "UNKNOWN", body[:60]
    if status == 403:
        return "BLOCKED", "403（Cloudflare 挡住脚本，需人工看一眼）"

    title = page_title(body)

    if status == 404:
        if kind == "generic":
            return "PENDING", "404（公开地址还不存在）"
        return "MISSING", "404（游客完全不可见：待审或已删，需登录核对）"

    if kind == "modrinth":
        if status == 200 and '"id"' in body:
            return "LIVE", "API 返回项目对象"
        return "UNKNOWN", f"HTTP {status}"

    if kind == "hangar":
        if status == 200 and '"name"' in body:
            return "LIVE", "API 返回项目对象"
        return "UNKNOWN", f"HTTP {status}"

    if kind == "discuz":
        # 未过审时 Discuz 给的是「提示信息」页
        if "提示信息" in title or "不存在或已被删除" in body:
            return "PENDING", "Discuz 提示页（主题不存在/待审）"
        if "mmSupernaturals" in title or "超自然" in title:
            return "LIVE", f"标题已渲染：{title[:40]}…"
        return "UNKNOWN", f"200 但标题不含项目名：{title[:40]}"

    if kind == "spa":
        if "mmSupernaturals" in title or "超自然" in title:
            return "LIVE", "HTML 里已含资源标题"
        for p in ERROR_PHRASES:
            if p in body:
                return "PENDING", "只拿到 SPA 外壳（内容未公开/未过审）"
        return "PENDING", "200 但未见资源标题（前端渲染壳）"

    # generic
    if status == 200:
        if re.search(r"<title>\s*404", body, re.I):
            return "PENDING", "页面标题是 404"
        if "mmSupernaturals" in title:
            return "LIVE", f"标题已渲染：{title[:40]}…"
        return "UNKNOWN", f"200 但标题不含项目名：{title[:40]}"
    return "UNKNOWN", f"HTTP {status}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--write", action="store_true", help="写入 .workbuddy/out/status-report.md")
    args = ap.parse_args()

    now = dt.datetime.now().strftime("%Y-%m-%d %H:%M")
    rows = []
    for name, url, kind in TARGETS:
        status, body = fetch(url)
        verdict, evidence = judge(kind, status, body)
        rows.append((name, verdict, evidence, url))
        print(f"{name:<12} {verdict:<8} {evidence}")

    counts = {}
    for _, v, _, _ in rows:
        counts[v] = counts.get(v, 0) + 1
    summary = "  ".join(f"{k} {v}" for k, v in sorted(counts.items()))
    print(f"\n{now}  {summary}")

    if args.write:
        out_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                               ".workbuddy", "out")
        os.makedirs(out_dir, exist_ok=True)
        path = os.path.join(out_dir, "status-report.md")
        icon = {"LIVE": "✅ 已上线", "PENDING": "🕒 待审/未上线",
                "MISSING": "🚨 404 不可见", "BLOCKED": "⛔ 被挡", "UNKNOWN": "❔ 未知"}
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(f"# 发布状态检查（{now}）\n\n")
            f.write("> 按**匿名访问**判定；作者登录可见 ≠ 已上线。\n\n")
            f.write("| 平台 | 状态 | 依据 | 链接 |\n|---|---|---|---|\n")
            for name, v, ev, url in rows:
                f.write(f"| {name} | {icon[v]} | {ev} | {url} |\n")
            f.write(f"\n统计：{summary}\n")
        print(f"\n已写入 {path}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
