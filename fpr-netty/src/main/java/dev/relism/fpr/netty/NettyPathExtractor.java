package dev.relism.fpr.netty;

import io.netty.buffer.ByteBuf;

public final class NettyPathExtractor {
    private NettyPathExtractor() {
    }

    public static PathSpan extractFromRequestLine(ByteBuf buffer, int offset, int length, PathSpan out) {
        if (out == null) {
            throw new IllegalArgumentException("out must not be null");
        }
        int end = offset + length;
        int i = offset;
        while (i < end && buffer.getByte(i) != ' ') {
            i++;
        }
        if (i >= end) {
            throw new IllegalArgumentException("invalid request line");
        }
        i++;
        int pathStart = i;
        while (i < end && buffer.getByte(i) != ' ') {
            i++;
        }
        int pathLen = i - pathStart;
        out.set(pathStart, pathLen);
        return out;
    }

    public static PathSpan extractFromUri(CharSequence uri, PathSpan out) {
        if (out == null) {
            throw new IllegalArgumentException("out must not be null");
        }
        int len = uri.length();
        int end = len;
        for (int i = 0; i < len; i++) {
            if (uri.charAt(i) == '?') {
                end = i;
                break;
            }
        }
        out.set(0, end);
        return out;
    }

    public static final class PathSpan {
        private int start;
        private int len;

        public int start() {
            return start;
        }

        public int len() {
            return len;
        }

        public void set(int start, int len) {
            this.start = start;
            this.len = len;
        }
    }
}
