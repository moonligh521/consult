# -*- coding: utf-8 -*-
"""生成挂号导诊回归测试集（急症判定 + 科室路由）。

- 话术：中国患者口吻原创，覆盖本人自述、代家属描述、带年龄性别、带病程、
  挂号咨询口吻、礼貌前缀、口语语气词、输入噪声（错别字/插空格/结巴/长句）。
- 标签：不手写，全部由 rule_replica 复刻生产逻辑算出来，保证和代码一致。
- 另给一列「临床期望」，用来做漏报/误报的差距分析，不用于回归门禁。
- 两大专项：描述不清（只说不舒服 + 无症状诉求 + 过短主诉 + 含糊但高危）、
  判不了科室（跨科症状、部位不明、全身非特异、需检查才能定）。
"""

from __future__ import annotations

import csv
import zlib
from pathlib import Path

import rule_replica as engine

TARGET = 13000
OUT = Path(__file__).with_name("registration-cases.csv")
FALLBACK_OUT = Path(__file__).with_name("registration-cases.next.csv")

G_RESP = "内科"
G_NEURO = "内科"
G_DIG = "内科"
G_GEN = "全科-无关键词"
G_GEN_LEAK = "全科-口语漏词"
G_MULTI = "多症状-顺序"
G_LONG = "同位置长短词"
G_URG = "急症-应120"
G_SHADOW = "急症-影子"
G_URG_LEAK = "急症-漏报"
G_URG_PRIOR = "急症-优先于科室"
G_HUMAN = "转人工"
G_HUMAN_NEG = "转人工-反例"
G_HUMAN_LEAK = "转人工-漏报"
G_VAGUE = "描述不清-笼统不适"
G_UNCLEAR = "描述不清-无症状诉求"
G_SHORT = "描述不清-过短主诉"
G_RISK = "描述不清-但高危"
G_NODEPT = "判不了科室"

EMERG = "急诊（线下）"
ASK = "（需追问）"
HUMAN_DEPT = "（人工）"
NO_DEPT = "（判不了，需追问）"

# 这几个组只有「先追问」才是正确行为，包装器必须保持短、不补出性别/年龄/病程。
UNCLEAR_GROUPS = (G_VAGUE, G_UNCLEAR, G_SHORT)

CORES: list[dict] = []


def add(
    group: str,
    topic: str,
    texts: list[str],
    clin_urgent: str,
    clin_dept: str,
    expect_dept: str | None = None,
    expect_evidence: str | None = None,
    shadow: str = "",
    note: str = "",
) -> None:
    for text in texts:
        CORES.append(
            {
                "text": text,
                "group": group,
                "topic": topic,
                "clin_urgent": clin_urgent,
                "clin_dept": clin_dept,
                "expect_dept": expect_dept,
                "expect_evidence": expect_evidence,
                "shadow": shadow,
                "note": note,
            }
        )


# ---------------------------------------------------------------- 内科（呼吸）
add(G_RESP, "咳嗽/咳痰", [
    "咳嗽三天", "干咳半个月", "晚上咳嗽厉害", "一躺下就咳嗽", "咳黄痰", "咳嗽有黄痰",
    "早上起来咳黄痰", "感冒后一直咳嗽", "咳嗽带点白痰", "嗓子痒就想咳嗽", "咳嗽咳得睡不着",
    "咳了两个月", "一直干咳", "干咳，嗓子发痒", "天冷就咳嗽", "咳嗽加重，想来复查",
    "咳嗽三天有黄痰", "咳嗽，嗓子疼", "咳嗽，鼻子堵", "咳嗽，声音哑了", "咳得肋骨疼",
    "一直咳，吃药没用", "咳嗽一周，晚上更厉害", "干咳，咽干", "咽喉炎，老咳嗽",
    "支气管炎，咳嗽", "肺炎复查，还有点咳", "老慢支，冬天咳得厉害", "过敏季咳嗽",
    "咳嗽变异性哮喘", "夜里咳醒", "干咳，无痰", "咳白痰，早上多", "咳嗽，怕冷",
    "孩子咳嗽三天", "咳嗽，流鼻涕", "干咳，喉咙干痒", "咳嗽，痰很多", "晨起咳嗽，有痰",
    "讲话多了就咳", "闻见烟味就咳嗽", "咳嗽三个月，瘦了", "干咳，夜里加重",
    "咳嗽，痰里有点血丝", "干咳半年，运动后加重", "咳嗽两周日渐加重", "咳得胸口都疼",
    "咳嗽，后背也跟着疼", "干咳，喝水也不管用",
], "否", "内科", expect_dept="内科", note="关键词：咳嗽/干咳/咳/黄痰")

add(G_RESP, "气促/哮喘/喘息", [
    "气促，走两步就喘", "爬楼气促", "活动后气促", "晚上气促", "哮喘犯了",
    "哮喘多年，最近又犯", "哮喘，想开点药", "孩子哮喘复查", "晚上喘息", "喘息，夜里加重",
    "慢阻肺，气促加重", "气促，走平路都累", "哮喘夜间发作", "气促，心慌",
    "走快了就气促", "哮喘，换季就犯", "喘息，运动后明显", "气促，坐着也喘",
], "否", "内科", expect_dept="内科", note="关键词：气促/哮喘/喘息")

add(G_RESP, "胸闷/呼吸", [
    "胸闷", "胸闷，深呼吸不舒服", "胸闷气短", "咳嗽伴胸闷", "呼吸有点急",
    "呼吸时胸口不舒服", "胸闷，后背也难受", "呼吸时嗓子有响声", "胸闷，压着慌",
    "胸闷，活动后明显",
], "否", "内科", expect_dept="内科", note="关键词：胸闷/呼吸")

