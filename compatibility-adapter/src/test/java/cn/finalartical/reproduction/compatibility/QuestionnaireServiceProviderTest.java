package cn.finalartical.reproduction.compatibility;

import cn.finalartical.reproduction.ontology.OntologyAssembler;
import cn.finalartical.reproduction.ontology.Questionnaire;
import cn.finalartical.reproduction.ontology.Subject;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class QuestionnaireServiceProviderTest {
    private JsfExAssessService service;

    @Before
    public void setUp() {
        InMemoryQuestionnaireRepository repository = new InMemoryQuestionnaireRepository()
                .add(new Questionnaire("q-001", "Java 面试基础", "subject-001")
                        .addSubject(new Subject("s-001", "集合")))
                .add(new Questionnaire("q-002", "Java 并发", "subject-001"));
        service = new JsfExAssessService(new QuestionnaireServiceProvider(repository, new OntologyAssembler()));
    }

    @Test
    public void queryNormalAndNullUseDifferentExplicitStatuses() {
        OperationResult<java.util.List<String>> normal = service.queryQuestionnaireIdsBySubjectId("subject-001", "trace-normal");
        OperationResult<java.util.List<String>> nullQuery = service.queryQuestionnaireIdsBySubjectId((String) null, "trace-null");

        assertEquals(OperationStatus.SUCCESS, normal.getStatus());
        assertEquals(Arrays.asList("q-001", "q-002"), normal.getData());
        assertEquals(OperationStatus.SUCCESS, nullQuery.getStatus());
        assertEquals(2, nullQuery.getData().size());
    }

    @Test
    public void rejectsInvalidInputAndMissingConfig() {
        assertEquals(OperationStatus.INVALID_INPUT,
                service.queryQuestionnaireIdsBySubjectId("!invalid", "trace-invalid").getStatus());
        assertEquals(OperationStatus.NOT_FOUND,
                service.queryQuestionnaireLinkageConfig("q-001", "trace-config").getStatus());
    }

    @Test
    public void savesConfigIdempotentlyAndBuildsOntologyDetail() {
        assertEquals(OperationStatus.SUCCESS,
                service.saveQuestionnaireLinkageConfig("q-001", "v1", "trace-save-1").getStatus());
        assertEquals(OperationStatus.SUCCESS,
                service.saveQuestionnaireLinkageConfig("q-001", "v1", "trace-save-2").getStatus());
        OperationResult<cn.finalartical.reproduction.ontology.JobOntologyDetail> detail =
                service.questionnaireDetail("q-001", "trace-detail");

        assertEquals(OperationStatus.SUCCESS, detail.getStatus());
        assertEquals("q-001", detail.getData().getObjectId());
        assertTrue(detail.getData().getDynamicAttributes().containsKey("subjectCount"));
    }

    @Test
    public void legacyRequestFieldNamesAreResolvedByTheCompatibilityBoundary() {
        Map<String, Object> query = new LinkedHashMap<String, Object>();
        query.put("legacy_subject_id", "subject-001");
        Map<String, Object> save = new LinkedHashMap<String, Object>();
        save.put("legacy_questionnaire_id", "q-001");
        save.put("config", "v1");

        assertEquals(OperationStatus.SUCCESS,
                service.queryQuestionnaireIdsByRequest(query, "trace-legacy-query").getStatus());
        assertEquals(OperationStatus.SUCCESS,
                service.saveQuestionnaireLinkageConfigByRequest(save, "trace-legacy-save").getStatus());
        assertEquals(OperationStatus.SUCCESS,
                service.questionnaireDetailByRequest(new LinkedHashMap<String, Object>() {{
                    put("legacy_questionnaire_id", "q-001");
                }}, "trace-legacy-detail").getStatus());
    }
}
