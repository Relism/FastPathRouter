package dev.relism.fpr.core.internal.runtime;

import dev.relism.fpr.core.ByteView;
import dev.relism.fpr.core.MatchResult;
import dev.relism.fpr.core.MatchResultAccess;
import dev.relism.fpr.core.internal.runtime.lookup.LiteralLookup;
import dev.relism.fpr.core.internal.runtime.lookup.MixedLookup;

final class RouteSearch {
    static final long NO_CANDIDATE = -1L;

    private int[] stackState;
    private int[] stackSegStart;
    private int[] stackSegLen;
    private int[] stackNextIdx;
    private int[] stackParamMark;
    private int[] stackEdgeIndex;
    private byte[] stackKind;
    private int[] keyIds;
    private int[] starts;
    private int[] lens;
    private int[] scratchKeyIds;
    private int[] scratchStarts;
    private int[] scratchLens;
    private int[] scratchEdges;
    private int stackSize;
    private final Frame frame = new Frame();

    private final byte kindLiteral = (byte) EdgeKind.LITERAL.ordinal();
    private final byte kindMixed = (byte) EdgeKind.MIXED.ordinal();
    private final byte kindParam = (byte) EdgeKind.PARAM.ordinal();
    private final byte kindWild = (byte) EdgeKind.WILD.ordinal();
    private final byte kindCatch = (byte) EdgeKind.CATCH.ordinal();

    void reset(MatchResult<?> out) {
        this.stackState = MatchResultAccess.stackState(out);
        this.stackSegStart = MatchResultAccess.stackSegStart(out);
        this.stackSegLen = MatchResultAccess.stackSegLen(out);
        this.stackNextIdx = MatchResultAccess.stackNextIdx(out);
        this.stackParamMark = MatchResultAccess.stackParamMark(out);
        this.stackEdgeIndex = MatchResultAccess.stackEdgeIndex(out);
        this.stackKind = MatchResultAccess.stackKind(out);
        this.keyIds = MatchResultAccess.keyIds(out);
        this.starts = MatchResultAccess.starts(out);
        this.lens = MatchResultAccess.lens(out);
        this.scratchKeyIds = MatchResultAccess.scratchKeyIds(out);
        this.scratchStarts = MatchResultAccess.scratchStarts(out);
        this.scratchLens = MatchResultAccess.scratchLens(out);
        this.scratchEdges = MatchResultAccess.scratchEdges(out);
        this.stackSize = 0;
    }

    Frame frame() {
        return frame;
    }