# ---------------------------------------------------------------- 内科（神经）
add(G_NEURO, "头痛/偏头痛", [
    "头痛两天", "偏头痛又犯了", "偏头痛，怕光", "头痛，时轻时重", "失眠一周",
    "头痛，睡不好", "后脑勺头痛", "一侧头痛", "头痛，眼睛胀", "偏头痛多年",
    "经期头痛", "压力大就头痛", "头痛，脖子僵", "头痛，记忆力下降", "头痛，想做个检查",
    "头痛伴头晕", "偏头痛，恶心", "头痛，血压高", "头痛，怕吵",
    "神经性头痛", "头痛，吃药能缓解", "头痛，感冒后开始", "偏头痛，家族史",
    "头痛，肩颈也酸", "头痛，眼前发黑", "偏头痛，月经前后", "头痛，持续一周",
    "头痛，像针扎", "头痛，睡一觉就好", "偏头痛，单侧跳痛", "头痛，想复查",
    "头痛，洗脸时加重", "头痛，做了 CT 没事", "偏头痛，熬夜就犯", "头痛，闷疼闷疼的",
    "头痛，吹了冷风开始", "偏头痛，吃止痛药才管用",
], "否", "内科", expect_dept="内科", note="关键词：头痛/偏头痛")

add(G_NEURO, "头晕/失眠", [
    "头晕", "头晕，站起来明显", "头晕，天旋地转", "头晕恶心", "失眠，入睡困难",
    "失眠三个月", "头晕，心慌", "失眠，早醒", "失眠，多梦", "头晕，乏力",
    "头晕两天，没精神", "长期失眠", "失眠，夜里醒好几次", "头晕，蹲下起来明显",
    "失眠，白天困", "头晕，走路不稳", "失眠，工作压力大", "头晕，耳朵响",
    "失眠，想开助眠药", "头晕，躺着好点", "失眠伴焦虑", "头晕，饭后明显",
    "失眠，靠手机才能睡", "头晕，眼前一黑", "失眠，夜里出汗", "头晕，转头时明显",
    "失眠，整宿睡不着", "头晕，站着发飘",
], "否", "内科", expect_dept="内科", note="关键词：头晕/失眠")

# ---------------------------------------------------------------- 内科（消化）
add(G_DIG, "腹痛/腹泻", [
    "腹痛两天", "腹泻三天", "上腹痛", "肚脐周围腹痛", "腹泻，水样便", "腹痛，还能吃饭",
    "腹泻，一天五次", "腹痛，一阵一阵", "腹泻，肚子咕噜响", "腹泻，吃坏东西",
    "腹痛，按着更疼", "腹泻一周", "腹痛，大便不正常", "腹泻，粘液便", "腹痛，弯腰好点",
    "腹痛，月经期", "腹泻，孩子", "腹痛，位置不固定", "腹泻，没发烧", "腹痛，左下腹",
    "腹痛，放屁后缓解", "腹痛，持续三天", "腹泻，在外面吃饭后", "右下腹痛",
    "腹痛，一阵一阵绞痛", "腹泻，带点没消化的东西", "腹痛，晚上加重",
], "否", "内科", expect_dept="内科", note="关键词：腹痛/腹泻")

add(G_DIG, "胃痛/胃胀/恶心/呕吐", [
    "胃痛", "胃胀", "恶心", "呕吐", "饭后胃痛", "空腹胃痛", "胃胀，打嗝",
    "恶心，吃不下", "呕吐两次", "胃痛，反酸", "吃辣就胃痛", "恶心，闻到油味",
    "胃胀，嗳气", "胃痛多年，想复查胃镜", "恶心，早上明显", "呕吐，吃什么吐什么",
    "胃痛，夜里疼醒", "恶心，头晕", "呕吐，发烧", "胃痛，瘦了好几斤", "恶心，嗓子发苦",
    "胃痛，喝冷水加重", "呕吐，肚子疼", "胃胀，屁多", "恶心，反胃", "胃痛，吃饱就疼",
    "恶心，刷牙时", "呕吐，头晕", "胃痛，后背也疼", "胃胀，消化不好", "恶心，油腻食物后",
    "呕吐，一天三次", "胃痛，吃药就好", "恶心，持续两天", "胃痛，喝粥好点",
], "否", "内科", expect_dept="内科", note="关键词：胃痛/胃胀/恶心/呕吐")

for core in CORES:
    if core["group"] in (G_RESP, G_NEURO, G_DIG):
        core["expect_dept"] = engine.advise(core["text"])[0]
        core["clin_dept"] = core["expect_dept"]

# ---------------------------------------------------------------- 全科（无关键词，临床也是全科）
add(G_GEN, "非目录专科/其他主诉", [
    "手腕骨折两天", "脚踝扭了，能走路", "腰疼一个月", "脖子疼，落枕", "肩膀疼，抬不起来",
    "牙疼三天", "牙龈肿了", "眼睛红，有分泌物", "眼睛干涩", "耳朵疼，没有发烧",
    "耳朵嗡嗡响", "嗓子疼三天", "皮肤痒两天", "起了一片红疹", "手上长湿疹",
    "尿频尿急", "小便疼", "血尿", "高血压要开药", "糖尿病复查", "血糖有点高想来复查",
    "甲状腺结节复查", "血脂高，想复查", "痛风，脚趾疼", "类风湿复查", "贫血，想复诊",
    "孕检，想建档", "产后复查", "月经不调", "白带异常", "备孕咨询", "前列腺复查",
    "痔疮，便血", "疝气复查", "静脉曲张", "下肢水肿", "颈椎病，手麻", "腰椎间盘突出复查",
    "膝关节疼", "足跟疼", "脱发", "痤疮，脸上长痘", "灰指甲", "烫伤，起了个泡",
    "被猫抓了，要打疫苗", "体检报告看不懂", "想做入职体检", "儿童体检", "打疫苗咨询",
    "狂犬疫苗第二针", "伤口换药", "拆线", "鼻出血", "打鼾，睡觉憋醒",
    "口臭", "口腔溃疡", "腮腺肿", "锁骨骨折", "手指麻木", "小腿抽筋", "关节酸痛",
    "带状疱疹，皮肤疼", "荨麻疹，皮肤起包", "甲沟炎", "腋下肿块", "乳房肿块",
    "淋巴结肿大", "体重下降，想检查", "乏力一个月", "低烧一周", "盗汗，消瘦",
    "面色发黄", "指甲发白", "走路不稳，容易摔", "说话声音哑了", "眼睛看东西模糊",
    "飞蚊症", "眼睛干，看电脑多", "牙齿矫正咨询", "落枕，脖子不敢转", "手腕腱鞘炎",
    "屁股上长了个包", "后背长了个疙瘩",
], "否", "全科", expect_dept=None, note="对得上专科走专科，对不上走全科；生产期望由复刻器算出")
for core in CORES:
    if core["group"] == G_GEN:
        core["clin_dept"] = engine.advise(core["text"])[0]

