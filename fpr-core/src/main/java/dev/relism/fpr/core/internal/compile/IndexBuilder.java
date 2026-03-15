package dev.relism.fpr.core.internal.compile;

import lombok.NoArgsConstructor;

import java.util.Arrays;

@NoArgsConstructor
final class IndexBuilder {
    private static final int SECOND_LEVEL_THRESHOLD = 64;

    static int buildIndex(PrimitiveBuilders.IntArrayList indexStart,
                          PrimitiveBuilders.ShortArrayList indexCount,
                          PrimitiveBuilders.IntArrayList indexSecondOff,
                          PrimitiveBuilders.IntArrayList edgeLabelOff,
                          PrimitiveBuilders.ShortArrayList edgeLabelLen,
                          PrimitiveBuilders.ByteBlobBuilder blob,
                          int start,
                          int count,
                          boolean allowSecondByte) {
        int base = allocateTable(indexStart, indexCount, indexSecondOff);
        int[] startTmp = new int[256];
        short[] countTmp = new short[256];
        int[] len2Count = new int[256];
        Arrays.fill(startTmp, -1);

        int end = start + count;
        for (int i = start; i < end; i++) {
            int off = edgeLabelOff.get(i);
            int len = edgeLabelLen.get(i);
            if (len == 0) {
                continue;
            }
            int first = blob.byteAt(off) & 0xFF;
            if (startTmp[first] == -1) {
                startTmp[first] = i - start;
            }
            countTmp[first]++;
            if (allowSecondByte && len > 1) {
                len2Count[first]++;
            }
        }

        for (int i = 0; i < 256; i++) {
            indexStart.set(base + i, startTmp[i]);
            indexCount.set(base + i, countTmp[i]);
        }

        if (!allowSecondByte) {
            return base;
        }

        for (int first = 0; first < 256; first++) {
            int bucketCount = countTmp[first];
            if (bucketCount < SECOND_LEVEL_THRESHOLD || len2Count[first] == 0) {
                continue;
            }
            int relStart = startTmp[first];
            if (relStart < 0) {
                continue;
            }
            int bucketStart = start + relStart;
            int bucketEnd = bucketStart + bucketCount;

            int secondBase = allocateTable(indexStart, indexCount, indexSecondOff);
            int[] secondStart = new int[256];
            short[] secondCount = new short[256];
            Arrays.fill(secondStart, -1);

            for (int i = bucketStart; i < bucketEnd; i++) {
                int len = edgeLabelLen.get(i);
                if (len <= 1) {
                    continue;
                }
                int off = edgeLabelOff.get(i);
                int second = blob.byteAt(off + 1) & 0xFF;
                if (secondStart[second] == -1) {
                    secondStart[second] = i - start;
                }
                secondCount[second]++;
            }

            for (int i = 0; i < 256; i++) {
                indexStart.set(secondBase + i, secondStart[i]);
                indexCount.set(secondBase + i, secondCount[i]);
            }
            indexSecondOff.set(base + first, secondBase);
        }
        return base;
    }

    private static int allocateTable(PrimitiveBuilders.IntArrayList indexStart,
                                     PrimitiveBuilders.ShortArrayList indexCount,
                                     PrimitiveBuilders.IntArrayList indexSecondOff) {
        int base = indexStart.size();
        for (int i = 0; i < 256; i++) {
            indexStart.add(-1);
            indexCount.add((short) 0);
            indexSecondOff.add(-1);
        }
        return base;
    }
}
