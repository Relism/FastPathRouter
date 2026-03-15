package dev.relism.fpr.core.internal.runtime.lookup;

import dev.relism.fpr.core.ByteView;
import dev.relism.fpr.core.MatchResult;
import dev.relism.fpr.core.internal.runtime.ByteCompare;
import dev.relism.fpr.core.internal.runtime.FrozenRouter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Matching strategies for mixed literal/param segments.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MixedLookup {
    public static boolean match(FrozenRouter<?> router,
                                int edgeIndex,
                                ByteView input,
                                int segStart,
                                int segLen,
                                boolean supportsLong,
                                MatchResult<?> out) {
        byte strategy = router.edgeMixedStrategy[edgeIndex];
        if (strategy == MixedLookupStrategy.ONE) {
            return matchOne(router, edgeIndex, input, segStart, segLen, supportsLong, out);
        }
        return matchGeneral(router, edgeIndex, input, segStart, segLen, supportsLong, out);
    }

    private static boolean matchOne(FrozenRouter<?> router,
                                    int edgeIndex,
                                    ByteView input,
                                    int segStart,
                                    int segLen,
                                    boolean supportsLong,
                                    MatchResult<?> out) {
        int chunkOff = router.edgeMixedChunkOff[edgeIndex];
        int paramOff = router.edgeMixedParamOff[edgeIndex];
        int lit0Off = router.mixedChunkOff[chunkOff];
        int lit0Len = router.mixedChunkLen[chunkOff];
        int lit1Off = router.mixedChunkOff[chunkOff + 1];
        int lit1Len = router.mixedChunkLen[chunkOff + 1];
        int required = lit0Len + lit1Len;
        int paramLen = segLen - required;
        if (paramLen <= 0) {
            return false;
        }
        if (!ByteCompare.equals(input, segStart, router.blob, lit0Off, lit0Len, supportsLong)) {
            return false;
        }
        int suffixStart = segStart + segLen - lit1Len;
        if (!ByteCompare.equals(input, suffixStart, router.blob, lit1Off, lit1Len, supportsLong)) {
            return false;
        }
        int paramStart = segStart + lit0Len;
        out.addParam(router.mixedParamKeyId[paramOff], paramStart, paramLen);
        return true;
    }

    private static boolean matchGeneral(FrozenRouter<?> router,
                                        int edgeIndex,
                                        ByteView input,
                                        int segStart,
                                        int segLen,
                                        boolean supportsLong,
                                        MatchResult<?> out) {
        int chunkOff = router.edgeMixedChunkOff[edgeIndex];
        int chunkCount = router.edgeMixedChunkCount[edgeIndex];
        int paramOff = router.edgeMixedParamOff[edgeIndex];
        int paramCount = router.edgeMixedParamCount[edgeIndex];
        int end = segStart + segLen;
        int cursor = segStart;

        for (int i = 0; i < paramCount; i++) {
            int litOff = router.mixedChunkOff[chunkOff + i];
            int litLen = router.mixedChunkLen[chunkOff + i];
            if (!ByteCompare.equals(input, cursor, router.blob, litOff, litLen, supportsLong)) {
                return false;
            }
            cursor += litLen;
            int nextLitOff = router.mixedChunkOff[chunkOff + i + 1];
            int nextLitLen = router.mixedChunkLen[chunkOff + i + 1];
            int nextPos;
            if (nextLitLen == 0) {
                nextPos = end;
            } else {
                nextPos = ByteCompare.indexOf(input, cursor, end - nextLitLen, router.blob, nextLitOff, nextLitLen, supportsLong);
                if (nextPos < 0) {
                    return false;
                }
            }
            int paramLen = nextPos - cursor;
            if (paramLen <= 0) {
                return false;
            }
            out.addParam(router.mixedParamKeyId[paramOff + i], cursor, paramLen);
            cursor = nextPos;
        }
        int tailOff = router.mixedChunkOff[chunkOff + chunkCount - 1];
        int tailLen = router.mixedChunkLen[chunkOff + chunkCount - 1];
        return ByteCompare.equals(input, cursor, router.blob, tailOff, tailLen, supportsLong);
    }
}