    long selectCandidate(FrozenRouter<?> router,
                         int state,
                         ByteView input,
                         SegmentCursor cursor,
                         boolean supportsLong,
                         MatchResult<?> out) {
        int segStart = cursor.segStart();
        int segLen = cursor.segLen();
        int nextIdx = cursor.nextIdx();
        int mark = out.mark();

        int literalEdge = LiteralLookup.find(router, input, segStart, segLen, supportsLong, state);

        boolean keepMixedParams = literalEdge == -1;
        int mixedCount = router.stateMixedCount[state];
        int mixedStart = router.stateMixedStart[state];
        int mixedEnd = mixedStart + mixedCount;
        int mixedMatchCount = 0;
        int mixedParamCount = 0;
        if (mixedCount > 0) {
            int firstByte = input.byteAt(segStart) & 0xFF;
            long prefixRange = EdgeDispatch.mixedPrefixRange(router, state, firstByte);
            int rangeStart = EdgeDispatch.rangeStart(prefixRange);
            int rangeCount = EdgeDispatch.rangeCount(prefixRange);
            if (rangeCount > 0) {
                int end = rangeStart + rangeCount;
                for (int i = rangeStart; i < end; i++) {
                    if (router.edgeLabelLen[i] == 0) {
                        continue;
                    }
                    if (MixedLookup.match(router, i, input, segStart, segLen, supportsLong, out)) {
                        if (mixedMatchCount >= scratchEdges.length) {
                            throw new IllegalStateException("MatchResult stack exhausted");
                        }
                        scratchEdges[mixedMatchCount++] = i;
                        if (mixedMatchCount == 1 && keepMixedParams) {
                            mixedParamCount = out.paramCount() - mark;
                            if (mixedParamCount > 0) {
                                System.arraycopy(keyIds, mark, scratchKeyIds, 0, mixedParamCount);
                                System.arraycopy(starts, mark, scratchStarts, 0, mixedParamCount);
                                System.arraycopy(lens, mark, scratchLens, 0, mixedParamCount);
                            }
                        }
                        out.rollbackTo(mark);
                    }
                }
            }
            if (mixedCount > router.stateMixedPrefixCount[state]) {
                for (int i = mixedStart; i < mixedEnd; i++) {
                    if (router.edgeLabelLen[i] != 0) {
                        continue;
                    }
                    if (MixedLookup.match(router, i, input, segStart, segLen, supportsLong, out)) {
                        if (mixedMatchCount >= scratchEdges.length) {
                            throw new IllegalStateException("MatchResult stack exhausted");
                        }
                        scratchEdges[mixedMatchCount++] = i;
                        if (mixedMatchCount == 1 && keepMixedParams) {
                            mixedParamCount = out.paramCount() - mark;
                            if (mixedParamCount > 0) {
                                System.arraycopy(keyIds, mark, scratchKeyIds, 0, mixedParamCount);
                                System.arraycopy(starts, mark, scratchStarts, 0, mixedParamCount);
                                System.arraycopy(lens, mark, scratchLens, 0, mixedParamCount);
                            }
                        }
                        out.rollbackTo(mark);
                    }
                }
            }
        }

        int mixedFirst = mixedMatchCount > 0 ? scratchEdges[0] : -1;
        int paramNext = router.stateParamNext[state];
        int wildIndex = router.stateWildIndex[state];
        int catchNext = router.stateCatchAllNext[state];

        byte kind = -1;
        int edgeIndex = -1;
        if (literalEdge != -1) {
            kind = kindLiteral;
            edgeIndex = literalEdge;
        } else if (mixedFirst != -1) {
            kind = kindMixed;
            edgeIndex = mixedFirst;
        } else if (paramNext != -1) {
            kind = kindParam;
        } else if (wildIndex != -1) {
            kind = kindWild;
            edgeIndex = wildIndex;
        } else if (catchNext != -1) {
            kind = kindCatch;
        }

        if (kind == -1) {
            return NO_CANDIDATE;
        }

        if (catchNext != -1 && kind != kindCatch) {
            push(kindCatch, state, -1, segStart, segLen, nextIdx, mark);
        }
        if (wildIndex != -1 && kind != kindWild) {
            push(kindWild, state, wildIndex, segStart, segLen, nextIdx, mark);
        }
        if (paramNext != -1 && kind != kindParam) {
            push(kindParam, state, -1, segStart, segLen, nextIdx, mark);
        }
        if (mixedMatchCount > 0 && (kind != kindMixed || mixedMatchCount > 1)) {
            for (int i = mixedMatchCount - 1; i >= 0; i--) {
                if (kind == kindMixed && i == 0) {
                    continue;
                }
                push(kindMixed, state, scratchEdges[i], segStart, segLen, nextIdx, mark);
            }
        }

        if (kind == kindMixed) {
            out.rollbackTo(mark);
            if (keepMixedParams) {
                if (mixedParamCount > 0) {
                    System.arraycopy(scratchKeyIds, 0, keyIds, mark, mixedParamCount);
                    System.arraycopy(scratchStarts, 0, starts, mark, mixedParamCount);
                    System.arraycopy(scratchLens, 0, lens, mark, mixedParamCount);
                }
                MatchResultAccess.paramCount(out, mark + mixedParamCount);
            }
        } else {
            out.rollbackTo(mark);
        }

        return pack(kind, edgeIndex);
    }

    boolean popInto(Frame out) {
        if (stackSize == 0) {
            return false;
        }
        int idx = --stackSize;
        out.state = stackState[idx];
        out.segStart = stackSegStart[idx];
        out.segLen = stackSegLen[idx];
        out.nextIdx = stackNextIdx[idx];
        out.paramMark = stackParamMark[idx];
        out.edgeIndex = stackEdgeIndex[idx];
        out.kind = stackKind[idx];
        return true;
    }

    private void push(byte kind,
                      int state,
                      int edgeIndex,
                      int segStart,
                      int segLen,
                      int nextIdx,
                      int paramMark) {
        if (stackSize >= stackState.length) {
            throw new IllegalStateException("MatchResult stack exhausted");
        }
        stackState[stackSize] = state;
        stackSegStart[stackSize] = segStart;
        stackSegLen[stackSize] = segLen;
        stackNextIdx[stackSize] = nextIdx;
        stackParamMark[stackSize] = paramMark;
        stackEdgeIndex[stackSize] = edgeIndex;
        stackKind[stackSize] = kind;
        stackSize++;
    }

    private static long pack(byte kind, int edgeIndex) {
        return ((long) kind << 32) | (edgeIndex & 0xffffffffL);
    }

    static byte kind(long packed) {
        return (byte) (packed >>> 32);
    }

    static int edgeIndex(long packed) {
        return (int) packed;
    }

    static final class Frame {
        int state;
        int segStart;
        int segLen;
        int nextIdx;
        int paramMark;
        int edgeIndex;
        byte kind;
    }
}
