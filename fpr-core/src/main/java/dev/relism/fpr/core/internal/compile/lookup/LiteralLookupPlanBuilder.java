package dev.relism.fpr.core.internal.compile.lookup;

import dev.relism.fpr.core.internal.compile.PrimitiveBuilders;
import dev.relism.fpr.core.internal.runtime.lookup.LiteralLookupStrategy;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LiteralLookupPlanBuilder {
    private static final int LINEAR_LIMIT = 16;
    private static final int ORDERED_LIMIT = 256;

    public static byte selectStrategy(int literalCount) {
        if (literalCount <= LINEAR_LIMIT) {
            return LiteralLookupStrategy.LINEAR;
        }
        if (literalCount <= ORDERED_LIMIT) {
            return LiteralLookupStrategy.ORDERED_PREFIX;
        }
        return LiteralLookupStrategy.HASH;
    }

    public static long prefixKey(byte[] literal) {
        int len = Math.min(8, literal.length);
        long key = 0;
        for (int i = 0; i < len; i++) {
            key |= ((long) literal[i] & 0xFFL) << (i * 8);
        }
        return key;
    }

    public static HashPlan buildHash(PrimitiveBuilders.LongArrayList hashKeys,
                                     PrimitiveBuilders.IntArrayList hashEdges,
                                     PrimitiveBuilders.LongArrayList edgePrefix,
                                     PrimitiveBuilders.ShortArrayList edgeLabelLen,
                                     int start,
                                     int count) {
        int size = tableSize(count);
        int mask = size - 1;
        int off = hashKeys.size();
        for (int i = 0; i < size; i++) {
            hashKeys.add(0L);
            hashEdges.add(-1);
        }
        int end = start + count;
        for (int i = start; i < end; i++) {
            int len = edgeLabelLen.get(i);
            if (len <= 0) {
                continue;
            }
            long key = edgePrefix.get(i);
            int slot = mix(key) & mask;
            while (hashKeys.get(off + slot) != 0L) {
                slot = (slot + 1) & mask;
            }
            hashKeys.set(off + slot, key);
            hashEdges.set(off + slot, i);
        }
        return new HashPlan(off, mask);
    }

    private static int tableSize(int count) {
        int size = 1;
        int target = Math.max(4, count * 2);
        while (size < target) {
            size <<= 1;
        }
        return size;
    }

    private static int mix(long key) {
        long h = key ^ (key >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= (h >>> 33);
        return (int) h;
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class HashPlan {
        public final int offset;
        public final int mask;
    }
}
