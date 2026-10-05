# 参与开发

感谢你愿意改进这个项目。为了让变更容易审阅、验证和回滚，请遵循下面的约定。

## 本地开发

1. 安装 JDK 17、Docker 与 Docker Compose。
2. 复制 `.env.example` 为 `.env`，并填写 API Key。
3. 使用 `docker compose up -d` 启动外部依赖。
4. 使用 `./mvnw spring-boot:run` 启动应用。

Windows PowerShell 中可将命令替换为 `Copy-Item .env.example .env` 和 `.\mvnw.cmd`。

## 代码约定

- Java 与 Python 使用 4 空格缩进，YAML 与 JSON 使用 2 空格缩进；Maven XML 延续项目现有的 Tab 缩进。
- 源码单行不超过 120 个字符，Java 不使用通配符 import。
- 优先使用构造器注入；Controller 只处理协议转换，业务规则保留在 Service 层。
- Request、Response 与 Entity 各自承担明确职责，不直接把持久化对象作为新增接口的请求模型。
- 新增配置时同步更新 `.env.example` 或 `docs/configuration.md`。
- 不在日志、测试数据或提交记录中写入 API Key、密码等敏感信息。

仓库根目录的 `.editorconfig` 与 `.gitattributes` 负责编辑器和换行符约定。提交前运行无第三方依赖的风格检查：

```bash
python scripts/quality/check_style.py
```

## 测试约定

提交前至少运行：

```bash
./mvnw test
```

默认测试不依赖外部基础设施。涉及数据库、索引或完整 RAG 链路时，请先启动 Docker Compose，再运行：

```bash
./mvnw verify -Pintegration-tests
```

`src/test/java/.../explore` 下的类用于生成分析材料，不属于回归测试，不应放入默认 CI。

## 提交约定

提交信息使用 Conventional Commits，统一使用英文：

- 格式：`<type>(<scope>): <summary>`
- 摘要使用祈使语气、小写开头、不加句号，最长 72 个字符。
- 常用 type：`feat`、`fix`、`refactor`、`perf`、`test`、`docs`、`build`、`ci`、`chore`、`revert`。
- 常用 scope：`api`、`chunk`、`dataset`、`eval`、`index`、`retrieval`、`docs`、`repo`。

示例：

```text
feat(retrieval): add reciprocal rank fusion
fix(index): preserve retry count after task recovery
docs(repo): clarify local development workflow
```

可以启用仓库内置的提交模板：

```bash
git config commit.template .gitmessage
```

检查当前提交信息：

```bash
python scripts/quality/check_commit_messages.py HEAD
```

CI 只检查本次 push 或 pull request 新增的提交，不追溯早期历史。一次提交只解决一个主题。业务行为变化必须在提交说明中明确指出，并同步更新对应测试和文档。已经推送的提交不通过 rebase 改写；需要整理时，在合并前 squash 当前分支。
