package dev.relism.fpr.core.internal.compile;

import dev.relism.fpr.core.ByteView;
import dev.relism.fpr.core.FastPathRouter;
import dev.relism.fpr.core.internal.runtime.FrozenRouter;
import dev.relism.fpr.core.RouterBuilder;

import java.util.List;
import java.util.Map;

public final class RouteCompiler {
    private RouteCompiler() {
    }

    public static <H> FastPathRouter<ByteView, H> compile(List<RouterBuilder.RouteSpec<H>> routes,
                                                         Map<String, Integer> paramIds) {
        if (routes.isEmpty()) {
            return emptyRouter();
        }
        RouteGraph<H> graph = RouteGraph.build(routes, paramIds).canonicalize();
        return FreezeWriter.freeze(graph);
    }

    private static <H> FastPathRouter<ByteView, H> emptyRouter() {
        return new FrozenRouter<>(
                new byte[0],
                (H[]) new Object[0],
                new int[]{0},     // stateFirstEdge
                new short[]{0},   // stateEdgeCount
                new int[]{0},     // stateLiteralStart
                new short[]{0},   // stateLiteralCount
                new byte[]{0},    // stateLiteralStrategy
                new int[]{-1},    // stateLiteralHashOff
                new int[]{-1},    // stateLiteralHashMask
                new int[]{0},     // stateMixedStart
                new short[]{0},   // stateMixedCount
                new short[]{0},   // stateMixedPrefixCount
                new int[]{-1},    // stateWildIndex
                new int[]{-1},    // stateParamNext
                new short[]{-1},  // stateParamKeyId
                new int[]{-1},    // stateCatchAllNext
                new short[]{-1},  // stateCatchAllKeyId
                new int[]{0},     // stateAcceptFirst
                new short[]{0},   // stateAcceptCount
                new int[]{-1},    // stateLiteralIndexOff
                new int[]{-1},    // stateMixedIndexOff
                new int[0],       // edgeNextState
                new int[0],       // edgeLabelOff
                new short[0],     // edgeLabelLen
                new long[0],      // edgeLiteralPrefix
                new byte[0],      // edgeKind
                new int[0],       // edgeMixedChunkOff
                new short[0],     // edgeMixedChunkCount
                new int[0],       // edgeMixedParamOff
                new short[0],     // edgeMixedParamCount
                new byte[0],      // edgeMixedStrategy
                new int[0],       // mixedChunkOff
                new short[0],     // mixedChunkLen
                new short[0],     // mixedParamKeyId
                new int[0],       // acceptHandlerId
                new int[0],       // acceptLabelId
                new int[0],       // acceptRouteId
                new int[0],       // indexStart
                new short[0],     // indexCount
                new int[0],       // indexSecondOff
                new long[0],      // literalHashKey
                new int[0]        // literalHashEdge
        );
    }
}
