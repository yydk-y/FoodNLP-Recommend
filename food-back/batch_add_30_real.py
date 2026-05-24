"""
批量添加30道菜品 - 实际脚本
"""
import os
os.environ.pop("HTTP_PROXY", None)
os.environ.pop("HTTPS_PROXY", None)
os.environ.pop("http_proxy", None)
os.environ.pop("https_proxy", None)
os.environ["NO_PROXY"] = "*"

import requests, subprocess, time
from PIL import Image

MYSQL = ["mysql", "-u", "root", "-pyandakun", "smart_ordering"]
BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "uploads", "images")

dishes = [
    ("红烧肉", 1, "咸甜", '["五花肉","冰糖","酱油","八角"]', 28.00, 150, "肥而不腻，入口即化"),
    ("可乐鸡翅", 1, "甜咸", '["鸡翅","可乐","酱油","姜"]', 22.00, 110, "甜咸适口，外焦里嫩"),
    ("蒜蓉西兰花", 1, "清淡", '["西兰花","蒜蓉","橄榄油"]', 14.00, 60, "清脆爽口，健康低脂"),
    ("酸辣土豆丝", 1, "酸辣", '["土豆","干辣椒","醋","花椒"]', 10.00, 180, "酸辣爽脆，家常必备"),
    ("香菇油菜", 1, "清淡", '["香菇","油菜","蚝油","蒜"]', 12.00, 55, "清淡爽口"),
    ("干锅花菜", 1, "微辣", '["花菜","五花肉","干辣椒","蒜"]', 18.00, 90, "香辣入味，下饭神器"),
    ("葱爆羊肉", 1, "咸鲜", '["羊肉","大葱","孜然","辣椒"]', 32.00, 105, "肉质鲜嫩，葱香四溢"),
    ("地三鲜", 1, "咸鲜", '["茄子","土豆","青椒","蒜"]', 16.00, 95, "东北经典，素菜下饭王"),
    ("宫保鸡丁", 1, "麻辣", '["鸡胸肉","花生","干辣椒","花椒"]', 22.00, 160, "糊辣荔枝味，经典名菜"),
    ("醋溜白菜", 1, "酸咸", '["白菜","醋","干辣椒"]', 10.00, 70, "酸甜爽脆，家常快手菜"),
    ("西红柿炒蛋", 1, "酸甜", '["番茄","鸡蛋","葱花"]', 12.00, 200, "国民家常菜，酸甜开胃"),
    ("鱼香肉丝", 1, "甜辣", '["猪里脊","木耳","胡萝卜","泡椒"]', 20.00, 145, "酸甜微辣，鱼香味型经典"),
    ("酱爆鸡丁", 1, "酱香", '["鸡胸肉","甜面酱","黄瓜","花生"]', 20.00, 100, "酱香浓郁，咸甜适口"),
    ("孜然牛肉", 1, "香辣", '["牛肉","孜然","辣椒粉","洋葱"]', 28.00, 115, "孜然香气扑鼻，烧烤风味"),
    ("腐乳通菜", 1, "咸鲜", '["空心菜","腐乳汁","蒜"]', 12.00, 50, "腐乳风味独特"),
    ("糖醋白菜", 1, "酸甜", '["白菜","糖","醋","酱油"]', 10.00, 65, "酸甜可口，开胃下饭"),
    ("凉拌黄瓜", 2, "酸辣", '["黄瓜","蒜","醋","辣椒油"]', 8.00, 120, "清脆爽口，夏日必备"),
    ("皮蛋豆腐", 2, "咸鲜", '["豆腐","皮蛋","葱花","生抽"]', 12.00, 70, "经典凉菜，简单美味"),
    ("凉拌木耳", 2, "酸辣", '["黑木耳","蒜","香菜","醋"]', 10.00, 80, "脆爽开胃"),
    ("红油肚丝", 2, "麻辣", '["猪肚","红油","花椒","蒜"]', 22.00, 75, "红油鲜亮，麻辣爽口"),
    ("凉拌三丝", 2, "咸鲜", '["海带丝","豆皮","胡萝卜丝","醋"]', 10.00, 55, "清爽解腻"),
    ("蒜泥白肉", 2, "蒜香", '["五花肉","蒜泥","生抽","辣椒油"]', 20.00, 88, "肥而不腻，蒜香浓郁"),
    ("紫菜蛋花汤", 3, "清淡", '["紫菜","鸡蛋","虾皮"]', 8.00, 80, "清淡鲜美，家常汤品"),
    ("番茄鸡蛋汤", 3, "酸甜", '["番茄","鸡蛋","葱花"]', 8.00, 85, "酸甜开胃"),
    ("玉米排骨汤", 3, "甜味", '["排骨","玉米","胡萝卜","姜"]', 22.00, 110, "汤清味甜，营养丰富"),
    ("酸辣汤", 3, "酸辣", '["豆腐","木耳","鸡蛋","醋","胡椒"]', 12.00, 90, "酸辣开胃，暖身暖胃"),
    ("鲫鱼豆腐汤", 3, "咸鲜", '["鲫鱼","豆腐","姜","葱"]', 24.00, 100, "汤白如奶，鲜美滋补"),
    ("疙瘩汤", 3, "酸咸", '["面粉","番茄","鸡蛋","香菜"]', 10.00, 75, "家常暖胃汤"),
    ("菌菇汤", 3, "咸鲜", '["香菇","金针菇","杏鲍菇","鸡蛋"]', 16.00, 65, "菌香浓郁，鲜美无比"),
    ("蛋炒饭", 4, "咸鲜", '["米饭","鸡蛋","葱花","火腿"]', 10.00, 220, "经典主食，粒粒分明"),
]

