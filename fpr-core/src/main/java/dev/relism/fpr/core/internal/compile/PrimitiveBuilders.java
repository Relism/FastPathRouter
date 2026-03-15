package dev.relism.fpr.core.internal.compile;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PrimitiveBuilders {
    public static final class IntArrayList {
        private int[] data;
        private int size;

        public IntArrayList(int initial) {
            this.data = new int[Math.max(8, initial)];
        }

        public int size() {
            return size;
        }

        public int get(int index) {
            return data[index];
        }

        public void set(int index, int value) {
            data[index] = value;
        }

        public void add(int value) {
            ensure(size + 1);
            data[size++] = value;
        }

        public int[] toArray() {
            int[] out = new int[size];
            System.arraycopy(data, 0, out, 0, size);
            return out;
        }

        private void ensure(int target) {
            if (target <= data.length) {
                return;
            }
            int newCap = Math.max(target, data.length * 2);
            int[] next = new int[newCap];
            System.arraycopy(data, 0, next, 0, size);
            data = next;
        }
    }

    public static final class ShortArrayList {
        private short[] data;
        private int size;

        public ShortArrayList(int initial) {
            this.data = new short[Math.max(8, initial)];
        }

        public int size() {
            return size;
        }

        public short get(int index) {
            return data[index];
        }

        public void set(int index, short value) {
            data[index] = value;
        }

        public void add(short value) {
            ensure(size + 1);
            data[size++] = value;
        }

        public short[] toArray() {
            short[] out = new short[size];
            System.arraycopy(data, 0, out, 0, size);
            return out;
        }

        private void ensure(int target) {
            if (target <= data.length) {
                return;
            }
            int newCap = Math.max(target, data.length * 2);
            short[] next = new short[newCap];
            System.arraycopy(data, 0, next, 0, size);
            data = next;
        }
    }

    public static final class ByteArrayList {
        private byte[] data;
        private int size;

        public ByteArrayList(int initial) {
            this.data = new byte[Math.max(8, initial)];
        }

        public int size() {
            return size;
        }

        public void add(byte value) {
            ensure(size + 1);
            data[size++] = value;
        }

        public byte[] toArray() {
            byte[] out = new byte[size];
            System.arraycopy(data, 0, out, 0, size);
            return out;
        }

        private void ensure(int target) {
            if (target <= data.length) {
                return;
            }
            int newCap = Math.max(target, data.length * 2);
            byte[] next = new byte[newCap];
            System.arraycopy(data, 0, next, 0, size);
            data = next;
        }
    }

    public static final class LongArrayList {
        private long[] data;
        private int size;

        public LongArrayList(int initial) {
            this.data = new long[Math.max(8, initial)];
        }

        public int size() {
            return size;
        }

        public long get(int index) {
            return data[index];
        }

        public void set(int index, long value) {
            data[index] = value;
        }

        public void add(long value) {
            ensure(size + 1);
            data[size++] = value;
        }

        public long[] toArray() {
            long[] out = new long[size];
            System.arraycopy(data, 0, out, 0, size);
            return out;
        }

        private void ensure(int target) {
            if (target <= data.length) {
                return;
            }
            int newCap = Math.max(target, data.length * 2);
            long[] next = new long[newCap];
            System.arraycopy(data, 0, next, 0, size);
            data = next;
        }
    }

    public static final class ByteBlobBuilder {
        private byte[] data;
        private int size;

        public ByteBlobBuilder(int initial) {
            this.data = new byte[Math.max(16, initial)];
        }

        public int append(byte[] bytes) {
            int off = size;
            ensure(size + bytes.length);
            System.arraycopy(bytes, 0, data, size, bytes.length);
            size += bytes.length;
            return off;
        }

        public byte byteAt(int index) {
            return data[index];
        }

        public byte[] toArray() {
            byte[] out = new byte[size];
            System.arraycopy(data, 0, out, 0, size);
            return out;
        }

        private void ensure(int target) {
            if (target <= data.length) {
                return;
            }
            int newCap = Math.max(target, data.length * 2);
            byte[] next = new byte[newCap];
            System.arraycopy(data, 0, next, 0, size);
            data = next;
        }
    }
}
