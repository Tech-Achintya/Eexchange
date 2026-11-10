package com.eexchange.backend.Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceAppNameTest {

    private RestTemplate restTemplate;
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        emailService = new EmailService(
                null,
                mock(Environment.class),
                "api-key",
                "sender@test.dev",
                "Sender",
                600,
                900,
                "CampusMart");

        // The service builds its own RestTemplate; swap in the mock.
        ReflectionTestUtils.setField(emailService, "restTemplate", restTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void subjectsUseTheConfiguredAppName() {
        emailService.sendOtpEmail("student@college.edu", "123456", "signup", null);

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor =
                ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), captor.capture(), eq(String.class));

        assertThat(captor.getValue().getBody().get("subject").toString())
                .contains("CampusMart");
    }
}
