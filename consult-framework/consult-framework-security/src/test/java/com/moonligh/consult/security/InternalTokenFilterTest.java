package com.moonligh.consult.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.framework.Result;

class InternalTokenFilterTest {

    @Test
    void rejectsMissingToken() throws Exception {
        mockMvc().perform(get("/internal/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.LOGIN_REQUIRED.getCode()));
    }

    @Test
    void acceptsMatchingToken() throws Exception {
        mockMvc().perform(get("/internal/ping").header(AuthTokens.INTERNAL_HEADER, "secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("ok"));
    }

    private static MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new PingController())
                .addFilters(new InternalTokenFilter("secret"))
                .build();
    }

    @RestController
    @RequestMapping("/internal")
    static class PingController {
        @GetMapping("/ping")
        Result<String> ping() {
            return Result.ok("ok");
        }
    }
}
