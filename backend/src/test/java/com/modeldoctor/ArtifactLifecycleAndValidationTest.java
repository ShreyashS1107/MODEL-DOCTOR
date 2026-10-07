package com.modeldoctor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.DiagnosticModule;
import com.modeldoctor.domain.DiagnosticStatus;
import com.modeldoctor.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public class ArtifactLifecycleAndValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testArtifactListingAndSoftDelete() throws Exception {
        // 1. Upload Model
        String modelJson = "{\"feature_names\": [\"feat_a\", \"feat_b\"]}";
        MockMultipartFile modelFile = new MockMultipartFile("file", "listing_model.json", "application/json", modelJson.getBytes());
        MvcResult mRes = mockMvc.perform(multipart("/api/artifacts/models")
                        .file(modelFile)
                        .param("framework", "xgboost")
                        .param("taskType", "binary_classification"))
                .andExpect(status().isCreated())
                .andReturn();
        ModelArtifactResponseDto model = objectMapper.readValue(mRes.getResponse().getContentAsString(), ModelArtifactResponseDto.class);

        // 2. Upload Dataset
        String csv = "feat_a,feat_b,target\n1,10,0\n2,20,1\n3,30,0\n4,40,1\n";
        MockMultipartFile datasetFile = new MockMultipartFile("file", "listing_data.csv", "text/csv", csv.getBytes());
        MvcResult dRes = mockMvc.perform(multipart("/api/artifacts/datasets").file(datasetFile))
                .andExpect(status().isCreated())
                .andReturn();
        DatasetArtifactResponseDto dataset = objectMapper.readValue(dRes.getResponse().getContentAsString(), DatasetArtifactResponseDto.class);

        // 3. List Models -> Must include uploaded model
        mockMvc.perform(get("/api/artifacts/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(model.getId())))
                .andExpect(jsonPath("$[?(@.id == '" + model.getId() + "')].originalFilename", hasItem("listing_model.json")))
                .andExpect(jsonPath("$[?(@.id == '" + model.getId() + "')].featureCount", hasItem(2)));

        // 4. List Datasets -> Must include uploaded dataset with schema summary
        mockMvc.perform(get("/api/artifacts/datasets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(dataset.getId())))
                .andExpect(jsonPath("$[?(@.id == '" + dataset.getId() + "')].rowCount", hasItem(4)))
                .andExpect(jsonPath("$[?(@.id == '" + dataset.getId() + "')].columnCount", hasItem(3)))
                .andExpect(jsonPath("$[?(@.id == '" + dataset.getId() + "')].schemaSummary.columns", notNullValue()));

        // 5. Soft Delete Model
        mockMvc.perform(delete("/api/artifacts/models/" + model.getId()))
                .andExpect(status().isNoContent());

        // 6. Soft Delete Dataset
        mockMvc.perform(delete("/api/artifacts/datasets/" + dataset.getId()))
                .andExpect(status().isNoContent());

        // 7. Verify Deleted Artifacts Are Excluded from Active Listing
        mockMvc.perform(get("/api/artifacts/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(model.getId()))));

        mockMvc.perform(get("/api/artifacts/datasets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(dataset.getId()))));

        // 8. Verify Direct Lookup Still Works for Provenance (with isDeleted = true)
        mockMvc.perform(get("/api/artifacts/models/" + model.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(model.getId()))
                .andExpect(jsonPath("$.isDeleted").value(true));

        mockMvc.perform(get("/api/artifacts/datasets/" + dataset.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dataset.getId()))
                .andExpect(jsonPath("$.isDeleted").value(true));
    }

    @Test
    public void testRichDatasetSchemaIntelligence() throws Exception {
        // Build CSV with numeric, boolean, constant, outlier, null, and identifier columns
        String csv = "id_col,amount,is_active,const_col,target\n" +
                "ID_001,100.5,true,STATIC_VAL,0\n" +
                "ID_002,200.0,false,STATIC_VAL,1\n" +
                "ID_003,,true,STATIC_VAL,0\n" +
                "ID_004,5000.0,false,STATIC_VAL,1\n";

        MockMultipartFile datasetFile = new MockMultipartFile("file", "rich_schema.csv", "text/csv", csv.getBytes());
        MvcResult dRes = mockMvc.perform(multipart("/api/artifacts/datasets").file(datasetFile))
                .andExpect(status().isCreated())
                .andReturn();

        DatasetArtifactResponseDto dataset = objectMapper.readValue(dRes.getResponse().getContentAsString(), DatasetArtifactResponseDto.class);
        assertNotNull(dataset.getSchemaSummary());
        assertEquals(4L, dataset.getRowCount());
        assertEquals(5, dataset.getColumnCount());

        List<ColumnProfileDto> columns = dataset.getSchemaSummary().getColumns();
        assertEquals(5, columns.size());

        // id_col -> identifier-like
        ColumnProfileDto idCol = columns.stream().filter(c -> c.getName().equals("id_col")).findFirst().orElseThrow();
        assertTrue(idCol.getIsIdentifierLike());

        // amount -> numeric with min, max, nullCount = 1
        ColumnProfileDto amountCol = columns.stream().filter(c -> c.getName().equals("amount")).findFirst().orElseThrow();
        assertEquals("numeric", amountCol.getClassification());
        assertEquals(1L, amountCol.getNullCount());
        assertEquals(25.0, amountCol.getNullPercentage());
        assertEquals(100.5, amountCol.getMin());
        assertEquals(5000.0, amountCol.getMax());

        // const_col -> constant
        ColumnProfileDto constCol = columns.stream().filter(c -> c.getName().equals("const_col")).findFirst().orElseThrow();
        assertTrue(constCol.getIsConstant());

        // target -> binary uniqueCount = 2
        ColumnProfileDto targetCol = columns.stream().filter(c -> c.getName().equals("target")).findFirst().orElseThrow();
        assertEquals(2L, targetCol.getUniqueCount());
    }

    @Test
    public void testPreflightValidationMissingModelFeature() throws Exception {
        // Model requiring feat_1 and feat_2
        String modelJson = "{\"feature_names\": [\"feat_1\", \"feat_2\"]}";
        MockMultipartFile modelFile = new MockMultipartFile("file", "missing_feat_model.json", "application/json", modelJson.getBytes());
        MvcResult mRes = mockMvc.perform(multipart("/api/artifacts/models").file(modelFile)).andExpect(status().isCreated()).andReturn();
        ModelArtifactResponseDto model = objectMapper.readValue(mRes.getResponse().getContentAsString(), ModelArtifactResponseDto.class);

        // Dataset with only feat_1 and target
        String csv = "feat_1,target\n10,0\n20,1\n";
        MockMultipartFile datasetFile = new MockMultipartFile("file", "missing_feat_data.csv", "text/csv", csv.getBytes());
        MvcResult dRes = mockMvc.perform(multipart("/api/artifacts/datasets").file(datasetFile)).andExpect(status().isCreated()).andReturn();
        DatasetArtifactResponseDto dataset = objectMapper.readValue(dRes.getResponse().getContentAsString(), DatasetArtifactResponseDto.class);

        CreateDiagnosticRunRequestDto valReq = CreateDiagnosticRunRequestDto.builder()
                .modelArtifactId(model.getId())
                .evaluationDatasetArtifactId(dataset.getId())
                .targetColumn("target")
                .modules(List.of(DiagnosticModule.DATA_QUALITY, DiagnosticModule.EXPLAINABILITY))
                .build();

        // 1. Check Preflight Validate API returns structured errors without creating a run
        MvcResult valRes = mockMvc.perform(post("/api/diagnostics/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(valReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[?(@.code == 'MISSING_MODEL_FEATURE')]").exists())
                .andReturn();

        ValidationResultDto valDto = objectMapper.readValue(valRes.getResponse().getContentAsString(), ValidationResultDto.class);
        assertFalse(valDto.isValid());
        assertTrue(valDto.getErrors().stream().anyMatch(e -> "MISSING_MODEL_FEATURE".equals(e.getCode()) && e.getMessage().contains("feat_2")));

        // 2. Direct run creation must be rejected
        mockMvc.perform(post("/api/diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(valReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("MISSING_MODEL_FEATURE")));
    }

    @Test
    public void testPreflightValidationMulticlassAndRegressionRejected() throws Exception {
        // Multiclass model
        String modelJson = "{\"task\": \"multiclass\", \"feature_names\": [\"feat_multi_a\"]}";
        MockMultipartFile modelFile = new MockMultipartFile("file", "multiclass_model.json", "application/json", modelJson.getBytes());
        MvcResult mRes = mockMvc.perform(multipart("/api/artifacts/models")
                        .file(modelFile)
                        .param("taskType", "multiclass"))
                .andExpect(status().isCreated())
                .andReturn();
        ModelArtifactResponseDto model = objectMapper.readValue(mRes.getResponse().getContentAsString(), ModelArtifactResponseDto.class);

        String csv = "feat_multi_a,target\n1,0\n2,1\n3,2\n";
        MockMultipartFile datasetFile = new MockMultipartFile("file", "multiclass_data.csv", "text/csv", csv.getBytes());
        MvcResult dRes = mockMvc.perform(multipart("/api/artifacts/datasets").file(datasetFile)).andExpect(status().isCreated()).andReturn();
        DatasetArtifactResponseDto dataset = objectMapper.readValue(dRes.getResponse().getContentAsString(), DatasetArtifactResponseDto.class);

        CreateDiagnosticRunRequestDto valReq = CreateDiagnosticRunRequestDto.builder()
                .modelArtifactId(model.getId())
                .evaluationDatasetArtifactId(dataset.getId())
                .targetColumn("target")
                .modules(List.of(DiagnosticModule.DATA_QUALITY, DiagnosticModule.PERFORMANCE))
                .build();

        mockMvc.perform(post("/api/diagnostics/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(valReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[?(@.code == 'MULTICLASS_NOT_SUPPORTED')]").exists());

        // Direct creation rejected
        mockMvc.perform(post("/api/diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(valReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("MULTICLASS_NOT_SUPPORTED")));
    }

    @Test
    public void testModulePrerequisiteValidation() throws Exception {
        String modelJson = "{\"feature_names\": [\"feat_a\"]}";
        MockMultipartFile modelFile = new MockMultipartFile("file", "valid_model.json", "application/json", modelJson.getBytes());
        MvcResult mRes = mockMvc.perform(multipart("/api/artifacts/models").file(modelFile)).andExpect(status().isCreated()).andReturn();
        ModelArtifactResponseDto model = objectMapper.readValue(mRes.getResponse().getContentAsString(), ModelArtifactResponseDto.class);

        String csv = "feat_a,target\n1,0\n2,1\n";
        MockMultipartFile datasetFile = new MockMultipartFile("file", "valid_eval.csv", "text/csv", csv.getBytes());
        MvcResult dRes = mockMvc.perform(multipart("/api/artifacts/datasets").file(datasetFile)).andExpect(status().isCreated()).andReturn();
        DatasetArtifactResponseDto dataset = objectMapper.readValue(dRes.getResponse().getContentAsString(), DatasetArtifactResponseDto.class);

        // 1. Request DRIFT without baseline dataset -> Must fail preflight
        CreateDiagnosticRunRequestDto driftReq = CreateDiagnosticRunRequestDto.builder()
                .modelArtifactId(model.getId())
                .evaluationDatasetArtifactId(dataset.getId())
                .targetColumn("target")
                .modules(List.of(DiagnosticModule.DRIFT))
                .build();

        mockMvc.perform(post("/api/diagnostics/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(driftReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[?(@.code == 'MODULE_PREREQUISITE_UNMET')]").exists())
                .andExpect(jsonPath("$.moduleValidation.DRIFT.compatible").value(false));

        // 2. Request BIAS without protected attribute -> Must fail preflight
        CreateDiagnosticRunRequestDto biasReq = CreateDiagnosticRunRequestDto.builder()
                .modelArtifactId(model.getId())
                .evaluationDatasetArtifactId(dataset.getId())
                .targetColumn("target")
                .modules(List.of(DiagnosticModule.BIAS))
                .build();

        mockMvc.perform(post("/api/diagnostics/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(biasReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[?(@.code == 'MODULE_PREREQUISITE_UNMET')]").exists())
                .andExpect(jsonPath("$.moduleValidation.BIAS.compatible").value(false));
    }

    @Test
    public void testFullValidRunCreationAndArtifactReuse() throws Exception {
        // 1. Upload Model
        String modelJson = "{\"feature_names\": [\"age\", \"balance\", \"is_foreign\"]}";
        MockMultipartFile modelFile = new MockMultipartFile("file", "reuse_model.json", "application/json", modelJson.getBytes());
        MvcResult mRes = mockMvc.perform(multipart("/api/artifacts/models").file(modelFile)).andExpect(status().isCreated()).andReturn();
        ModelArtifactResponseDto model = objectMapper.readValue(mRes.getResponse().getContentAsString(), ModelArtifactResponseDto.class);

        // 2. Upload Eval Dataset
        String evalCsv = "age,balance,is_foreign,is_fraud\n25,500.0,1,0\n45,1200.0,0,1\n35,800.0,0,0\n55,3000.0,1,1\n";
        MockMultipartFile evalFile = new MockMultipartFile("file", "reuse_eval.csv", "text/csv", evalCsv.getBytes());
        MvcResult eRes = mockMvc.perform(multipart("/api/artifacts/datasets").file(evalFile)).andExpect(status().isCreated()).andReturn();
        DatasetArtifactResponseDto evalDataset = objectMapper.readValue(eRes.getResponse().getContentAsString(), DatasetArtifactResponseDto.class);

        // 3. Upload Baseline Dataset
        String baseCsv = "age,balance,is_foreign,is_fraud\n22,400.0,1,0\n40,1100.0,0,1\n";
        MockMultipartFile baseFile = new MockMultipartFile("file", "reuse_base.csv", "text/csv", baseCsv.getBytes());
        MvcResult bRes = mockMvc.perform(multipart("/api/artifacts/datasets").file(baseFile)).andExpect(status().isCreated()).andReturn();
        DatasetArtifactResponseDto baseDataset = objectMapper.readValue(bRes.getResponse().getContentAsString(), DatasetArtifactResponseDto.class);

        // 4. Validate Configuration
        CreateDiagnosticRunRequestDto req = CreateDiagnosticRunRequestDto.builder()
                .modelArtifactId(model.getId())
                .evaluationDatasetArtifactId(evalDataset.getId())
                .baselineDatasetArtifactId(baseDataset.getId())
                .targetColumn("is_fraud")
                .protectedAttribute("is_foreign")
                .modules(List.of(DiagnosticModule.DATA_QUALITY, DiagnosticModule.LEAKAGE, DiagnosticModule.DRIFT, DiagnosticModule.PERFORMANCE, DiagnosticModule.EXPLAINABILITY, DiagnosticModule.BIAS, DiagnosticModule.ROBUSTNESS))
                .build();

        mockMvc.perform(post("/api/diagnostics/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.errors", empty()))
                .andExpect(jsonPath("$.targetSuggestions", not(empty())));

        // 5. Create First Run
        MvcResult r1 = mockMvc.perform(post("/api/diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.modelArtifactId").value(model.getId()))
                .andExpect(jsonPath("$.evaluationDatasetArtifactId").value(evalDataset.getId()))
                .andExpect(jsonPath("$.baselineDatasetArtifactId").value(baseDataset.getId()))
                .andReturn();

        // 6. Create Second Run Reusing the exact same artifacts
        MvcResult r2 = mockMvc.perform(post("/api/diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.modelArtifactId").value(model.getId()))
                .andExpect(jsonPath("$.evaluationDatasetArtifactId").value(evalDataset.getId()))
                .andReturn();

        DiagnosticRunResponseDto run1Dto = objectMapper.readValue(r1.getResponse().getContentAsString(), DiagnosticRunResponseDto.class);
        DiagnosticRunResponseDto run2Dto = objectMapper.readValue(r2.getResponse().getContentAsString(), DiagnosticRunResponseDto.class);

        assertNotEquals(run1Dto.getId(), run2Dto.getId());
        assertEquals(run1Dto.getModelArtifactId(), run2Dto.getModelArtifactId());
        assertEquals(run1Dto.getEvaluationDatasetArtifactId(), run2Dto.getEvaluationDatasetArtifactId());
    }
}
