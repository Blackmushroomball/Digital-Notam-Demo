# Digital NOTAM Demo

面向航空领域的 Digital NOTAM 演示系统，展示通告创建、发布和查询流程。后端使用 Java 21 标准库，前端使用 Vue 3 + Vite，示例交换数据采用 AIXM 5.1.1 风格 XML。

## 运行

```powershell
.\run.ps1
```

启动后访问 <http://localhost:8080>。脚本先使用 NVM 当前选中的 Node/npm 执行 Vite 构建，再使用仓库内置的 Maven Wrapper 将 Java 源码按 `--release 21` 编译并启动；不需要全局安装 Maven。首次运行前应先在当前 PowerShell 中通过 `nvm use <version>` 选择 Node 20.19+ 或 22.12+，并在 `frontend` 目录执行一次 `npm install`。

前端构建结果输出到 `src/main/resources/public`，因此修改前端后仍直接运行 `run.ps1` 即可。

`verify-external-resources.ps1` 只校验外部模板和虚拟数据目录的 SHA-256 指纹，确认这些基准资源未被意外修改；它不会编译或启动系统，不能替代 `run.ps1`。

## API

- `GET /api/notams`：查询通告，可使用 `status`、`q` 参数过滤
- `GET /api/notams/{id}`：获取单条通告
- `POST /api/notams`：创建草稿
- `POST /api/notams/{id}/publish`：发布通告并生成 AIXM XML
- `DELETE /api/notams/{id}`：删除通告
- `GET /api/notams/{id}/aixm`：下载/查看 AIXM 5.1.1 风格 XML

## 本地 XML 存储

通告发布后，AIXM XML 会写入 `data/notams/`。为兼容 Windows 文件名，通告编号中的 `/` 会转换为 `_`，例如 `A1001/26` 对应 `data/notams/A1001_26.xml`。删除通告时，对应 XML 文件也会同步删除；草稿不会生成 XML 文件。

> 本项目是业务流程 Demo。XML 展示了 AIXM 5.1.1 命名空间、时态切片和 Digital NOTAM 事件关联方式，但不替代正式的 AIXM XSD 全量校验与生产级数据质量规则。
