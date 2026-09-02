# 柔性引擎与本体化行为复现系统

这是一个独立实现的、可提交源码的复现系统。它依据当前工作区中的接口契约、静态调用链、领域对象和历史行为材料，复现柔性字段、流程状态、本体对象、关系配置以及兼容服务的可运行行为。

## 重要边界

本项目不是原始业务系统的源码复制，也不连接原生产环境。由本项目产生的数据应标记为 `REPRODUCED_SYSTEM_RUN`，表示“本文复现系统的真实运行结果”，不能表述为原系统生产指标。

## 当前阶段

当前版本先提供：

- Java 8 语法/字节码目标的 Maven 多模块骨架。
- 柔性字段、动态记录、工作流状态机和上下文快照。
- Questionnaire、Subject、Option、JobOntologyDetail 与关系组装。
- 本地 Provider/Consumer 兼容服务。
- 20 条契约实验规格文件。
- 可运行的命令行 smoke demo。

## 构建与运行

```powershell
mvn test
mvn package
java -jar reproduction-app/target/reproduction-app-0.1.0-SNAPSHOT.jar
java -jar reproduction-app/target/reproduction-app-0.1.0-SNAPSHOT.jar contract
java -jar reproduction-app/target/reproduction-app-0.1.0-SNAPSHOT.jar contract experiments/contract-20/contract-20.csv runs/contract-20/custom-seed 20260903
# 或使用统一脚本
./scripts/run-contract.ps1
```

当前开发机使用 JDK 17 和 Maven 3.8.6；源码保持 Java 8 兼容语法。`contract` 模式会执行 20 条契约规格并生成 `runs/contract-20/latest` 下的逐用例运行文件。