# ---------------------------------------------------------------- 全科（口语漏词：临床应有专科）
add(G_GEN_LEAK, "口语说法未进关键词表", [
    "喉咙痒，老想清嗓子", "嗓子眼发紧，喘气粗", "有点喘", "走快了喘得慌", "嗓子呼噜呼噜的",
    "打喷嚏流鼻涕", "感冒了，鼻子不通气", "睡觉打呼噜", "嗓子有异物感，总咳不出来",
    "脑袋疼", "脑袋昏昏沉沉", "睡不好，入睡难", "整宿睡不着", "脑子迷糊", "站着要晕",
    "头昏", "偏头疼", "太阳穴跳着疼", "记性越来越差", "手脚发麻", "脑子里嗡嗡响",
    "拉肚子", "肚子疼", "肚子里咕噜叫", "想吐", "反酸烧心", "吃不下饭，看见饭就腻",
    "大便不成形", "便秘一周", "放屁特别臭", "嘴里发苦", "吃完就跑厕所", "胃酸，夜里加重",
    "胆囊那边疼", "肝区不舒服", "打嗝停不下来", "舌苔厚，口臭", "食欲差，瘦了",
    "饭后打饱嗝", "上腹部堵得慌", "肚脐上面疼",
], "否", "见临床期望", note="对不上词生产落全科；临床期望按开通 10 科划分")

# 单独给这批补临床科室（只落开通的 10 科，对不上走全科）
_LEAK_DEPT = {
    "喉咙痒，老想清嗓子": "内科", "嗓子眼发紧，喘气粗": "内科", "有点喘": "内科",
    "走快了喘得慌": "内科", "嗓子呼噜呼噜的": "内科", "打喷嚏流鼻涕": "耳鼻喉科",
    "感冒了，鼻子不通气": "内科", "睡觉打呼噜": "耳鼻喉科",
    "嗓子有异物感，总咳不出来": "内科",
    "脑袋疼": "内科", "脑袋昏昏沉沉": "内科", "睡不好，入睡难": "内科",
    "整宿睡不着": "内科", "脑子迷糊": "内科", "站着要晕": "内科",
    "头昏": "内科", "偏头疼": "内科", "太阳穴跳着疼": "内科",
    "记性越来越差": "内科", "手脚发麻": "内科", "脑子里嗡嗡响": "内科",
    "拉肚子": "内科", "肚子疼": "内科", "肚子里咕噜叫": "内科", "想吐": "内科",
    "反酸烧心": "内科", "吃不下饭，看见饭就腻": "内科", "大便不成形": "内科",
    "便秘一周": "内科", "放屁特别臭": "内科", "嘴里发苦": "内科",
    "吃完就跑厕所": "内科", "胃酸，夜里加重": "内科", "胆囊那边疼": "内科",
    "肝区不舒服": "内科", "打嗝停不下来": "内科", "舌苔厚，口臭": "口腔科",
    "食欲差，瘦了": "内科", "饭后打饱嗝": "内科", "上腹部堵得慌": "内科",
    "肚脐上面疼": "内科",
}
for core in CORES:
    if core["group"] == G_GEN_LEAK:
        produced = engine.advise(core["text"])[0]
        core["clin_dept"] = produced if produced != "全科" else _LEAK_DEPT.get(core["text"], "全科")

# ---------------------------------------------------------------- 多症状：谁先出现谁赢
add(G_MULTI, "两科关键词同时出现", [
    "咳嗽还头痛", "头痛加咳嗽", "咳嗽，肚子也不舒服", "腹痛，还有点咳嗽",
    "头痛伴腹痛", "腹痛伴头痛", "头晕还腹泻", "腹泻还头晕", "失眠，胃也痛",
    "胃痛，还失眠", "气促，还有点恶心", "恶心，还有点气促", "胸闷，头也痛",
    "头痛，还胸闷", "呕吐，还有点咳嗽", "咳嗽，还有点呕吐", "咳嗽、头痛、腹痛",
    "腹痛、头痛、咳嗽", "头痛、咳嗽、腹痛", "腹泻，咳嗽", "咳嗽，腹泻", "头晕，失眠",
    "失眠，头晕", "哮喘，还失眠", "失眠，还有点哮喘", "胃胀，头痛", "头痛，胃胀",
    "恶心，失眠", "失眠，恶心", "干咳，还头晕", "头晕，还干咳", "咳黄痰，胃不舒服",
    "胃痛，还咳嗽", "咳嗽，胃痛", "腹痛，头晕", "头晕，腹痛", "喘息，失眠",
    "失眠，喘息", "呕吐两天，还头痛", "头痛两天，还呕吐",
], "否", "见期望", expect_dept=None, note="同句多个科室关键词，按最先出现的关键词定科")
_MULTI_DEPT = {
    "咳嗽还头痛": G_RESP, "头痛加咳嗽": G_NEURO, "咳嗽，肚子也不舒服": G_RESP,
    "腹痛，还有点咳嗽": G_DIG, "头痛伴腹痛": G_NEURO, "腹痛伴头痛": G_DIG,
    "头晕还腹泻": G_NEURO, "腹泻还头晕": G_DIG, "失眠，胃也痛": G_NEURO,
    "胃痛，还失眠": G_DIG, "气促，还有点恶心": G_RESP, "恶心，还有点气促": G_DIG,
    "胸闷，头也痛": G_RESP, "头痛，还胸闷": G_NEURO, "呕吐，还有点咳嗽": G_DIG,
    "咳嗽，还有点呕吐": G_RESP, "咳嗽、头痛、腹痛": G_RESP, "腹痛、头痛、咳嗽": G_DIG,
    "头痛、咳嗽、腹痛": G_NEURO, "腹泻，咳嗽": G_DIG, "咳嗽，腹泻": G_RESP,
    "头晕，失眠": G_NEURO, "失眠，头晕": G_NEURO, "哮喘，还失眠": G_RESP,
    "失眠，还有点哮喘": G_NEURO, "胃胀，头痛": G_DIG, "头痛，胃胀": G_NEURO,
    "恶心，失眠": G_DIG, "失眠，恶心": G_NEURO, "干咳，还头晕": G_RESP,
    "头晕，还干咳": G_NEURO, "咳黄痰，胃不舒服": G_RESP, "胃痛，还咳嗽": G_DIG,
    "咳嗽，胃痛": G_RESP, "腹痛，头晕": G_DIG, "头晕，腹痛": G_NEURO,
    "喘息，失眠": G_RESP, "失眠，喘息": G_NEURO, "呕吐两天，还头痛": G_DIG,
    "头痛两天，还呕吐": G_NEURO,
}
for core in CORES:
    if core["group"] == G_MULTI:
        core["clin_dept"] = _MULTI_DEPT[core["text"]]
        core["expect_dept"] = _MULTI_DEPT[core["text"]]

