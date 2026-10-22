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

export const DEPARTMENT_OPTIONS = [
  { label: '呼吸内科', value: 1 },
  { label: '神经内科', value: 2 },
  { label: '消化内科', value: 3 },
  { label: '全科医学科', value: 4 },
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
