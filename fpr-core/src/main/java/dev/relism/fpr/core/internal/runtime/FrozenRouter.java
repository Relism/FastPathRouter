package dev.relism.fpr.core.internal.runtime;

import dev.relism.fpr.core.ByteView;
import dev.relism.fpr.core.FastPathRouter;
import dev.relism.fpr.core.MatchResult;
import dev.relism.fpr.core.internal.runtime.lookup.MixedLookup;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class FrozenRouter<H> implements FastPathRouter<ByteView, H> {
    public final byte[] blob;
    public final H[] handlers;

    public final int[] stateFirstEdge;
    public final short[] stateEdgeCount;
    public final int[] stateLiteralStart;
    public final short[] stateLiteralCount;
    public final byte[] stateLiteralStrategy;
    public final int[] stateLiteralHashOff;
    public final int[] stateLiteralHashMask;
    public final int[] stateMixedStart;
    public final short[] stateMixedCount;
    public final short[] stateMixedPrefixCount;
    public final int[] stateWildIndex;

    public final int[] stateParamNext;
    public final short[] stateParamKeyId;
    public final int[] stateCatchAllNext;
    public final short[] stateCatchAllKeyId;

    public final int[] stateAcceptFirst;
    public final short[] stateAcceptCount;

    public final int[] stateLiteralIndexOff;
    public final int[] stateMixedIndexOff;

    public final int[] edgeNextState;
    public final int[] edgeLabelOff;
    public final short[] edgeLabelLen;
    public final long[] edgeLiteralPrefix;
    public final byte[] edgeKind;
    public final int[] edgeMixedChunkOff;
    public final short[] edgeMixedChunkCount;
    public final int[] edgeMixedParamOff;
    public final short[] edgeMixedParamCount;
    public final byte[] edgeMixedStrategy;

    public final int[] mixedChunkOff;
    public final short[] mixedChunkLen;
    public final short[] mixedParamKeyId;

    public final int[] acceptHandlerId;
    public final int[] acceptLabelId;
    public final int[] acceptRouteId;

    public final int[] indexStart;
    public final short[] indexCount;
    public final int[] indexSecondOff;
    public final long[] literalHashKey;
    public final int[] literalHashEdge;

    /**
     * Matches without allocations, using the provided reusable MatchResult buffer.
     */
    @Override
    public int match(ByteView input, MatchResult<H> out) {
        RouteSearch search = new RouteSearch();
        SegmentCursor cursor = new SegmentCursor();
        out.reset();
        boolean supportsLong = input.supportsLong();
        cursor.reset(input);
        search.reset(out);
        RouteSearch.Frame frame = search.frame();

        int len = cursor.length();
        int state = 0;
        boolean hasSegment = cursor.advance();

        final byte kindLiteral = (byte) EdgeKind.LITERAL.ordinal();
        final byte kindMixed = (byte) EdgeKind.MIXED.ordinal();
        final byte kindParam = (byte) EdgeKind.PARAM.ordinal();
        final byte kindWild = (byte) EdgeKind.WILD.ordinal();
        final byte kindCatch = (byte) EdgeKind.CATCH.ordinal();

        while (true) {
            if (!hasSegment) {
                int accept = accept(state, out);
                if (accept != NO_MATCH) {
                    return accept;
                }
                if (stateCatchAllNext[state] != -1) {
                    short key = stateCatchAllKeyId[state];
                    if (key >= 0) {
                        out.addParam(key, len, 0);
                    }
                    return accept(stateCatchAllNext[state], out);
                }
                long backtracked = backtrack(frame, input, supportsLong, out,
                        kindLiteral, kindMixed, kindParam, kindWild, kindCatch, len, search, cursor);
                if (backtracked == BACKTRACK_NO_MATCH) {
                    return NO_MATCH;
                }
                if ((backtracked & BACKTRACK_ACCEPT_MASK) != 0) {
                    return (int) backtracked;
                }
                state = (int) backtracked;
                hasSegment = cursor.advance();
                continue;
            }

            int segStart = cursor.segStart();
            int segLen = cursor.segLen();

            if (segLen <= 0) {
                long backtracked = backtrack(frame, input, supportsLong, out,
                        kindLiteral, kindMixed, kindParam, kindWild, kindCatch, len, search, cursor);
                if (backtracked == BACKTRACK_NO_MATCH) {
                    return NO_MATCH;
                }
                if ((backtracked & BACKTRACK_ACCEPT_MASK) != 0) {
                    return (int) backtracked;
                }
                state = (int) backtracked;
                hasSegment = cursor.advance();
                continue;
            }

            long candidate = search.selectCandidate(this, state, input, cursor, supportsLong, out);
            if (candidate == RouteSearch.NO_CANDIDATE) {
                long backtracked = backtrack(frame, input, supportsLong, out,
                        kindLiteral, kindMixed, kindParam, kindWild, kindCatch, len, search, cursor);
                if (backtracked == BACKTRACK_NO_MATCH) {
                    return NO_MATCH;
                }
                if ((backtracked & BACKTRACK_ACCEPT_MASK) != 0) {
                    return (int) backtracked;
                }
                state = (int) backtracked;
                hasSegment = cursor.advance();
                continue;
            }

            byte kind = RouteSearch.kind(candidate);
            int edgeIndex = RouteSearch.edgeIndex(candidate);

            if (kind == kindLiteral) {
                state = edgeNextState[edgeIndex];
            } else if (kind == kindMixed) {
                state = edgeNextState[edgeIndex];
            } else if (kind == kindParam) {
                out.addParam(stateParamKeyId[state], segStart, segLen);
                state = stateParamNext[state];
            } else if (kind == kindWild) {
                state = edgeNextState[edgeIndex];
            } else if (kind == kindCatch) {
                short key = stateCatchAllKeyId[state];
                if (key >= 0) {
                    out.addParam(key, segStart, len - segStart);
                }
                return accept(stateCatchAllNext[state], out);
            } else {
                state = NO_MATCH;
            }

            if (state == NO_MATCH) {
                long backtracked = backtrack(frame, input, supportsLong, out,
                        kindLiteral, kindMixed, kindParam, kindWild, kindCatch, len, search, cursor);
                if (backtracked == BACKTRACK_NO_MATCH) {
                    return NO_MATCH;
                }
                if ((backtracked & BACKTRACK_ACCEPT_MASK) != 0) {
                    return (int) backtracked;
                }
                state = (int) backtracked;
                hasSegment = cursor.advance();
                continue;
            }

            hasSegment = cursor.advance();
        }
    }

    private long backtrack(RouteSearch.Frame frame,
                           ByteView input,
                           boolean supportsLong,
                           MatchResult<H> out,
                           byte kindLiteral,
                           byte kindMixed,
                           byte kindParam,
                           byte kindWild,
                           byte kindCatch,
                           int len,
                           RouteSearch search,
                           SegmentCursor cursor) {
        while (search.popInto(frame)) {
            out.rollbackTo(frame.paramMark);
            cursor.restore(frame.segStart, frame.segLen, frame.nextIdx);
            if (frame.kind == kindCatch) {
                int catchNext = stateCatchAllNext[frame.state];
                if (catchNext == -1) {
                    continue;
                }
                short key = stateCatchAllKeyId[frame.state];
                if (key >= 0) {
                    out.addParam(key, frame.segStart, len - frame.segStart);
                }
                return BACKTRACK_ACCEPT_MASK | (accept(catchNext, out) & 0xffffffffL);
            }
            int next;
            if (frame.kind == kindLiteral) {
                next = edgeNextState[frame.edgeIndex];
            } else if (frame.kind == kindMixed) {
                if (!MixedLookup.match(this, frame.edgeIndex, input, frame.segStart, frame.segLen, supportsLong, out)) {
                    continue;
                }
                next = edgeNextState[frame.edgeIndex];
            } else if (frame.kind == kindParam) {
                out.addParam(stateParamKeyId[frame.state], frame.segStart, frame.segLen);
                next = stateParamNext[frame.state];
            } else if (frame.kind == kindWild) {
                next = edgeNextState[frame.edgeIndex];
            } else {
                continue;
            }
            if (next >= 0) {
                return next;
            }
        }
        return BACKTRACK_NO_MATCH;
    }

    int accept(int state, MatchResult<H> out) {
        int labelId = out.labelId();
        int start = stateAcceptFirst[state];
        int count = stateAcceptCount[state];
        for (int i = 0; i < count; i++) {
            int idx = start + i;
            int acceptLabel = acceptLabelId[idx];
            if (acceptLabel == 0 || acceptLabel == labelId) {
                out.setHandler(handlers[acceptHandlerId[idx]]);
                return acceptRouteId[idx];
            }
        }
        return NO_MATCH;
    }

    private static final long BACKTRACK_ACCEPT_MASK = 0x4000000000000000L;
    private static final long BACKTRACK_NO_MATCH = -1L;
    private static final int NO_MATCH = FastPathRouter.NO_MATCH;
}
