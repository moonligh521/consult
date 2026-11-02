package com.moonligh.consult.framework;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    @Test
    void prefersOtelTraceId() throws Exception {
        TraceIdFilter filter = new TraceIdFilter(() -> "otel-trace");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.HEADER, "legacy");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals("otel-trace", response.getHeader(TraceIdFilter.HEADER));
    }

    @Test
    void fallsBackToRequestHeader() throws Exception {
        TraceIdFilter filter = new TraceIdFilter(TraceIdSource.NONE);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.HEADER, "legacy");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals("legacy", response.getHeader(TraceIdFilter.HEADER));
    }
}
