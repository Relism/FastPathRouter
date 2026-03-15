package dev.relism.fpr.core.internal.runtime;

import dev.relism.fpr.core.ByteView;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ByteCompare {
    private static final VarHandle LONG_VIEW = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

    public static boolean equals(ByteView view, int start, byte[] blob, int off, int len, boolean supportsLong) {
        if (len == 0) {
            return true;
        }
        int i = 0;
        if (supportsLong && len >= 8) {
            int end = len - 8;
            for (; i <= end; i += 8) {
                long a = view.longAt(start + i);
                long b = (long) LONG_VIEW.get(blob, off + i);
                if (a != b) {
                    return false;
                }
            }
        }
        for (; i < len; i++) {
            if (view.byteAt(start + i) != blob[off + i]) {
                return false;
            }
        }
        return true;
    }

    public static int indexOf(ByteView view, int start, int max, byte[] blob, int off, int len, boolean supportsLong) {
        if (len == 0) {
            return start;
        }
        byte first = blob[off];
        for (int i = start; i <= max; i++) {
            if (view.byteAt(i) != first) {
                continue;
            }
            if (equals(view, i, blob, off, len, supportsLong)) {
                return i;
            }
        }
        return -1;
    }
}
