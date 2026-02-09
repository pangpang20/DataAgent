# 企业级数据运营平台 Web UI 设计需求文档

## 📋 目标定位

生成一个企业级数据运营平台 Web UI，风格稳重克制，强调结构化导航与高可读性。以"深色顶栏 + 浅色侧栏 + 大留白内容区"的三段式布局为核心。避免花哨装饰与过强阴影。

## 🎨 核心设计系统

### 1. 颜色系统

#### 主色调
- **主色（Primary）**: `#2F78F6`
  - 悬停态: `#4C8BF8`
  - 背景胶囊: `#E8F0FE`
  - 用于：主要操作按钮、链接、选中状态

#### 中性色
- **标题色**: `#1F2937` (Gray-900)
- **正文色**: `#374151` (Gray-700)
- **次要文字**: `#6B7280` (Gray-500)
- **边框色**: `#E5E7EB` (Gray-200)
- **禁用态**: `#9CA3AF` (Gray-400)

#### 背景色
- **深色顶栏背景**: `#2E3646`
- **浅色侧栏背景**: `#F5F7FB`
- **内容区背景**: `#F5F7FB`
- **卡片/面板背景**: `#FFFFFF`
- **悬停背景**: `#F3F4F6`

#### 功能色
- **成功色**: `#10B981` (Green-500)
- **警告色**: `#F59E0B` (Amber-500)
- **错误色**: `#EF4444` (Red-500)
- **信息色**: `#3B82F6` (Blue-500)

#### 顶部导航专用色
- **背景**: `#2E3646`
- **未激活文字**: `#C9D1D9`
- **激活文字**: `#FFFFFF`
- **激活下划线/顶部条**: `#2F78F6`

### 2. 排版系统

#### 字体族
```css
font-family: "Noto Sans SC", "Source Han Sans CN", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
```

#### 字重规范
- **导航/分组标题**: `600` (SemiBold)
- **菜单项**: `500` (Medium)
- **正文**: `400` (Regular)

#### 字号体系
- **特大标题**: `24px` (1.5rem)
- **大标题**: `20px` (1.25rem)
- **中标题**: `18px` (1.125rem)
- **小标题**: `16px` (1rem)
- **正文**: `14px` (0.875rem)
- **辅助文字**: `12px` (0.75rem)

#### 行高
- **标题行高**: `1.2` - `1.3`
- **正文行高**: `1.5`
- **紧凑行高**: `1.4`

### 3. 间距系统

#### 基础间距单位
- `4px` (0.25rem)
- `8px` (0.5rem)
- `12px` (0.75rem)
- `16px` (1rem)
- `24px` (1.5rem)
- `32px` (2rem)
- `48px` (3rem)

#### 页面级间距
- **页面外边距**: `24–32px`
- **栅格间距**: `16–24px`
- **卡片内边距**: `16px / 24px`
- **组件间距**: `16px`

#### 圆角规范
- **小圆角**: `6px` (按钮、输入框)
- **中圆角**: `8px` (卡片、胶囊标签)
- **大圆角**: `12px` (大型面板)

### 4. 阴影系统

#### 层级定义（极弱阴影原则）
```css
/* 一级阴影 - 卡片 */
box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);

/* 二级阴影 - 悬浮卡片 */
box-shadow: 0 2px 4px rgba(0, 0, 0, 0.06);

/* 三级阴影 - 下拉菜单 */
box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);

/* 四级阴影 - 模态框 */
box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
```

## 🖼️ 布局架构

### 1. 三段式布局结构

```
┌─────────────────────────────────────────────────────────┐
│  顶部导航栏 (Top Navigation Bar)                         │
│  高度: 56-64px  背景: #2E3646                            │
├──────────┬──────────────────────────────────────────────┤
│          │                                              │
│  左侧    │  主内容区 (Main Content Area)                 │
│  侧栏    │  背景: #F5F7FB                                │
│          │                                              │
│  宽度:   │  ┌────────────────────────────────────┐      │
│  240-264 │  │  卡片/面板                          │      │
│  px      │  │  背景: #FFFFFF                       │      │
│          │  │  边框: #E5E7EB (1px)                │      │
│  背景:   │  │  阴影: 0 1px 2px rgba(0,0,0,0.04)   │      │
│  #F5F7FB │  └────────────────────────────────────┘      │
│          │                                              │
└──────────┴──────────────────────────────────────────────┘
```

