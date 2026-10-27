package com.moonligh.consult.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.moonligh.consult.agent.domain.department.DepartmentCatalog;
import com.moonligh.consult.agent.domain.triage.ComplaintModel;
import com.moonligh.consult.agent.infrastructure.model.DeepSeekComplaintModel;
import com.moonligh.consult.framework.model.ModelGateway;

/**
 * 主诉模型装配。HTTP 网关在 consult-framework-model。
 */
@Configuration
public class ModelConfiguration {

    @Bean
    public ComplaintModel complaintModel(ModelGateway gateway, DepartmentCatalog catalog) {
        return new DeepSeekComplaintModel(gateway, catalog);
    }
}
