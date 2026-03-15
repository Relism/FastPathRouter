package dev.relism.fpr.core;

public interface ByteView {
    int length();

    byte byteAt(int index);

    default boolean supportsLong() {
        return false;
    }

    default long longAt(int index) {
        throw new UnsupportedOperationException("longAt not supported");
    }
}
