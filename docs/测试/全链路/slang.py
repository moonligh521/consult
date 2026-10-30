# -*- coding: utf-8 -*-
"""口语表：拼音、英文、错别字、数字单位、提问句式、家属代述。"""

LEXICON_VERSION = "2026.10.29"

# 来源与审核状态跟表达内容分开管理。REVIEW_REQUIRED 不能进入临床发布门禁。
EXPRESSION_METADATA = {
    "pinyin": {"source": "工程扰动词典", "region": "通用输入法", "review_status": "ENGINEERING_REVIEWED"},
    "english": {"source": "工程扰动词典", "region": "通用中英混输", "review_status": "ENGINEERING_REVIEWED"},
    "typo": {"source": "工程扰动词典", "region": "通用输入法", "review_status": "ENGINEERING_REVIEWED"},
    "dialect": {"source": "临床场景候选", "region": "待标注", "review_status": "REVIEW_REQUIRED"},
    "asr": {"source": "语音转写候选", "region": "通用普通话", "review_status": "REVIEW_REQUIRED"},
    "family_report": {"source": "多轮场景候选", "region": "通用", "review_status": "REVIEW_REQUIRED"},
}

# ---------------------------------------------------------------- ① 拼音
# canonical 词条 → 患者可能打出的拼音串：全拼连写 / 加空格 / 手滑拼写 / 首字母缩写
PINYIN_FORMS = {
    "咳嗽": ["kesou", "ke sou", "kesouu", "ks"],
    "头痛": ["toutong", "touteng", "tou tong", "tt"],
    "头疼": ["touteng", "toutong", "tou teng", "tt"],
    "头晕": ["touyun", "tou yun", "touyn", "ty"],
    "眩晕": ["xuanyun", "xuan yun"],
    "发热": ["fare", "fa re"],
    "发烧": ["fashao", "fa shao", "fs"],
    "乏力": ["fali", "fa li"],
    "腹泻": ["fuxie", "fu xie", "fx"],
    "腹痛": ["futong", "futeng", "fu tong"],
    "胃痛": ["weitong", "weiteng", "wei tong"],
    "恶心": ["exin", "e xin"],
    "呕吐": ["outu", "ou tu"],
    "便秘": ["bianmi", "bian mi", "bm"],
    "便血": ["bianxie", "bian xue"],
    "痔疮": ["zhichuang", "zhi chuang", "zc"],
    "肛裂": ["ganglie", "gang lie"],
    "皮疹": ["pizhen", "pi zhen", "pz"],
    "湿疹": ["shizhen", "shi zhen", "sz"],
    "荨麻疹": ["xunmazhen", "xun ma zhen", "xmz"],
    "痤疮": ["cuochuang", "cuo chuang", "cc"],
    "瘙痒": ["saoyang", "sao yang"],
    "过敏": ["guomin", "guo min", "gm"],
    "黄疸": ["huangdan", "huang dan", "hd"],
    "水肿": ["shuizhong", "shui zhong"],
    "牙疼": ["yateng", "yatong", "ya teng", "yt"],
    "牙痛": ["yatong", "ya tong"],
    "牙龈": ["yayin", "ya yin"],
    "龋齿": ["quchi", "qu chi"],
    "口腔溃疡": ["kouqiangkuiyang", "kouqiang kuiyang", "kqky"],
    "咽痛": ["yantong", "yan tong"],
    "鼻塞": ["bisai", "bi sai", "bs"],
    "流鼻涕": ["liubiti", "liu biti", "lbt"],
    "打喷嚏": ["dapenti", "da penti"],
    "耳鸣": ["erming", "er ming", "em"],
    "近视": ["jinshi", "jin shi", "js"],
    "白内障": ["baineizhang", "bai neizhang", "bnzz"],
    "青光眼": ["qingguangyan", "qgy"],
    "失眠": ["shimian", "shi mian", "sm"],
    "焦虑": ["jiaolv", "jiao lv", "jl"],
    "抑郁": ["yiyu", "yi yu", "yy"],
    "哮喘": ["xiaochuan", "xiao chuan", "xc"],
    "骨折": ["guzhe", "gu zhe", "gz"],
    "腰痛": ["yaotong", "yao tong", "yaoteng"],
    "颈椎病": ["jingzhuibing", "jzb"],
    "关节炎": ["guanjieyan", "gjy"],
    "骨质疏松": ["guzhishusong", "gzss"],
    "扭伤": ["niushang", "niu shang"],
    "烫伤": ["tangshang", "tang shang"],
    "高血压": ["gaoxueya", "gao xue ya", "gxy"],
    "糖尿病": ["tangniaobing", "tnb"],
    "甲亢": ["jiakang", "jia kang"],
    "冠心病": ["guanxinbing", "gxb"],
    "贫血": ["pinxue", "pin xue", "px"],
    "甲状腺结节": ["jiazhuangxianjiejie", "jzxjj"],
    "尿频": ["niaopin", "niao pin", "np"],
    "尿痛": ["niaotong", "niao tong"],
    "肾结石": ["shenjieshi", "sjs"],
    "膀胱炎": ["pangguangyan", "pgy"],
    "前列腺炎": ["qianliexian", "qlx"],
    "月经不调": ["yuejingbutiao", "yjbt"],
    "痛经": ["tongjing", "tong jing"],
    "阴道炎": ["yindaoyan", "ydy"],
    "打鼾": ["dahan", "da han"],
    "淋巴结肿大": ["linbajiezhongda", "lbjzd"],
    "肺结核": ["feijiehe", "fjh"],
}

