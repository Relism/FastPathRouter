package dev.relism.fpr.core;

/**
 * Immutable router interface for hot-path matching.
 */
public interface FastPathRouter<I, H> {
    int NO_MATCH = -1;

    int match(I input, MatchResult<H> out);
}
