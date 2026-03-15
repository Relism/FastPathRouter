package dev.relism.fpr.core;

import dev.relism.fpr.core.dsl.StringRouteParser;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouterMatchTest {
    @Test
    void routePrecedencePrefersLiteralMixedParamWildcardCatchAll() {
        RouterBuilder<String> builder = new RouterBuilder<>();
        builder.add(StringRouteParser.parse("/a/b"), "LITERAL");
        builder.add(StringRouteParser.parse("/a/pre-{x}-suf"), "MIXED");
        builder.add(StringRouteParser.parse("/a/{id}"), "PARAM");
        builder.add(StringRouteParser.parse("/a/*"), "WILD");
        builder.add(StringRouteParser.parse("/a/**"), "CATCH");

        FastPathRouter<ByteView, String> router = builder.compile();
        MatchResult<String> out = new MatchResult<>(builder.maxParamCount());

        assertThat(router.match(view("/a/b"), out)).isNotEqualTo(FastPathRouter.NO_MATCH);
        assertThat(out.handler()).isEqualTo("LITERAL");

        assertThat(router.match(view("/a/pre-123-suf"), out)).isNotEqualTo(FastPathRouter.NO_MATCH);
        assertThat(out.handler()).isEqualTo("MIXED");

        assertThat(router.match(view("/a/zzz"), out)).isNotEqualTo(FastPathRouter.NO_MATCH);
        assertThat(out.handler()).isEqualTo("PARAM");

        assertThat(router.match(view("/a/zzz/extra"), out)).isNotEqualTo(FastPathRouter.NO_MATCH);
        assertThat(out.handler()).isEqualTo("CATCH");
    }

    @Test
    void mixedSegmentsCaptureParams() {
        RouterBuilder<String> builder = new RouterBuilder<>();
        builder.add(StringRouteParser.parse("/some{id}"), "A");
        builder.add(StringRouteParser.parse("/pre-{x}-suf"), "B");
        builder.add(RoutePattern.of(RoutePattern.mixed(new String[]{"", "-suf"}, new String[]{"x"})), "C");
        builder.add(RoutePattern.of(RoutePattern.mixed(new String[]{"pre-", ""}, new String[]{"x"})), "D");

        FastPathRouter<ByteView, String> router = builder.compile();
        MatchResult<String> out = new MatchResult<>(builder.maxParamCount());

        router.match(view("/some123"), out);
        assertThat(out.paramCount()).isEqualTo(1);
        assertThat(span(view("/some123"), out, 0)).isEqualTo("123");

        router.match(view("/pre-abc-suf"), out);
        assertThat(out.paramCount()).isEqualTo(1);
        assertThat(span(view("/pre-abc-suf"), out, 0)).isEqualTo("abc");

        router.match(view("/123-suf"), out);
        assertThat(out.paramCount()).isEqualTo(1);
        assertThat(out.handler()).isEqualTo("C");
        assertThat(span(view("/123-suf"), out, 0)).isEqualTo("123");

        router.match(view("/pre-123"), out);
        assertThat(out.paramCount()).isEqualTo(1);
        assertThat(out.handler()).isEqualTo("D");
        assertThat(span(view("/pre-123"), out, 0)).isEqualTo("123");
    }

    @Test
    void mixedEmptyPrefixStillMatches() {
        RouterBuilder<String> builder = new RouterBuilder<>();
        builder.add(RoutePattern.of(RoutePattern.mixed(new String[]{"", "-verylongsuffix"}, new String[]{"x"})), "A");
        builder.add(StringRouteParser.parse("/b{y}"), "B");

        FastPathRouter<ByteView, String> router = builder.compile();
        MatchResult<String> out = new MatchResult<>(builder.maxParamCount());

        assertThat(router.match(view("/abc-verylongsuffix"), out)).isNotEqualTo(FastPathRouter.NO_MATCH);
        assertThat(out.handler()).isEqualTo("A");
        assertThat(span(view("/abc-verylongsuffix"), out, 0)).isEqualTo("abc");
    }

    @Test
    void fallbackToLowerPrecedenceWhenHigherPathFails() {
        RouterBuilder<String> builder = new RouterBuilder<>();
        builder.add(StringRouteParser.parse("/a/b/x"), "LIT");
        builder.add(StringRouteParser.parse("/a/{id}/y"), "PARAM");
        builder.add(StringRouteParser.parse("/a/**"), "CATCH");

        FastPathRouter<ByteView, String> router = builder.compile();
        MatchResult<String> out = new MatchResult<>(builder.maxParamCount(), 64);

        router.match(view("/a/b/y"), out);
        assertThat(out.handler()).isEqualTo("PARAM");

        router.match(view("/a/123/z"), out);
        assertThat(out.handler()).isEqualTo("CATCH");
    }

    @Test
    void indexedLiteralLookupMatchesAcrossStates() {
        RouterBuilder<String> builder = new RouterBuilder<>();
        for (int i = 0; i < 20; i++) {
            builder.add(StringRouteParser.parse("/s0/r" + i), "S0-" + i);
            builder.add(StringRouteParser.parse("/s1/r" + i), "S1-" + i);
            builder.add(StringRouteParser.parse("/s2/r" + i), "S2-" + i);
        }

        FastPathRouter<ByteView, String> router = builder.compile();
        MatchResult<String> out = new MatchResult<>(builder.maxParamCount(), 64);

        assertThat(router.match(view("/s0/r19"), out)).isNotEqualTo(FastPathRouter.NO_MATCH);
        assertThat(out.handler()).isEqualTo("S0-19");

        assertThat(router.match(view("/s1/r7"), out)).isNotEqualTo(FastPathRouter.NO_MATCH);
        assertThat(out.handler()).isEqualTo("S1-7");

        assertThat(router.match(view("/s2/r3"), out)).isNotEqualTo(FastPathRouter.NO_MATCH);
        assertThat(out.handler()).isEqualTo("S2-3");
    }

    @Test
    void matchDoesNotReplaceResultArrays() {
        RouterBuilder<String> builder = new RouterBuilder<>();
        builder.add(StringRouteParser.parse("/a/{id}"), "A");

        FastPathRouter<ByteView, String> router = builder.compile();
        MatchResult<String> out = new MatchResult<>(builder.maxParamCount());
        int[] keyIds = out.keyIdsArray();
        int[] starts = out.startsArray();
        int[] lens = out.lensArray();

        router.match(view("/a/123"), out);

        assertThat(out.keyIdsArray()).isSameAs(keyIds);
        assertThat(out.startsArray()).isSameAs(starts);
        assertThat(out.lensArray()).isSameAs(lens);
    }

    @Test
    void forEachParamProvidesNamesAndSpans() {
        RouterBuilder<String> builder = new RouterBuilder<>();
        builder.add(StringRouteParser.parse("/users/{id}/orders/{orderId}"), "A");

        FastPathRouter<ByteView, String> router = builder.compile();
        MatchResult<String> out = new MatchResult<>(builder.maxParamCount());
        ByteView view = view("/users/42/orders/7");

        assertThat(router.match(view, out)).isNotEqualTo(FastPathRouter.NO_MATCH);

        String[] paramNames = builder.paramNames();
        List<String> seen = new ArrayList<>();
        out.forEachParam(view, paramNames, (name, bytes, start, len) -> {
            seen.add(name + "=" + span(bytes, start, len));
        });

        assertThat(seen).containsExactly("id=42", "orderId=7");
    }

    @Test
    void paramNamesAreOrderedAndUnique() {
        RouterBuilder<String> builder = new RouterBuilder<>();
        builder.add(StringRouteParser.parse("/a/{id}"), "A");
        builder.add(StringRouteParser.parse("/b/{id}/c/{slug}"), "B");
        builder.add(StringRouteParser.parse("/c/{slug}/d/{id}"), "C");

        assertThat(builder.paramNames()).containsExactly("id", "slug");
    }

    @Test
    void forEachParamRejectsMissingNameEntries() {
        RouterBuilder<String> builder = new RouterBuilder<>();
        builder.add(StringRouteParser.parse("/users/{id}/orders/{orderId}"), "A");

        FastPathRouter<ByteView, String> router = builder.compile();
        MatchResult<String> out = new MatchResult<>(builder.maxParamCount());
        ByteView view = view("/users/42/orders/7");
        router.match(view, out);

        String[] paramNames = new String[]{"id"};
        assertThatThrownBy(() -> out.forEachParam(view, paramNames, (name, bytes, start, len) -> {
        })).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void labelsDifferentiateSamePath() {
        RouterBuilder<String> builder = new RouterBuilder<>();
        builder.add("GET", StringRouteParser.parse("/users"), "GET_HANDLER");
        builder.add("POST", StringRouteParser.parse("/users"), "POST_HANDLER");

        int getId = builder.labelId("GET");
        int postId = builder.labelId("POST");

        FastPathRouter<ByteView, String> router = builder.compile();
        MatchResult<String> out = new MatchResult<>(builder.maxParamCount());

        out.labelId(getId);
        assertThat(router.match(view("/users"), out)).isNotEqualTo(FastPathRouter.NO_MATCH);
        assertThat(out.handler()).isEqualTo("GET_HANDLER");

        out.labelId(postId);
        assertThat(router.match(view("/users"), out)).isNotEqualTo(FastPathRouter.NO_MATCH);
        assertThat(out.handler()).isEqualTo("POST_HANDLER");
    }

    @Test
    void labelZeroMatchesAny() {
        RouterBuilder<String> builder = new RouterBuilder<>();
        builder.add("GET", StringRouteParser.parse("/assets"), "GET_ASSETS");
        builder.add(StringRouteParser.parse("/assets"), "ANY_ASSETS");

        int getId = builder.labelId("GET");
        FastPathRouter<ByteView, String> router = builder.compile();
        MatchResult<String> out = new MatchResult<>(builder.maxParamCount());

        out.labelId(getId);
        assertThat(router.match(view("/assets"), out)).isNotEqualTo(FastPathRouter.NO_MATCH);
        assertThat(out.handler()).isEqualTo("GET_ASSETS");

        out.labelId(0);
        assertThat(router.match(view("/assets"), out)).isNotEqualTo(FastPathRouter.NO_MATCH);
        assertThat(out.handler()).isEqualTo("ANY_ASSETS");
    }

    private static ByteView view(String path) {
        return new ByteArrayView(path.getBytes(StandardCharsets.US_ASCII));
    }

    private static String span(ByteView view, MatchResult<String> out, int index) {
        int start = out.startAt(index);
        int len = out.lenAt(index);
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) {
            bytes[i] = view.byteAt(start + i);
        }
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private static String span(ByteView view, int start, int len) {
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) {
            bytes[i] = view.byteAt(start + i);
        }
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private static final class ByteArrayView implements ByteView {
        private static final VarHandle LONG_VIEW = MethodHandles.byteArrayViewVarHandle(long[].class, java.nio.ByteOrder.LITTLE_ENDIAN);
        private final byte[] bytes;

        private ByteArrayView(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public int length() {
            return bytes.length;
        }

        @Override
        public byte byteAt(int index) {
            return bytes[index];
        }

        @Override
        public boolean supportsLong() {
            return true;
        }

        @Override
        public long longAt(int index) {
            return (long) LONG_VIEW.get(bytes, index);
        }
    }
}