# ---------------------------------------------------------------- ② 英文 / 中英夹杂
ENGLISH_FORMS = {
    "咳嗽": ["cough", "coughing", "have a cough"],
    "头痛": ["headache", "head ache"],
    "头疼": ["headache"],
    "头晕": ["dizzy", "dizziness"],
    "发热": ["fever"],
    "乏力": ["fatigue", "tired all the time"],
    "腹泻": ["diarrhea", "loose stool"],
    "便秘": ["constipation"],
    "腹痛": ["stomachache", "bellyache", "abdominal pain"],
    "胃痛": ["stomach pain", "stomachache"],
    "恶心": ["nausea"],
    "呕吐": ["vomiting", "throwing up"],
    "痔疮": ["hemorrhoids", "piles"],
    "皮疹": ["rash", "skin rash"],
    "湿疹": ["eczema"],
    "痤疮": ["acne"],
    "瘙痒": ["itching", "itchy"],
    "过敏": ["allergy", "allergic"],
    "哮喘": ["asthma"],
    "高血压": ["hypertension", "high blood pressure"],
    "糖尿病": ["diabetes", "high blood sugar"],
    "甲亢": ["hyperthyroidism"],
    "冠心病": ["heart disease", "coronary"],
    "贫血": ["anemia"],
    "失眠": ["insomnia", "can not sleep", "trouble sleeping"],
    "焦虑": ["anxiety"],
    "抑郁": ["depression"],
    "牙疼": ["toothache", "tooth pain"],
    "牙龈": ["gum"],
    "近视": ["myopia", "nearsighted"],
    "白内障": ["cataract"],
    "青光眼": ["glaucoma"],
    "耳鸣": ["tinnitus"],
    "鼻塞": ["stuffy nose", "nasal congestion"],
    "咽痛": ["sore throat"],
    "骨折": ["fracture", "broken bone"],
    "扭伤": ["sprain"],
    "关节炎": ["arthritis"],
    "骨质疏松": ["osteoporosis"],
    "颈椎病": ["neck pain"],
    "肾结石": ["kidney stone"],
    "尿路感染": ["UTI", "urinary tract infection"],
    "前列腺炎": ["prostatitis"],
    "月经不调": ["irregular period", "period is not regular"],
    "阴道炎": ["vaginitis"],
    "不孕": ["infertility", "can not get pregnant"],
    "打鼾": ["snoring", "snore"],
    "结核病": ["tuberculosis", "TB"],
    "艾滋病": ["HIV"],
    "肥胖": ["obesity", "lose weight"],
    "黄褐斑": ["melasma"],
    "脱发": ["hair loss"],
}

# ---------------------------------------------------------------- ③ 错别字：同音 / 形近 / 输入法选错
CHAR_CONFUSE = {
    "痛": ["疼", "痌", "庝"], "疼": ["痛", "庝"], "咳": ["刻", "该", "孩"],
    "嗽": ["漱", "嗖", "喇"], "炎": ["焱", "严", "言"], "泻": ["泄", "写"],
    "痒": ["氧", "养", "洋"], "疹": ["诊", "珍"], "哮": ["孝", "笑"],
    "喘": ["揣", "端"], "溃": ["贵", "馈"], "疡": ["阳", "扬"],
    "龈": ["银", "根"], "龋": ["曲", "禹"], "癌": ["艾", "炎"],
    "癫": ["颠"], "痫": ["闲", "娴"], "疱": ["泡"], "黏": ["粘", "年"],
    "矫": ["娇", "桥"], "畸": ["奇"], "踝": ["裸", "棵"], "腕": ["碗", "宛"],
    "巩": ["功"], "膜": ["模", "莫"], "障": ["章", "帳"], "盲": ["忙", "芒"],
    "聋": ["龙"], "嘶": ["撕", "斯"], "鼾": ["憨", "酣"], "疝": ["山"],
    "痔": ["志", "治"], "疮": ["仓", "窗"], "湿": ["温", "显"], "痤": ["座"],
    "藓": ["鲜"], "斑": ["班"], "褐": ["喝"], "瘤": ["留", "榴"],
    "肿": ["种", "中"], "梗": ["更", "埂"], "栓": ["拴"], "麻": ["嘛"],
    "腰": ["要"], "颈": ["劲"], "椎": ["追", "锥"], "睾": ["高", "睪"],
    "前列腺": ["前烈腺"], "结石": ["结食"], "糖尿": ["糖鸟"], "血压": ["学压"],
}


