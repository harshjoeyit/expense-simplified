package io.github.harshjoeyit.core.model;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class SaveRule {
    private SaveRuleByType saveRuleByType;

    // id is counterparty Id when saveRuleByType = COUNTERPARTY_ID, else counterparty Name
    private String id;

    private boolean autoReview;

    public enum SaveRuleByType {
        COUNTERPARTY_ID,
        COUNTERPARTY_NAME
    }

    @Override
    public String toString() {
        return "SaveRule{" +
                "saveRuleByType=" + saveRuleByType +
                ", id='" + id + '\'' +
                ", autoReview=" + autoReview +
                '}';
    }
}
