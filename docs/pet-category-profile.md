# 宠物分类与档案增强

当前项目已经支持宠物档案升级，目标是同时满足两类需求：

- 面向用户的宠物档案录入与展示
- 面向 AI / Tool / RAG 的结构化分类数据基础

## 数据设计

后端新增了 `pet_categories` 分类表，分类树最多三层。

设计原则：

- 分类树只表达“这是什么宠物”
- 颜色、体型、变异、特殊特征等不进入分类树
- 这类补充描述统一放到 `customSpeciesNote`
- `code` 和 `path` 使用稳定英文值，便于 AI、检索和工具调用
- `category_path` 在 `pets` 表中冗余存储，便于查询和下游能力使用

示例：

- `categoryPath = bird/parrot/cockatiel`
- `customSpeciesNote = 黄化大体`

`pets` 表当前使用的结构化字段包括：

- `category_id`
- `category_path`
- `custom_species_note`
- `gender`
- `birth_date`
- `neutered`
- `current_weight`

## 当前内置分类覆盖

当前内置分类覆盖 12 个一级分类：

- 猫类
- 狗类
- 鸟类
- 啮齿类
- 兔类
- 雪貂类
- 爬宠
- 两栖
- 水族
- 龟鳖类
- 节肢/无脊椎
- 其他

分类树控制在三级以内，支持一级、二级、三级选择，不会把“黄化、蓝化、长毛、特殊体型”这类内容塞进分类树。

## 后端接口

新增分类树接口：

- `GET /api/pet/categories/tree`

返回三级分类树，前端可以直接用于级联选择。

创建宠物接口 `POST /api/pets` 当前支持这些核心字段：

- `categoryId`
- `customSpeciesNote`
- `gender`
- `birthDate`
- `neutered`
- `currentWeight`

保存时，backend 会根据 `categoryId` 自动写入 `categoryPath`。

## 前端行为

宠物创建页当前支持：

- 一级 / 二级 / 三级分类联动选择
- 自定义补充说明输入
- 允许停留在一级或二级分类
- 找不到精确分类时，仍可通过补充说明描述个体特征

宠物详情页和我的宠物列表也已经切到新的档案字段展示。

## 兼容策略

当前仍保留 `breed` 这个展示字段，用于兼容旧页面和部分旧逻辑。

但需要明确：

- `breed` 更偏向展示文本
- 真正结构化的分类来源是 `categoryId + categoryPath + customSpeciesNote`

后续 AI 能力开发应优先依赖结构化字段，而不是只依赖 `breed`。
