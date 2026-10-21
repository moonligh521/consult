package com.moonligh.consult.agent;

import com.moonligh.consult.agent.api.enums.ConsultationStatus;
import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.api.res.ConsultationCardRes;

class ConsultationSession {

    private final String id;
    private ConsultationStatus status = ConsultationStatus.NEED_DESCRIPTION;
    private Gender gender;
    private Integer age;
    private String duration;
    private String description = "";
    private ConsultationCardRes card;

    ConsultationSession(String id) {
        this.id = id;
    }

    String getId() {
        return id;
    }

    ConsultationStatus getStatus() {
        return status;
    }

    void setStatus(ConsultationStatus status) {
        this.status = status;
    }

    Gender getGender() {
        return gender;
    }

    void setGender(Gender gender) {
        this.gender = gender;
    }

    Integer getAge() {
        return age;
    }

    void setAge(Integer age) {
        this.age = age;
    }

    String getDuration() {
        return duration;
    }

    void setDuration(String duration) {
        this.duration = duration;
    }

    String getDescription() {
        return description;
    }

    void setDescription(String description) {
        this.description = description;
    }

    ConsultationCardRes getCard() {
        return card;
    }

    void setCard(ConsultationCardRes card) {
        this.card = card;
    }
}
