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
        if (supportsLong && len >= 8) {
            int last = len - 8;
            for (int i = 0; i < last; i += 8) {
                if (view.longAt(start + i) != (long) LONG_VIEW.get(blob, off + i)) {
                    return false;
                }
            }
            // The tail is the last 8 bytes, overlapping the chunk before it, so no byte loop is left.
            return view.longAt(start + last) == (long) LONG_VIEW.get(blob, off + last);
        }
        if (supportsLong && start + 8 <= view.length() && off + 8 <= blob.length) {
            // Shorter than a long: compare the first len bytes of one little-endian read each.
            long mask = -1L >>> ((8 - len) << 3);
            return ((view.longAt(start) ^ (long) LONG_VIEW.get(blob, off)) & mask) == 0;
        }
        for (int i = 0; i < len; i++) {
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
