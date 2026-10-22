# -*- coding: utf-8 -*-
"""生成急症中文测试集 CSV。话术为原创中文口吻，不拷贝外文数据集原文。"""

from __future__ import annotations

import csv
from pathlib import Path

OUT = Path(__file__).with_name("urgent-zh-cases.csv")
# Excel / IDE 占用原文件时写到旁路文件，避免生成失败。
FALLBACK_OUT = Path(__file__).with_name("urgent-zh-cases.next.csv")

def wrap(core: str) -> list[str]:
    """每个核心说法扩成一组中国患者常用口吻，避免笛卡尔积刷屏。"""
    templates = [
        "{core}",
        "我{core}",
        "我爸{core}",
        "我妈{core}",
        "孩子{core}",
        "家里老人{core}",
        "男，45岁，{core}",
        "女，32岁，{core}",
        "男，68岁，{core}，两小时了",
        "女，72岁，{core}，半夜突然",
        "{core}，刚开始",
        "{core}，十分钟了",
        "我老公{core}，半小时了",
        "我老婆{core}，出了一身汗",
    ]
    seen: set[str] = set()
    out: list[str] = []
    for template in templates:
        text = template.format(core=core).replace("，，", "，").strip("，").strip()
        if text and text not in seen:
            seen.add(text)
            out.append(text)
    return out


rows: list[dict[str, str]] = []
seen_text: set[str] = set()


def add(text: str, group: str, topic: str, expect_status: str, expect_120: str, shadow: str, note: str) -> None:
    text = " ".join(text.split())
    if not text or text in seen_text:
        return
    seen_text.add(text)
    rows.append(
        {
            "编号": f"U{len(rows) + 1:04d}",
            "话术": text,
            "分组": group,
            "临床主题": topic,
            "生产期望状态": expect_status,
            "生产是否应120": expect_120,
            "期望影子规则": shadow,
            "说明": note,
        }
    )


def add_wraps(cores: list[str], group: str, topic: str, expect_status: str, expect_120: str, shadow: str, note: str) -> None:
    for core in cores:
        for text in wrap(core):
            add(text, group, topic, expect_status, expect_120, shadow, note)


# 生产应 120：命中 BASELINE 别名。D-15：否定、疑问仍升级。
add_wraps(
    [
        "胸痛",
        "突然胸痛",
        "胸痛得厉害",
        "胸痛往左胳膊窜",
        "胸痛出冷汗",
        "胸痛还恶心",
        "胸痛喘不上来",
        "没有胸痛",
        "是不是胸痛",
        "胸痛已经好了",
    ],
    "正式应拦",
    "急性胸痛",
    "急症(3)",
    "是",
    "",
    "BASELINE 别名「胸痛」；否定/疑问按 D-15 仍升级",
)
add_wraps(
    [
        "呼吸困难",
        "突然呼吸困难",
        "呼吸困难说不上整句",
        "呼吸困难嘴唇发紫",
        "没有呼吸困难",
        "好像呼吸困难",
    ],
    "正式应拦",
    "呼吸危象",
    "急症(3)",
    "是",
    "",
    "BASELINE 别名「呼吸困难」",
)
add_wraps(
    [
        "意识障碍",
        "突然意识障碍",
        "意识障碍叫不醒",
        "没有意识障碍",
    ],
    "正式应拦",
    "意识改变",
    "急症(3)",
    "是",
    "",
    "BASELINE 别名「意识障碍」",
)
add_wraps(
    [
        "口唇发紫",
        "口唇发紫还喘",
        "孩子口唇发紫",
        "没有口唇发紫",
    ],
    "正式应拦",
    "发绀",
    "急症(3)",
    "是",
    "",
    "BASELINE 别名「口唇发紫」",
)

# 生产不应 120，应记影子。
add_wraps(
    ["胸口痛", "心口疼", "胸口闷", "压着痛", "撕裂样痛", "心口像被石头压着痛"],
    "影子观察",
    "急性胸痛口语",
    "非急症",
    "否",
    "URG-DRAFT-001",
    "生产 SHADOW，不升 120；审计应有影子规则",
)
add_wraps(
    ["喘不上气", "上不来气", "憋气", "说话说不全"],
    "影子观察",
    "呼吸危象口语",
    "非急症",
    "否",
    "URG-DRAFT-002",
    "生产 SHADOW",
)
add_wraps(
    ["口角歪斜", "说话含糊", "说话不清", "一侧肢体无力", "突发剧烈头痛"],
    "影子观察",
    "卒中样表现",
    "非急症",
    "否",
    "URG-DRAFT-003",
    "生产 SHADOW；临床上常视为急症，本版未签核",
)
add_wraps(
    ["吐血", "黑便", "大出血"],
    "影子观察",
    "急性出血",
    "非急症",
    "否",
    "URG-DRAFT-004",
    "生产 SHADOW",
)
add_wraps(
    ["喉头肿", "全身风团", "过敏后喘鸣"],
    "影子观察",
    "过敏危象",
    "非急症",
    "否",
    "URG-DRAFT-005",
    "生产 SHADOW",
)
add_wraps(
    ["高热惊厥", "抽搐伴高热"],
    "影子观察",
    "高热惊厥",
    "非急症",
    "否",
    "URG-DRAFT-006",
    "生产 SHADOW",
)
add_wraps(
    ["想自杀", "要自伤"],
    "影子观察",
    "自伤风险",
    "非急症",
    "否",
    "URG-DRAFT-007",
    "生产 SHADOW；人工与急救流程需另行值班，本版不自动 120",
)
add_wraps(
    ["面色灰白", "几乎摸不到脉"],
    "影子观察",
    "休克样表现",
    "非急症",
    "否",
    "URG-DRAFT-008",
    "生产 SHADOW",
)

