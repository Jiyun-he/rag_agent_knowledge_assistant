# RAG Evaluation 评测模块说明

RAG Evaluation 模块用于对不同检索策略进行量化评估。核心类包括 RagEvaluationController、RagEvaluationService、RagEvaluationServiceImpl、EvalDataset、EvalCase、EvalRun、EvalCaseResult、StartEvalRunRequest 和 CompareEvalRunsRequest。基础路径为 /api/rag/eval。

EvalDataset 表示一个评测数据集，并绑定具体 spaceId。EvalCase 表示一个评测问题，包含 question、expectedAnswer、expectedChunkIds、expectedKeywords 和 difficulty。expectedChunkIds 用于计算检索相关指标和引用相关指标，expectedKeywords 用于计算生成回答中的关键词命中情况。expectedAnswer 是预留字段，当前不参与任何评分，回答质量评测尚未实现。

EvalRun 表示一次评测运行，记录 datasetId、runName、retrievalMode、topK、candidateK 和 enableAnswerGeneration 等配置。启动 EvalRun 后，系统会遍历该数据集下的所有 EvalCase。每个 case 会构造 SearchChunksRequest，并调用 RagRetrievalService.search 执行检索。系统随后记录 retrievedChunkIds，并计算 Recall@K、Hit@K 和 MRR。

Recall@K 表示前 K 个结果召回了多少比例的 expectedChunkIds。Hit@K 表示前 K 个结果中是否命中至少一个 expectedChunkId。MRR 表示第一个正确 chunk 的倒数排名。评测同时记录 Average Latency，即每个 case 的平均耗时。

如果 enableAnswerGeneration 为 true，系统还会调用 ChatClient 生成回答，并在此基础上计算回答类指标。prompt 会把检索结果编号为 [Reference 1] 到 [Reference N] 并要求模型在回答中标注引用，评测侧用 CitationParser 从回答解析这些编号，并映射回本次检索结果的 chunkId，即编号 N 对应第 N 条检索结果。citationCount 表示解析到的有效引用数；citationPrecision = |引用∩期望| / (有效引用数 + 越界引用数)，其中越界引用即超出本次检索结果范围的编造引用，会拉低精确率；citationRecall = |引用∩期望| / |期望|。

grounded 表示回答的可溯源性：有至少一个有效引用且没有越界引用时为 1，否则为 0。Answer Keyword Hit、citationPrecision、citationRecall 和 grounded 都属于回答类指标，在未生成回答时为 NULL，表示不适用；汇总时只对非 NULL 的 case 求平均，而生成回答但没有引用则记 0。

EvalCaseResult 保存单个评测问题的结果，EvalRun 保存整体汇总指标。compare run 接口可以对多个 EvalRun 进行比较，常用于比较 VECTOR_ONLY、KEYWORD_ONLY、HYBRID 和 HYBRID_RERANK 的检索效果。