def confuse_term(term: str, pick: int = 0) -> str:
    """把词条里第一个可替换的字换成常见的同音/形近错字。"""
    for i, ch in enumerate(term):
        for word, wrongs in CHAR_CONFUSE.items():
            if term.startswith(word, i):
                wrong = wrongs[pick % len(wrongs)]
                if wrong and wrong != term[i:i + len(word)]:
                    return term[:i] + wrong + term[i + len(word):]
    for i, ch in enumerate(term):
        if ch in CHAR_CONFUSE:
            wrongs = CHAR_CONFUSE[ch]
            wrong = wrongs[pick % len(wrongs)]
            return term[:i] + wrong + term[i + 1:]
    return ""

# ---------------------------------------------------------------- ④ 病程单位怎么写
# (口径名, 替换「三天」的字符串)；「三天」是规格默认病程，这里列 pacientes 可能写的所有样子
DURATION_FORMS = [
    ("DUR01-中文三天", "三天"), ("DUR02-中文加了一", "三天了"), ("DUR03-大约三天", "大概三天"),
    ("DUR04-已经有三天", "已经三天了"), ("DUR05-数字3天", "3天"), ("DUR06-数字空格", "3 天"),
    ("DUR07-数字两天半", "两天半"), ("DUR08-两三天", "两三天"), ("DUR09-三四天", "三四天"),
    ("DUR10-好几天", "好几天"), ("DUR11-不到一周", "不到一周"), ("DUR12-一周左右", "一周左右"),
    ("DUR13-半个多月", "半个多月"), ("DUR14-一个多月", "一个多月"), ("DUR15-三个月", "三个月"),
    ("DUR16-英文3days", "3 days"), ("DUR17-英文three", "three days"), ("DUR18-第3天", "第3天"),
    ("DUR19-三四天左右", "三四天左右"), ("DUR20-记不清多久", "记不清多久了"),
]

# ---------------------------------------------------------------- ⑤ 提问句式（真实挂号前的开场白）
ASK_FORMS = [
    ("ASK01-问挂什么科", "{core}，请问挂什么科"),
    ("ASK02-挂啥科", "我想问一下，{core}挂啥科啊"),
    ("ASK03-看哪个科", "医生您好，{core}应该看哪个科"),
    ("ASK04-大夫求助", "大夫，{core}，麻烦帮我看看挂哪个科"),
    ("ASK05-首诊不知道", "我第一次来，{core}，不知道该挂什么"),
    ("ASK06-在线问诊开场", "你好，在线问诊可以吗？{core}"),
    ("ASK07-挂号指令", "{core}，我要挂号"),
    ("ASK08-带描述的求助", "{core}，这种情况严重吗，看什么科"),
    ("ASK09-礼貌打扰", "不好意思打扰了，{core}，麻烦问下挂哪科"),
    ("ASK10-拖了很久", "{core}，拖了很久了，实在受不了了，挂啥科"),
    ("ASK11-截图式描述", "症状：{core}"),
    ("ASK12-省略主语", "{core}，挂那个科合适"),
]

# ---------------------------------------------------------------- ⑥ 家属代述 / 第三人称
# (口径名, 模板, 是否为儿童/需要走儿科)
PROXY_FORMS = [
    ("PXY01-我妈", "我妈{core}", False),
    ("PXY02-我爸", "我爸{core}", False),
    ("PXY03-我老公", "我老公{core}", False),
    ("PXY04-我老婆", "我老婆{core}", False),
    ("PXY05-我对象", "我对象{core}", False),
    ("PXY06-我朋友", "我朋友{core}，让我帮他问问挂什么科", False),
    ("PXY07-我家老人", "我家老人{core}", False),
    ("PXY08-我孩子", "我孩子{core}", True),
    ("PXY09-我儿子", "我儿子{core}", True),
    ("PXY10-我女儿", "我女儿{core}", True),
    ("PXY11-宝宝", "宝宝{core}", True),
    ("PXY12-给孩子挂号", "想给孩子挂号，{core}", True),
]