# ---------------------------------------------------------------- 同位置长短词竞争
add(G_LONG, "长短关键词同位置", [
    "干咳两个月", "咳嗽有痰", "偏头痛三年", "头痛三天", "哮喘复查", "喘息两天",
    "腹痛两天", "腹泻一周", "恶心两天", "呕吐一次", "胃痛三天", "胃胀两天",
    "气促两天", "黄痰三天", "胸闷两天",
], "否", "见期望", note="「干咳/咳」「偏头痛/头痛」同位置，应取更长的那个做依据")
_LONG_EVIDENCE = {
    "干咳两个月": (G_RESP, "干咳"), "咳嗽有痰": (G_RESP, "咳嗽"), "偏头痛三年": (G_NEURO, "偏头痛"),
    "头痛三天": (G_NEURO, "头痛"), "哮喘复查": (G_RESP, "哮喘"), "喘息两天": (G_RESP, "喘息"),
    "腹痛两天": (G_DIG, "腹痛"), "腹泻一周": (G_DIG, "腹泻"), "恶心两天": (G_DIG, "恶心"),
    "呕吐一次": (G_DIG, "呕吐"), "胃痛三天": (G_DIG, "胃痛"), "胃胀两天": (G_DIG, "胃胀"),
    "气促两天": (G_RESP, "气促"), "黄痰三天": (G_RESP, "黄痰"), "胸闷两天": (G_RESP, "胸闷"),
}
for core in CORES:
    if core["group"] == G_LONG:
        dept, evidence = _LONG_EVIDENCE[core["text"]]
        core["clin_dept"] = dept
        core["expect_dept"] = dept
        core["expect_evidence"] = evidence

# ---------------------------------------------------------------- 急症 BASELINE（应 120）
add(G_URG, "急性胸痛", [
    "胸痛", "突然胸痛", "胸痛得厉害", "胸痛往左胳膊窜", "胸痛出冷汗", "胸痛还恶心",
    "胸痛喘不上来", "胸痛两小时了", "半夜胸痛", "胸痛，站不住",
], "是", EMERG, note="BASELINE 别名「胸痛」，生产应 120 且不出卡")
add(G_URG, "呼吸危象", [
    "呼吸困难", "突然呼吸困难", "呼吸困难说不上整句", "呼吸困难嘴唇发紫", "我爸呼吸困难",
    "孩子呼吸困难", "呼吸困难，脸都白了",
], "是", EMERG, note="BASELINE 别名「呼吸困难」")
add(G_URG, "意识改变", [
    "意识障碍", "突然意识障碍", "意识障碍叫不醒",
], "是", EMERG, note="BASELINE 别名「意识障碍」")
add(G_URG, "发绀", [
    "口唇发紫", "口唇发紫还喘", "孩子口唇发紫",
], "是", EMERG, note="BASELINE 别名「口唇发紫」")
# D-15：否定/疑问/已缓解仍升级（已发布行为），临床期望是不该 120，用来暴露误报
add(G_URG, "否定与疑问仍升级", [
    "没有胸痛", "是不是胸痛", "胸痛已经好了", "没有呼吸困难", "好像呼吸困难",
    "没有意识障碍", "没有口唇发紫",
], "否", "全科", note="D-15 已发布行为：否定/疑问仍升级；临床期望不 120，属误报观察项")

# ---------------------------------------------------------------- 急症 DRAFT（影子）
add(G_SHADOW, "急性胸痛口语", [
    "胸口痛", "心口疼", "胸口闷", "压着痛", "撕裂样痛", "心口像被石头压着痛",
], "是", EMERG, shadow="URG-DRAFT-001", note="生产 SHADOW，不 120，应记影子")
add(G_SHADOW, "呼吸危象口语", [
    "喘不上气", "上不来气", "憋气", "说话说不全",
], "是", EMERG, shadow="URG-DRAFT-002", note="生产 SHADOW")
add(G_SHADOW, "卒中样表现", [
    "口角歪斜", "说话含糊", "说话不清", "一侧肢体无力", "突发剧烈头痛",
], "是", EMERG, shadow="URG-DRAFT-003", note="生产 SHADOW；突发剧烈头痛仍会先落内科")
add(G_SHADOW, "急性出血", [
    "吐血", "黑便", "大出血",
], "是", EMERG, shadow="URG-DRAFT-004", note="生产 SHADOW")
add(G_SHADOW, "过敏危象", [
    "喉头肿", "全身风团", "过敏后喘鸣",
], "是", EMERG, shadow="URG-DRAFT-005", note="生产 SHADOW")
add(G_SHADOW, "高热惊厥", [
    "高热惊厥", "抽搐伴高热",
], "是", EMERG, shadow="URG-DRAFT-006", note="生产 SHADOW")
add(G_SHADOW, "自伤风险", [
    "想自杀", "要自伤",
], "是", EMERG, shadow="URG-DRAFT-007", note="生产 SHADOW；上线需另配值班与转介")
add(G_SHADOW, "休克样表现", [
    "面色灰白", "几乎摸不到脉",
], "是", EMERG, shadow="URG-DRAFT-008", note="生产 SHADOW")

