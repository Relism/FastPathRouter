package dev.relism.fpr.core.dsl;

import dev.relism.fpr.core.RoutePattern;

import java.util.ArrayList;
import java.util.List;

/**
 * Cold-path string parser for building RoutePattern instances.
 */
public final class StringRouteParser {
    private StringRouteParser() {
    }

    public static RoutePattern parse(String path) {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        String trimmed = path.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("path must not be empty");
        }
        List<RoutePattern.Segment> segments = new ArrayList<>();
        int start = 0;
        int len = trimmed.length();
        if (trimmed.charAt(0) == '/') {
            start = 1;
        }
        while (true) {
            int slash = trimmed.indexOf('/', start);
            if (slash == -1) {
                slash = len;
            }
            if (slash > start) {
                segments.add(parseSegment(trimmed.substring(start, slash)));
            } else if (slash == len) {
                break;
            } else {
                throw new IllegalArgumentException("empty segment in path: " + path);
            }
            start = slash + 1;
            if (start > len) {
                break;
            }
        }
        return RoutePattern.fromSegments(segments);
    }

    private static RoutePattern.Segment parseSegment(String segment) {
        if (segment.equals("*")) {
            return RoutePattern.wildcard();
        }
        if (segment.equals("**")) {
            return RoutePattern.catchAll();
        }
        int len = segment.length();
        if (len >= 2 && segment.charAt(0) == '{' && segment.charAt(len - 1) == '}'
                && segment.indexOf('{', 1) == -1 && segment.indexOf('}') == len - 1) {
            String name = segment.substring(1, len - 1);
            if (name.isEmpty()) {
                throw new IllegalArgumentException("param segment requires name");
            }
            if (!isValidParamName(name)) {
                throw new IllegalArgumentException("param segment has invalid name: " + segment);
            }
            return RoutePattern.param(name);
        }
        if (segment.indexOf('{') >= 0 || segment.indexOf('}') >= 0) {
            if (segment.indexOf('{') < 0) {
                throw new IllegalArgumentException("mixed segment has stray closing brace: " + segment);
            }
            return parseMixed(segment);
        }
        return RoutePattern.literal(segment);
    }

    private static RoutePattern.Segment parseMixed(String segment) {
        List<String> literals = new ArrayList<>();
        List<String> params = new ArrayList<>();
        int i = 0;
        int len = segment.length();
        while (i < len) {
            int open = segment.indexOf('{', i);
            int close = segment.indexOf('}', i);
            if (close >= 0 && (open < 0 || close < open)) {
                throw new IllegalArgumentException("mixed segment has stray closing brace: " + segment);
            }
            if (open < 0) {
                literals.add(segment.substring(i));
                i = len;
                break;
            }
            literals.add(segment.substring(i, open));
            int nameStart = open + 1;
            int nameEnd = segment.indexOf('}', nameStart);
            if (nameEnd < 0) {
                throw new IllegalArgumentException("mixed segment has unterminated param: " + segment);
            }
            if (nameEnd == nameStart) {
                throw new IllegalArgumentException("mixed segment has empty param name: " + segment);
            }
            String name = segment.substring(nameStart, nameEnd);
            if (!isValidParamName(name)) {
                throw new IllegalArgumentException("mixed segment has invalid param name: " + segment);
            }
            params.add(name);
            i = nameEnd + 1;
        }
        if (params.isEmpty()) {
            return RoutePattern.literal(segment);
        }
        if (literals.size() == params.size()) {
            literals.add("");
        }
        if (literals.size() != params.size() + 1) {
            throw new IllegalArgumentException("mixed segment literals/params mismatch: " + segment);
        }
        return RoutePattern.mixed(literals.toArray(new String[0]), params.toArray(new String[0]));
    }

    private static boolean isValidParamName(String name) {
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (!(Character.isLetterOrDigit(ch) || ch == '_')) {
                return false;
            }
        }
        return true;
    }
}
