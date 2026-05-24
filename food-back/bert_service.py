#!/usr/bin/env python3
"""
BERT NER模型服务
提供HTTP接口供Java应用调用

<功能说明>
- 加载预训练的BERT NER模型
- 提供实体提取接口，从用户输入中提取口味、菜品和忌口信息
- 提供健康检查接口
- 提供文本嵌入向量生成接口
- 当模型加载失败时，使用基于规则的回退方法

<API接口>
- GET /health: 健康检查
- POST /extract: 实体提取
- POST /embedding: 文本嵌入向量生成

<标签定义>
- B-TASTE: 口味开始
- I-TASTE: 口味中间
- B-DISH: 菜品开始
- I-DISH: 菜品中间
- B-TABOO: 忌口开始
- I-TABOO: 忌口中间
- O: 其他
"""

import os
import sys
import torch
from flask import Flask, request, jsonify
from transformers import AutoTokenizer, AutoModelForTokenClassification

# 添加当前目录到Python路径，确保模块能正确导入
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

# 创建Flask应用实例
app = Flask(__name__)

# 全局变量，用于存储模型、分词器和设备信息
model = None  # BERT NER模型
tokenizer = None  # 分词器
device = None  # 运行设备（CPU或GPU）

# 标签映射，将模型输出的标签ID映射到具体标签
ID2LABEL = {
    0: "O",          # 其他
    1: "B-TASTE",    # 口味开始
    2: "I-TASTE",    # 口味中间
    3: "B-DISH",     # 菜品开始
    4: "I-DISH",     # 菜品中间
    5: "B-TABOO",    # 忌口开始
    6: "I-TABOO"     # 忌口中间
}

def load_model():
    """
    加载BERT NER模型

    <功能>
    - 从指定路径加载预训练的BERT NER模型和分词器
    - 设置模型运行设备（优先使用GPU）
    - 将模型设置为评估模式

    <异常处理>
    - 检查模型路径是否存在
    - 捕获并打印模型加载过程中的异常
    """
    global model, tokenizer, device

    try:
        model_path = "bert_ner_model_next"  # 模型存储路径

        print(f"正在加载模型从: {model_path}")

        # 检查模型文件是否存在
        if not os.path.exists(model_path):
            raise FileNotFoundError(f"模型路径不存在: {model_path}")

        # 加载tokenizer和模型
        tokenizer = AutoTokenizer.from_pretrained(model_path)
        model = AutoModelForTokenClassification.from_pretrained(
            model_path,
            num_labels=len(ID2LABEL)  # 指定标签数量
        )

        # 设置设备（优先使用GPU）
        device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        model.to(device)
        model.eval()  # 设置为评估模式

        print(f"模型加载成功，使用设备: {device}")

    except Exception as e:
        print(f"模型加载失败: {e}")
        raise

def extract_entities(text):
    """
    使用BERT模型提取实体

    <参数>
    - text: 待提取实体的文本

    <返回值>
    - entities: 提取的实体字典，包含TASTE、DISH、TABOO三类实体

    <功能>
    - 对输入文本进行分词
    - 使用模型进行推理
    - 从预测结果中提取实体
    """
    if model is None or tokenizer is None:
        raise RuntimeError("模型未加载")

    # 分词，返回PyTorch张量
    tokens = tokenizer(text, return_tensors="pt", padding=True, truncation=True, max_length=512)

    # 获取word_ids，用于映射token到原始文本的位置
    word_ids = tokens.word_ids(batch_index=0)

    # 移动到设备（GPU或CPU）
    tokens = {k: v.to(device) for k, v in tokens.items()}

    # 模型推理（禁用梯度计算，提高速度）
    with torch.no_grad():
        outputs = model(**tokens)

    # 获取预测结果（取概率最大的标签）
    predictions = torch.argmax(outputs.logits, dim=-1)

    # 转换为标签
    predicted_labels = [ID2LABEL[label_id] for label_id in predictions[0].cpu().numpy()]

    # 提取实体
    entities = extract_entities_from_labels(predicted_labels, word_ids, text)

    return entities

def extract_entities_from_labels(labels, word_ids, original_text):
    """
    从预测标签中提取实体

    <参数>
    - labels: 预测的标签列表
    - word_ids: token对应的word ID列表
    - original_text: 原始文本

    <返回值>
    - entities: 提取的实体字典

    <功能>
    - 遍历标签，识别实体的开始和结束位置
    - 提取实体文本并分类
    """
    # 初始化实体字典
    entities = {
        "TASTE": [],  # 口味
        "DISH": [],   # 菜品
        "TABOO": []   # 忌口
    }

    current_entity = None  # 当前实体类型
    current_start = None   # 当前实体开始位置
    current_end = None     # 当前实体结束位置

    # 遍历标签和对应的word ID
    for i, (label, word_id) in enumerate(zip(labels, word_ids)):
        if word_id is None:
            # 特殊token，跳过
            continue

        if label.startswith("B-"):
            # 开始新实体
            if current_entity:
                # 保存前一个实体
                entity_type = current_entity.replace("B-", "").replace("I-", "")
                if entity_type in entities:
                    entity_text = original_text[current_start:current_end]
                    entities[entity_type].append(entity_text)

            # 开始新实体
            current_entity = label
            current_start = word_id
            current_end = word_id + 1

        elif label.startswith("I-") and current_entity:
            # 继续当前实体
            entity_type = current_entity.replace("B-", "").replace("I-", "")
            if label.replace("I-", "") == entity_type:
                current_end = word_id + 1
        else:
            # 结束当前实体
            if current_entity:
                entity_type = current_entity.replace("B-", "").replace("I-", "")
                if entity_type in entities:
                    entity_text = original_text[current_start:current_end]
                    entities[entity_type].append(entity_text)
                # 重置状态
                current_entity = None
                current_start = None
                current_end = None

    # 处理最后一个实体
    if current_entity:
        entity_type = current_entity.replace("B-", "").replace("I-", "")
        if entity_type in entities:
            entity_text = original_text[current_start:current_end]
            entities[entity_type].append(entity_text)

    return entities