# ---------------------------------------------------------------- 急症漏报（目录未覆盖的中文口语）
add(G_URG_LEAK, "急性冠脉口语", [
    "心口像压了块大石头", "胸口像被撕开一样", "左胸口发紧出冷汗", "心口窝疼得满头汗",
    "前胸疼得直不起腰", "心跳特别快胸口发慌",
], "是", EMERG, note="临床急症，目录无别名，生产不 120")
add(G_URG_LEAK, "呼吸危象口语", [
    "喘不过气来", "吸不上气要憋死", "喘气像拉风箱", "气都快上不来了", "哮喘发作吸药也没用",
], "是", EMERG, note="临床急症，目录无别名")
add(G_URG_LEAK, "发绀口语", [
    "嘴唇发青", "嘴唇乌青", "脸白得像纸浑身冷汗",
], "是", EMERG, note="临床急症，目录无别名")
add(G_URG_LEAK, "意识/神经急症口语", [
    "昏过去了叫不醒", "人已经不省人事", "抽过去了眼睛往上翻", "口眼歪斜",
    "一边脸麻一边手抬不起来", "说话大舌头", "突然说不出话右边身子动不了",
    "叫不醒怎么摇都没反应", "脖子硬高热身上紫斑", "抽风停不下来",
    "腰以下突然没知觉还尿失禁", "头像炸开一样痛还吐", "吃了好多安眠药现在困得叫不醒",
], "是", EMERG, note="临床急症，目录无别名")
add(G_URG_LEAK, "出血/创伤口语", [
    "喷出一大口血", "大便黑得像柏油", "咯血", "吐了一大口鲜血", "肚子剧痛蜷成一团",
    "高处摔下来站不起来", "被车撞了胳膊变形", "异物卡喉说不出话", "小孩误吞纽扣电池",
], "是", EMERG, note="临床急症，目录无别名")
add(G_URG_LEAK, "过敏/其他急症口语", [
    "吃花生后嗓子堵住了", "浑身风疹块还喘", "小孩发烧后全身抽", "活不下去了要跳楼",
    "摸不到脉搏脸色惨白", "坐飞机后胸口刺痛小腿肿",
], "是", EMERG, note="临床急症，目录无别名")

# ---------------------------------------------------------------- 急症优先于科室
add(G_URG_PRIOR, "急症压过科室路由", [
    "呼吸困难，想去呼吸内科看看", "胸痛，帮我挂个心内科", "胸痛并转人工",
    "呼吸困难还要找人工", "口唇发紫，想挂呼吸内科", "意识障碍，先帮我挂号",
], "是", EMERG, note="含科室/转人工信号时，急症仍优先，不出卡")

# ---------------------------------------------------------------- 转人工
add(G_HUMAN, "要求人工", [
    "转人工", "找人工", "人工客服", "转人工客服", "接人工客服", "转接人工", "转接客服",
    "找客服", "转客服", "接人工", "请转人工客服", "帮我接人工客服", "我要人工客服",
    "接通人工客服", "帮我转人工", "我想找人工客服",
], "否", HUMAN_DEPT, note="BASELINE 转人工；不 120，不出卡")
add(G_HUMAN_NEG, "转人工反例", [
    "不要转人工，干咳半年", "人工智能导诊，干咳半年", "不要接人工，头痛两天",
    "不要给我转人工，胃痛三天", "不用人工客服，咳嗽三天", "不是要转人工，就是想问问咳嗽",
], "否", "见期望", note="否定转人工 / 「人工智能」不应转人工")
for core in CORES:
    if core["group"] == G_HUMAN_NEG:
        got = engine.advise(core["text"])[0]
        core["clin_dept"] = got
        core["expect_dept"] = got
add(G_HUMAN_LEAK, "转人工漏报", [
    "客服在哪", "有人工吗", "人工呢", "找个人问问", "能找个人帮我吗",
], "否", HUMAN_DEPT, note="口语转人工；已纳入 HUMAN-BASE-001")

# ---------------------------------------------------------------- 描述不清：只说「不舒服」
add(G_VAGUE, "只说不舒服/难受", [
    "不舒服", "难受", "有点不舒服", "不太舒服", "浑身不舒服", "身上不舒服", "身体不舒服",
    "不太舒服，说不上来", "浑身难受，说不清", "不舒服，也不知道咋回事", "感觉不太好",
    "浑身不好受", "身体不适", "最近身体不适", "有点难受", "特别难受", "难受死了",
    "浑身难受", "总觉得不舒服", "浑身都不舒服", "感觉身体不适", "不太对劲", "有点不对劲",
    "说不清哪里不舒服", "哪儿都不舒服", "浑身不自在", "有点事", "生病了", "最近生病了",
    "不得劲", "身上不得劲", "浑身不得劲",
], "否", ASK, note="只说笼统不适：无症状词、无病程/年龄性别，应追问，不进选就诊人")

add(G_UNCLEAR, "打招呼/无意义输入", [
    "你好", "在吗", "有人吗", "请问", "咨询一下", "我想问问", "问个事", "在不在",
    "能听见吗", "说话", "嗯", "哦", "啊", "。。。。", "？？？", "啊啊啊", "hi", "哈喽",
    "有人没", "测试", "我是",
], "否", ASK, note="没说病情：应追问，不进选就诊人")
add(G_UNCLEAR, "只说诉求/问流程，不说病情", [
    "帮我", "帮我看看", "帮我挂号", "我想挂号", "挂什么科", "我要看病", "挂个号",
    "预约挂号", "挂号流程", "我要预约", "我要咨询", "看看病", "想看医生", "我要退号",
    "怎么挂号", "挂哪个科", "专家号", "要排队吗", "能预约吗", "医保能报吗", "今天能挂吗",
    "挂号多少钱", "要带啥", "我有医保", "第一次来", "我在外地", "明天", "下午",
    "我要拿药", "我要开药", "开个证明", "我来复查", "我复诊",
], "否", ASK, note="只有诉求没有病情：应追问，不进选就诊人")
add(G_UNCLEAR, "只说情绪，不说症状", [
    "我很担心", "我害怕", "急死了",
], "否", ASK, note="只表达情绪：应追问，不进选就诊人")

