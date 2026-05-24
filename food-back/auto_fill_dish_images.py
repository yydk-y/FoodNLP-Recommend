#!/usr/bin/env python3
"""
根据菜名自动搜索图片，下载到本地并回写 dish_info.image_url。

默认行为：
- 仅处理 image_url 为空的菜品
- 图片保存到 ./uploads/images/dish_{dish_id}/
- 数据库 image_url 写为 /images/dish_{dish_id}/{filename}

依赖：
  pip install pymysql requests duckduckgo-search
"""

from __future__ import annotations

import argparse
import os
import re
import time
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Optional, Iterable

import pymysql
import requests
from duckduckgo_search import DDGS


@dataclass
class Dish:
    dish_id: int
    dish_name: str
    image_url: Optional[str]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="自动搜索菜品图片并更新数据库")

    parser.add_argument("--db-host", default=os.getenv("DB_HOST", "localhost"))
    parser.add_argument("--db-port", type=int, default=int(os.getenv("DB_PORT", "3306")))
    parser.add_argument("--db-user", default=os.getenv("DB_USER", "root"))
    parser.add_argument("--db-password", default=os.getenv("DB_PASSWORD", "yandakun"))
    parser.add_argument("--db-name", default=os.getenv("DB_NAME", "smart_ordering"))
    parser.add_argument("--db-charset", default="utf8mb4")

    parser.add_argument("--storage-path", default=os.getenv("IMAGE_STORAGE_PATH", "./uploads/images"))
    parser.add_argument("--base-url", default=os.getenv("IMAGE_BASE_URL", "/images"))

    parser.add_argument("--only-missing", action="store_true", default=True,
                        help="仅处理 image_url 为空的菜品（默认开启）")
    parser.add_argument("--overwrite", action="store_true", help="覆盖已有 image_url 的菜品")
    parser.add_argument("--limit", type=int, default=0, help="最多处理多少个菜品，0 表示不限制")
    parser.add_argument("--delay", type=float, default=0.8, help="每个菜品处理间隔秒数，避免请求过快")
    parser.add_argument("--dry-run", action="store_true", help="只打印，不写文件/不更新数据库")

    return parser.parse_args()


def get_connection(args: argparse.Namespace):
    return pymysql.connect(
        host=args.db_host,
        port=args.db_port,
        user=args.db_user,
        password=args.db_password,
        database=args.db_name,
        charset=args.db_charset,
        autocommit=False,
        cursorclass=pymysql.cursors.DictCursor,
    )


def load_dishes(conn, only_missing: bool, overwrite: bool, limit: int) -> list[Dish]:
    where = []
    if only_missing and not overwrite:
        where.append("(image_url IS NULL OR TRIM(image_url) = '')")

    sql = "SELECT dish_id, dish_name, image_url FROM dish_info"
    if where:
        sql += " WHERE " + " AND ".join(where)
    sql += " ORDER BY dish_id ASC"
    if limit and limit > 0:
        sql += f" LIMIT {int(limit)}"

    with conn.cursor() as cur:
        cur.execute(sql)
        rows = cur.fetchall()

    dishes = []
    for row in rows:
        name = (row.get("dish_name") or "").strip()
        if not name:
            continue
        dishes.append(Dish(
            dish_id=int(row["dish_id"]),
            dish_name=name,
            image_url=row.get("image_url"),
        ))
    return dishes


def search_image_url(query: str) -> Optional[str]:
    # 关键词加“美食 实拍”提升结果质量
    q = f"{query} 美食 实拍"
    with DDGS() as ddgs:
        results: Iterable[dict] = ddgs.images(
            q,
            region="cn-zh",
            safesearch="moderate",
            size="Medium",
            max_results=10,
        )
        for item in results:
            url = item.get("image")
            if url and url.startswith("http"):
                return url
    return None


def safe_ext_from_url_or_content_type(url: str, content_type: str) -> str:
    ext_map = {
        "image/jpeg": ".jpg",
        "image/jpg": ".jpg",
        "image/png": ".png",
        "image/gif": ".gif",
        "image/webp": ".webp",
    }
    ct = (content_type or "").split(";")[0].strip().lower()
    if ct in ext_map:
        return ext_map[ct]

    path = requests.utils.urlparse(url).path.lower()
    m = re.search(r"\.(jpg|jpeg|png|gif|webp)$", path)
    if m:
        e = m.group(1)
        return ".jpg" if e == "jpeg" else f".{e}"

    return ".jpg"


def generate_filename() -> str:
    ts = time.strftime("%Y%m%d_%H%M%S")
    uid = uuid.uuid4().hex[:8]
    return f"{ts}_{uid}"


def download_image(url: str, target_dir: Path) -> Optional[Path]:
    headers = {
        "User-Agent": "Mozilla/5.0",
        "Accept": "image/*,*/*;q=0.8",
    }
    resp = requests.get(url, timeout=15, headers=headers)
    if resp.status_code != 200:
        return None

    content_type = resp.headers.get("Content-Type", "")
    if not content_type.lower().startswith("image/"):
        return None

    ext = safe_ext_from_url_or_content_type(url, content_type)
    file_path = target_dir / f"{generate_filename()}{ext}"
    file_path.write_bytes(resp.content)
    return file_path


def update_dish_image_url(conn, dish_id: int, image_url: str):
    sql = "UPDATE dish_info SET image_url=%s WHERE dish_id=%s"
    with conn.cursor() as cur:
        cur.execute(sql, (image_url, dish_id))


def process_one(conn, dish: Dish, storage_path: Path, base_url: str, dry_run: bool) -> bool:
    image_url = search_image_url(dish.dish_name)
    if not image_url:
        print(f"[跳过] 未找到图片: {dish.dish_name}")
        return False

    dish_dir = storage_path / f"dish_{dish.dish_id}"
    dish_dir.mkdir(parents=True, exist_ok=True)

    if dry_run:
        print(f"[预览] {dish.dish_name} -> {image_url}")
        return True

    local_file = download_image(image_url, dish_dir)
    if not local_file:
        print(f"[失败] 下载图片失败: {dish.dish_name} <- {image_url}")
        return False

    relative = f"dish_{dish.dish_id}/{local_file.name}"
    db_url = f"{base_url.rstrip('/')}/{relative}"

    update_dish_image_url(conn, dish.dish_id, db_url)
    print(f"[成功] {dish.dish_name} -> {db_url}")
    return True


def main():
    args = parse_args()
    storage_path = Path(args.storage_path).resolve()
    storage_path.mkdir(parents=True, exist_ok=True)

    conn = get_connection(args)
    try:
        dishes = load_dishes(conn, only_missing=args.only_missing, overwrite=args.overwrite, limit=args.limit)
        if not dishes:
            print("没有需要处理的菜品。")
            return

        print(f"开始处理 {len(dishes)} 个菜品，图片目录: {storage_path}")

        ok = 0
        for i, dish in enumerate(dishes, start=1):
            print(f"\n[{i}/{len(dishes)}] {dish.dish_name}")
            try:
                if process_one(conn, dish, storage_path, args.base_url, args.dry_run):
                    ok += 1
                    if not args.dry_run:
                        conn.commit()
                else:
                    if not args.dry_run:
                        conn.rollback()
            except Exception as e:
                if not args.dry_run:
                    conn.rollback()
                print(f"[异常] {dish.dish_name}: {e}")

            if args.delay > 0:
                time.sleep(args.delay)

        print(f"\n完成：成功 {ok}/{len(dishes)}")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