r = subprocess.run(MYSQL + ["-N", "-e", "SELECT COALESCE(MAX(dish_id),0) FROM dish_info"], capture_output=True, text=True)
start_id = int(r.stdout.strip()) + 1

s = requests.Session()
s.headers.update({"User-Agent": "Mozilla/5.0"})
s.proxies = {"http": None, "https": None}
s.get("https://image.baidu.com/", timeout=10)

ok = 0
for i, (name, cat, taste, ing, price, heat, desc) in enumerate(dishes):
    did = start_id + i
    print(f"[{i+1}/30] dish_{did} {name}...", end=" ", flush=True)
    sql = (f"INSERT INTO dish_info (dish_id, dish_name, category_id, taste, ingredient, "
           f"price, heat, description, image_url, status) VALUES ({did}, "
           f"'{name.replace(chr(39),chr(39)+chr(39))}', {cat}, '{taste}', '{ing}', "
           f"{price:.2f}, {heat}, '{desc.replace(chr(39),chr(39)+chr(39))}', "
           f"'/images/dish_{did}/20260501_{did}.jpg', 1)")
    subprocess.run(MYSQL + ["-e", sql], capture_output=True)
    d = os.path.join(BASE, f"dish_{did}")
    os.makedirs(d, exist_ok=True)
    dst = os.path.join(d, f"20260501_{did}.jpg")
    r = s.get("https://image.baidu.com/search/acjson",
              params={"tn": "resultjson_com", "word": name, "pn": 0, "rn": 10}, timeout=15)
    img_ok = False
    if r.status_code == 200:
        for item in r.json(strict=False).get("data", []):
            if not isinstance(item, dict): continue
            u = item.get("middleURL") or item.get("thumbURL") or ""
            if not u or u.startswith("data:"): continue
            try:
                resp = requests.get(u, headers={"User-Agent":"Mozilla/5.0", "Referer":"https://image.baidu.com/"}, timeout=15, proxies={"http":None,"https":None})
                if resp.status_code == 200 and len(resp.content) > 8000:
                    for f in os.listdir(d): os.remove(os.path.join(d, f))
                    with open(dst, "wb") as f: f.write(resp.content)
                    img = Image.open(dst)
                    if img.mode != "RGB": img = img.convert("RGB")
                    img.save(dst, "JPEG", quality=92)
                    img_ok = True
                    break
            except: continue
    if img_ok:
        ok += 1; print("OK")
    else:
        print("DB_OK(IMG_FAIL)")
    time.sleep(1.5)

print(f"\n{len(dishes)}道入库, {ok}张图片下载成功")
print("重启后端 + Ctrl+F5 刷新")
