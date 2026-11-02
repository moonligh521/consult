package com.moonligh.consult.agent.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.moonligh.consult.agent.infrastructure.judge.HttpJudgeModelClient;
import com.moonligh.consult.agent.infrastructure.judge.HttpLangfuseTurnClient;
import com.moonligh.consult.agent.infrastructure.judge.JudgeModelPort;
import com.moonligh.consult.agent.infrastructure.judge.LangfuseTurnPort;
import com.moonligh.consult.agent.service.TurnJudgeService;
import com.moonligh.consult.framework.model.ModelProperties;
import com.moonligh.consult.framework.observability.ObservabilityProperties;

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "consult.observability.judge", name = "enabled", havingValue = "true")
public class TurnJudgeConfiguration {

    @Bean
    public LangfuseTurnPort langfuseTurnPort(ObservabilityProperties observability) {
        return new HttpLangfuseTurnClient(observability);
    }

    @Bean
    public JudgeModelPort judgeModelPort(ModelProperties model) {
        return new HttpJudgeModelClient(model);
    }

    @Bean
    public TurnJudgeService turnJudgeService(
            ObservabilityProperties observability,
            ModelProperties model,
            LangfuseTurnPort langfuse,
            JudgeModelPort judgeModel) {
        return new TurnJudgeService(observability, model, langfuse, judgeModel);
    }
}
