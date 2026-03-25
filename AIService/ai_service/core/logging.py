"""日志初始化工具。

这里同时负责两类日志：
1. 应用级日志：写入控制台和 application.log
2. 单次 AI 对话日志：由 observability/log_service.py 单独写入文件

这样既能保留标准运行日志，也能把 AI 排查信息拆出来独立查看。
"""

import logging
from pathlib import Path


def configure_logging(level: str, log_dir: str) -> None:
    """初始化应用级日志。

    当前默认同时输出到：
    - 控制台
    - `log_dir/application.log`
    """
    root_logger = logging.getLogger()
    root_logger.setLevel(getattr(logging, level.upper(), logging.INFO))

    formatter = logging.Formatter(
        fmt="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )

    root_logger.handlers.clear()

    console_handler = logging.StreamHandler()
    console_handler.setFormatter(formatter)
    root_logger.addHandler(console_handler)

    log_path = Path(log_dir)
    log_path.mkdir(parents=True, exist_ok=True)
    file_handler = logging.FileHandler(log_path / "application.log", encoding="utf-8")
    file_handler.setLevel(getattr(logging, level.upper(), logging.INFO))
    file_handler.setFormatter(formatter)
    root_logger.addHandler(file_handler)


def get_logger(name: str) -> logging.Logger:
    """获取指定名称的 logger。"""
    return logging.getLogger(name)
