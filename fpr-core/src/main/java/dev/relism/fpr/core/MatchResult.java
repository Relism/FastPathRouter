package dev.relism.fpr.core;

import lombok.Setter;

/**
 * Reusable match result container for parameter spans.
 */
public final class MatchResult<H> {
    private final int[] keyIds;
    private final int[] starts;
    private final int[] lens;
    private final int[] stackState;
    private final int[] stackSegStart;
    private final int[] stackSegLen;
    private final int[] stackNextIdx;
    private final int[] stackParamMark;
    private final int[] stackEdgeIndex;
    private final byte[] stackKind;
    private final int[] scratchKeyIds;
    private final int[] scratchStarts;
    private final int[] scratchLens;
    private final int[] scratchEdges;
    private int stackSize;
    private int paramCount;
    @Setter
    private H handler;
    private int labelId;

    public MatchResult() {
        this(8, 32);
    }

    public MatchResult(int maxParams) {
        this(maxParams, Math.max(32, maxParams));
    }

    public MatchResult(int maxParams, int maxStack) {
        if (maxParams < 0) {
            throw new IllegalArgumentException("maxParams must be >= 0");
        }
        if (maxStack <= 0) {
            throw new IllegalArgumentException("maxStack must be > 0");
        }
        this.keyIds = new int[maxParams];
        this.starts = new int[maxParams];
        this.lens = new int[maxParams];
        this.stackState = new int[maxStack];
        this.stackSegStart = new int[maxStack];
        this.stackSegLen = new int[maxStack];
        this.stackNextIdx = new int[maxStack];
        this.stackParamMark = new int[maxStack];
        this.stackEdgeIndex = new int[maxStack];
        this.stackKind = new byte[maxStack];
        this.scratchKeyIds = new int[maxParams];
        this.scratchStarts = new int[maxParams];
        this.scratchLens = new int[maxParams];
        this.scratchEdges = new int[maxStack];
    }

    /**
     * Clears handler and params but keeps label id.
     */
    public MatchResult<H> reset() {
        this.paramCount = 0;
        this.stackSize = 0;
        this.handler = null;
        return this;
    }

    public MatchResult<H> labelId(int labelId) {
        this.labelId = labelId;
        return this;
    }

    public int labelId() {
        return labelId;
    }

    public int paramCount() {
        return paramCount;
    }

    /**
     * Iterates params without allocations using a precomputed name array.
     * Use {@link RouterBuilder#paramNames()} to obtain the name array.
     */
    public void forEachParam(ByteView view, String[] paramNames, ParamConsumer consumer) {
        if (view == null) {
            throw new IllegalArgumentException("view must not be null");
        }
        if (paramNames == null) {
            throw new IllegalArgumentException("paramNames must not be null");
        }
        if (consumer == null) {
            throw new IllegalArgumentException("consumer must not be null");
        }
        for (int i = 0; i < paramCount; i++) {
            int keyId = keyIds[i];
            if (keyId < 0 || keyId >= paramNames.length) {
                throw new IllegalArgumentException("param name missing for keyId " + keyId);
            }
            consumer.accept(paramNames[keyId], view, starts[i], lens[i]);
        }
    }

    public int keyIdAt(int index) {
        return keyIds[index];
    }

    public int startAt(int index) {
        return starts[index];
    }

    public int lenAt(int index) {
        return lens[index];
    }

    public H handler() {
        return handler;
    }

    public int mark() {
        return paramCount;
    }

    public void rollbackTo(int mark) {
        paramCount = mark;
    }

    public void resetTo(int mark) {
        paramCount = mark;
    }

    public void addParam(int keyId, int start, int len) {
        if (paramCount >= keyIds.length) {
            throw new IllegalStateException("MatchResult capacity exceeded");
        }
        keyIds[paramCount] = keyId;
        starts[paramCount] = start;
        lens[paramCount] = len;
        paramCount++;
    }

    int[] stackStateArray() {
        return stackState;
    }

    int[] stackSegStartArray() {
        return stackSegStart;
    }

    int[] stackSegLenArray() {
        return stackSegLen;
    }

    int[] stackNextIdxArray() {
        return stackNextIdx;
    }

    int[] stackParamMarkArray() {
        return stackParamMark;
    }

    int[] stackEdgeIndexArray() {
        return stackEdgeIndex;
    }

    byte[] stackKindArray() {
        return stackKind;
    }

    int stackSize() {
        return stackSize;
    }

    void stackSize(int size) {
        this.stackSize = size;
    }

    int[] keyIdsArray() {
        return keyIds;
    }

    int[] startsArray() {
        return starts;
    }

    int[] lensArray() {
        return lens;
    }

    int[] scratchKeyIdsArray() {
        return scratchKeyIds;
    }

    int[] scratchStartsArray() {
        return scratchStarts;
    }

    int[] scratchLensArray() {
        return scratchLens;
    }

    int[] scratchEdgesArray() {
        return scratchEdges;
    }

    void paramCount(int count) {
        this.paramCount = count;
    }
}
