package com.modeldoctor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.DiagnosticModule;
import com.modeldoctor.domain.DiagnosticStatus;
import com.modeldoctor.dto.CreateDiagnosticRunRequestDto;
import com.modeldoctor.dto.DatasetArtifactResponseDto;
import com.modeldoctor.dto.ModelArtifactResponseDto;
import com.modeldoctor.service.MlEngineClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class ArtifactControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MlEngineClient mlEngineClient;

    @Test
    public void testUploadValidModelArtifactSuccess() throws Exception {
        String validModelJson = "{\"version\": [1, 0, 0], \"learner\": {\"feature_names\": [\"f0\", \"f1\", \"is_foreign\"]}}";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "model_xgboost_v1.json",
                "application/json",
                validModelJson.getBytes()
        );

        MvcResult result = mockMvc.perform(multipart("/api/artifacts/models")
                        .file(file)
                        .param("framework", "xgboost")
                        .param("taskType", "binary_classification"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.originalFilename").value("model_xgboost_v1.json"))
                .andExpect(jsonPath("$.framework").value("xgboost"))
                .andExpect(jsonPath("$.modelFormat").value("json"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.sha256").exists())
                .andReturn();

        ModelArtifactResponseDto dto = objectMapper.readValue(result.getResponse().getContentAsString(), ModelArtifactResponseDto.class);
        assertNotNull(dto.getId());
        assertTrue(dto.getId().startsWith("mdl_"));
        assertEquals(3, dto.getFeatureNames().size());
        assertTrue(dto.getFeatureNames().contains("f0"));

        // Fetch model by ID
        mockMvc.perform(get("/api/artifacts/models/" + dto.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dto.getId()))
                .andExpect(jsonPath("$.originalFilename").value("model_xgboost_v1.json"));
    }

    @Test
    public void testUploadDuplicateModelReusesSha256() throws Exception {
        String modelBytes = "{\"model\": \"deterministic_same_bytes_123\"}";
        MockMultipartFile file1 = new MockMultipartFile("file", "first.json", "application/json", modelBytes.getBytes());
        MockMultipartFile file2 = new MockMultipartFile("file", "second.json", "application/json", modelBytes.getBytes());

        MvcResult res1 = mockMvc.perform(multipart("/api/artifacts/models").file(file1))
                .andExpect(status().isCreated())
                .andReturn();
        ModelArtifactResponseDto dto1 = objectMapper.readValue(res1.getResponse().getContentAsString(), ModelArtifactResponseDto.class);

        MvcResult res2 = mockMvc.perform(multipart("/api/artifacts/models").file(file2))
                .andExpect(status().isCreated())
                .andReturn();
        ModelArtifactResponseDto dto2 = objectMapper.readValue(res2.getResponse().getContentAsString(), ModelArtifactResponseDto.class);

        assertEquals(dto1.getSha256(), dto2.getSha256());
        assertEquals(dto1.getId(), dto2.getId());
    }

    @Test
    public void testUploadEmptyModelRejected() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.json", "application/json", new byte[0]);

        mockMvc.perform(multipart("/api/artifacts/models").file(emptyFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("empty")));
    }

    @Test
    public void testUploadUnsupportedModelExtensionRejected() throws Exception {
        MockMultipartFile badFile = new MockMultipartFile("file", "virus.exe", "application/octet-stream", "bad binary".getBytes());

        mockMvc.perform(multipart("/api/artifacts/models").file(badFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Unsupported model format")));
    }

    @Test
    public void testUploadValidDatasetArtifactSuccess() throws Exception {
        String csvContent = "user_id,amount,is_foreign,is_fraud\n101,45.50,1,0\n102,1200.00,0,1\n103,15.20,0,0\n";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fraud_eval.csv",
                "text/csv",
                csvContent.getBytes()
        );

        MvcResult result = mockMvc.perform(multipart("/api/artifacts/datasets")
                        .file(file)
                        .param("format", "csv"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.originalFilename").value("fraud_eval.csv"))
                .andExpect(jsonPath("$.datasetFormat").value("csv"))
                .andExpect(jsonPath("$.rowCount").value(3))
                .andExpect(jsonPath("$.columnCount").value(4))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.sha256").exists())
                .andReturn();

        DatasetArtifactResponseDto dto = objectMapper.readValue(result.getResponse().getContentAsString(), DatasetArtifactResponseDto.class);
        assertNotNull(dto.getId());
        assertTrue(dto.getId().startsWith("ds_"));
        assertEquals(List.of("user_id", "amount", "is_foreign", "is_fraud"), dto.getColumnNames());

        // Fetch dataset by ID
        mockMvc.perform(get("/api/artifacts/datasets/" + dto.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dto.getId()))
                .andExpect(jsonPath("$.rowCount").value(3));
    }

    @Test
    public void testUploadEmptyDatasetRejected() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);

        mockMvc.perform(multipart("/api/artifacts/datasets").file(emptyFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("empty")));
    }

    @Test
    public void testPathTraversalInFilenameSanitized() throws Exception {
        String validJson = "{\"feature_names\": [\"x1\", \"x2\"]}";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../../../../etc/passwd_model.json",
                "application/json",
                validJson.getBytes()
        );

        MvcResult result = mockMvc.perform(multipart("/api/artifacts/models").file(file))
                .andExpect(status().isCreated())
                .andReturn();

        ModelArtifactResponseDto dto = objectMapper.readValue(result.getResponse().getContentAsString(), ModelArtifactResponseDto.class);
        // Original filename is sanitized without path traversal tokens
        assertFalse(dto.getOriginalFilename().contains(".."));
        assertFalse(dto.getOriginalFilename().contains("/"));
        assertFalse(dto.getOriginalFilename().contains("\\"));
        assertEquals("passwd_model.json", dto.getOriginalFilename());
    }

    @Test
    public void testCreateDiagnosticRunWithArtifactIdsSuccess() throws Exception {
        // 1. Upload Model
        String modelJson = "{\"features\": [\"x1\", \"x2\"]}";
        MockMultipartFile modelFile = new MockMultipartFile("file", "tree.json", "application/json", modelJson.getBytes());
        MvcResult mRes = mockMvc.perform(multipart("/api/artifacts/models").file(modelFile)).andExpect(status().isCreated()).andReturn();
        ModelArtifactResponseDto modelDto = objectMapper.readValue(mRes.getResponse().getContentAsString(), ModelArtifactResponseDto.class);

        // 2. Upload Evaluation Dataset
        String evalCsv = "x1,x2,gender,is_fraud\n1,2,M,0\n3,4,F,1\n";
        MockMultipartFile evalFile = new MockMultipartFile("file", "eval.csv", "text/csv", evalCsv.getBytes());
        MvcResult dRes = mockMvc.perform(multipart("/api/artifacts/datasets").file(evalFile)).andExpect(status().isCreated()).andReturn();
        DatasetArtifactResponseDto evalDto = objectMapper.readValue(dRes.getResponse().getContentAsString(), DatasetArtifactResponseDto.class);

        // 3. Create Diagnostic Run with Artifact IDs
        CreateDiagnosticRunRequestDto runReq = CreateDiagnosticRunRequestDto.builder()
                .modelArtifactId(modelDto.getId())
                .evaluationDatasetArtifactId(evalDto.getId())
                .targetColumn("is_fraud")
                .protectedAttribute("gender")
                .modules(List.of(DiagnosticModule.DATA_QUALITY, DiagnosticModule.LEAKAGE, DiagnosticModule.BIAS))
                .build();

        mockMvc.perform(post("/api/diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(runReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.modelArtifactId").value(modelDto.getId()))
                .andExpect(jsonPath("$.evaluationDatasetArtifactId").value(evalDto.getId()))
                .andExpect(jsonPath("$.modelArtifact.originalFilename").value("tree.json"))
                .andExpect(jsonPath("$.evaluationDatasetArtifact.originalFilename").value("eval.csv"))
                .andExpect(jsonPath("$.executionMode").value("REAL"));
    }

    @Test
    public void testCreateDiagnosticRunWithMissingTargetInDatasetRejected() throws Exception {
        // 1. Upload Model
        String modelJson = "{\"features\": [\"col_a\", \"col_b\"]}";
        MockMultipartFile modelFile = new MockMultipartFile("file", "tree.json", "application/json", modelJson.getBytes());
        MvcResult mRes = mockMvc.perform(multipart("/api/artifacts/models").file(modelFile)).andExpect(status().isCreated()).andReturn();
        ModelArtifactResponseDto modelDto = objectMapper.readValue(mRes.getResponse().getContentAsString(), ModelArtifactResponseDto.class);

        // 2. Upload dataset without target column
        String csv = "col_a,col_b\n1,2\n3,4\n";
        MockMultipartFile evalFile = new MockMultipartFile("file", "notarget.csv", "text/csv", csv.getBytes());
        MvcResult dRes = mockMvc.perform(multipart("/api/artifacts/datasets").file(evalFile)).andExpect(status().isCreated()).andReturn();
        DatasetArtifactResponseDto evalDto = objectMapper.readValue(dRes.getResponse().getContentAsString(), DatasetArtifactResponseDto.class);

        CreateDiagnosticRunRequestDto runReq = CreateDiagnosticRunRequestDto.builder()
                .modelArtifactId(modelDto.getId())
                .evaluationDatasetArtifactId(evalDto.getId())
                .targetColumn("non_existent_target")
                .modules(List.of(DiagnosticModule.LEAKAGE))
                .build();

        mockMvc.perform(post("/api/diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(runReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Target column 'non_existent_target' not found in evaluation dataset")));
    }

    @Test
    public void testCreateDiagnosticRunWithMissingProtectedAttributeRejected() throws Exception {
        // 1. Upload Model
        String modelJson = "{\"features\": [\"col_a\", \"is_fraud\"]}";
        MockMultipartFile modelFile = new MockMultipartFile("file", "tree.json", "application/json", modelJson.getBytes());
        MvcResult mRes = mockMvc.perform(multipart("/api/artifacts/models").file(modelFile)).andExpect(status().isCreated()).andReturn();
        ModelArtifactResponseDto modelDto = objectMapper.readValue(mRes.getResponse().getContentAsString(), ModelArtifactResponseDto.class);

        // 2. Upload dataset without protected attribute
        String csv = "col_a,is_fraud\n1,0\n3,1\n";
        MockMultipartFile evalFile = new MockMultipartFile("file", "nobias.csv", "text/csv", csv.getBytes());
        MvcResult dRes = mockMvc.perform(multipart("/api/artifacts/datasets").file(evalFile)).andExpect(status().isCreated()).andReturn();
        DatasetArtifactResponseDto evalDto = objectMapper.readValue(dRes.getResponse().getContentAsString(), DatasetArtifactResponseDto.class);

        CreateDiagnosticRunRequestDto runReq = CreateDiagnosticRunRequestDto.builder()
                .modelArtifactId(modelDto.getId())
                .evaluationDatasetArtifactId(evalDto.getId())
                .targetColumn("is_fraud")
                .protectedAttribute("gender")
                .modules(List.of(DiagnosticModule.BIAS))
                .build();

        mockMvc.perform(post("/api/diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(runReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Protected attribute 'gender' not found in evaluation dataset")));
    }

    @Test
    public void testCreateDiagnosticRunWithNonExistentArtifactIdRejected() throws Exception {
        CreateDiagnosticRunRequestDto runReq = CreateDiagnosticRunRequestDto.builder()
                .modelArtifactId("mdl_non_existent_999")
                .evaluationDataset("some_file.csv")
                .targetColumn("is_fraud")
                .modules(List.of(DiagnosticModule.DATA_QUALITY))
                .build();

        mockMvc.perform(post("/api/diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(runReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsStringIgnoringCase("Model artifact not found with ID: mdl_non_existent_999")));
    }
}
