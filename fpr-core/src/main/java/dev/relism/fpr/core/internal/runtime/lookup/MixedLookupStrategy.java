package dev.relism.fpr.core.internal.runtime.lookup;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Strategy ids for mixed-segment matching, selected at compile time.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MixedLookupStrategy {
    public static final byte ONE = 0;
    public static final byte TWO = 1;
    public static final byte N = 2;
}
