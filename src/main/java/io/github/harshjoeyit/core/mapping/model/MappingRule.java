package io.github.harshjoeyit.core.mapping.model;

import io.github.harshjoeyit.core.model.TxnCategory;
import io.github.harshjoeyit.core.parse.model.TxnType;
import lombok.Builder;

@Builder
public record MappingRule(
        TxnCategory txnCategory,

        /**
         *  Credit card transaction are mostly purchage can can be determined during parsing,
         *  but TxnType cannot be determined for UPI transactions, hence mapping rules
         */
        TxnType txnType,

        /**
         *  autoReview - if true, automatically flips reviewStatus to REVIEWED;
         *  if false, leaves it as PENDING for user validation
         */
        boolean autoReview
) {}