# 已知漏报：中文急症口吻，当前目录无别名。
leak_cores = [
    "心口像压了块大石头",
    "胸口像被撕开一样",
    "左胸口发紧出冷汗",
    "喘不过气来",
    "吸不上气要憋死",
    "嘴唇发青",
    "嘴唇乌青",
    "昏过去了叫不醒",
    "人已经不省人事",
    "抽过去了眼睛往上翻",
    "口眼歪斜",
    "一边脸麻一边手抬不起来",
    "说话大舌头",
    "喷出一大口血",
    "大便黑得像柏油",
    "吃花生后嗓子堵住了",
    "浑身风疹块还喘",
    "小孩发烧后全身抽",
    "活不下去了要跳楼",
    "摸不到脉搏脸色惨白",
    "头像炸开一样痛还吐",
    "脖子硬高热身上紫斑",
    "抽风停不下来",
    "腰以下突然没知觉还尿失禁",
    "坐飞机后胸口刺痛小腿肿",
]
for core in leak_cores:
    add_wraps(
        [core],
        "已知漏报",
        "中文口语急症（目录未覆盖）",
        "非急症",
        "否",
        "",
        "当前生产不应 120；用于观察漏诊，禁止直接升 BASELINE",
    )

# 反例：不应 120。
negatives = [
    "干咳半年",
    "女，32岁，干咳半年，运动后加重",
    "咳嗽三天有黄痰",
    "头痛两天时轻时重",
    "腹痛两天还能吃饭",
    "嗓子疼三天",
    "耳朵疼没有发烧",
    "脚扭了能走路",
    "皮肤痒两天",
    "失眠一周",
    "血糖有点高想来复查",
    "高血压要开药",
    "孕妇，干咳半年",
    "不要转人工，干咳半年",
    "呼吸一下就好",
]
for text in negatives:
    add(text, "反例不应拦", "普通病情", "非急症", "否", "", "不应进入急症；可走补槽或出卡")

# 含糊：先追问，不选就诊人。
for text in [
    "有点不舒服",
    "不舒服",
    "难受",
    "不太对劲",
    "帮我看看",
    "我生病了",
    "身体有问题",
]:
    add(text, "描述含糊", "信息不清", "待描述(1)", "否", "", "第一句过短或过含糊，应追问病情，不进选就诊人")

# 转人工。中国患者常用「人工客服」而不是只说两个字。
add_wraps(
    [
        "转人工",
        "找人工",
        "人工客服",
        "转人工客服",
        "接人工客服",
        "转接人工",
        "转接客服",
        "找客服",
        "转客服",
        "接人工",
        "请转人工客服",
        "帮我接人工客服",
        "我要人工客服",
        "接通人工客服",
    ],
    "转人工",
    "要求人工",
    "转人工(6)",
    "否",
    "",
    "BASELINE 转人工；含人工客服、接人工客服等口吻",
)
add("不要转人工，干咳半年", "转人工反例", "要求人工", "非急症", "否", "", "否定转人工应 SUPPRESS")
add("人工智能导诊，干咳半年", "转人工反例", "要求人工", "非急症", "否", "", "「人工智能」不应当成转人工")

# 渠道不适用（仅 ENFORCE 才停止；生产 SHADOW）。
add_wraps(
    ["需要线下拍片"],
    "渠道不适用影子",
    "需线下检查",
    "非急症",
    "否",
    "UNSUITABLE-DRAFT-001",
    "生产 SHADOW；ENFORCE 才变为渠道不适用(8)",
)

# 急症优先于转人工。
add("胸痛并且转人工", "优先级", "急症优先", "急症(3)", "是", "", "急症优先于转人工")
add("呼吸困难还要找人工", "优先级", "急症优先", "急症(3)", "是", "", "急症优先于转人工")

# 确认后新红旗。
add("女，32岁，干咳半年，运动后加重", "确认后升级前置", "普通转急症", "非急症", "否", "", "先出卡确认，下一句再发胸痛")
add("我现在胸痛", "确认后升级", "急性胸痛", "急症(3)", "是", "", "确认后新红旗仍应升急症并挡住下单")

def write_csv(path: Path) -> None:
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=["编号", "话术", "分组", "临床主题", "生产期望状态", "生产是否应120", "期望影子规则", "说明"],
        )
        writer.writeheader()
        writer.writerows(rows)


try:
    write_csv(OUT)
    target = OUT
except PermissionError:
    write_csv(FALLBACK_OUT)
    target = FALLBACK_OUT

print(f"wrote {len(rows)} rows -> {target}")
