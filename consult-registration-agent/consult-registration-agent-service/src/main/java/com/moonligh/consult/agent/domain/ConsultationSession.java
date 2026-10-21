package com.moonligh.consult.agent.domain;

import com.moonligh.consult.agent.api.enums.ConsultationStatus;
import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.api.res.ConsultationCardRes;

/**
 * 一轮咨询在内存里的状态。
 * 新开一轮时状态是「待描述」；性别、年龄、病程齐了才有卡片。
 */
public class ConsultationSession {

    /**
     * 本轮编号，后续请求都带它。
     */
    private final String id;
    /**
     * 当前走到哪一步。
     */
    private ConsultationStatus status = ConsultationStatus.NEED_DESCRIPTION;
    private Gender gender;
    private Integer age;
    /**
     * 病程，例如「半年」。
     */
    private String duration;
    /**
     * 多轮发言拼起来的病情描述。
     */
    private String description = "";
    /**
     * 信息齐了才有；急症或还在追问时为 null。
     */
    private ConsultationCardRes card;

    public ConsultationSession(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public ConsultationStatus getStatus() {
        return status;
    }

    public void setStatus(ConsultationStatus status) {
        this.status = status;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ConsultationCardRes getCard() {
        return card;
    }

    public void setCard(ConsultationCardRes card) {
        this.card = card;
    }
}