add(G_SHORT, "只说部位/症状，短到判不了", [
    "牙疼", "腰疼", "皮疹", "脚崴了", "耳鸣", "口臭", "脱发", "鼻塞", "打鼾",
    "手麻", "盗汗", "便血", "尿频", "乏力", "低烧", "头疼", "肚子疼", "背疼", "腿疼",
    "脖子疼", "肩膀疼", "膝盖疼", "脚疼", "关节疼", "手疼", "身上痒", "下面痒",
    "脸上痒", "头皮痒", "手上起皮", "没劲", "没力气", "心慌", "胸闷", "气短", "呕吐",
    "失眠", "哮喘", "发烧", "出汗", "浮肿", "肿块", "结节", "我失眠", "我喘", "我想吐",
    "我反酸", "我拉肚子",
], "否", ASK, note="长度小于 6 且无可识别症状/槽位：应追问。多数连部位都没说清，临床也判不了科")

add(G_RISK, "含糊但听起来很急", [
    "快不行了", "撑不住了", "疼得受不了", "难受得要命", "要疼死了", "顶不住了",
    "实在受不了", "感觉要死了", "快没气了", "撑不下去了",
], "是", EMERG, note="高危短句生产转人工，不升 120；临床期望仍是急诊")

# ---------------------------------------------------------------- 判不了科室
add(G_NODEPT, "部位说不清", [
    "肚子疼，说不清哪个位置", "肚子不舒服，具体哪儿说不上来", "胸口疼，也不知道是哪儿",
    "头疼，说不清是哪种疼", "浑身疼，哪儿都疼", "后背疼，位置不固定",
    "关节疼，好几个关节都疼", "肚子疼，一会这儿一会那儿", "疼的地方串着走",
], "否", NO_DEPT, note="部位不明，临床也要先追问；落全科算可接受兜底")
add(G_NODEPT, "跨科症状并存", [
    "又咳嗽又胃痛，不知道先看哪个", "头痛还呕吐，分不清先后", "头晕加肚子疼，一起来",
    "胸闷还拉肚子", "咳嗽，胃也不舒服，都挺重", "头痛，肚子也疼，说不上哪个厉害",
    "又喘又拉肚子", "恶心还咳嗽，不知道挂哪个科", "失眠加胃痛，都两三个月了",
    "头晕伴呕吐，站不住", "咳嗽，腰也疼，不知道挂哪科", "胃痛加头晕，一起来",
], "否", NO_DEPT, note="两个科的症状同时在：谁先出现谁赢是关键词规则，不是临床判断")
add(G_NODEPT, "全身/非特异症状", [
    "浑身没劲，也说不清哪儿有病", "人瘦了一大圈，查不出原因", "低烧一个月，找不到原因",
    "晚上出汗，白天没精神", "全身浮肿，不知道看什么科", "食欲差，人也没劲",
    "总是累，休息也不解乏", "体重突然下降，不知道查什么", "一阵一阵发热，也说不好",
    "浑身发沉，干什么都提不起劲",
], "否", NO_DEPT, note="非特异全身症状：本就该落全科或追问")
add(G_NODEPT, "症状自相矛盾/说不连贯", [
    "一会头疼一会肚子疼", "说不清是胃疼还是肚子疼", "好像是头疼，又好像是晕",
    "胸口不舒服，也分不清是喘还是疼", "有时候咳，有时候吐，说不好",
    "前两天说肚子疼，今天又说头晕", "一会儿冷一会儿热，说不清",
], "否", NO_DEPT, note="描述前后不一致：应追问澄清")
add(G_NODEPT, "要检查才定得了", [
    "体检查出有个结节，不知道挂什么科", "化验单好几项箭头，看不懂",
    "拍片说肺上有点阴影，要复查", "B 超说有个肿块，让进一步查",
    "体检报告一堆异常，不知道看哪个科", "肿瘤标志物高，要复查",
    "心电图有点问题，让去医院看", "验血好几项不正常",
], "否", NO_DEPT, note="需要线下检查才能定科；「需要线下拍片」是 UNSUITABLE 的 DRAFT 测试规则")
add(G_NODEPT, "特殊人群说不清", [
    "孩子一直哭，说不清哪儿疼", "老人糊涂，说不明白哪儿不舒服",
    "孕妇，说不清是胃疼还是胎动", "小孩不会说，就是闹", "老人只会说难受，问不出别的",
    "小孩光哭，指不出哪儿疼", "老人耳背，问半天问不清",
], "否", NO_DEPT, note="代述者描述不清：应追问或转人工")

# ---------------------------------------------------------------- 口吻包装
WRAPPERS: list[tuple] = [
    ("{core}", "本人自述", False),
    ("我{core}", "本人自述", False),
    ("我{core}，想挂个号", "挂号咨询口吻", False),
    ("我{core}，该挂什么科", "挂号咨询口吻", False),
    ("我爸{core}", "代家属描述", False),
    ("我妈{core}", "代家属描述", False),
    ("我家孩子{core}", "代家属描述", False),
    ("家里老人{core}", "代家属描述", False),
    ("我老公{core}", "代家属描述", False),
    ("我老婆{core}", "代家属描述", False),
    ("我儿子{core}", "代家属描述", False),
    ("我女儿{core}", "代家属描述", False),
    ("我奶奶{core}", "代家属描述", False),
    ("我爷爷{core}", "代家属描述", False),
    ("男，45岁，{core}", "带年龄性别", False),
    ("女，32岁，{core}", "带年龄性别", False),
    ("男，68岁，{core}", "带年龄性别", False),
    ("女，72岁，{core}", "带年龄性别", False),
    ("男，8岁，{core}", "带年龄性别", False),
    ("{core}，已经三天了", "带病程", False),
    ("{core}，反反复复的", "带病程", False),
    ("{core}，吃药也没见好", "带病程", False),
    ("{core}，晚上更厉害", "带病程", False),
    ("{core}，白天还好点", "带病程", False),
    ("你好，{core}", "礼貌前缀", False),
    ("医生您好，{core}", "礼貌前缀", False),
    ("那个……{core}", "口语语气词", False),
    ("{core}，需要去医院吗", "咨询口吻", False),
    ("{core}，要紧吗", "咨询口吻", False),
    ("{core}，麻烦帮我约个号", "挂号咨询口吻", False),
]

