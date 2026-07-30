# AIXM 5.1.1 几何编码工具

该工具把版本化 JSON 输入编码为独立的 AIXM 5.1.1/GML 3.2.1
几何片段。公共代码位于 `com.example.aixm.geometry`，不依赖 Digital
NOTAM 领域对象。

## V1 范围

- 支持 `POINT`、`LINE`、`POLYGON`、`CIRCLE`、`CIRCLE_SECTOR`、
  `CORRIDOR`。
- `LINE` 和 `POLYGON` 支持 `GEODESIC`、`PARALLEL`、
  `ARC_BY_EDGE`、`ARC_BY_CENTER`。
- `POLYGON` 只支持一个外环，不支持内环。
- 只接受 WGS-84 输入。
- 不支持 SignificantPoint 引用和图形预览。

## 构建与测试

项目提供 Maven Wrapper，无需预装 Maven：

```powershell
.\mvnw.cmd test
```

运行 Digital NOTAM Demo：

```powershell
.\run.ps1
```

## Java API

```java
AixmGeometryService service = new DefaultAixmGeometryService();
EncodingResult result = service.encode(json);

if (result.valid()) {
    String aixmFragment = result.aixmXml();
} else {
    result.issues().forEach(System.out::println);
}
```

默认服务从以下位置加载 AIXM 5.1.1 Schema：

```text
schemas/aixm-5.1.1/aixm-5.1.1/AIXM_Features.xsd
```

其他项目可以通过构造函数传入自己的 Schema 路径：

```java
new DefaultAixmGeometryService(aixmFeaturesSchema);
```

## Demo HTTP API

```http
POST /api/aixm/geometry
Content-Type: application/json
```

成功时返回 `200 application/xml` 和独立 AIXM 片段；校验失败时返回
`400 application/json` 和结构化问题列表。

## 通用 JSON 约定

所有请求必须包含：

```json
{
  "schemaVersion": "1.0",
  "geometry": {
    "type": "POINT",
    "gmlId": "point-001",
    "crs": "EPSG:4326",
    "position": {
      "x": 5.20833,
      "y": 52.18556
    }
  }
}
```

- `x`固定表示经度。
- `y`固定表示纬度。
- `crs`必须为`EPSG:4326`或`urn:ogc:def:crs:EPSG::4326`。
- JSON采用`x y`顺序；EPSG:4326 GML输出采用纬度、经度轴顺序。
- `gmlId`可省略。省略时工具生成带类型前缀的随机UUID。
- 调用方提供的`gmlId`必须是合法XML NCName，且在片段中唯一。

上述坐标输出为：

```xml
<gml:pos>52.18556 5.20833</gml:pos>
```

## 可选高程

Point、Line、Polygon和Circle/CircleSector可以包含：

```json
{
  "elevation": {
    "elevation": {"value": 30, "uom": "M"},
    "geoidUndulation": {"value": -2.5, "uom": "M"},
    "verticalDatum": "EGM_96",
    "verticalAccuracy": {"value": 1, "uom": "M"}
  }
}
```

四个属性相互独立；提供哪个就编码哪个。只要存在任一属性，就生成相应的
`ElevatedPoint`、`ElevatedCurve`或`ElevatedSurface`。

## Line

```json
{
  "schemaVersion": "1.0",
  "geometry": {
    "type": "LINE",
    "gmlId": "line-001",
    "crs": "EPSG:4326",
    "segments": [
      {
        "type": "GEODESIC",
        "positions": [
          {"x": 5.0, "y": 52.0},
          {"x": 6.0, "y": 52.0}
        ]
      },
      {
        "type": "PARALLEL",
        "start": {"x": 6.0, "y": 52.0},
        "end": {"x": 7.0, "y": 52.0}
      },
      {
        "type": "ARC_BY_EDGE",
        "start": {"x": 7.0, "y": 52.0},
        "through": {"x": 7.5, "y": 52.5},
        "end": {"x": 8.0, "y": 52.0}
      }
    ]
  }
}
```

相邻段必须首尾连续。工具不会自动插入连接线。

`ARC_BY_CENTER`使用中心、半径和真北起止角：

```json
{
  "type": "ARC_BY_CENTER",
  "center": {"x": 5.0, "y": 52.0},
  "radius": {"value": 10, "uom": "NM"},
  "startAngle": 0,
  "endAngle": 90
}
```

工具使用WGS-84椭球推导圆弧端点，用于相邻段连续性校验。

## Polygon

Polygon使用与Line相同的`segments`。最后一个线段的终点必须等于第一个
线段的起点。工具检查边界闭合和显式控制点形成的拓扑，不接受内环字段。

## CircleSector

```json
{
  "schemaVersion": "1.0",
  "geometry": {
    "type": "CIRCLE_SECTOR",
    "gmlId": "sector-001",
    "crs": "EPSG:4326",
    "center": {"x": 5.0, "y": 52.0},
    "innerRadius": {"value": 5, "uom": "NM"},
    "outerRadius": {"value": 10, "uom": "NM"},
    "startAngle": 90,
    "endAngle": 180,
    "angleReference": "MAGNETIC_NORTH",
    "magneticVariation": {
      "value": 2,
      "direction": "EAST"
    }
  }
}
```

- `innerRadius`可省略。
- `angleReference`支持`TRUE_NORTH`和`MAGNETIC_NORTH`。
- 磁北角必须由调用方提供`magneticVariation`。
- 东磁差加到磁航向，西磁差从磁航向中减去，以得到编码所需真北角。
- V1不支持`RADIAL`。

## Corridor

```json
{
  "schemaVersion": "1.0",
  "geometry": {
    "type": "CORRIDOR",
    "gmlId": "volume-001",
    "crs": "EPSG:4326",
    "centreline": {
      "gmlId": "centreline-001",
      "segments": [
        {
          "type": "GEODESIC",
          "positions": [
            {"x": 5.0, "y": 52.0},
            {"x": 6.0, "y": 53.0}
          ]
        }
      ]
    },
    "width": {"value": 10, "uom": "NM"}
  }
}
```

`width`是AIXM完整宽度，不是half-width。输出根元素为：

```xml
<aixm:AirspaceVolume>
  <aixm:width/>
  <aixm:centreline>
    <aixm:Curve/>
  </aixm:centreline>
</aixm:AirspaceVolume>
```

## 诊断格式

```json
{
  "valid": false,
  "issues": [
    {
      "severity": "ERROR",
      "rule": "GEOM-POLYGON-001",
      "path": "/geometry/segments",
      "message": "多边形最后一个线段的终点必须等于第一个线段的起点"
    }
  ]
}
```

规则编号和JSON路径属于公共错误契约，调用方不应解析自然语言消息。
