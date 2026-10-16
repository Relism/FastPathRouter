package dev.relism.fpr.core;

public interface ByteView {
    int length();

    byte byteAt(int index);

    default boolean supportsLong() {
        return false;
    }

    /**
     * The 8 bytes starting at {@code index}, read little-endian. Called only when
     * {@link #supportsLong()} is true and {@code index + 8 <= length()}.
     */
    default long longAt(int index) {
        throw new UnsupportedOperationException("longAt not supported");
    }
}
