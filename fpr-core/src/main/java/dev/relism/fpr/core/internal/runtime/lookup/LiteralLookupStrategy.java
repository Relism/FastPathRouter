package dev.relism.fpr.core.internal.runtime.lookup;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Strategy ids for literal edge lookup, selected at compile time.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LiteralLookupStrategy {
    public static final byte LINEAR = 0;
    public static final byte ORDERED_PREFIX = 1;
    public static final byte HASH = 2;
}
