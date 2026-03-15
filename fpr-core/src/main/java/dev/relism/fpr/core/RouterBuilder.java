package dev.relism.fpr.core;

import dev.relism.fpr.core.internal.compile.RouteCompiler;
import lombok.Getter;
import lombok.experimental.Accessors;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder for creating and compiling immutable routers.
 */
public final class RouterBuilder<H> {
    private final List<RouteSpec<H>> routes = new ArrayList<>();
    private final LinkedHashMap<String, Integer> paramIds = new LinkedHashMap<>();
    private final List<String> paramNames = new ArrayList<>();
    private final LinkedHashMap<String, Integer> labelIds = new LinkedHashMap<>();

    public RouterBuilder<H> add(RoutePattern pattern, H handler) {
        return add(0, pattern, handler);
    }

    public RouterBuilder<H> add(String label, RoutePattern pattern, H handler) {
        return add(labelId(label), pattern, handler);
    }

    public RouterBuilder<H> add(Enum<?> label, RoutePattern pattern, H handler) {
        return add(labelId(label), pattern, handler);
    }

    public RouterBuilder<H> add(String[] labels, RoutePattern pattern, H handler) {
        if (labels == null || labels.length == 0) {
            return add(0, pattern, handler);
        }
        for (String label : labels) {
            add(labelId(label), pattern, handler);
        }
        return this;
    }

    public RouterBuilder<H> add(Enum<?>[] labels, RoutePattern pattern, H handler) {
        if (labels == null || labels.length == 0) {
            return add(0, pattern, handler);
        }
        for (Enum<?> label : labels) {
            add(labelId(label), pattern, handler);
        }
        return this;
    }

    public RouterBuilder<H> add(int labelId, RoutePattern pattern, H handler) {
        if (pattern == null) {
            throw new IllegalArgumentException("pattern must not be null");
        }
        if (labelId < 0) {
            throw new IllegalArgumentException("labelId must be >= 0");
        }
        registerParams(pattern);
        routes.add(new RouteSpec<>(labelId, pattern, handler, routes.size()));
        return this;
    }

    public int maxParamCount() {
        return paramIds.size();
    }

    /**
     * Returns param names by key id order for optional zero-copy extraction.
     * Cache the returned array for reuse; it is stable after route registration.
     */
    public String[] paramNames() {
        return paramNames.toArray(new String[0]);
    }

    public int labelId(String label) {
        if (label == null || label.isEmpty()) {
            return 0;
        }
        Integer existing = labelIds.get(label);
        if (existing != null) {
            return existing;
        }
        int id = labelIds.size() + 1;
        labelIds.put(label, id);
        return id;
    }

    public int labelId(Enum<?> label) {
        if (label == null) {
            return 0;
        }
        String key = label.getDeclaringClass().getName() + "#" + label.name();
        return labelId(key);
    }

    public FastPathRouter<ByteView, H> compile() {
        return RouteCompiler.compile(routes, paramIds);
    }

    private void registerParams(RoutePattern pattern) {
        for (RoutePattern.Segment segment : pattern.segments()) {
            if (segment instanceof RoutePattern.Param) {
                paramId(((RoutePattern.Param) segment).name());
            } else if (segment instanceof RoutePattern.CatchAll) {
                String name = ((RoutePattern.CatchAll) segment).name();
                if (name != null && !name.isEmpty()) {
                    paramId(name);
                }
            } else if (segment instanceof RoutePattern.Mixed) {
                for (String name : ((RoutePattern.Mixed) segment).params()) {
                    paramId(name);
                }
            }
        }
    }

    private int paramId(String name) {
        Integer existing = paramIds.get(name);
        if (existing != null) {
            return existing;
        }
        int id = paramIds.size();
        paramIds.put(name, id);
        paramNames.add(name);
        return id;
    }

    @Getter
    @Accessors(fluent = true)
    public static final class RouteSpec<H> {
        private final int labelId;
        private final RoutePattern pattern;
        private final H handler;
        private final int order;

        public RouteSpec(int labelId, RoutePattern pattern, H handler, int order) {
            this.labelId = labelId;
            this.pattern = pattern;
            this.handler = handler;
            this.order = order;
        }
    }
}
