package dev.relism.fpr.core.internal.runtime;

import dev.relism.fpr.core.ByteView;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ByteCompareTest {
    @Test
    void equalsAgreesWithArraysEqualsForEveryLengthAndOffset() {
        byte[] data = new byte[40];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) ('a' + i % 26);
        }
        for (boolean supportsLong : new boolean[]{false, true}) {
            ByteView view = new View(data, supportsLong);
            for (int len = 0; len <= 20; len++) {
                for (int start = 0; start + len <= data.length; start++) {
                    for (int off = 0; off + len <= data.length; off++) {
                        byte[] blob = Arrays.copyOf(data, data.length);
                        boolean expected = Arrays.equals(data, start, start + len, blob, off, off + len);
                        assertThat(ByteCompare.equals(view, start, blob, off, len, supportsLong))
                                .as("len=%d start=%d off=%d long=%s", len, start, off, supportsLong)
                                .isEqualTo(expected);
                        if (len > 0) {
                            // A difference in the last compared byte must be seen, never the one after it.
                            blob[off + len - 1] ^= 1;
                            assertThat(ByteCompare.equals(view, start, blob, off, len, supportsLong))
                                    .isEqualTo(Arrays.equals(data, start, start + len, blob, off, off + len));
                            blob[off + len - 1] ^= 1;
                            if (off + len < blob.length) {
                                blob[off + len] ^= 1;
                                assertThat(ByteCompare.equals(view, start, blob, off, len, supportsLong))
                                        .isEqualTo(expected);
                            }
                        }
                    }
                }
            }
        }
    }

    private static final class View implements ByteView {
        private static final VarHandle LONG = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
        private final byte[] data;
        private final boolean supportsLong;

        View(byte[] data, boolean supportsLong) {
            this.data = data;
            this.supportsLong = supportsLong;
        }

        @Override
        public int length() {
            return data.length;
        }

        @Override
        public byte byteAt(int index) {
            return data[index];
        }

        @Override
        public boolean supportsLong() {
            return supportsLong;
        }

        @Override
        public long longAt(int index) {
            return (long) LONG.get(data, index);
        }
    }
}
