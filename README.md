# Digital NOTAM Demo

面向航空领域的 Digital NOTAM 演示系统，展示通告创建、发布和查询流程。后端使用 Java 21 标准库，前端使用 Vue 3 + Vite，示例交换数据采用 AIXM 5.1.1 风格 XML。

## 运行

```powershell
.\run.ps1
```

启动后访问 <http://localhost:8080>。脚本会将 Java 源码按 `--release 21` 编译后启动，不需要 Maven。

如需修改并重新构建前端：

```powershell
cd frontend
npm install
npm run build
```

构建结果会输出到 `src/main/resources/public`。

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
