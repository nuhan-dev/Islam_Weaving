#!/usr/bin/env python3
"""Collects today's top Bangladesh headlines (Bengali) from public RSS feeds and writes news.json.
Only headline + source name are stored. If every feed fails, the old news.json is kept."""
import datetime
import html
import json
import os
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET

# (source label, feed url, how many headlines to take)
FEEDS = [
    ("গুগল নিউজ", "https://news.google.com/rss?hl=bn&gl=BD&ceid=BD:bn", 9),
    ("প্রথম আলো", "https://www.prothomalo.com/feed/", 4),
    ("বিবিসি বাংলা", "https://feeds.bbci.co.uk/bengali/rss.xml", 4),
    ("ডেইলি স্টার", "https://www.thedailystar.net/frontpage/rss.xml", 3),
]
MAX_ITEMS = 16
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "news.json")


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (compatible; IslamWeavingNews/1.0)"})
    with urllib.request.urlopen(req, timeout=25) as r:
        return r.read()


def clean(text):
    text = html.unescape(re.sub(r"<[^>]+>", " ", text or ""))
    return re.sub(r"\s+", " ", text).strip()


def parse(xml_bytes):
    """Returns [(title, source_or_None)] for RSS 2.0 and Atom."""
    root = ET.fromstring(xml_bytes)
    out = []
    for it in root.iter("item"):
        title = clean(it.findtext("title"))
        src = it.find("source")
        out.append((title, clean(src.text) if src is not None and src.text else None))
    if not out:
        ns = "{http://www.w3.org/2005/Atom}"
        for en in root.iter(ns + "entry"):
            out.append((clean(en.findtext(ns + "title")), None))
    return out


def norm(title):
    return re.sub(r"[\W_]+", "", title.lower())[:40]


def main():
    items, seen = [], set()
    for label, url, take in FEEDS:
        try:
            got = parse(fetch(url))
        except Exception as e:  # one broken feed must not stop the others
            print("skip", label, "-", e)
            continue
        added = 0
        for title, src in got:
            source = src or label
            # Google News adds " - Source" at the end of the title
            if label == "গুগল নিউজ" and " - " in title:
                head, tail = title.rsplit(" - ", 1)
                if 2 <= len(tail) <= 40:
                    title, source = head.strip(), tail.strip()
            if not (12 <= len(title) <= 150):
                continue
            key = norm(title)
            if key in seen:
                continue
            seen.add(key)
            items.append({"t": title, "s": source})
            added += 1
            if added >= take:
                break
        print(label, "->", added)
    items = items[:MAX_ITEMS]
    if not items:
        print("no headlines found; keeping the old news.json")
        sys.exit(0 if os.path.exists(OUT) else 1)
    new = {"updated": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"), "items": items}
    try:
        old = json.load(open(OUT, encoding="utf-8"))
    except Exception:
        old = {}
    if old.get("items") == items:
        print("headlines unchanged")
        return
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(new, f, ensure_ascii=False, indent=1)
    print("wrote", len(items), "headlines")


if __name__ == "__main__":
    main()
