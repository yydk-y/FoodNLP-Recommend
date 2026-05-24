# BERT NER模型集成文档

## 概述

本项目成功集成了您训练的BERT NER模型（位于`bert_ner_model_next`目录）到Spring Boot应用中，用于从用户输入中提取食物偏好信息，包括口味偏好、忌口和菜品偏好。

## 项目结构

```
food-back/
├── bert_ner_model_next/          # 您的训练好的BERT模型
│   ├── config.json
│   ├── model.safetensors
│   ├── tokenizer.json
│   └── tokenizer_config.json
├── bert_service.py               # Python BERT服务
├── start_bert_service.bat        # BERT服务启动脚本
├── system/src/main/java/com/SFood/system/service/
│   ├── NLPService.java           # NLP服务接口
│   └── impl/
│       ├── BERTNLPServiceImpl.java    # BERT服务实现
│       ├── BERTNERModel.java          # BERT模型封装（备用）
│       └── BERTServiceClient.java     # BERT服务客户端
└── admin/src/main/java/com/SFood/admin/controller/
    └── NLPController.java        # NLP REST API控制器
```

## 集成架构

### 1. 架构设计

采用微服务架构，通过Python Flask服务包装BERT模型，Java应用通过REST API调用：

```
Java Spring Boot应用 (端口8080) ←HTTP→ Python BERT服务 (端口5000) ←PyTorch→ BERT NER模型
```

### 2. 核心组件

- **NLPService接口**: 定义食物偏好提取功能
- **BERTNLPServiceImpl**: 主要的服务实现，使用BERT服务客户端
- **BERTServiceClient**: 负责与Python BERT服务通信
- **NLPController**: 提供REST API接口
- **bert_service.py**: Python Flask服务，加载和运行BERT模型

## 使用方法

### 1. 启动BERT服务

在启动Java应用之前，需要先启动Python BERT服务：

```bash
# 方式1: 使用启动脚本（Windows）
start_bert_service.bat

# 方式2: 手动启动
python bert_service.py
```

BERT服务将在 `http://localhost:5000` 启动。

### 2. 启动Java应用

```bash
# 在项目根目录
mvn clean install
mvn spring-boot:run
```

Java应用将在 `http://localhost:8080` 启动。

### 3. API使用示例

#### 单个文本提取

```http
POST /api/nlp/extract-preferences
Content-Type: application/json

{
    "userInput": "我不喜欢吃辣，喜欢吃甜的，不要香菜"
}
```

**响应示例:**
```json
{
    "code": 200,
    "message": "成功",
    "data": {
        "originalInput": "我不喜欢吃辣，喜欢吃甜的，不要香菜",
        "tastes": ["甜"],
        "taboos": ["辣", "香菜"],
        "dishes": []
    }
}
```

#### 批量提取

```http
POST /api/nlp/extract-preferences-batch
Content-Type: application/json

{
    "userInputs": [
        "我喜欢吃辣的",
        "我不吃海鲜",
        "来一份宫保鸡丁"
    ]
}
```

#### 健康检查

```http
GET /api/nlp/health
```

## 模型训练代码回顾

您的BERT NER模型训练代码具有以下特点：

### 1. 数据预处理
- **纯精准匹配**: 无规则、无正则增强，直接字符串匹配
- **实体类型**: TASTE（口味）、DISH（菜品）、TABOO（忌口）
- **标签格式**: BIO标注（B-XXX, I-XXX, O）

### 2. 模型训练
- **模型架构**: BERT-based Token Classification
- **训练策略**: 混合精度训练（FP16）、梯度裁剪、学习率调度
- **验证机制**: 基于验证损失的最佳模型保存

### 3. 数据集构建
- **对齐处理**: 处理BERT分词与字符级标注的对齐
- **数据分割**: 80%训练集，20%验证集

## 故障排除

### 1. BERT服务无法启动

**问题**: Python依赖缺失
**解决**: 
```bash
pip install torch transformers flask
```

### 2. 模型加载失败

**问题**: 模型文件路径错误
**解决**: 确保`bert_ner_model_next`目录包含完整的模型文件

### 3. Java应用无法连接BERT服务

**问题**: 端口冲突或服务未启动
**解决**: 检查5000端口是否被占用，确保BERT服务正常运行

### 4. 实体提取效果不佳

**问题**: 模型泛化能力不足
**解决**: 
- 检查训练数据质量
- 考虑数据增强
- 调整模型超参数

## 性能优化建议

### 1. 模型优化
- **量化**: 使用INT8量化减少模型大小
- **剪枝**: 移除不重要的权重
- **蒸馏**: 使用知识蒸馏训练更小的模型

### 2. 服务优化
- **批处理**: 支持批量推理提高吞吐量
- **缓存**: 缓存常见查询结果
- **异步**: 使用异步处理提高并发性能

### 3. 部署优化
- **Docker化**: 容器化部署
- **负载均衡**: 多实例部署
- **监控**: 添加性能监控和日志

## 扩展功能

### 1. 实时学习
实现用户反馈机制，持续优化模型：
- 用户对提取结果的确认/修正
- 增量学习更新模型

### 2. 多语言支持
扩展支持其他语言的食品偏好提取

### 3. 个性化推荐
结合用户历史数据，提供个性化菜品推荐

## 技术栈

- **后端**: Spring Boot 3.1.5, Java 21
- **NLP**: BERT (PyTorch), Transformers库
- **API**: RESTful API, JSON
- **通信**: HTTP/REST
- **构建**: Maven

## 开发团队

- **模型训练**: 您的BERT NER模型训练代码
- **系统集成**: AI助手基于您的代码完成集成
- **测试验证**: 需要实际测试验证集成效果

## 后续工作

1. **测试验证**: 实际测试API接口和模型效果
2. **性能测试**: 压力测试和性能优化
3. **用户界面**: 开发前端界面方便用户使用
4. **监控告警**: 添加系统监控和异常告警

---

**注意**: 本集成基于您的训练代码和项目结构，实际效果需要通过测试验证。如有问题请检查模型文件完整性和服务配置。