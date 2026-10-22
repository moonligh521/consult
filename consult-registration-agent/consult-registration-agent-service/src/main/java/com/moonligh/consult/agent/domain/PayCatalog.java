package com.moonligh.consult.agent.domain;

import java.math.BigDecimal;
import java.util.List;

import com.moonligh.consult.agent.api.enums.PayPackage;
import com.moonligh.consult.agent.api.res.PayPackageRes;

/**
 * 演示档位目录。价格写死在代码里，不接真实支付渠道。
 */
public final class PayCatalog {

    private static final List<PayPackageRes> PACKAGES = List.of(
            new PayPackageRes(
                    PayPackage.NORMAL.getCode(),
                    PayPackage.NORMAL.getLabel(),
                    "",
                    "随机指派医生",
                    new BigDecimal("3.9"),
                    new BigDecimal("3.0"),
                    new BigDecimal("0.9"),
                    List.of("公立医院医生提供服务，不指定医生职称", "15分钟不限轮次"),
                    "支付后立即发起咨询；时长到达上限时，服务结束。",
                    "日间服务时段为 8:00-23:00，夜间服务时段为 23:00-次日 8:00。",
                    "医生接诊前，可随时取消，自动退款；医生接诊后 15 分钟内不回复，反馈平台，退款保障。"),
            new PayPackageRes(
                    PayPackage.FAST.getCode(),
                    PayPackage.FAST.getLabel(),
                    "",
                    "秒级响应 标准服务",
                    new BigDecimal("6.9"),
                    new BigDecimal("3.9"),
                    new BigDecimal("3.0"),
                    List.of("平台医生提供服务，秒级接诊（演示）", "15分钟不限轮次", "服务时间：08:00-11:00 / 12:00-17:00 / 18:00-23:45"),
                    "支付后立即发起咨询；医生和患者每相互回复一次记为一轮，轮次或时长达到上限时，服务结束。",
                    "",
                    "医生首次回复前，可随时取消，自动退款；医生接诊后 15 分钟内不回复，反馈平台，退款保障。"),
            new PayPackageRes(
                    PayPackage.TERTIARY.getCode(),
                    PayPackage.TERTIARY.getLabel(),
                    "99%+好评",
                    "公立三甲医生 平均1分钟响应",
                    new BigDecimal("12.9"),
                    new BigDecimal("6.9"),
                    new BigDecimal("6.0"),
                    List.of("公立三甲医院医生提供服务", "1小时20轮沟通", "服务时间：00:00 - 24:00"),
                    "支付后立即发起咨询；医生和患者每相互回复一次记为一轮，轮次或时长达到上限时，服务结束。",
                    "",
                    "医生首次回复前，可随时取消，自动退款；医生接诊后 20 分钟内不回复，反馈平台，退款保障。"),
            new PayPackageRes(
                    PayPackage.EXPERT.getCode(),
                    PayPackage.EXPERT.getLabel(),
                    "资深",
                    "三甲副主任以上 10年+执业经验",
                    new BigDecimal("29.9"),
                    new BigDecimal("23.9"),
                    new BigDecimal("6.0"),
                    List.of("公立三甲医院10年以上副主任及以上主任医师接诊", "1小时20轮沟通", "服务时间：00:00 - 24:00"),
                    "支付后立即发起咨询；医生和患者每相互回复一次记为一轮，轮次或时长达到上限时，服务结束。",
                    "",
                    "医生首次回复前，可随时取消，自动退款；医生接诊后 20 分钟内不回复，反馈平台，退款保障。"));

    private PayCatalog() {
    }

    /** 下单页四档。 */
    public static List<PayPackageRes> list() {
        return PACKAGES;
    }

    /** 按数字找档位。对不上返回 null，由调用方报 1116。 */
    public static PayPackageRes find(int code) {
        for (PayPackageRes item : PACKAGES) {
            if (item.code() == code) {
                return item;
            }
        }
        return null;
    }
}
