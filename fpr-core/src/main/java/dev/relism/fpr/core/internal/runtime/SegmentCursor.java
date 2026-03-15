package dev.relism.fpr.core.internal.runtime;

import dev.relism.fpr.core.ByteView;

final class SegmentCursor {
    private ByteView input;
    private int len;
    private int idx;
    private int segStart;
    private int segLen;
    private int nextIdx;

    void reset(ByteView input) {
        this.input = input;
        this.len = input.length();
        this.idx = 0;
        if (idx < len && input.byteAt(idx) == '/') {
            idx++;
        }
        this.segStart = 0;
        this.segLen = -1;
        this.nextIdx = idx;
    }

    boolean advance() {
        if (idx >= len) {
            return false;
        }
        segStart = idx;
        while (idx < len && input.byteAt(idx) != '/') {
            idx++;
        }
        segLen = idx - segStart;
        nextIdx = idx;
        if (idx < len && input.byteAt(idx) == '/') {
            nextIdx = idx + 1;
        }
        idx = nextIdx;
        return true;
    }

    void restore(int segStart, int segLen, int nextIdx) {
        this.segStart = segStart;
        this.segLen = segLen;
        this.nextIdx = nextIdx;
        this.idx = nextIdx;
    }

    int segStart() {
        return segStart;
    }

    int segLen() {
        return segLen;
    }

    int nextIdx() {
        return nextIdx;
    }

    int length() {
        return len;
    }
}
