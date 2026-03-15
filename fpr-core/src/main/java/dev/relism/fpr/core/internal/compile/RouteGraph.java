package dev.relism.fpr.core.internal.compile;

import dev.relism.fpr.core.RoutePattern;
import dev.relism.fpr.core.RouterBuilder;
import dev.relism.fpr.core.internal.runtime.EdgeKind;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class RouteGraph<H> {
    final List<Node<H>> nodes;
    final List<H> handlers;

    private RouteGraph(List<Node<H>> nodes, List<H> handlers) {
        this.nodes = nodes;
        this.handlers = handlers;
    }

    static <H> RouteGraph<H> build(List<RouterBuilder.RouteSpec<H>> routes, Map<String, Integer> paramIds) {
        List<RouterBuilder.RouteSpec<H>> ordered = new ArrayList<>(routes);
        ordered.sort(routeComparator());

        List<Node<H>> nodes = new ArrayList<>();
        Node<H> root = new Node<>(0);
        nodes.add(root);

        List<H> handlers = new ArrayList<>();
        int routeId = 0;

        for (RouterBuilder.RouteSpec<H> spec : ordered) {
            int handlerId = handlers.size();
            handlers.add(spec.handler());
            Node<H> current = root;
            List<RoutePattern.Segment> segments = spec.pattern().segments();
            for (int i = 0; i < segments.size(); i++) {
                RoutePattern.Segment segment = segments.get(i);
                boolean last = i == segments.size() - 1;
                switch (segment.type()) {
                    case LITERAL:
                        current = current.literal(nodes, literalBytes((RoutePattern.Literal) segment));
                        break;
                    case MIXED:
                        current = current.mixed(nodes, mixedDef((RoutePattern.Mixed) segment, paramIds));
                        break;
                    case PARAM:
                        int paramId = paramId(((RoutePattern.Param) segment).name(), paramIds);
                        current = current.param(nodes, paramId);
                        break;
                    case WILDCARD:
                        current = current.wild(nodes);
                        break;
                    case CATCH_ALL:
                        if (!last) {
                            throw new IllegalArgumentException("catch-all must be last segment");
                        }
                        Integer catchId = catchId((RoutePattern.CatchAll) segment, paramIds);
                        current = current.catchAll(nodes, catchId);
                        break;
                    default:
                        throw new IllegalStateException("Unhandled segment type: " + segment.type());
                }
            }
            current.accept(spec.labelId(), handlerId, routeId);
            routeId++;
        }

        return new RouteGraph<>(nodes, handlers);
    }

    RouteGraph<H> canonicalize() {
        int size = nodes.size();
        int[] remap = new int[size];
        Arrays.fill(remap, -1);
        Map<NodeKey, Integer> canonical = new HashMap<>();
        List<Node<H>> canonNodes = new ArrayList<>();

        for (int i = size - 1; i >= 0; i--) {
            Node<H> node = nodes.get(i);
            NodeKey key = node.key(remap);
            Integer existing = canonical.get(key);
            if (existing != null) {
                remap[node.id] = existing;
            } else {
                int id = canonNodes.size();
                remap[node.id] = id;
                canonical.put(key, id);
                canonNodes.add(node);
            }
        }

        for (Node<H> node : canonNodes) {
            node.remap(remap);
        }
        int rootIndex = remap[0];
        if (rootIndex != 0) {
            int[] reorder = new int[canonNodes.size()];
            Arrays.fill(reorder, -1);
            reorder[rootIndex] = 0;
            int next = 1;
            for (int i = 0; i < canonNodes.size(); i++) {
                if (i == rootIndex) {
                    continue;
                }
                reorder[i] = next++;
            }
            List<Node<H>> reordered = new ArrayList<>(canonNodes.size());
            for (int i = 0; i < canonNodes.size(); i++) {
                reordered.add(null);
            }
            for (int i = 0; i < canonNodes.size(); i++) {
                reordered.set(reorder[i], canonNodes.get(i));
            }
            for (Node<H> node : reordered) {
                node.remap(reorder);
            }
            return new RouteGraph<>(reordered, handlers);
        }

        return new RouteGraph<>(canonNodes, handlers);
    }

    private static int paramId(String name, Map<String, Integer> paramIds) {
        Integer id = paramIds.get(name);
        if (id == null) {
            throw new IllegalArgumentException("Unknown param id: " + name);
        }
        return id;
    }

    private static Integer catchId(RoutePattern.CatchAll segment, Map<String, Integer> paramIds) {
        String name = segment.name();
        if (name == null || name.isEmpty()) {
            return null;
        }
        return paramId(name, paramIds);
    }

    private static byte[] literalBytes(RoutePattern.Literal literal) {
        return literal.text().getBytes(StandardCharsets.UTF_8);
    }

    private static MixedDef mixedDef(RoutePattern.Mixed mixed, Map<String, Integer> paramIds) {
        String[] literals = mixed.literals();
        String[] params = mixed.params();
        byte[][] literalBytes = new byte[literals.length][];
        for (int i = 0; i < literals.length; i++) {
            literalBytes[i] = literals[i].getBytes(StandardCharsets.UTF_8);
        }
        short[] keys = new short[params.length];
        for (int i = 0; i < params.length; i++) {
            keys[i] = (short) paramId(params[i], paramIds);
        }
        return new MixedDef(literalBytes, keys);
    }

    private static Comparator<RouterBuilder.RouteSpec<?>> routeComparator() {
        return (a, b) -> {
            List<RoutePattern.Segment> segA = a.pattern().segments();
            List<RoutePattern.Segment> segB = b.pattern().segments();
            int min = Math.min(segA.size(), segB.size());
            for (int i = 0; i < min; i++) {
                int rankA = rank(segA.get(i).type());
                int rankB = rank(segB.get(i).type());
                if (rankA != rankB) {
                    return Integer.compare(rankB, rankA);
                }
            }
            if (segA.size() != segB.size()) {
                return Integer.compare(segB.size(), segA.size());
            }
            return Integer.compare(a.order(), b.order());
        };
    }

    private static int rank(RoutePattern.SegmentType type) {
        switch (type) {
            case LITERAL:
                return 5;
            case MIXED:
                return 4;
            case PARAM:
                return 3;
            case WILDCARD:
                return 2;
            case CATCH_ALL:
                return 1;
            default:
                return 0;
        }
    }

    static final class MixedDef {
        final byte[][] literals;
        final short[] paramKeys;

        MixedDef(byte[][] literals, short[] paramKeys) {
            this.literals = literals;
            this.paramKeys = paramKeys;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof MixedDef)) {
                return false;
            }
            MixedDef other = (MixedDef) obj;
            return Arrays.deepEquals(literals, other.literals) && Arrays.equals(paramKeys, other.paramKeys);
        }

        @Override
        public int hashCode() {
            int h = Arrays.deepHashCode(literals);
            h = 31 * h + Arrays.hashCode(paramKeys);
            return h;
        }
    }

    static final class Accept {
        final int labelId;
        final int handlerId;
        final int routeId;

        Accept(int labelId, int handlerId, int routeId) {
            this.labelId = labelId;
            this.handlerId = handlerId;
            this.routeId = routeId;
        }
    }

    static final class Edge {
        final EdgeKind kind;
        final byte[] literal;
        final MixedDef mixed;
        int nextState;

        Edge(EdgeKind kind, byte[] literal, MixedDef mixed, int nextState) {
            this.kind = kind;
            this.literal = literal;
            this.mixed = mixed;
            this.nextState = nextState;
        }

        static Comparator<Edge> literalComparator() {
            return (a, b) -> {
                if (a.literal.length != b.literal.length) {
                    return Integer.compare(a.literal.length, b.literal.length);
                }
                long aKey = literalPrefix(a.literal);
                long bKey = literalPrefix(b.literal);
                int cmp = Long.compareUnsigned(aKey, bKey);
                if (cmp != 0) {
                    return cmp;
                }
                return Arrays.compare(a.literal, b.literal);
            };
        }

        static Comparator<Edge> mixedComparator() {
            return (a, b) -> {
                int aTotal = totalLiteralLen(a.mixed.literals);
                int bTotal = totalLiteralLen(b.mixed.literals);
                if (aTotal != bTotal) {
                    return Integer.compare(bTotal, aTotal);
                }

                byte[] aFirst = a.mixed.literals[0];
                byte[] bFirst = b.mixed.literals[0];
                int aLen = aFirst.length;
                int bLen = bFirst.length;
                if (aLen == 0 && bLen != 0) {
                    return 1;
                }
                if (aLen != 0 && bLen == 0) {
                    return -1;
                }
                if (aLen != bLen) {
                    return Integer.compare(bLen, aLen);
                }
                int cmp = Arrays.compare(aFirst, bFirst);
                if (cmp != 0) {
                    return cmp;
                }
                int count = Math.min(a.mixed.literals.length, b.mixed.literals.length);
                for (int i = 1; i < count; i++) {
                    cmp = Arrays.compare(a.mixed.literals[i], b.mixed.literals[i]);
                    if (cmp != 0) {
                        return cmp;
                    }
                }
                return Integer.compare(b.mixed.literals.length, a.mixed.literals.length);
            };
        }

        private static int totalLiteralLen(byte[][] literals) {
            int total = 0;
            for (byte[] literal : literals) {
                total += literal.length;
            }
            return total;
        }
    }

    private static long literalPrefix(byte[] literal) {
        int len = Math.min(8, literal.length);
        long key = 0;
        for (int i = 0; i < len; i++) {
            key |= ((long) literal[i] & 0xFFL) << (i * 8);
        }
        return key;
    }

    static final class Node<H> {
        final int id;
        final List<Edge> edges = new ArrayList<>();
        int paramNext = -1;
        short paramKeyId = -1;
        int catchAllNext = -1;
        short catchAllKeyId = -1;
        final List<Accept> accepts = new ArrayList<>();

        Node(int id) {
            this.id = id;
        }

        Node<H> literal(List<Node<H>> nodes, byte[] literal) {
            for (Edge edge : edges) {
                if (edge.kind == EdgeKind.LITERAL && Arrays.equals(edge.literal, literal)) {
                    return nodes.get(edge.nextState);
                }
            }
            Node<H> next = new Node<>(nodes.size());
            nodes.add(next);
            edges.add(new Edge(EdgeKind.LITERAL, literal, null, next.id));
            return next;
        }

        Node<H> mixed(List<Node<H>> nodes, MixedDef def) {
            for (Edge edge : edges) {
                if (edge.kind == EdgeKind.MIXED && mixedEquals(edge.mixed, def)) {
                    if (!Arrays.equals(edge.mixed.paramKeys, def.paramKeys)) {
                        throw new IllegalArgumentException("Ambiguous mixed segment: conflicting param keys");
                    }
                    return nodes.get(edge.nextState);
                }
            }
            Node<H> next = new Node<>(nodes.size());
            nodes.add(next);
            edges.add(new Edge(EdgeKind.MIXED, null, def, next.id));
            return next;
        }

        Node<H> param(List<Node<H>> nodes, int keyId) {
            if (paramNext != -1) {
                if (paramKeyId != (short) keyId) {
                    throw new IllegalArgumentException("Ambiguous param segment at state " + id);
                }
                return nodes.get(paramNext);
            }
            Node<H> next = new Node<>(nodes.size());
            nodes.add(next);
            paramNext = next.id;
            paramKeyId = (short) keyId;
            return next;
        }

        Node<H> wild(List<Node<H>> nodes) {
            for (Edge edge : edges) {
                if (edge.kind == EdgeKind.WILD) {
                    return nodes.get(edge.nextState);
                }
            }
            Node<H> next = new Node<>(nodes.size());
            nodes.add(next);
            edges.add(new Edge(EdgeKind.WILD, null, null, next.id));
            return next;
        }

        Node<H> catchAll(List<Node<H>> nodes, Integer keyId) {
            if (catchAllNext != -1) {
                short existing = catchAllKeyId;
                short incoming = keyId == null ? -1 : keyId.shortValue();
                if (existing != incoming) {
                    throw new IllegalArgumentException("Ambiguous catch-all segment at state " + id);
                }
                return nodes.get(catchAllNext);
            }
            Node<H> next = new Node<>(nodes.size());
            nodes.add(next);
            catchAllNext = next.id;
            catchAllKeyId = keyId == null ? (short) -1 : keyId.shortValue();
            return next;
        }

        void accept(int labelId, int handlerId, int routeId) {
            for (Accept accept : accepts) {
                if (accept.labelId == labelId) {
                    throw new IllegalArgumentException("Ambiguous route: duplicate pattern for label " + labelId);
                }
            }
            accepts.add(new Accept(labelId, handlerId, routeId));
        }

        List<Edge> literalEdges() {
            List<Edge> out = new ArrayList<>();
            for (Edge edge : edges) {
                if (edge.kind == EdgeKind.LITERAL) {
                    out.add(edge);
                }
            }
            return out;
        }

        List<Edge> mixedEdges() {
            List<Edge> out = new ArrayList<>();
            for (Edge edge : edges) {
                if (edge.kind == EdgeKind.MIXED) {
                    out.add(edge);
                }
            }
            return out;
        }

        Edge wildEdge() {
            for (Edge edge : edges) {
                if (edge.kind == EdgeKind.WILD) {
                    return edge;
                }
            }
            return null;
        }

        NodeKey key(int[] remap) {
            return new NodeKey(this, remap);
        }

        void remap(int[] remap) {
            for (Edge edge : edges) {
                edge.nextState = remap[edge.nextState];
            }
            if (paramNext != -1) {
                paramNext = remap[paramNext];
            }
            if (catchAllNext != -1) {
                catchAllNext = remap[catchAllNext];
            }
        }
    }

    private static final class NodeKey {
        private final int hash;
        private final EdgeKind[] edgeKinds;
        private final int[] edgeNext;
        private final byte[][] edgeLiterals;
        private final MixedDef[] edgeMixed;
        private final int paramNext;
        private final short paramKeyId;
        private final int catchNext;
        private final short catchKeyId;
        private final int[] acceptMethods;
        private final int[] acceptHandlers;
        private final int[] acceptRoutes;

        private NodeKey(Node<?> node, int[] remap) {
            this.paramNext = node.paramNext == -1 ? -1 : remap[node.paramNext];
            this.paramKeyId = node.paramKeyId;
            this.catchNext = node.catchAllNext == -1 ? -1 : remap[node.catchAllNext];
            this.catchKeyId = node.catchAllKeyId;

            int edgeCount = node.edges.size();
            this.edgeKinds = new EdgeKind[edgeCount];
            this.edgeNext = new int[edgeCount];
            this.edgeLiterals = new byte[edgeCount][];
            this.edgeMixed = new MixedDef[edgeCount];
            for (int i = 0; i < edgeCount; i++) {
                Edge edge = node.edges.get(i);
                edgeKinds[i] = edge.kind;
                edgeNext[i] = remap[edge.nextState];
                edgeLiterals[i] = edge.literal;
                edgeMixed[i] = edge.mixed;
            }

            int acceptCount = node.accepts.size();
            acceptMethods = new int[acceptCount];
            acceptHandlers = new int[acceptCount];
            acceptRoutes = new int[acceptCount];
            for (int i = 0; i < acceptCount; i++) {
                Accept accept = node.accepts.get(i);
                acceptMethods[i] = accept.labelId;
                acceptHandlers[i] = accept.handlerId;
                acceptRoutes[i] = accept.routeId;
            }

            this.hash = computeHash();
        }

        private int computeHash() {
            int h = 1;
            h = 31 * h + paramNext;
            h = 31 * h + paramKeyId;
            h = 31 * h + catchNext;
            h = 31 * h + catchKeyId;
            h = 31 * h + Arrays.hashCode(edgeKinds);
            h = 31 * h + Arrays.hashCode(edgeNext);
            h = 31 * h + Arrays.deepHashCode(edgeLiterals);
            h = 31 * h + Arrays.deepHashCode(edgeMixed);
            h = 31 * h + Arrays.hashCode(acceptMethods);
            h = 31 * h + Arrays.hashCode(acceptHandlers);
            h = 31 * h + Arrays.hashCode(acceptRoutes);
            return h;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof NodeKey)) {
                return false;
            }
            NodeKey other = (NodeKey) obj;
            if (paramNext != other.paramNext || paramKeyId != other.paramKeyId) {
                return false;
            }
            if (catchNext != other.catchNext || catchKeyId != other.catchKeyId) {
                return false;
            }
            if (!Arrays.equals(edgeKinds, other.edgeKinds) || !Arrays.equals(edgeNext, other.edgeNext)) {
                return false;
            }
            if (!Arrays.deepEquals(edgeLiterals, other.edgeLiterals)) {
                return false;
            }
            if (!Arrays.deepEquals(edgeMixed, other.edgeMixed)) {
                return false;
            }
            return Arrays.equals(acceptMethods, other.acceptMethods)
                    && Arrays.equals(acceptHandlers, other.acceptHandlers)
                    && Arrays.equals(acceptRoutes, other.acceptRoutes);
        }
    }

    private static boolean mixedEquals(MixedDef a, MixedDef b) {
        return Arrays.deepEquals(a.literals, b.literals);
    }
}
