"""日志初始化工具。

这里的日志只负责“应用级日志”，例如：
- 服务启动/关闭
- 非对话级错误

“单次 AI 对话日志”不在这里处理，而是由 observability/log_service.py
按文件单独写到 AIlog 目录，避免混在控制台日志里。
"""

import logging


def configure_logging(level: str, log_dir: str) -> None:
    """初始化标准输出日志。

    参数里虽然保留了 `log_dir`，但这里暂时不直接写文件。
    这样接口形式保持稳定，后续如果要恢复应用级文件日志，不需要再改调用方。
    """
    root_logger = logging.getLogger()
    root_logger.setLevel(getattr(logging, level.upper(), logging.INFO))

    formatter = logging.Formatter(
        fmt="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )

    # 先清空旧 handler，避免开发模式热重载后重复打印日志。
    root_logger.handlers.clear()

    console_handler = logging.StreamHandler()
    console_handler.setFormatter(formatter)
    root_logger.addHandler(console_handler)


def get_logger(name: str) -> logging.Logger:
    """获取指定名称的 logger。"""
    return logging.getLogger(name)
