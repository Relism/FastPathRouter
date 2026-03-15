package dev.relism.fpr.core;

/**
 * Internal access bridge for match-time scratch storage.
 */
public final class MatchResultAccess {
    private MatchResultAccess() {
    }

    public static int[] stackState(MatchResult<?> result) {
        return result.stackStateArray();
    }

    public static int[] stackSegStart(MatchResult<?> result) {
        return result.stackSegStartArray();
    }

    public static int[] stackSegLen(MatchResult<?> result) {
        return result.stackSegLenArray();
    }

    public static int[] stackNextIdx(MatchResult<?> result) {
        return result.stackNextIdxArray();
    }

    public static int[] stackParamMark(MatchResult<?> result) {
        return result.stackParamMarkArray();
    }

    public static int[] stackEdgeIndex(MatchResult<?> result) {
        return result.stackEdgeIndexArray();
    }

    public static byte[] stackKind(MatchResult<?> result) {
        return result.stackKindArray();
    }

    public static int[] keyIds(MatchResult<?> result) {
        return result.keyIdsArray();
    }

    public static int[] starts(MatchResult<?> result) {
        return result.startsArray();
    }

    public static int[] lens(MatchResult<?> result) {
        return result.lensArray();
    }

    public static int[] scratchKeyIds(MatchResult<?> result) {
        return result.scratchKeyIdsArray();
    }

    public static int[] scratchStarts(MatchResult<?> result) {
        return result.scratchStartsArray();
    }

    public static int[] scratchLens(MatchResult<?> result) {
        return result.scratchLensArray();
    }

    public static int[] scratchEdges(MatchResult<?> result) {
        return result.scratchEdgesArray();
    }

    public static int stackSize(MatchResult<?> result) {
        return result.stackSize();
    }

    public static void stackSize(MatchResult<?> result, int size) {
        result.stackSize(size);
    }

    public static void paramCount(MatchResult<?> result, int count) {
        result.paramCount(count);
    }
}
