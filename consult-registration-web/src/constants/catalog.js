/** 对外状态。未知码按安全停止处理：只展示 prompt，不展示卡片。 */
export const STATUS = {
  NEED_DESCRIPTION: 1,
  NEED_SLOT: 2,
  URGENT: 3,
  CARD_READY: 4,
  CONFIRMED: 5,
  NEED_HUMAN: 6,
  SAFE_FALLBACK: 7,
  UNSUITABLE_FOR_ONLINE: 8,
}

export const STATUS_LABEL = {
  1: '待描述',
  2: '待补槽',
  3: '急症分流',
  4: '卡片待确认',
  5: '已确认',
  6: '人工客服已接入',
  7: '安全分流暂不可用',
  8: '当前渠道不适用',
}

export const GENDER_OPTIONS = [
  { label: '男', value: 1 },
  { label: '女', value: 2 },
]

/**
 * 当前账号绑定的就诊人。演示数据，以后换成登录账号的档案接口。
 * 没有绑定就诊人时返回空数组，这一步整段不展示。
 * gender：1 男 2 女。
 */
export const BOUND_PATIENTS = [
  { id: 'p-self', name: '张明', gender: 1, age: 32, relation: '本人', isDefault: true },
  { id: 'p-spouse', name: '李芳', gender: 2, age: 28, relation: '配偶', isDefault: false },
  { id: 'p-child', name: '张小童', gender: 1, age: 8, relation: '子女', isDefault: false },
]

/** 问诊通道。closed 与后端 inquiry.closed 对齐，演示默认开放。 */
export const INQUIRY = {
  closed: false,
  matchTimeoutMs: 20000,
}

/** 当前账号下的就诊人。没有档案就空列表。 */
export function patientsOfAccount() {
  return BOUND_PATIENTS
}

/** 选择器默认落在成年，不从 0 起。列表仍含 1～120 岁。 */
export const DEFAULT_AGE = 18

export const AGE_OPTIONS = Array.from({ length: 120 }, (_, index) => {
  const age = index + 1
  return { label: `${age}岁`, value: age }
})

export const MISSING_SLOT = {
  GENDER: 1,
  AGE: 2,
  DURATION: 3,
}

/** 病情描述太含糊时给出的示例，点选即发送。 */
export const DESCRIPTION_EXAMPLES = [
  '咳嗽3天，伴有黄痰和头痛，无发热',
  '干咳半年，运动后加重',
  '头痛两天，时轻时重',
]

export const DEPARTMENT_OPTIONS = [
  { label: '内科', value: 1 },
  { label: '外科', value: 2 },
  { label: '男科', value: 3 },
  { label: '全科', value: 4 },
  { label: '骨科', value: 5 },
  { label: '妇产科', value: 6 },
  { label: '口腔科', value: 7 },
  { label: '儿科', value: 8 },
  { label: '耳鼻喉科', value: 9 },
  { label: '中医科', value: 10 },
]

export const SERVICE_OPTIONS = [
  { label: '未指定（预约前整理）', value: 0 },
  { label: '线下门诊挂号', value: 1 },
  { label: '互联网复诊', value: 2 },
  { label: '远程会诊', value: 3 },
]

export function money(value) {
  const text = Number(value).toFixed(1)
  return text.endsWith('.0') ? text.slice(0, -2) : text
}

export const HANDOFF_LABEL = {
  1: '预约前信息整理',
  2: '人工/医师预审',
}

export function isSafetyStop(status) {
  return status === STATUS.URGENT
    || status === STATUS.SAFE_FALLBACK
    || status === STATUS.UNSUITABLE_FOR_ONLINE
    || !Object.values(STATUS).includes(status)
}

export function canShowCard(status, card) {
  return !!card && (status === STATUS.CARD_READY || status === STATUS.CONFIRMED)
}

export function genderLabel(code) {
  return GENDER_OPTIONS.find((item) => item.value === code)?.label || '—'
}

export function departmentLabel(code) {
  return DEPARTMENT_OPTIONS.find((item) => item.value === code)?.label || '—'
}
