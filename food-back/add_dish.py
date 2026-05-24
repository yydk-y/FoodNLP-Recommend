"""
一键添加菜品脚本
用法: python add_dish.py "菜名" 分类ID "口味" "食材JSON" 价格 热度 "描述"

示例:
  python add_dish.py "宫保鸡丁" 7 "麻辣" '["鸡肉","花生","干辣椒"]' 24.00 120 "经典川菜，鸡肉嫩滑，花生酥脆"
  python add_dish.py "清蒸鲈鱼" 6 "咸鲜" '["鲈鱼","葱","姜","蒸鱼豉油"]' 32.00 85 "鱼肉鲜嫩，原汁原味"
"""

import os, sys, json, time, requests, subprocess
from PIL import Image

# ==================== 配置 ====================
MYSQL = ["mysql", "-u", "root", "-pyandakun", "smart_ordering"]
UPLOAD_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "uploads", "images")

# ==================== 数据库操作 ====================
def db(query):
    subprocess.run(MYSQL + ["-e", query], capture_output=True)

def get_next_id():
    r = subprocess.run(MYSQL + ["-N", "-e", "SELECT COALESCE(MAX(dish_id),0)+1 FROM dish_info"],
                       capture_output=True, text=True)
    return int(r.stdout.strip())

def insert_dish(did, name, cat_id, taste, ingredient, price, heat, desc):
    sql = (
        f"INSERT INTO dish_info (dish_id, dish_name, category_id, taste, ingredient, "
        f"price, heat, description, image_url, status) VALUES ("
        f"{did}, '{name.replace(chr(39), chr(39)+chr(39))}', {cat_id}, "
        f"'{taste}', '{ingredient}', {price:.2f}, {heat}, "
        f"'{desc.replace(chr(39), chr(39)+chr(39))}', "
        f"'/images/dish_{did}/20260501_{did}.jpg', 1)"
    )
    db(sql)

# ==================== 图片下载 ====================
def download_image(did, name):
    d = os.path.join(UPLOAD_DIR, f"dish_{did}")
    os.makedirs(d, exist_ok=True)
    dst = os.path.join(d, f"20260501_{did}.jpg")
    
    # 1. 百度搜索
    s = requests.Session()
    s.headers.update({"User-Agent": "Mozilla/5.0"})
    s.get("https://image.baidu.com/", timeout=10)
    
    r = s.get("https://image.baidu.com/search/acjson", params={
        "tn": "resultjson_com", "word": name, "pn": 0, "rn": 10,
    }, timeout=15)
    if r.status_code != 200:
        return False
    
    urls = []
    for item in r.json(strict=False).get("data", []):
        if isinstance(item, dict):
            u = item.get("middleURL") or item.get("thumbURL") or ""
            if u and not u.startswith("data:"):
                urls.append(u)
    
    for img_url in urls[:5]:
        try:
            resp = requests.get(img_url, headers={
                "User-Agent": "Mozilla/5.0",
                "Referer": "https://image.baidu.com/",
            }, timeout=15)
            if resp.status_code == 200 and len(resp.content) > 8000:
                # 保存
                for f in os.listdir(d):
                    os.remove(os.path.join(d, f))
                with open(dst, "wb") as f:
                    f.write(resp.content)
                # 转 JPEG
                img = Image.open(dst)
                if img.mode != "RGB":
                    img = img.convert("RGB")
                img.save(dst, "JPEG", quality=92)
                return True
        except:
            continue
    return False

# ==================== 主流程 ====================
if __name__ == "__main__":
    if len(sys.argv) < 6:
        print(__doc__)
        sys.exit(1)
    
    name = sys.argv[1]
    cat_id = int(sys.argv[2])
    taste = sys.argv[3]
    ingredient = sys.argv[4]
    price = float(sys.argv[5])
    heat = int(sys.argv[6]) if len(sys.argv) > 6 else 50
    desc = sys.argv[7] if len(sys.argv) > 7 else f"美味{name}"
    
    did = get_next_id()
    print(f"[1/3] 入库: {name} (dish_id={did})")
    insert_dish(did, name, cat_id, taste, ingredient, price, heat, desc)
    
    print(f"[2/3] 下载图片: {name}")
    ok = download_image(did, name)
    if ok:
        print(f"  ✅ 图片下载成功")
    else:
        print(f"  ⚠️ 百度未搜到图片，请手动下载放到 uploads/images/dish_{did}/")
    
    print(f"[3/3] 完成！dish_id={did}")
    print(f"重启后端后 Ctrl+F5 即可看到新菜品")
