package dev.relism.fpr.core.internal.compile;

import dev.relism.fpr.core.ByteView;
import dev.relism.fpr.core.FastPathRouter;
import dev.relism.fpr.core.internal.compile.lookup.LiteralLookupPlanBuilder;
import dev.relism.fpr.core.internal.compile.lookup.MixedLookupPlanBuilder;
import dev.relism.fpr.core.internal.runtime.EdgeKind;
import dev.relism.fpr.core.internal.runtime.FrozenRouter;
import dev.relism.fpr.core.internal.runtime.lookup.LiteralLookupStrategy;

import java.util.Arrays;
import java.util.List;

final class FreezeWriter {
    private static final int INDEX_THRESHOLD = 12;

    private FreezeWriter() {
    }

    static <H> FastPathRouter<ByteView, H> freeze(RouteGraph<H> graph) {
        List<RouteGraph.Node<H>> nodes = graph.nodes;
        List<H> handlers = graph.handlers;

        int stateCount = nodes.size();
        int totalEdges = 0;
        int totalAccepts = 0;
        for (RouteGraph.Node<H> node : nodes) {
            totalEdges += node.edges.size();
            totalAccepts += node.accepts.size();
        }

        PrimitiveBuilders.ByteBlobBuilder blob = new PrimitiveBuilders.ByteBlobBuilder(1024);
        PrimitiveBuilders.IntArrayList edgeNextState = new PrimitiveBuilders.IntArrayList(totalEdges);
        PrimitiveBuilders.IntArrayList edgeLabelOff = new PrimitiveBuilders.IntArrayList(totalEdges);
        PrimitiveBuilders.ShortArrayList edgeLabelLen = new PrimitiveBuilders.ShortArrayList(totalEdges);
        PrimitiveBuilders.LongArrayList edgeLiteralPrefix = new PrimitiveBuilders.LongArrayList(totalEdges);
        PrimitiveBuilders.ByteArrayList edgeKind = new PrimitiveBuilders.ByteArrayList(totalEdges);
        PrimitiveBuilders.IntArrayList edgeMixedChunkOff = new PrimitiveBuilders.IntArrayList(totalEdges);
        PrimitiveBuilders.ShortArrayList edgeMixedChunkCount = new PrimitiveBuilders.ShortArrayList(totalEdges);
        PrimitiveBuilders.IntArrayList edgeMixedParamOff = new PrimitiveBuilders.IntArrayList(totalEdges);
        PrimitiveBuilders.ShortArrayList edgeMixedParamCount = new PrimitiveBuilders.ShortArrayList(totalEdges);
        PrimitiveBuilders.ByteArrayList edgeMixedStrategy = new PrimitiveBuilders.ByteArrayList(totalEdges);

        PrimitiveBuilders.IntArrayList mixedChunkOff = new PrimitiveBuilders.IntArrayList(totalEdges * 2);
        PrimitiveBuilders.ShortArrayList mixedChunkLen = new PrimitiveBuilders.ShortArrayList(totalEdges * 2);
        PrimitiveBuilders.ShortArrayList mixedParamKeyId = new PrimitiveBuilders.ShortArrayList(totalEdges * 2);

        PrimitiveBuilders.IntArrayList acceptHandlerId = new PrimitiveBuilders.IntArrayList(totalAccepts);
        PrimitiveBuilders.IntArrayList acceptLabelId = new PrimitiveBuilders.IntArrayList(totalAccepts);
        PrimitiveBuilders.IntArrayList acceptRouteId = new PrimitiveBuilders.IntArrayList(totalAccepts);

        PrimitiveBuilders.IntArrayList indexStart = new PrimitiveBuilders.IntArrayList(stateCount * 4);
        PrimitiveBuilders.ShortArrayList indexCount = new PrimitiveBuilders.ShortArrayList(stateCount * 4);
        PrimitiveBuilders.IntArrayList indexSecondOff = new PrimitiveBuilders.IntArrayList(stateCount * 4);
        PrimitiveBuilders.LongArrayList literalHashKey = new PrimitiveBuilders.LongArrayList(stateCount * 8);
        PrimitiveBuilders.IntArrayList literalHashEdge = new PrimitiveBuilders.IntArrayList(stateCount * 8);

        int[] stateFirstEdge = new int[stateCount];
        short[] stateEdgeCount = new short[stateCount];
        int[] stateLiteralStart = new int[stateCount];
        short[] stateLiteralCount = new short[stateCount];
        byte[] stateLiteralStrategy = new byte[stateCount];
        int[] stateLiteralHashOff = new int[stateCount];
        int[] stateLiteralHashMask = new int[stateCount];
        int[] stateMixedStart = new int[stateCount];
        short[] stateMixedCount = new short[stateCount];
        short[] stateMixedPrefixCount = new short[stateCount];
        int[] stateWildIndex = new int[stateCount];
        int[] stateParamNext = new int[stateCount];
        short[] stateParamKeyId = new short[stateCount];
        int[] stateCatchAllNext = new int[stateCount];
        short[] stateCatchAllKeyId = new short[stateCount];
        int[] stateAcceptFirst = new int[stateCount];
        short[] stateAcceptCount = new short[stateCount];
        int[] stateLiteralIndexOff = new int[stateCount];
        int[] stateMixedIndexOff = new int[stateCount];

        Arrays.fill(stateWildIndex, -1);
        Arrays.fill(stateParamNext, -1);
        Arrays.fill(stateCatchAllNext, -1);
        Arrays.fill(stateParamKeyId, (short) -1);
        Arrays.fill(stateCatchAllKeyId, (short) -1);
        Arrays.fill(stateLiteralIndexOff, -1);
        Arrays.fill(stateMixedIndexOff, -1);
        Arrays.fill(stateLiteralHashOff, -1);
        Arrays.fill(stateLiteralHashMask, -1);
        Arrays.fill(stateLiteralStrategy, LiteralLookupStrategy.LINEAR);

        for (int s = 0; s < stateCount; s++) {
            RouteGraph.Node<H> node = nodes.get(s);
            stateFirstEdge[s] = edgeNextState.size();

            List<RouteGraph.Edge> literals = node.literalEdges();
            List<RouteGraph.Edge> mixed = node.mixedEdges();
            RouteGraph.Edge wild = node.wildEdge();

            literals.sort(RouteGraph.Edge.literalComparator());
            mixed.sort(RouteGraph.Edge.mixedComparator());

            int literalStart = edgeNextState.size();
            for (RouteGraph.Edge edge : literals) {
                int off = blob.append(edge.literal);
                edgeNextState.add(edge.nextState);
                edgeLabelOff.add(off);
                edgeLabelLen.add((short) edge.literal.length);
                edgeLiteralPrefix.add(LiteralLookupPlanBuilder.prefixKey(edge.literal));
                edgeKind.add((byte) edge.kind.ordinal());
                edgeMixedChunkOff.add(-1);
                edgeMixedChunkCount.add((short) 0);
                edgeMixedParamOff.add(-1);
                edgeMixedParamCount.add((short) 0);
                edgeMixedStrategy.add((byte) 0);
            }
            int literalCount = literals.size();

            int mixedStart = edgeNextState.size();
            int mixedPrefixCount = 0;
            for (RouteGraph.Edge edge : mixed) {
                int chunkBase = mixedChunkOff.size();
                for (byte[] chunk : edge.mixed.literals) {
                    int off = blob.append(chunk);
                    mixedChunkOff.add(off);
                    mixedChunkLen.add((short) chunk.length);
                }
                int paramBase = mixedParamKeyId.size();
                for (short key : edge.mixed.paramKeys) {
                    mixedParamKeyId.add(key);
                }
                int firstOff = mixedChunkOff.get(chunkBase);
                short firstLen = mixedChunkLen.get(chunkBase);

                edgeNextState.add(edge.nextState);
                edgeLabelOff.add(firstOff);
                edgeLabelLen.add(firstLen);
                edgeLiteralPrefix.add(0L);
                edgeKind.add((byte) edge.kind.ordinal());
                edgeMixedChunkOff.add(chunkBase);
                edgeMixedChunkCount.add((short) edge.mixed.literals.length);
                edgeMixedParamOff.add(paramBase);
                edgeMixedParamCount.add((short) edge.mixed.paramKeys.length);
                edgeMixedStrategy.add(MixedLookupPlanBuilder.strategyForParamCount(edge.mixed.paramKeys.length));

                if (firstLen > 0) {
                    mixedPrefixCount++;
                }
            }
            int mixedCount = mixed.size();

            if (wild != null) {
                stateWildIndex[s] = edgeNextState.size();
                edgeNextState.add(wild.nextState);
                edgeLabelOff.add(0);
                edgeLabelLen.add((short) 0);
                edgeLiteralPrefix.add(0L);
                edgeKind.add((byte) EdgeKind.WILD.ordinal());
                edgeMixedChunkOff.add(-1);
                edgeMixedChunkCount.add((short) 0);
                edgeMixedParamOff.add(-1);
                edgeMixedParamCount.add((short) 0);
                edgeMixedStrategy.add((byte) 0);
            }

            int edgeCount = edgeNextState.size() - stateFirstEdge[s];
            stateEdgeCount[s] = (short) edgeCount;
            stateLiteralStart[s] = literalStart;
            stateLiteralCount[s] = (short) literalCount;
            stateMixedStart[s] = mixedStart;
            stateMixedCount[s] = (short) mixedCount;
            stateMixedPrefixCount[s] = (short) mixedPrefixCount;

            if (literalCount > 0) {
                byte literalStrategy = LiteralLookupPlanBuilder.selectStrategy(literalCount);
                stateLiteralStrategy[s] = literalStrategy;
                if (literalStrategy == LiteralLookupStrategy.HASH) {
                    LiteralLookupPlanBuilder.HashPlan plan = LiteralLookupPlanBuilder.buildHash(
                            literalHashKey,
                            literalHashEdge,
                            edgeLiteralPrefix,
                            edgeLabelLen,
                            literalStart,
                            literalCount
                    );
                    stateLiteralHashOff[s] = plan.offset;
                    stateLiteralHashMask[s] = plan.mask;
                }
            }
            if (mixedPrefixCount > 0 && mixedCount >= INDEX_THRESHOLD) {
                stateMixedIndexOff[s] = IndexBuilder.buildIndex(indexStart, indexCount, indexSecondOff, edgeLabelOff, edgeLabelLen,
                        blob, mixedStart, mixedCount, false);
            }

            stateParamNext[s] = node.paramNext;
            stateParamKeyId[s] = node.paramKeyId;
            stateCatchAllNext[s] = node.catchAllNext;
            stateCatchAllKeyId[s] = node.catchAllKeyId;

            stateAcceptFirst[s] = acceptHandlerId.size();
            for (RouteGraph.Accept accept : node.accepts) {
                acceptHandlerId.add(accept.handlerId);
                acceptLabelId.add(accept.labelId);
                acceptRouteId.add(accept.routeId);
            }
            stateAcceptCount[s] = (short) node.accepts.size();
        }

        H[] handlerArray = (H[]) handlers.toArray(new Object[0]);

        return new FrozenRouter<>(
                blob.toArray(),
                handlerArray,
                stateFirstEdge,
                stateEdgeCount,
                stateLiteralStart,
                stateLiteralCount,
                stateLiteralStrategy,
                stateLiteralHashOff,
                stateLiteralHashMask,
                stateMixedStart,
                stateMixedCount,
                stateMixedPrefixCount,
                stateWildIndex,
                stateParamNext,
                stateParamKeyId,
                stateCatchAllNext,
                stateCatchAllKeyId,
                stateAcceptFirst,
                stateAcceptCount,
                stateLiteralIndexOff,
                stateMixedIndexOff,
                edgeNextState.toArray(),
                edgeLabelOff.toArray(),
                edgeLabelLen.toArray(),
                edgeLiteralPrefix.toArray(),
                edgeKind.toArray(),
                edgeMixedChunkOff.toArray(),
                edgeMixedChunkCount.toArray(),
                edgeMixedParamOff.toArray(),
                edgeMixedParamCount.toArray(),
                edgeMixedStrategy.toArray(),
                mixedChunkOff.toArray(),
                mixedChunkLen.toArray(),
                mixedParamKeyId.toArray(),
                acceptHandlerId.toArray(),
                acceptLabelId.toArray(),
                acceptRouteId.toArray(),
                indexStart.toArray(),
                indexCount.toArray(),
                indexSecondOff.toArray(),
                literalHashKey.toArray(),
                literalHashEdge.toArray()
        );
    }

}
