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
- React/Vite 中文实验控制台：总览、契约矩阵、Trace Inspector、柔性引擎、本体模型和运行记录。

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

## 前端可视化

前端位于 `frontend/`，当前使用已验证的 `contract-20` 运行结果作为只读展示快照；页面中的数据身份仍明确标记为 `REPRODUCED_SYSTEM_RUN`。启动后访问 `http://127.0.0.1:5174/`：

```powershell
Set-Location frontend
npm install
npm run dev
# 生产构建检查
npm run build
```

当前前端不把静态展示快照伪装成后端接口。下一阶段会让页面直接读取 `runs/contract-20/<run-id>` 的 manifest、report、request、response、trace 和 sha256 文件，再接入实验执行与导出接口。
