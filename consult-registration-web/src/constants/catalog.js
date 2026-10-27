import { DEPARTMENT_TREE } from './departments'

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

export const RELATION_OPTIONS = [
  { label: '本人', value: '本人' },
  { label: '配偶', value: '配偶' },
  { label: '子女', value: '子女' },
  { label: '父母', value: '父母' },
  { label: '其他', value: '其他' },
]

/** 姓名脱敏：张明 → *明。没有姓名时显示匿名。 */
export function maskPatientName(name) {
  const value = (name || '').trim()
  if (!value) {
    return '匿名'
  }
  if (value.length === 1) {
    return '*'
  }
  return `*${value.slice(1)}`
}

/** 问诊通道。closed 与后端 inquiry.closed 对齐，演示默认开放。 */
export const INQUIRY = {
  closed: false,
  matchTimeoutMs: 20000,
}

/** 问诊时长。默认 15 分钟，用户可改。 */
export const CONSULT_MINUTES = {
  default: 15,
  storageKey: 'consult-minutes',
  options: [
    { label: '5 分钟', value: 5 },
    { label: '10 分钟', value: 10 },
    { label: '15 分钟', value: 15 },
    { label: '20 分钟', value: 20 },
    { label: '30 分钟', value: 30 },
  ],
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

/** 补病程快捷选项。点选即发送，须能被后端 SlotParser 识别。 */
export const DURATION_OPTIONS = [
  '半天',
  '1天',
  '2天',
  '3天',
  '一周',
  '半个月',
  '一个月',
  '三个月',
  '半年',
  '一年',
]

/** 病情描述太含糊时给出的示例，点选即发送。 */
export const DESCRIPTION_EXAMPLES = [
  '咳嗽3天，伴有黄痰和头痛，无发热',
  '干咳半年，运动后加重',
  '头痛两天，时轻时重',
]

export const DEPARTMENT_OPTIONS = DEPARTMENT_TREE.map((item) => ({
  label: item.label,
  value: item.code,
}))

const SPECIALTY_PARENT = new Map()
const DEFAULT_SPECIALTY = new Map()
for (const item of DEPARTMENT_TREE) {
  const sameName = item.specialties.find((spec) => spec.name === item.label)
  DEFAULT_SPECIALTY.set(item.code, sameName?.name || item.specialties[0]?.name || '全科')
  for (const spec of item.specialties) {
    if (!SPECIALTY_PARENT.has(spec.name)) {
      SPECIALTY_PARENT.set(spec.name, item.code)
    }
  }
}

/** 修改咨询科室：一级只作分组，选项和取值都是二级挂号科。 */
export const SPECIALTY_OPTIONS = DEPARTMENT_TREE.map((item) => ({
  type: 'group',
  label: item.label,
  key: `l1-${item.code}`,
  children: item.specialties.map((spec) => ({
    label: spec.name,
    value: spec.name,
    key: spec.name,
  })),
}))

/** 级联回显：内科 / 呼吸内科。只能选叶子二级科。 */
export const SPECIALTY_CASCADE = DEPARTMENT_TREE.map((item) => ({
  label: item.label,
  value: `l1-${item.code}`,
  children: item.specialties.map((spec) => ({
    label: spec.name,
    value: spec.name,
  })),
}))

export function money(value) {
  const text = Number(value).toFixed(1)
  return text.endsWith('.0') ? text.slice(0, -2) : text
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

export function knownSpecialty(name) {
  return name && SPECIALTY_PARENT.has(name) ? name : null
}

export function defaultSpecialtyOf(code) {
  return DEFAULT_SPECIALTY.get(code) || '全科'
}

/** 卡片上的咨询科室：只回显目录里的二级科。缺二级时落到该一级类的默认二级。 */
export function resolveConsultSpecialty(card) {
  return knownSpecialty(card?.specialty) || defaultSpecialtyOf(card?.department)
}

export function consultDepartmentLabel(card) {
  if (!card?.specialty && card?.department == null) {
    return '—'
  }
  return resolveConsultSpecialty(card)
}

export function parentDepartmentCode(specialty) {
  return SPECIALTY_PARENT.get(specialty) ?? null
}
