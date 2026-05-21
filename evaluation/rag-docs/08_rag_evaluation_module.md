# RAG Evaluation 评测模块说明

RAG Evaluation 模块用于对不同检索策略进行量化评估。核心类包括 RagEvaluationController、RagEvaluationService、RagEvaluationServiceImpl、EvalDataset、EvalCase、EvalRun、EvalCaseResult、StartEvalRunRequest 和 CompareEvalRunsRequest。基础路径为 /api/rag/eval。

EvalDataset 表示一个评测数据集，并绑定具体 spaceId。EvalCase 表示一个评测问题，包含 question、expectedAnswer、expectedChunkIds、expectedKeywords 和 difficulty。expectedChunkIds 用于计算检索相关指标，expectedKeywords 用于计算生成回答中的关键词命中情况。

EvalRun 表示一次评测运行，记录 datasetId、runName、retrievalMode、topK、candidateK、vectorWeight、keywordWeight 和 enableAnswerGeneration 等配置。启动 EvalRun 后，系统会遍历该数据集下的所有 EvalCase。每个 case 会构造 SearchChunksRequest，并调用 RagRetrievalService.search 执行检索。系统随后记录 retrievedChunkIds，并计算 Recall@K、Hit@K 和 MRR。

Recall@K 表示前 K 个结果召回了多少比例的 expectedChunkIds。Hit@K 表示前 K 个结果中是否命中至少一个 expectedChunkId。MRR 表示第一个正确 chunk 的倒数排名。如果 enableAnswerGeneration 为 true，系统还会调用 ChatClient 生成回答，并计算 Answer Keyword Hit。

EvalCaseResult 保存单个评测问题的结果，EvalRun 保存整体汇总指标。compare run 接口可以对多个 EvalRun 进行比较，常用于比较 VECTOR_ONLY、KEYWORD_ONLY、HYBRID 和 HYBRID_RERANK 的检索效果。