# 输入噪声：错别字 / 插空格 / 结巴重复 / 冗长口播 / 语气填充 / 标点轰炸
TYPO = [
    ("胸痛", "胸疼"), ("头痛", "头通"), ("咳嗽", "刻嗽"), ("腹痛", "腹疼"), ("腹泻", "腹泄"),
    ("胃痛", "胃疼"), ("失眠", "失眼"), ("哮喘", "孝喘"), ("呕吐", "藕吐"), ("头晕", "头运"),
    ("恶心", "饿心"), ("呼吸困难", "呼息困难"), ("口唇发紫", "口唇发子"), ("胸闷", "胸焖"),
]


def typo_text(core: str) -> str:
    out = core
    for good, bad in TYPO:
        out = out.replace(good, bad)
    return out


NOISE_WRAPPERS: list[tuple] = [
    (lambda core: f"嗯，就是，那个，{core}，麻烦您给看看", "输入噪声-语气填充", True),
    (lambda core: f"我就是想问一下啊，{core}，这个情况严不严重，要不要去医院看看，麻烦您帮我看看挂哪个科合适", "输入噪声-冗长口播", True),
    (lambda core: " ".join(core), "输入噪声-字间插空格", True),
    (lambda core: typo_text(core), "输入噪声-错别字", True),
    (lambda core: f"{core}，{core}，老是这样", "输入噪声-结巴重复", True),
    (lambda core: f"{core}！！！在线等", "输入噪声-标点轰炸", True),
]

# 包装会注入孩子/年龄/性别，可能把科室从专科改到儿科或按性别改科，不算声明失败。
WRAPPER_MAY_REROUTE = {"代家属描述", "带年龄性别"}

# 描述不清组只能用「不会补出性别/年龄/病程、不会把句子拉长」的短包装。
# 按新增字数从少到多排，保证短核心说法先拿到能用的包装。
SHORT_WRAPPERS: list[tuple] = [
    ("{core}", "本人自述", False),
    ("我{core}", "本人自述", False),
    ("就是{core}", "口语语气词", False),
    ("最近{core}", "口语语气词", False),
    ("{core}……", "口语语气词", False),
    ("我爸{core}", "代家属描述", False),
    ("{core}，咋办", "咨询口吻", False),
    ("医生，{core}", "礼貌前缀", False),
    ("你好，{core}", "礼貌前缀", False),
    ("请问，{core}", "礼貌前缀", False),
    ("{core}，谢谢", "礼貌前缀", False),
    ("我儿子{core}", "代家属描述", False),
    ("我老公{core}", "代家属描述", False),
    ("家里老人{core}", "代家属描述", False),
    ("{core}，麻烦了", "礼貌前缀", False),
    ("{core}，能看看吗", "咨询口吻", False),
]


def allowed_wrappers(core: dict) -> list[tuple]:
    if core["group"] in UNCLEAR_GROUPS:
        # 这组不轮转，让每条核心说法都从最短的包装开始拿。
        return SHORT_WRAPPERS
    base = WRAPPERS + NOISE_WRAPPERS
    # 用稳定哈希做轮转起点，保证每次生成结果完全一致。
    offset = zlib.crc32(core["text"].encode("utf-8")) % len(base)
    return base[offset:] + base[:offset]


def wrap_text(raw, core: str) -> str | None:
    """套包装。核心说法已经带「我」「孩子」时，不再叠一层，避免「我我要」「孩子孩子」。"""
    if callable(raw):
        return raw(core)
    if raw.startswith("我{core}") and core.startswith("我"):
        return None
    if "我家孩子{core}" in raw and core.startswith("孩子"):
        return None
    if raw.startswith("男，8岁，{core}") and core.startswith(("我爸", "我妈", "我老公", "我老婆", "我奶奶", "我爷爷")):
        return None
    return raw.format(core=core)


def clean(text: str) -> str:
    return " ".join(text.split()).replace("，，", "，").strip("，").strip()


