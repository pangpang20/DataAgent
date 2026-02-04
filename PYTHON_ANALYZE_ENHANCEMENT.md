# Python Analyze 提示词增强说明

## 修改概述

对 `python-analyze.txt` 提示词进行了增强,使 `PythonAnalyzeNode` 不仅能生成文本总结,还能根据数据特点生成 ECharts 图表配置,提升数据可视化效果。

## 修改文件

**文件路径**: `data-agent-management/src/main/resources/prompts/python-analyze.txt`

## 核心改进

### 1. 双重输出能力
- **文本总结**: 保留原有的自然语言总结功能
- **图表配置**: 新增 ECharts 图表配置生成能力(可选)

### 2. 智能判断机制
LLM 会根据数据特征自动判断是否需要生成图表:
- ✅ **生成图表**: 时间序列、分类对比、占比关系、数据分布等
- ❌ **不生成图表**: 简单数值统计、文本描述、错误结果等

### 3. 图表类型选择指南
提供了清晰的图表类型选择表:

| 数据特征     | 推荐图表类型 | ECharts type |
| ------------ | ------------ | ------------ |
| 时间序列趋势 | 折线图       | line         |
| 分类数据对比 | 柱状图       | bar          |
| 占比关系     | 饼图         | pie          |
| 数据分布     | 散点图       | scatter      |
| 多维度对比   | 雷达图       | radar        |
| 地理数据     | 地图         | map          |

### 4. 输出格式规范

**纯文本总结**:
```text
[文本总结内容]
```

**文本+图表**:
```text
[文本总结内容]

```echarts
{
  "title": { "text": "图表标题" },
  "tooltip": { "trigger": "axis" },
  "xAxis": { "type": "category", "data": ["类别1", "类别2"] },
  "yAxis": { "type": "value" },
  "series": [{ "type": "bar", "data": [值1, 值2] }]
}
```
```

## 示例场景

### 示例 1: 带图表的分析结果
**输入**: 统计各渠道的线索数量和转化率
**输出**: 文本总结 + 柱状图和折线图组合的双轴图表

### 示例 2: 只有文本总结
**输入**: 计算总销售额
**输出**: 仅文本总结(简单数值不需要图表)

### 示例 3: 时间序列趋势
**输入**: 展示过去6个月的销售趋势
**输出**: 文本总结 + 面积折线图

## 技术细节

### 与前端渲染的集成

生成的 ECharts 配置会被前端的 `ReportTemplateUtil` 处理:
1. Markdown 代码块的语言标记识别为 `echarts`
2. 使用 `new Function('return ' + code)()` 解析配置对象
3. 通过 ECharts 库渲染成可交互的图表

### 配置要求

1. **格式**: 必须是合法的 JSON 格式
2. **属性名**: 使用双引号包裹
3. **完整性**: 包含 title、tooltip、xAxis/yAxis、series 等必要配置
4. **数据准确性**: 数据必须完全来自 Python 输出,不得虚构

## 优势

1. **增强可视化**: Python 分析结果自动配合图表展示,更直观
2. **智能适配**: LLM 自动判断是否需要图表,避免不必要的可视化
3. **类型丰富**: 支持柱状图、折线图、饼图、散点图等多种图表类型
4. **用户体验**: 文本+图表的组合提供更好的数据洞察

## 测试建议

### 测试场景

1. **多维度对比数据** (应生成图表)
   - 示例: "分析各地区的销售业绩"
   - 预期: 文本总结 + 柱状图

2. **时间序列数据** (应生成图表)
   - 示例: "展示本月每天的访问量"
   - 预期: 文本总结 + 折线图

3. **简单统计** (不应生成图表)
   - 示例: "计算平均订单金额"
   - 预期: 仅文本总结

4. **异常情况** (不应生成图表)
   - Python 执行出错
   - 预期: 错误提示文本

## 注意事项

1. ⚠️ **JSON 格式**: 确保生成的配置是合法的 JSON
2. ⚠️ **数据真实性**: 图表数据必须来自 Python 输出
3. ⚠️ **适度可视化**: 不是所有数据都需要图表
4. ⚠️ **代码块标记**: 使用 `echarts` 作为语言标记

## 编译验证

✅ 后端编译成功,无错误
```
[INFO] BUILD SUCCESS
[INFO] Total time:  21.217 s
```

## 相关文件

- **提示词文件**: `data-agent-management/src/main/resources/prompts/python-analyze.txt`
- **调用节点**: `data-agent-management/src/main/java/com/audaque/cloud/ai/dataagent/workflow/node/PythonAnalyzeNode.java`
- **前端渲染**: `data-agent-management/src/main/java/com/audaque/cloud/ai/dataagent/util/ReportTemplateUtil.java`

## 更新日期

2026-02-04
