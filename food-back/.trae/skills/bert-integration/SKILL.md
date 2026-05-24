---
name: "bert-integration"
description: "Integrates BERT models for NLP tasks like keyword extraction and text classification. Invoke when user wants to use real BERT models instead of rule-based approaches."
---

# BERT Integration Skill

This skill provides guidance and tools for integrating BERT (Bidirectional Encoder Representations from Transformers) models into applications for natural language processing tasks.

## When to Use

- User wants to replace rule-based keyword extraction with BERT models
- User needs text classification or named entity recognition
- User wants to implement advanced NLP features
- User asks for transformer model integration

## Supported Tasks

- Text classification
- Named entity recognition (NER)
- Keyword extraction
- Semantic similarity
- Text embedding

## Implementation Guidelines

1. **Model Selection**: Choose appropriate BERT variants (BERT-base, BERT-wwm, RoBERTa, etc.)
2. **Dependency Management**: Add transformers library dependencies
3. **Model Loading**: Implement efficient model loading and caching
4. **Inference Optimization**: Use batching and GPU acceleration
5. **Error Handling**: Implement proper exception handling for model inference