### 2. 顶部导航栏详细规范

#### 尺寸与布局
- **高度**: `56–64px` (推荐 `60px`)
- **内边距**: 水平 `24px`，垂直 `0`
- **背景色**: `#2E3646`

#### 导航项规范
```css
.nav-item {
  height: 100%;
  padding: 0 20px;
  font-size: 14px;
  font-weight: 500;
  color: #C9D1D9;
  border-bottom: 3px solid transparent;
  transition: all 0.2s ease;
}

.nav-item:hover {
  color: #FFFFFF;
  background-color: rgba(255, 255, 255, 0.08);
}

.nav-item.active {
  color: #FFFFFF;
  border-bottom-color: #2F78F6;
}
```

#### 右侧功能区
- **图标尺寸**: `24px`
- **间距**: 图标间距 `20px`
- **颜色**: `#C9D1D9`，悬停 `#FFFFFF`
- **包含**: 通知图标、设置图标、用户头像下拉

### 3. 左侧侧栏详细规范

#### 尺寸与布局
- **宽度**: `240–264px` (推荐 `256px`)
- **背景色**: `#F5F7FB`
- **内边距**: 顶部 `16px`，底部 `16px`，左右 `12px`

#### 分组标题样式
```css
.sidebar-group-title {
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 600;
  color: #6B7280;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-top: 16px;
}

.sidebar-group-title:first-child {
  margin-top: 0;
}
```

#### 菜单项样式（一级）
```css
.sidebar-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  margin: 2px 0;
  font-size: 14px;
  font-weight: 500;
  color: #374151;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.sidebar-item:hover {
  background-color: #E8F0FE;
  color: #2F78F6;
}

.sidebar-item.active {
  background-color: #E8F0FE;
  color: #2F78F6;
  font-weight: 600;
}

.sidebar-item.disabled {
  color: #9CA3AF;
  cursor: not-allowed;
  opacity: 0.6;
}
```

#### 菜单项样式（二级）
```css
.sidebar-subitem {
  padding: 8px 12px 8px 40px; /* 左侧增加缩进 */
  font-size: 14px;
  font-weight: 400;
  color: #6B7280;
}

.sidebar-subitem:hover {
  color: #2F78F6;
  background-color: #F3F4F6;
}

.sidebar-subitem.active {
  color: #2F78F6;
  background-color: #E8F0FE;
  font-weight: 500;
}
```

#### 图标规范
- **尺寸**: `24px × 24px`
- **线宽**: `2px` (线性图标)
- **样式**: Heroicons Outline 或 Lucide Icons
- **间距**: 图标与文字间距 `12px`
- **颜色**: 继承文字颜色

#### 折叠功能
```css
.sidebar-collapse-icon {
  width: 24px;
  height: 24px;
  margin-left: auto;
  transition: transform 0.2s ease;
}

.sidebar-group.expanded .sidebar-collapse-icon {
  transform: rotate(180deg);
}
```

### 4. 主内容区详细规范

#### 布局容器
```css
.content-container {
  flex: 1;
  padding: 24px 32px;
  background-color: #F5F7FB;
  overflow-y: auto;
}
```

#### 页面标题区
```css
.page-header {
  margin-bottom: 24px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #1F2937;
  line-height: 1.3;
  margin-bottom: 8px;
}

.page-description {
  font-size: 14px;
  color: #6B7280;
  line-height: 1.5;
}
```

## 🧩 核心组件规范

### 1. 卡片组件 (Card)

