package dev.relism.fpr.core;

@FunctionalInterface
public interface ParamConsumer {
    /**
     * Receives a param name and its byte span in the matched input.
     */
    void accept(String name, ByteView view, int start, int len);
}
