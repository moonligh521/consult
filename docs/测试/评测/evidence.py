# -*- coding: utf-8 -*-
"""一条运行的证据。评分只读这些字段。"""

from __future__ import annotations

from dataclasses import asdict, dataclass, field


@dataclass
class Evidence:
    case_id: str
    status_code: int | None = None
    error_code: int | None = None
    prompt: str = ""
    department: str = ""
    specialty: str = ""
    evidence: str = ""
    rule_ids: list[str] = field(default_factory=list)
    shadow_rule_ids: list[str] = field(default_factory=list)
    source: str = ""
    model_called: bool | None = None
    model_field_names: list[str] = field(default_factory=list)
    order_count: int = 0
    skipped: bool = False

    def to_dict(self) -> dict:
        return asdict(self)
