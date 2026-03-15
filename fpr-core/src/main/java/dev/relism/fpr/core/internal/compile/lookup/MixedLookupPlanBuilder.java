package dev.relism.fpr.core.internal.compile.lookup;

import dev.relism.fpr.core.internal.runtime.lookup.MixedLookupStrategy;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MixedLookupPlanBuilder {
    public static byte strategyForParamCount(int paramCount) {
        if (paramCount <= 1) {
            return MixedLookupStrategy.ONE;
        }
        if (paramCount == 2) {
            return MixedLookupStrategy.TWO;
        }
        return MixedLookupStrategy.N;
    }
}
