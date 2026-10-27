package io.github.harshjoeyit.split.model;

import lombok.Builder;
import java.util.Map;

@Builder
public record SplitInput (
        Double amount,
        SplitStrategy strategy,
        /**
         * multi-purpose map depending on strategy
         * strategy = EQUAL, value is ignored
         * strategy = CUSTOM, value is absolute amount (e.g. 120)
         * strategy = PERCENT, value denotes percentage (e.g. 40%)
         * strategy = SHARE, value denotes share (e.g. 2)
         */
        Map<Friend, Double> valuePerFriend
) {}
