package dev.relism.fpr.core.internal.runtime.lookup;

import dev.relism.fpr.core.ByteView;
import dev.relism.fpr.core.internal.runtime.ByteCompare;
import dev.relism.fpr.core.internal.runtime.FrozenRouter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Lookup strategies for literal edges, selected per state at compile-time.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LiteralLookup {
    public static int find(FrozenRouter<?> router,
                           ByteView input,
                           int segStart,
                           int segLen,
                           boolean supportsLong,
                           int state) {
        int count = router.stateLiteralCount[state];
        if (count == 0) {
            return -1;
        }
        int start = router.stateLiteralStart[state];
        byte strategy = router.stateLiteralStrategy[state];
        if (strategy == LiteralLookupStrategy.LINEAR) {
            return linear(router, input, segStart, segLen, supportsLong, start, count);
        }
        if (strategy == LiteralLookupStrategy.ORDERED_PREFIX) {
            return orderedPrefix(router, input, segStart, segLen, supportsLong, start, count);
        }
        if (strategy == LiteralLookupStrategy.HASH) {
            return hash(router, input, segStart, segLen, supportsLong, state, start, count);
        }
        return linear(router, input, segStart, segLen, supportsLong, start, count);
    }

    private static int linear(FrozenRouter<?> router,
                              ByteView input,
                              int segStart,
                              int segLen,
                              boolean supportsLong,
                              int start,
                              int count) {
        int end = start + count;
        for (int i = start; i < end; i++) {
            if (router.edgeLabelLen[i] != segLen) {
                continue;
            }
            if (ByteCompare.equals(input, segStart, router.blob, router.edgeLabelOff[i], segLen, supportsLong)) {
                return i;
            }
        }
        return -1;
    }

    private static int orderedPrefix(FrozenRouter<?> router,
                                     ByteView input,
                                     int segStart,
                                     int segLen,
                                     boolean supportsLong,
                                     int start,
                                     int count) {
        long key = prefixKey(input, segStart, segLen, supportsLong);
        int lo = start;
        int hi = start + count - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            long midKey = router.edgeLiteralPrefix[mid];
            int cmp = compare(segLen, key, router.edgeLabelLen[mid], midKey);
            if (cmp < 0) {
                hi = mid - 1;
            } else if (cmp > 0) {
                lo = mid + 1;
            } else {
                int left = mid;
                while (left > start && compare(segLen, key, router.edgeLabelLen[left - 1], router.edgeLiteralPrefix[left - 1]) == 0) {
                    left--;
                }
                int right = mid;
                int end = start + count;
                while (right + 1 < end && compare(segLen, key, router.edgeLabelLen[right + 1], router.edgeLiteralPrefix[right + 1]) == 0) {
                    right++;
                }
                for (int i = left; i <= right; i++) {
                    if (router.edgeLabelLen[i] != segLen) {
                        continue;
                    }
                    if (ByteCompare.equals(input, segStart, router.blob, router.edgeLabelOff[i], segLen, supportsLong)) {
                        return i;
                    }
                }
                return -1;
            }
        }
        return -1;
    }

    private static int hash(FrozenRouter<?> router,
                            ByteView input,
                            int segStart,
                            int segLen,
                            boolean supportsLong,
                            int state,
                            int start,
                            int count) {
        int mask = router.stateLiteralHashMask[state];
        int off = router.stateLiteralHashOff[state];
        if (mask <= 0 || off < 0) {
            return linear(router, input, segStart, segLen, supportsLong, start, count);
        }
        long key = prefixKey(input, segStart, segLen, supportsLong);
        int slot = mix(key) & mask;
        while (true) {
            long stored = router.literalHashKey[off + slot];
            if (stored == 0L) {
                return -1;
            }
            if (stored == key) {
                int edgeIndex = router.literalHashEdge[off + slot];
                if (edgeIndex >= start && edgeIndex < start + count && router.edgeLabelLen[edgeIndex] == segLen
                        && ByteCompare.equals(input, segStart, router.blob, router.edgeLabelOff[edgeIndex], segLen, supportsLong)) {
                    return edgeIndex;
                }
            }
            slot = (slot + 1) & mask;
        }
    }

    private static int compare(int aLen, long aKey, short bLen, long bKey) {
        if (aLen != bLen) {
            return Integer.compare(aLen, bLen);
        }
        return Long.compareUnsigned(aKey, bKey);
    }

    private static long prefixKey(ByteView input,
                                  int segStart,
                                  int segLen,
                                  boolean supportsLong) {
        if (segLen >= 8 && supportsLong && segStart + 8 <= input.length()) {
            return input.longAt(segStart);
        }
        long key = 0;
        int len = Math.min(8, segLen);
        for (int i = 0; i < len; i++) {
            key |= ((long) input.byteAt(segStart + i) & 0xFFL) << (i * 8);
        }
        return key;
    }

    private static int mix(long key) {
        long h = key ^ (key >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= (h >>> 33);
        return (int) h;
    }
}
