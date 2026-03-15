package dev.relism.fpr.core;

import java.util.Arrays;
import java.util.List;

/**
 * Immutable route pattern built from segment tokens.
 */
public final class RoutePattern {
    private final List<Segment> segments;

    RoutePattern(List<Segment> segments) {
        this.segments = segments;
    }

    public List<Segment> segments() {
        return segments;
    }

    public static RoutePattern of(Segment... segments) {
        return new RoutePattern(Arrays.asList(segments));
    }

    public static RoutePattern fromSegments(List<Segment> segments) {
        return new RoutePattern(segments);
    }

    public static Literal literal(String text) {
        return new Literal(text);
    }

    public static Param param(String name) {
        return new Param(name);
    }

    public static Wildcard wildcard() {
        return new Wildcard();
    }

    public static CatchAll catchAll() {
        return new CatchAll(null);
    }

    public static CatchAll catchAll(String name) {
        return new CatchAll(name);
    }

    public static Mixed mixed(String[] literals, String[] params) {
        return new Mixed(literals, params);
    }

    public enum SegmentType {
        LITERAL,
        PARAM,
        WILDCARD,
        CATCH_ALL,
        MIXED
    }

    public interface Segment {
        SegmentType type();
    }

    public static final class Literal implements Segment {
        private final String text;

        public Literal(String text) {
            if (text == null || text.isEmpty()) {
                throw new IllegalArgumentException("literal must be non-empty");
            }
            this.text = text;
        }

        public String text() {
            return text;
        }

        @Override
        public SegmentType type() {
            return SegmentType.LITERAL;
        }
    }

    public static final class Param implements Segment {
        private final String name;

        public Param(String name) {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("param name must be non-empty");
            }
            this.name = name;
        }

        public String name() {
            return name;
        }

        @Override
        public SegmentType type() {
            return SegmentType.PARAM;
        }
    }

    public static final class Wildcard implements Segment {
        @Override
        public SegmentType type() {
            return SegmentType.WILDCARD;
        }
    }

    public static final class CatchAll implements Segment {
        private final String name;

        public CatchAll(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        @Override
        public SegmentType type() {
            return SegmentType.CATCH_ALL;
        }
    }

    public static final class Mixed implements Segment {
        private final String[] literals;
        private final String[] params;

        public Mixed(String[] literals, String[] params) {
            if (literals == null || params == null || literals.length != params.length + 1) {
                throw new IllegalArgumentException("mixed segment requires literals=paramCount+1");
            }
            this.literals = literals;
            this.params = params;
        }

        public String[] literals() {
            return literals;
        }

        public String[] params() {
            return params;
        }

        @Override
        public SegmentType type() {
            return SegmentType.MIXED;
        }
    }
}
