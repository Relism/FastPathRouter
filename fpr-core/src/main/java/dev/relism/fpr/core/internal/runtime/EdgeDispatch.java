package dev.relism.fpr.core.internal.runtime;

final class EdgeDispatch {
    private EdgeDispatch() {
    }

    static long mixedPrefixRange(FrozenRouter<?> router, int state, int firstByte) {
        int prefixCount = router.stateMixedPrefixCount[state];
        if (prefixCount <= 0) {
            return range(0, 0);
        }
        int indexOff = router.stateMixedIndexOff[state];
        if (indexOff >= 0) {
            int rangeStartRel = router.indexStart[indexOff + firstByte];
            int rangeCount = router.indexCount[indexOff + firstByte];
            if (rangeCount == 0 || rangeStartRel < 0) {
                return range(0, 0);
            }
            return range(router.stateMixedStart[state] + rangeStartRel, rangeCount);
        }
        return range(router.stateMixedStart[state], router.stateMixedCount[state]);
    }

    static long range(int start, int count) {
        return ((long) start << 32) | (count & 0xffffffffL);
    }

    static int rangeStart(long range) {
        return (int) (range >>> 32);
    }

    static int rangeCount(long range) {
        return (int) range;
    }
}
