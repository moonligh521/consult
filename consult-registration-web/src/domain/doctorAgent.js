import { consultDepartmentLabel } from '../constants/catalog'

const DOCTORS = {
  1: { name: '周宁', title: '主治医师', hospital: '演示医院', dept: '内科' },
  2: { name: '陈岚', title: '主治医师', hospital: '演示医院', dept: '外科' },
  3: { name: '赵衡', title: '主治医师', hospital: '演示医院', dept: '男科' },
  4: { name: '王启明', title: '主治医师', hospital: '演示医院', dept: '全科' },
  5: { name: '刘岩', title: '主治医师', hospital: '演示医院', dept: '骨科' },
  6: { name: '苏敏', title: '主治医师', hospital: '演示医院', dept: '妇产科' },
  7: { name: '何川', title: '主治医师', hospital: '演示医院', dept: '口腔科' },
  8: { name: '林晓', title: '主治医师', hospital: '演示医院', dept: '儿科' },
  9: { name: '吴桐', title: '主治医师', hospital: '演示医院', dept: '耳鼻咽喉科' },
  10: { name: '郑白', title: '主治医师', hospital: '演示医院', dept: '中医科' },
  11: { name: '韩霜', title: '主治医师', hospital: '演示医院', dept: '皮肤科' },
  12: { name: '方毅', title: '主治医师', hospital: '演示医院', dept: '感染病科' },
  13: { name: '沈清', title: '主治医师', hospital: '演示医院', dept: '眼科' },
  14: { name: '曹宁', title: '主治医师', hospital: '演示医院', dept: '精神科' },
  15: { name: '姚岚', title: '主治医师', hospital: '演示医院', dept: '整形外科' },
  16: { name: '顾青', title: '主治医师', hospital: '演示医院', dept: '营养科' },
  17: { name: '蒋远', title: '主治医师', hospital: '演示医院', dept: '肿瘤综合科' },
  18: { name: '潘夏', title: '主治医师', hospital: '演示医院', dept: '辅助生殖科' },
  19: { name: '丁和', title: '主治医师', hospital: '演示医院', dept: '疼痛科' },
  20: { name: '魏安', title: '主治医师', hospital: '演示医院', dept: '睡眠医学科' },
  21: { name: '钱合', title: '主治医师', hospital: '演示医院', dept: '中西医结合科' },
  22: { name: '冯康', title: '主治医师', hospital: '演示医院', dept: '康复科' },
}

function wait(ms) {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms)
  })
}

export function waitMs(ms) {
  return wait(ms)
}

/** 按分诊科室派一位演示医生。不接真实号源。 */
export function assignDoctor(card) {
  const fallback = consultDepartmentLabel(card)
  const doctor = DOCTORS[card?.department] || DOCTORS[4]
  return {
    ...doctor,
    dept: fallback === '—' ? doctor.dept : fallback,
  }
}

export function waitForDoctorAssign() {
  return wait(1800)
}

export function waitForDoctorServe() {
  return wait(900)
}

export function waitForDoctor() {
  return wait(700)
}

/** 支付后、医生接诊前的助手说明。 */
export const MATCH_ASSIST =
  '您好，我是AI医生助手小智，已收到您的问诊资料，正在为您匹配合适的医生，预计 1 分钟内接诊。'

/** 接诊打招呼。只说明已看到资料，不给诊断。 */
export function greetAsDoctor(doctor) {
  return `您好，我是${doctor.dept}${doctor.name}医生，非常高兴由我为您提供咨询服务。请稍等，我正在阅读您的病情描述。`
}

export function firstAskAsDoctor() {
  return '是您本人咨询吗？'
}

/** 按患者这句话给一条演示追问。不编造诊断或用药。 */
export function replyAsDoctor(text, card, doctor) {
  const sentence = (text || '').trim()
  const dept = doctor?.dept || consultDepartmentLabel(card)

  if (/结束|再见|谢谢|不用了|没有了/.test(sentence)) {
    return '问诊可以先到这里。如症状加重、出现胸痛气促或意识改变，请立即就医或拨打 120。祝早日康复。'
  }
  if (/本人|是的|对$/.test(sentence)) {
    return '您既往有无其他疾病或用药过敏史？'
  }
  if (/药|开药|处方|吃什么/.test(sentence)) {
    return '线上演示问诊不能开药或给出具体用药方案。用药须由执业医师面诊或互联网医院复诊后开具。'
  }
  if (/检查|化验|报告|片子|CT|核磁/.test(sentence)) {
    return '请用文字补充检查时间和主要结论。演示环境暂不能看原件。'
  }
  if (/过敏|病史|慢性|高血压|糖尿病/.test(sentence)) {
    return '做过检查，拍过片子吗？'
  }
  if (/痛|咳|热|吐|泻|晕|不舒服|症状|难受|疫苗/.test(sentence)) {
    return '请再说一下：从什么时候开始、有没有发热，以及是否影响睡眠或进食。我只会基于您提供的信息追问，不下诊断。'
  }
  return `已收到。请尽量按时间顺序补充起病、变化和已做过的处理。${dept === '—' ? '' : `本次按${dept}接诊。`}不能替代到院诊查。`
}
