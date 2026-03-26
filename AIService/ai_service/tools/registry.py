"""Tool 注册表。

当前阶段只有体重分析 Tool 真正接入，但注册表从一开始就按“多 Tool 可扩展”方式设计。
这样后续新增地点查询、服务查询、用品推荐时，只需要在这里补充定义和实例，
不需要再回到编排主流程里堆新的 if / else。
"""

from ai_service.core.settings import Settings
from ai_service.tools.definitions import ToolDefinition
from ai_service.tools.location_search.tool import LocationSearchTool
from ai_service.tools.weight_analysis.tool import WeightAnalysisTool


class ToolRegistry:
    """集中管理所有 Tool 的定义、开关状态和运行实例。"""

    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._definitions = self._build_definitions()

    def list_enabled_tools(self) -> list[ToolDefinition]:
        """返回当前启用的 Tool 列表。"""
        return [definition for definition in self._definitions if definition.enabled]

    def get_tool(self, tool_name: str) -> ToolDefinition | None:
        """按名称查找当前可执行的 Tool。"""
        for definition in self._definitions:
            if definition.name == tool_name and definition.enabled:
                return definition
        return None

    def build_registry_prompt_text(self) -> str:
        """把注册表转换为适合喂给第一轮决策模型的说明文本。"""
        sections: list[str] = []
        for index, definition in enumerate(self.list_enabled_tools(), start=1):
            lines = [
                f"{index}. {definition.name}",
                f"用途：{definition.description}",
                "适用场景：",
            ]
            lines.extend(f"- {item}" for item in definition.when_to_use)
            lines.append("调用前提：")
            lines.extend(f"- {item}" for item in definition.required_inputs)
            lines.append("不要调用的情况：")
            lines.extend(f"- {item}" for item in definition.when_not_to_use)
            if definition.notes:
                lines.append("补充说明：")
                lines.extend(f"- {item}" for item in definition.notes)
            sections.append("\n".join(lines))
        return "\n\n".join(sections)

    def _build_definitions(self) -> list[ToolDefinition]:
        """构建当前版本的 Tool 清单。"""
        enabled_tools = set(self._settings.tool_enabled_list)

        weight_tool = WeightAnalysisTool(settings=self._settings)
        location_tool = LocationSearchTool(settings=self._settings)
        return [
            ToolDefinition(
                name="weight_analysis",
                description="先读取宠物最近体重记录，再由 Tool 内部的 LLM 分析当前体重变化和整体趋势。",
                when_to_use=[
                    "用户明确询问宠物最近体重、胖了没有、瘦了没有、增重、减重或体重趋势。",
                    "用户希望结合历史体重记录判断近期变化。",
                ],
                required_inputs=[
                    "必须提供 userId，用于内部接口做归属校验。",
                    "必须提供 petId，明确要分析哪一只宠物。",
                ],
                when_not_to_use=[
                    "用户问题与体重无关。",
                    "没有 petId，无法定位具体宠物。",
                    "只是泛泛咨询喂养建议，没有要求分析体重记录。",
                ],
                notes=[
                    "当前版本默认拉取最近 10 次体重记录，再由 Tool 内部调用 LLM 做趋势分析。",
                    "分析结果只做趋势解释，不做医学诊断。",
                ],
                tool=weight_tool,
                enabled="weight_analysis" in enabled_tools,
            ),
            ToolDefinition(
                name="location_search",
                description="根据地点描述和地点类型关键词，调用高德文本搜索查询附近相关地点，例如宠物医院、宠物店或洗护门店。",
                when_to_use=[
                    "用户在问某个城市、区域、商圈或地址附近哪里有宠物医院、门店或某类服务地点。",
                    "用户希望查找和地理位置强相关的线下地点信息。",
                ],
                required_inputs=[
                    "需要明确地点信息，例如城市、区域、商圈或地址片段。",
                    "最好提供具体地点类型关键词，例如宠物医院、宠物店、洗护门店。",
                ],
                when_not_to_use=[
                    "用户只有“附近”这类相对描述，但没有提供可定位的城市或区域。",
                    "用户问题与地理位置无关。",
                ],
                notes=[
                    "当前版本先接高德文本搜索接口，适合处理“浦东附近宠物医院”这类文本区域查询。",
                    "如果缺少明确地点，Tool 会返回 missing_location，而不是盲目调用第三方搜索。",
                ],
                tool=location_tool,
                enabled="location_search" in enabled_tools,
            ),
            ToolDefinition(
                name="service_lookup",
                description="查询和解释宠物服务，例如洗护、寄养、疫苗或检查服务。",
                when_to_use=[
                    "用户明确在问服务类型、服务选择或服务推荐。",
                ],
                required_inputs=[
                    "需要明确服务意图，必要时结合 petId 或 query。",
                ],
                when_not_to_use=[
                    "用户只是问常识，并没有服务查询意图。",
                ],
                enabled="service_lookup" in enabled_tools,
            ),
            ToolDefinition(
                name="product_recommendation",
                description="根据宠物信息和需求推荐用品、粮食或护理产品。",
                when_to_use=[
                    "用户明确在问用品、商品或推荐购买内容。",
                ],
                required_inputs=[
                    "最好提供 petId，或至少有明确宠物类型和需求描述。",
                ],
                when_not_to_use=[
                    "用户问题不是商品推荐。",
                ],
                enabled="product_recommendation" in enabled_tools,
            ),
        ]
