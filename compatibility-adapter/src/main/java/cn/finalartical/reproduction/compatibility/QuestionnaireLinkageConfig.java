package cn.finalartical.reproduction.compatibility;

import java.util.Objects;

public final class QuestionnaireLinkageConfig {
    private final String questionnaireId;
    private final String version;

    public QuestionnaireLinkageConfig(String questionnaireId, String version) {
        if (isBlank(questionnaireId) || isBlank(version)) {
            throw new IllegalArgumentException("linkage config values must not be blank");
        }
        this.questionnaireId = questionnaireId;
        this.version = version;
    }

    public String getQuestionnaireId() {
        return questionnaireId;
    }

    public String getVersion() {
        return version;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof QuestionnaireLinkageConfig)) {
            return false;
        }
        QuestionnaireLinkageConfig that = (QuestionnaireLinkageConfig) other;
        return questionnaireId.equals(that.questionnaireId) && version.equals(that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(questionnaireId, version);
    }
}
