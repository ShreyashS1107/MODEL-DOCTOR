package com.modeldoctor;

import com.modeldoctor.dto.HealthResponseDto;
import com.modeldoctor.service.HealthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HealthService healthService;

    @Test
    public void shouldReturnHealthStatusUp() throws Exception {
        when(healthService.getSystemHealth()).thenReturn(HealthResponseDto.builder()
                .status("UP")
                .service("model-doctor-backend")
                .version("0.1.0-SNAPSHOT")
                .timestamp(Instant.now())
                .components(Map.of("database", "UP (runs: 0)", "mlEngine", "UP", "apiGateway", "UP", "storageBridge", "READY"))
                .build());

        mockMvc.perform(get("/api/health")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("model-doctor-backend"));
    }
}

