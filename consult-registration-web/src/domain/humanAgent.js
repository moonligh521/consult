import { departmentLabel } from '../constants/catalog'

/** 演示坐席接入语。不看病、不开药。 */
export const HUMAN_JOIN =
  '您好，我是人工客服小慧。小智已把本轮咨询转给我。我可以协助整理挂号信息，不能诊断、开药或代替医师诊查。请问还需要补充什么？'

export const HUMAN_SHORTCUTS = ['帮我挂号', '怎么就诊', '结束咨询']

function wait(ms) {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms)
  })
}

export function waitForHuman() {
  return wait(700)
}

/** 按患者这句话给一条演示回复。不编造诊断。 */
export function replyAsHuman(text, card) {
  const sentence = (text || '').trim()
  const dept = departmentLabel(card?.department)
  const hasDept = dept !== '—'

  if (/结束|再见|谢谢|不用了/.test(sentence)) {
    return '不客气。演示会话可以点左上角开始新的咨询。若症状加重，请立即就医或拨打 120。'
  }
  if (/挂号|预约|科室|门诊/.test(sentence)) {
    if (hasDept) {
      return `已记下您希望挂${dept}。演示坐席不会真实挂号，请到医院窗口或医院官方预约渠道办理。`
    }
    return '已记下您的挂号诉求。演示坐席不会真实挂号，请告诉我想挂的科室，再到医院窗口或官方渠道办理。'
  }
  if (/就诊|怎么去|怎么看|医院/.test(sentence)) {
    return '建议携带身份证和医保卡，按分诊科室到窗口或自助机取号。我这边不能代替医师面诊。'
  }
  if (/电话|手机|联系|回拨/.test(sentence)) {
    return '演示环境不会收集真实联系方式。正式上线后，坐席会在工作时间回拨。'
  }
  if (/痛|咳|热|不舒服|症状|难受/.test(sentence)) {
    return '您补充的情况已记录。我只协助分诊和挂号指引，不能判断病情。如症状加重，请立即就医或拨打 120。'
  }
  return '已收到。请告诉我想挂的科室、方便的就诊时间，或需要我帮您看刚才的分诊建议。紧急情况请拨打 120。'
}