#### 基础卡片
```css
.card {
  background: #FFFFFF;
  border: 1px solid #E5E7EB;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  padding: 24px;
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.card-title {
  font-size: 18px;
  font-weight: 600;
  color: #1F2937;
}

.card-body {
  font-size: 14px;
  color: #374151;
  line-height: 1.5;
}
```

#### 悬浮效果卡片
```css
.card-hoverable {
  transition: all 0.2s ease;
  cursor: pointer;
}

.card-hoverable:hover {
  border-color: #2F78F6;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.06);
  transform: translateY(-2px);
}
```

### 2. 表格组件 (Table)

```css
.table-container {
  background: #FFFFFF;
  border: 1px solid #E5E7EB;
  border-radius: 8px;
  overflow: hidden;
}

.table {
  width: 100%;
  border-collapse: collapse;
}

.table thead {
  background-color: #F9FAFB;
}

.table th {
  padding: 12px 16px;
  font-size: 14px;
  font-weight: 600;
  color: #6B7280;
  text-align: left;
  border-bottom: 1px solid #E5E7EB;
}

.table td {
  padding: 12px 16px;
  font-size: 14px;
  color: #374151;
  border-bottom: 1px solid #F3F4F6;
}

.table tbody tr:hover {
  background-color: #F9FAFB;
}

.table tbody tr:last-child td {
  border-bottom: none;
}
```

### 3. 按钮组件 (Button)

#### 主按钮
```css
.btn-primary {
  padding: 10px 20px;
  font-size: 14px;
  font-weight: 500;
  color: #FFFFFF;
  background-color: #2F78F6;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-primary:hover {
  background-color: #4C8BF8;
}

.btn-primary:active {
  background-color: #1E6FE8;
}

.btn-primary:disabled {
  background-color: #9CA3AF;
  cursor: not-allowed;
}
```

#### 次要按钮
```css
.btn-secondary {
  padding: 10px 20px;
  font-size: 14px;
  font-weight: 500;
  color: #374151;
  background-color: #FFFFFF;
  border: 1px solid #E5E7EB;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-secondary:hover {
  background-color: #F9FAFB;
  border-color: #2F78F6;
  color: #2F78F6;
}
```

#### 文字按钮
```css
.btn-text {
  padding: 10px 16px;
  font-size: 14px;
  font-weight: 500;
  color: #2F78F6;
  background-color: transparent;
  border: none;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-text:hover {
  background-color: #E8F0FE;
}
```

### 4. 分页组件 (Pagination)

```css
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 16px 0;
}

.pagination-item {
  min-width: 32px;
  height: 32px;
  padding: 0 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: #374151;
  background-color: #FFFFFF;
  border: 1px solid #E5E7EB;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.pagination-item:hover {
  border-color: #2F78F6;
  color: #2F78F6;
}

.pagination-item.active {
  background-color: #2F78F6;
  border-color: #2F78F6;
  color: #FFFFFF;
}

.pagination-item.disabled {
  color: #9CA3AF;
  cursor: not-allowed;
  opacity: 0.5;
}
```

### 5. 空状态组件 (Empty State)

```css
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
}

.empty-state-icon {
  width: 64px;
  height: 64px;
  margin-bottom: 16px;
  color: #D1D5DB;
}

.empty-state-title {
  font-size: 16px;
  font-weight: 500;
  color: #6B7280;
  margin-bottom: 8px;
}

.empty-state-description {
  font-size: 14px;
  color: #9CA3AF;
  line-height: 1.5;
  max-width: 400px;
}

.empty-state-action {
  margin-top: 16px;
}
```

### 6. 输入框组件 (Input)