def build_rows() -> list[dict]:
    rules = engine.load_rules()
    rows: list[dict] = []
    seen: set[str] = set()
    mismatch: list[str] = []
    prepared = [(core, allowed_wrappers(core)) for core in CORES]
    round_no = 0
    while len(rows) < TARGET:
        produced = False
        for core, wrappers in prepared:
            if round_no >= len(wrappers):
                continue
            produced = True
            wrapper = wrappers[round_no]
            raw = wrapper[0]
            rendered = wrap_text(raw, core["text"])
            if rendered is None:
                continue
            text = clean(rendered)
            if not text or text in seen:
                continue
            got = engine.evaluate(text, rules)
            # 描述不清组必须真的落「待描述」才收；包装后变成可出卡的，就不属于这组。
            if core["group"] in UNCLEAR_GROUPS and got["status"] != engine.STATUS_NEED_DESC:
                continue
            seen.add(text)
            dept = got["department"]
            evidence = got["evidence"] or ""
            noise = bool(wrapper[2])
            clin_dept = core["clin_dept"]
            if got["status"] == engine.STATUS_PLAIN:
                if core["group"] in (G_RESP, G_NEURO, G_DIG, G_GEN, G_MULTI, G_LONG):
                    clin_dept = dept
                elif dept == "儿科" and clin_dept not in (NO_DEPT, EMERG, ASK, HUMAN_DEPT):
                    clin_dept = dept
            # 声明与复刻结果对不上就报出来，避免数据集自相矛盾（噪声包装允许改变结果）
            if not noise:
                if (
                    core["expect_dept"]
                    and got["status"] == engine.STATUS_PLAIN
                    and dept != core["expect_dept"]
                    and wrapper[1] not in WRAPPER_MAY_REROUTE
                ):
                    mismatch.append(f"[科室] {core['text']} -> {text} 期望{core['expect_dept']} 实得{dept}({evidence})")
                if (
                    core["expect_evidence"]
                    and got["status"] == engine.STATUS_PLAIN
                    and evidence != core["expect_evidence"]
                    and wrapper[1] not in WRAPPER_MAY_REROUTE
                ):
                    mismatch.append(f"[依据] {core['text']} -> {text} 期望{core['expect_evidence']} 实得{evidence}")
                if core["group"] == G_URG and not got["urgent"]:
                    mismatch.append(f"[急症] {core['text']} -> {text} 应 120 实得 {got['status']}")
                if core["group"] == G_SHADOW and core["shadow"] not in got["shadows"]:
                    mismatch.append(f"[影子] {core['text']} -> {text} 应记 {core['shadow']} 实得 {got['shadows']}")
                if core["group"] == G_HUMAN and got["status"] != engine.STATUS_HUMAN:
                    mismatch.append(f"[转人工] {core['text']} -> {text} 实得 {got['status']}")
                if core["group"] == G_HUMAN_NEG and got["status"] == engine.STATUS_HUMAN:
                    mismatch.append(f"[转人工反例] {core['text']} -> {text} 不应转人工")
                if core["group"] in UNCLEAR_GROUPS and got["status"] != engine.STATUS_NEED_DESC:
                    mismatch.append(f"[待描述] {core['text']} -> {text} 实得 {got['status']}")
            rows.append(
                {
                    "核心说法": core["text"],
                    "话术": text,
                    "分组": core["group"],
                    "临床主题": core["topic"],
                    "场景": wrapper[1],
                    "生产期望状态": got["status"],
                    "生产是否应120": "是" if got["urgent"] else "否",
                    "生产期望科室": dept if got["status"] == engine.STATUS_PLAIN else (
                        "（未出卡-追问）" if got["status"] == engine.STATUS_NEED_DESC else "（不出卡）"
                    ),
                    "生产科室依据": evidence if got["status"] == engine.STATUS_PLAIN else "",
                    "期望影子规则": ",".join(got["shadows"]),
                    "命中症状标签": ",".join(got["symptoms"]),
                    "临床期望急症": core["clin_urgent"],
                    "临床期望科室": clin_dept,
                    "生产与临床差异": "",
                    "说明": core["note"],
                }
            )
            if len(rows) >= TARGET:
                break
        if not produced:
            break
        round_no += 1
    for row in rows:
        row["生产与临床差异"] = diff_of(row)
    if mismatch:
        print(f"!! 声明与复刻不一致 {len(mismatch)} 处，前 20 条：")
        for line in mismatch[:20]:
            print("   " + line)
    return rows


def diff_of(row: dict) -> str:
    """生产结果 vs 临床期望的差距标签。"""
    prod_urgent = row["生产是否应120"] == "是"
    clin_urgent = row["临床期望急症"] == "是"
    diffs: list[str] = []
    prod_dept = row["生产期望科室"]
    clin_dept = row["临床期望科室"]
    general = engine.general_label()

    # 高危短句：生产应转人工。没转到仍算「高危被当含糊」。
    if row["分组"] == G_RISK:
        if row["生产期望状态"] != engine.STATUS_HUMAN:
            diffs.append("高危被当含糊")
        return "/".join(diffs)

    # 描述不清组：临床结论就是「先追问」，生产也追问了就是一致。
    if row["分组"] in UNCLEAR_GROUPS:
        if clin_urgent and not prod_urgent:
            diffs.append("高危被当含糊")
        return "/".join(diffs)

    if clin_urgent and not prod_urgent:
        diffs.append("急症漏报")
    if prod_urgent and not clin_urgent:
        diffs.append("急症误报")
    if row["分组"] == G_HUMAN_LEAK and row["生产期望状态"] != engine.STATUS_HUMAN:
        diffs.append("转人工漏报")
    if row["生产期望状态"] == engine.STATUS_NEED_DESC:
        diffs.append("被追问未出卡")

    # 判不了科室：落全科是可接受兜底；被某个先出现的关键词硬定科才是问题。
    if row["分组"] == G_NODEPT and row["生产期望状态"] == engine.STATUS_PLAIN:
        if prod_dept != general:
            diffs.append("判不了却硬定科")
        return "/".join(diffs)

    # 临床期望是急诊/人工/追问时，差异已被对应标签覆盖，不再重复计一次科室差异。
    skip = (EMERG, HUMAN_DEPT, ASK)
    if row["生产期望状态"] == engine.STATUS_PLAIN and clin_dept not in skip and prod_dept != clin_dept:
        if prod_dept == general:
            diffs.append("科室漏词")
        else:
            diffs.append("科室误判")
    return "/".join(diffs)


def write_csv(path: Path, rows: list[dict]) -> None:
    fields = [
        "编号", "核心说法", "话术", "分组", "临床主题", "场景", "生产期望状态", "生产是否应120",
        "生产期望科室", "生产科室依据", "期望影子规则", "命中症状标签", "临床期望急症",
        "临床期望科室", "生产与临床差异", "说明",
    ]
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        for index, row in enumerate(rows, start=1):
            row = dict(row)
            row["编号"] = f"R{index:05d}"
            writer.writerow({key: row.get(key, "") for key in fields})


def main() -> None:
    rows = build_rows()
    try:
        write_csv(OUT, rows)
        target = OUT
    except PermissionError:
        write_csv(FALLBACK_OUT, rows)
        target = FALLBACK_OUT
    print(f"wrote {len(rows)} rows -> {target}")
    print(f"cores {len(CORES)}  catalog {engine.catalog_version()}")


if __name__ == "__main__":
    main()