# ---------------------------------------------------------------- ⑦ 字形与全半角
TRAD_MAP = {
    "头": "頭", "痛": "痛", "疼": "疼", "咳": "咳", "嗽": "嗽", "泻": "瀉",
    "肤": "膚", "过": "過", "敏": "敏", "糖": "糖", "尿": "尿", "病": "病",
    "压": "壓", "白": "白", "内": "內", "障": "障", "青": "青", "光": "光",
    "眼": "眼", "骨": "骨", "折": "折", "烫": "燙", "伤": "傷", "肾": "腎",
    "结": "結", "石": "石", "前": "前", "列": "列", "腺": "腺", "炎": "炎",
    "月": "月", "经": "經", "不": "不", "调": "調", "阴": "陰", "道": "道",
    "失": "失", "眠": "眠", "焦": "焦", "虑": "慮", "抑": "抑", "郁": "鬱",
    "近": "近", "视": "視", "耳": "耳", "鸣": "鳴", "鼻": "鼻", "塞": "塞",
    "咽": "咽", "肿": "腫", "痒": "癢", "湿": "濕", "痤": "痤", "疮": "瘡",
    "贫": "貧", "血": "血", "哮": "哮", "喘": "喘", "腰": "腰", "颈": "頸",
    "椎": "椎", "关": "關", "节": "節", "质": "質", "疏": "疏", "松": "鬆",
    "烧": "燒", "肛": "肛", "裂": "裂", "便": "便", "秘": "秘", "腹": "腹",
    "呕": "嘔", "吐": "吐", "恶": "惡", "心": "心", "乏": "乏", "力": "力",
    "头": "頭", "晕": "暈", "眩": "眩", "皮": "皮", "疹": "疹", "荨": "蕁",
    "麻": "麻", "痔": "痔", "腭": "齶", "反": "反", "酸": "酸", "扭": "扭",
    "口": "口", "腔": "腔", "溃": "潰", "疡": "瘍", "牙": "牙", "龈": "齦",
    "龋": "齲", "齿": "齒", "鼾": "鼾", "淋": "淋", "巴": "巴", "结": "結",
}


def to_trad(term: str) -> str:
    """患者用繁体/港台输入法打出来的样子。"""
    out = "".join(TRAD_MAP.get(ch, ch) for ch in term)
    return out if out != term else ""


def stutter(term: str) -> str:
    """输入法连按：咳咳嗽 / 头头痛。"""
    return term[:1] + term if len(term) > 1 else ""


def drop_char(term: str) -> str:
    """漏打一个字：咳三天 → 咳。"""
    return term[:-1] if len(term) > 2 else ""


def emoji_tail(term: str) -> str:
    """手机输入法常见的表情与全角符号尾巴。"""
    return f"{term}😣"


# 这些说法患者天天打，但目录里查不到对应的挂词；
# 凡是能按规格判定唯一二级科的，这里手工给出期望，其余只记录现网行为。
MANUAL_GOLD = {
    "头疼": "神经内科",      # 规格：头痛/脑袋疼 → 神经内科
    "抑郁": "精神科",        # 规格：抑郁症 → 精神科
    "白内障": "白内障科",    # 规格：老年性白内障 → 白内障科
    "青光眼": "青光眼科",    # 规格：急性闭角型青光眼 → 青光眼科
    "结核病": "结核病科",    # 规格：肺结核 → 结核病科
    "不孕": "辅助生殖科",    # 规格：不孕症/人工授精 → 辅助生殖科
    "过敏": "过敏反应科",    # 规格：青霉素过敏/皮肤过敏 → 过敏反应科
    "便血": "肛肠外科",      # 规格：痔疮出血/肛裂 → 肛肠外科
    "乏力": "全科",         # 规格 N7：头晕乏力 → AMBIGUOUS_ONLY → 全科候选
    "打鼾": "耳鼻咽喉科",    # 规格 NB8：打呼噜 → 耳鼻咽喉科，不是睡眠
    "肾结石": "泌尿外科",    # 规格：尿路感染/膀胱炎 → 泌尿外科
    "瘙痒": "皮肤科",        # 同等看待：皮肤瘙痒 → 皮肤科
    "水肿": "肾内科",        # 同等看待：水肿 → 肾内科
}

# 拼音行会说的话（{x} = 拼音串）
PINYIN_CARRIERS = [
    ("PIN00-裸拼音", "{x}"),
    ("PIN01-带个数", "{x}，三天了"),
    ("PIN02-求挂号", "{x}，挂什么科啊"),
    ("PIN03-拼音加汉字", "{x}得厉害"),
]

# 英文/中英夹杂行会说的话（{x} = 英文词）
ENGLISH_CARRIERS = [
    ("ENG00-裸单词", "{x}"),
    ("ENG01-我加单词", "我有{x}"),
    ("ENG02-求挂号", "{x}三天了，挂什么科"),
    ("ENG03-中英叙述", "最近{x}挺严重的"),
    ("ENG04-不确定拼写", "好像是{x}吧，三天了"),
]


def fullwidth(term: str) -> str:
    return "".join(chr(ord(ch) + 0xFEE0) if 0x21 <= ord(ch) <= 0x7E else ch for ch in term)