```css
.input-wrapper {
  margin-bottom: 16px;
}

.input-label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: #374151;
  margin-bottom: 8px;
}

.input-field {
  width: 100%;
  padding: 10px 12px;
  font-size: 14px;
  color: #1F2937;
  background-color: #FFFFFF;
  border: 1px solid #D1D5DB;
  border-radius: 6px;
  transition: all 0.2s ease;
}

.input-field:hover {
  border-color: #9CA3AF;
}

.input-field:focus {
  outline: none;
  border-color: #2F78F6;
  box-shadow: 0 0 0 3px rgba(47, 120, 246, 0.1);
}

.input-field:disabled {
  background-color: #F3F4F6;
  color: #9CA3AF;
  cursor: not-allowed;
}

.input-field.error {
  border-color: #EF4444;
}

.input-helper {
  font-size: 12px;
  color: #6B7280;
  margin-top: 4px;
}

.input-error {
  font-size: 12px;
  color: #EF4444;
  margin-top: 4px;
}
```

### 7. 标签组件 (Tag/Badge)

```css
.tag {
  display: inline-flex;
  align-items: center;
  padding: 4px 12px;
  font-size: 12px;
  font-weight: 500;
  border-radius: 12px;
  white-space: nowrap;
}

.tag-default {
  color: #374151;
  background-color: #F3F4F6;
}

.tag-primary {
  color: #2F78F6;
  background-color: #E8F0FE;
}

.tag-success {
  color: #10B981;
  background-color: #D1FAE5;
}

.tag-warning {
  color: #F59E0B;
  background-color: #FEF3C7;
}

.tag-error {
  color: #EF4444;
  background-color: #FEE2E2;
}
```

## 🎭 交互状态规范

### 1. 导航项状态矩阵

| 状态     | 背景色                   | 文字颜色  | 边框/标识          | 说明              |
| -------- | ------------------------ | --------- | ------------------ | ----------------- |
| **默认** | `transparent`            | `#C9D1D9` | 无                 | 未选中的常规状态  |
| **悬停** | `rgba(255,255,255,0.08)` | `#FFFFFF` | 无                 | 鼠标悬停效果      |
| **激活** | `transparent`            | `#FFFFFF` | 底部 3px `#2F78F6` | 当前页面/选中状态 |
| **禁用** | `transparent`            | `#6B7280` | 无                 | 不可点击状态      |

### 2. 侧栏菜单项状态矩阵

| 状态     | 背景色        | 文字颜色  | 字重  | 说明                     |
| -------- | ------------- | --------- | ----- | ------------------------ |
| **默认** | `transparent` | `#374151` | `500` | 未选中的常规状态         |
| **悬停** | `#E8F0FE`     | `#2F78F6` | `500` | 鼠标悬停效果             |
| **激活** | `#E8F0FE`     | `#2F78F6` | `600` | 当前选中状态（胶囊背景） |
| **禁用** | `transparent` | `#9CA3AF` | `500` | 不可点击，透明度 0.6     |

### 3. 按钮状态矩阵

#### 主按钮 (Primary)
| 状态     | 背景色    | 文字颜色  | 说明                 |
| -------- | --------- | --------- | -------------------- |
| **默认** | `#2F78F6` | `#FFFFFF` | 正常状态             |
| **悬停** | `#4C8BF8` | `#FFFFFF` | 鼠标悬停             |
| **按下** | `#1E6FE8` | `#FFFFFF` | 点击按下             |
| **禁用** | `#9CA3AF` | `#FFFFFF` | 不可点击，透明度 0.6 |

#### 次要按钮 (Secondary)
| 状态     | 背景色    | 边框色    | 文字颜色  | 说明     |
| -------- | --------- | --------- | --------- | -------- |
| **默认** | `#FFFFFF` | `#E5E7EB` | `#374151` | 正常状态 |
| **悬停** | `#F9FAFB` | `#2F78F6` | `#2F78F6` | 鼠标悬停 |
| **按下** | `#F3F4F6` | `#1E6FE8` | `#1E6FE8` | 点击按下 |
| **禁用** | `#F9FAFB` | `#E5E7EB` | `#9CA3AF` | 不可点击 |

### 4. 输入框状态矩阵

