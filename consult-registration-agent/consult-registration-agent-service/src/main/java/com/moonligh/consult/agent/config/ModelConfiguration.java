package com.moonligh.consult.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.moonligh.consult.agent.domain.DepartmentRouter;
import com.moonligh.consult.agent.domain.department.DepartmentCatalog;
import com.moonligh.consult.agent.domain.department.InformalLexicon;
import com.moonligh.consult.agent.domain.triage.ComplaintModel;
import com.moonligh.consult.agent.infrastructure.model.CascadeComplaintModel;
import com.moonligh.consult.agent.infrastructure.model.DeepSeekComplaintModel;
import com.moonligh.consult.agent.infrastructure.model.LexiconComplaintModel;
import com.moonligh.consult.framework.model.ModelGateway;

/**
 * 主诉模型装配。HTTP 网关在 consult-framework-model。
 */
@Configuration
public class ModelConfiguration {

    @Bean
    public InformalLexicon informalLexicon() {
        return InformalLexicon.load();
    }

    @Bean
    public ComplaintModel complaintModel(
            ModelGateway gateway,
            DepartmentCatalog catalog,
            DepartmentRouter router,
            InformalLexicon lexicon) {
        ComplaintModel local = new LexiconComplaintModel(catalog, router, lexicon);
        ComplaintModel llm = new DeepSeekComplaintModel(gateway, catalog);
        return new CascadeComplaintModel(local, llm);
    }
}