@app.route('/health', methods=['GET'])
def health_check():
    """
    健康检查接口

    <返回>
    - status: 服务状态
    - model_loaded: 模型是否加载成功
    """
    return jsonify({
        "status": "healthy",
        "model_loaded": model is not None
    })

@app.route('/extract', methods=['POST'])
def extract_entities_endpoint():
    """
    实体提取接口

    <请求参数>
    - text: 待提取实体的文本

    <返回>
    - originalInput: 原始输入文本
    - entities: 提取的实体字典
    - method: 使用的提取方法（bert_ner或rule_based）
    """
    try:
        data = request.get_json()

        if not data or 'text' not in data:
            return jsonify({
                "error": "缺少text参数"
            }), 400

        text = data['text']

        if model is None:
            # 模型未加载，使用基于规则的回退方法
            entities = fallback_extraction(text)
            return jsonify({
                "originalInput": text,
                "entities": entities,
                "method": "rule_based"
            })

        # 使用BERT模型提取实体
        entities = extract_entities(text)

        return jsonify({
            "originalInput": text,
            "entities": entities,
            "method": "bert_ner"
        })

    except Exception as e:
        return jsonify({
            "error": str(e),
            "originalInput": data.get('text', '') if data else ''
        }), 500

@app.route('/embedding', methods=['POST'])
def get_embedding_endpoint():
    """
    嵌入向量生成接口

    <请求参数>
    - text: 待生成嵌入向量的文本

    <返回>
    - originalInput: 原始输入文本
    - embedding: 文本的BERT嵌入向量
    - dimension: 嵌入向量的维度
    """
    try:
        data = request.get_json()

        if not data or 'text' not in data:
            return jsonify({
                "error": "缺少text参数"
            }), 400

        text = data['text']

        if model is None:
            return jsonify({
                "error": "模型未加载",
                "originalInput": text
            }), 500

        # 获取文本嵌入向量
        embedding = get_text_embedding(text)

        if embedding is None:
            return jsonify({
                "error": "嵌入向量生成失败",
                "originalInput": text
            }), 500

        return jsonify({
            "originalInput": text,
            "embedding": embedding,
            "dimension": len(embedding)
        })

    except Exception as e:
        return jsonify({
            "error": str(e),
            "originalInput": data.get('text', '') if data else ''
        }), 500

def get_text_embedding(text):
    """
    获取文本的BERT嵌入向量

    <参数>
    - text: 待生成嵌入向量的文本

    <返回值>
    - embedding: 文本的BERT嵌入向量（列表形式）

    <功能>
    - 对文本进行分词
    - 使用模型生成隐藏状态
    - 对最后一层隐藏状态进行平均池化，得到文本嵌入向量
    """
    if model is None or tokenizer is None:
        return None

    # 分词
    tokens = tokenizer(text, return_tensors="pt", padding=True, truncation=True, max_length=512)

    # 移动到设备
    tokens = {k: v.to(device) for k, v in tokens.items()}

    # 模型推理，获取隐藏状态
    with torch.no_grad():
        outputs = model(**tokens, output_hidden_states=True)

    # 获取最后一层隐藏状态的平均池化作为嵌入向量
    last_hidden_state = outputs.hidden_states[-1]
    embedding = last_hidden_state.mean(dim=1).squeeze().cpu().numpy()

    return embedding.tolist()

def fallback_extraction(text):
    """
    基于规则的回退实体提取

    <参数>
    - text: 待提取实体的文本

    <返回值>
    - entities: 提取的实体字典

    <功能>
    - 当BERT模型加载失败时，使用基于关键词匹配的方法提取实体
    """
    entities = {
        "TASTE": [],
        "DISH": [],
        "TABOO": []
    }

    text_lower = text.lower()

    # 口味关键词
    taste_keywords = {
        "TASTE": ["辣", "甜", "酸", "咸", "清淡", "麻辣", "酸甜", "咸鲜"]
    }

    # 忌口关键词
    taboo_keywords = {
        "TABOO": ["香菜", "海鲜", "猪肉", "牛肉", "羊肉", "不吃", "不要", "忌口","辣"]
    }

    # 菜品关键词
    dish_keywords = {
        "DISH": ["宫保鸡丁", "鱼香肉丝", "麻婆豆腐", "红烧肉", "糖醋里脊"]
    }

    # 简单关键词匹配
    for entity_type, keywords in taste_keywords.items():
        for keyword in keywords:
            if keyword in text:
                entities[entity_type].append(keyword)

    for entity_type, keywords in taboo_keywords.items():
        for keyword in keywords:
            if keyword in text:
                entities[entity_type].append(keyword)

    for entity_type, keywords in dish_keywords.items():
        for keyword in keywords:
            if keyword in text:
                entities[entity_type].append(keyword)

    return entities

if __name__ == '__main__':
    # 加载模型
    try:
        load_model()
    except Exception as e:
        print(f"模型加载失败，将使用基于规则的方法: {e}")

    # 启动服务
    print("BERT NER服务启动中...")
    app.run(host='0.0.0.0', port=5000, debug=False)  # 0.0.0.0表示监听所有网络接口