| 状态     | 边框色    | 阴影                             | 说明                     |
| -------- | --------- | -------------------------------- | ------------------------ |
| **默认** | `#D1D5DB` | 无                               | 正常状态                 |
| **悬停** | `#9CA3AF` | 无                               | 鼠标悬停                 |
| **聚焦** | `#2F78F6` | `0 0 0 3px rgba(47,120,246,0.1)` | 获得焦点                 |
| **错误** | `#EF4444` | `0 0 0 3px rgba(239,68,68,0.1)`  | 验证失败                 |
| **禁用** | `#E5E7EB` | 无                               | 不可编辑，背景 `#F3F4F6` |

## 📐 响应式断点

```css
/* 超大屏幕 */
@media (min-width: 1920px) {
  .content-container {
    max-width: 1600px;
    margin: 0 auto;
  }
}

/* 大屏幕 */
@media (min-width: 1280px) {
  /* 默认布局 */
}

/* 中等屏幕 */
@media (max-width: 1279px) {
  .sidebar {
    width: 220px;
  }
}

/* 小屏幕 */
@media (max-width: 1023px) {
  .sidebar {
    position: fixed;
    left: -100%;
    transition: left 0.3s ease;
    z-index: 1000;
  }
  
  .sidebar.open {
    left: 0;
  }
}

/* 移动端 */
@media (max-width: 767px) {
  .top-nav {
    height: 56px;
  }
  
  .content-container {
    padding: 16px;
  }
}
```

## ♿ 可访问性要求

### 1. 对比度标准（WCAG AA）

- **正文文字**: 对比度 ≥ 4.5:1
- **大号文字** (18px+): 对比度 ≥ 3:1
- **图标与背景**: 对比度 ≥ 3:1

### 2. 键盘导航

```css
/* 聚焦指示器 */
*:focus-visible {
  outline: 2px solid #2F78F6;
  outline-offset: 2px;
}

/* 跳过导航链接 */
.skip-to-content {
  position: absolute;
  top: -40px;
  left: 0;
  background: #2F78F6;
  color: #FFFFFF;
  padding: 8px 16px;
  z-index: 9999;
}

.skip-to-content:focus {
  top: 0;
}
```

### 3. ARIA 标签规范

- 导航区域: `<nav aria-label="主导航">`
- 侧栏: `<aside aria-label="侧边栏菜单">`
- 主内容: `<main aria-label="主要内容">`
- 折叠菜单: `aria-expanded="true/false"`
- 当前页面: `aria-current="page"`

## 🎬 动画与过渡

### 1. 过渡时长规范

```css
/* 快速交互 */
transition: all 0.15s ease;  /* 按钮点击、输入框聚焦 */

/* 常规交互 */
transition: all 0.2s ease;   /* 悬停、卡片展开 */

/* 慢速交互 */
transition: all 0.3s ease;   /* 侧栏展开、模态框显示 */
```

### 2. 缓动函数

- **ease**: 默认，适用于大多数场景
- **ease-in-out**: 页面/组件进出
- **cubic-bezier(0.4, 0, 0.2, 1)**: 精细控制（Material Design）

### 3. 禁用过渡场景

- 页面初次加载
- 主题切换时的全局颜色变化
- 数据批量更新

## 📦 可交付成果清单

### 1. 基础布局
- ✅ 顶部导航栏（含激活/悬停/禁用状态）
- ✅ 左侧侧栏（含分组、二级菜单、折叠功能）
- ✅ 主内容区容器
- ✅ 页面标题区

### 2. 核心组件
- ✅ 基础卡片组件
- ✅ 悬浮卡片组件
- ✅ 表格组件
- ✅ 分页组件
- ✅ 空状态占位组件
- ✅ 按钮组件（主按钮、次要按钮、文字按钮）
- ✅ 输入框组件
- ✅ 标签/徽章组件

### 3. 状态样式图
- ✅ 导航项三态样式（默认、悬停、激活）
- ✅ 侧栏菜单项三态样式（默认、悬停、激活）
- ✅ 按钮三态样式（默认、悬停、禁用）
- ✅ 输入框四态样式（默认、悬停、聚焦、错误）

### 4. 设计资源
- ✅ 完整颜色系统定义
- ✅ 排版系统定义
- ✅ 间距系统定义
- ✅ 阴影系统定义
- ✅ 图标规范
- ✅ 响应式断点

## 🚫 设计约束与禁忌

### 必须遵守
1. ❌ **避免过度渐变**: 不使用多色渐变背景，纯色为主
2. ❌ **避免重阴影**: 阴影透明度不超过 0.12
3. ❌ **避免花哨装饰**: 不使用纹理、斜线、光效等装饰元素
4. ❌ **避免过高信息密度**: 保持适当留白，行间距不小于 1.4
5. ✅ **保持克制**: 主色仅用于关键操作与状态标识
6. ✅ **保证对比度**: 所有文本对比度达到 WCAG AA 标准
7. ✅ **统一圆角**: 全局使用 6/8/12px 圆角规范
8. ✅ **一致的间距**: 使用 4px 基础间距单位的倍数

### 反面案例
```css
/* ❌ 禁止：过强阴影 */
box-shadow: 0 10px 40px rgba(0, 0, 0, 0.3);

/* ❌ 禁止：多色渐变 */
background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);

/* ❌ 禁止：过小字号 */
font-size: 10px;

/* ❌ 禁止：过窄行高 */
line-height: 1.2; /* 仅标题可用 */

/* ❌ 禁止：过多颜色 */
/* 不要在一个界面中使用超过 3 种主要颜色 */
```

## 🛠️ 技术实现建议

### 1. CSS 变量定义

```css
:root {
  /* 颜色变量 */
  --color-primary: #2F78F6;
  --color-primary-hover: #4C8BF8;
  --color-primary-light: #E8F0FE;
  
  --color-text-primary: #1F2937;
  --color-text-secondary: #374151;
  --color-text-tertiary: #6B7280;
  
  --color-bg-base: #F5F7FB;
  --color-bg-card: #FFFFFF;
  --color-bg-nav: #2E3646;
  
  --color-border: #E5E7EB;
  
  /* 间距变量 */
  --spacing-xs: 4px;
  --spacing-sm: 8px;
  --spacing-md: 16px;
  --spacing-lg: 24px;
  --spacing-xl: 32px;
  
  /* 圆角变量 */
  --radius-sm: 6px;
  --radius-md: 8px;
  --radius-lg: 12px;
  
  /* 阴影变量 */
  --shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.04);
  --shadow-md: 0 2px 4px rgba(0, 0, 0, 0.06);
  --shadow-lg: 0 4px 12px rgba(0, 0, 0, 0.08);
  
  /* 字体变量 */
  --font-family: "Noto Sans SC", "Source Han Sans CN", sans-serif;
  --font-size-xs: 12px;
  --font-size-sm: 14px;
  --font-size-md: 16px;
  --font-size-lg: 18px;
  --font-size-xl: 20px;
  --font-size-2xl: 24px;
  
  --font-weight-normal: 400;
  --font-weight-medium: 500;
  --font-weight-semibold: 600;
}
```

### 2. 推荐技术栈

- **前端框架**: Vue 3 / React 18
- **UI 组件库**: 基于 Headless UI 自定义实现
- **CSS 方案**: CSS Modules / Tailwind CSS (自定义主题)
- **图标库**: Heroicons / Lucide Icons
- **构建工具**: Vite / Webpack 5

### 3. 组件库结构建议

```
components/
├── layout/
│   ├── TopNav.vue
│   ├── Sidebar.vue
│   ├── MainContent.vue
│   └── PageHeader.vue
├── basic/
│   ├── Button.vue
│   ├── Input.vue
│   ├── Card.vue
│   ├── Table.vue
│   ├── Pagination.vue
│   ├── Tag.vue
│   └── EmptyState.vue
├── navigation/
│   ├── NavItem.vue
│   ├── SidebarGroup.vue
│   ├── SidebarItem.vue
│   └── Breadcrumb.vue
└── feedback/
    ├── Alert.vue
    ├── Toast.vue
    └── Modal.vue
```
