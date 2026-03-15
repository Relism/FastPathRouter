package dev.relism.fpr.bench;

import dev.relism.fpr.core.ByteView;
import dev.relism.fpr.core.FastPathRouter;
import dev.relism.fpr.core.MatchResult;
import dev.relism.fpr.core.RouterBuilder;
import dev.relism.fpr.core.dsl.StringRouteParser;
import dev.relism.fpr.netty.NettyByteBufView;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;

@State(Scope.Benchmark)
public class RouterBenchState {
    private FastPathRouter<ByteView, String> routerSmall;
    private FastPathRouter<ByteView, String> routerLiteralHeavy;
    private FastPathRouter<ByteView, String> routerParamHeavy;
    private FastPathRouter<ByteView, String> routerMixedHeavy;
    private FastPathRouter<ByteView, String> routerCatchAll;
    private FastPathRouter<ByteView, String> routerLarge;

    private MatchResult<String> outSmall;
    private MatchResult<String> outLarge;

    private ByteView arrayLiteral;
    private ByteView arrayParam;
    private ByteView arrayMixed;
    private ByteView arrayCatchAll;
    private NettyByteBufView nettyLiteral;
    private NettyByteBufView nettyParam;
    private NettyByteBufView nettyMixed;
    private NettyByteBufView nettyCatchAll;

    private ByteView literalHeavyPath;
    private ByteView paramHeavyPath;
    private ByteView mixedHeavyPath;
    private ByteView catchAllPath;
    private ByteView largePath;

    private ByteView[] varietyPaths;
    private int varietyIndex;

    private ByteBuf nettyBufLiteral;
    private ByteBuf nettyBufParam;
    private ByteBuf nettyBufMixed;
    private ByteBuf nettyBufCatchAll;

    @Setup(Level.Trial)
    public void setup() {
        RouterBuilder<String> builder = new RouterBuilder<>();
        builder.add(StringRouteParser.parse("/users"), "A");
        builder.add(StringRouteParser.parse("/users/{id}"), "B");
        builder.add(StringRouteParser.parse("/users/{id}/orders/{orderId}"), "C");
        builder.add(StringRouteParser.parse("/assets/**"), "D");
        builder.add(StringRouteParser.parse("/static/pre-{x}-suf"), "E");
        routerSmall = builder.compile();
        outSmall = new MatchResult<>(builder.maxParamCount(), 64);

        byte[] literal = "/users".getBytes(StandardCharsets.US_ASCII);
        byte[] param = "/users/123".getBytes(StandardCharsets.US_ASCII);
        byte[] mixed = "/static/pre-xyz-suf".getBytes(StandardCharsets.US_ASCII);
        byte[] catchAll = "/assets/css/app.css".getBytes(StandardCharsets.US_ASCII);

        arrayLiteral = new ByteArrayView(literal);
        arrayParam = new ByteArrayView(param);
        arrayMixed = new ByteArrayView(mixed);
        arrayCatchAll = new ByteArrayView(catchAll);

        nettyBufLiteral = Unpooled.wrappedBuffer(literal);
        nettyBufParam = Unpooled.wrappedBuffer(param);
        nettyBufMixed = Unpooled.wrappedBuffer(mixed);
        nettyBufCatchAll = Unpooled.wrappedBuffer(catchAll);

        nettyLiteral = new NettyByteBufView(nettyBufLiteral, 0, nettyBufLiteral.readableBytes());
        nettyParam = new NettyByteBufView(nettyBufParam, 0, nettyBufParam.readableBytes());
        nettyMixed = new NettyByteBufView(nettyBufMixed, 0, nettyBufMixed.readableBytes());
        nettyCatchAll = new NettyByteBufView(nettyBufCatchAll, 0, nettyBufCatchAll.readableBytes());

        routerLiteralHeavy = buildLiteralHeavy();
        routerParamHeavy = buildParamHeavy();
        routerMixedHeavy = buildMixedHeavy();
        routerCatchAll = buildCatchAll();
        routerLarge = buildLarge();
        outLarge = new MatchResult<>(4, 128);

        literalHeavyPath = new ByteArrayView("/route-199".getBytes(StandardCharsets.US_ASCII));
        paramHeavyPath = new ByteArrayView("/p199/alpha".getBytes(StandardCharsets.US_ASCII));
        mixedHeavyPath = new ByteArrayView("/m199/pre-xyz-suf".getBytes(StandardCharsets.US_ASCII));
        catchAllPath = new ByteArrayView("/assets/dir/file.js".getBytes(StandardCharsets.US_ASCII));
        largePath = new ByteArrayView("/r9999".getBytes(StandardCharsets.US_ASCII));

        varietyPaths = new ByteView[]{
                new ByteArrayView("/users".getBytes(StandardCharsets.US_ASCII)),
                new ByteArrayView("/users/7".getBytes(StandardCharsets.US_ASCII)),
                new ByteArrayView("/static/pre-foo-suf".getBytes(StandardCharsets.US_ASCII)),
                new ByteArrayView("/assets/img/logo.png".getBytes(StandardCharsets.US_ASCII)),
                new ByteArrayView("/users/9/orders/3".getBytes(StandardCharsets.US_ASCII))
        };
    }

    public int matchArrayLiteral(Blackhole blackhole) {
        outSmall.reset();
        int id = routerSmall.match(arrayLiteral, outSmall);
        blackhole.consume(outSmall.handler());
        return id;
    }

    public int matchArrayParam(Blackhole blackhole) {
        outSmall.reset();
        int id = routerSmall.match(arrayParam, outSmall);
        blackhole.consume(outSmall.handler());
        return id;
    }

    public int matchArrayMixed(Blackhole blackhole) {
        outSmall.reset();
        int id = routerSmall.match(arrayMixed, outSmall);
        blackhole.consume(outSmall.handler());
        return id;
    }

    public int matchArrayCatchAll(Blackhole blackhole) {
        outSmall.reset();
        int id = routerSmall.match(arrayCatchAll, outSmall);
        blackhole.consume(outSmall.handler());
        return id;
    }

    public int matchNettyLiteral(Blackhole blackhole) {
        outSmall.reset();
        int id = routerSmall.match(nettyLiteral, outSmall);
        blackhole.consume(outSmall.handler());
        return id;
    }

    public int matchNettyParam(Blackhole blackhole) {
        outSmall.reset();
        int id = routerSmall.match(nettyParam, outSmall);
        blackhole.consume(outSmall.handler());
        return id;
    }

    public int matchNettyMixed(Blackhole blackhole) {
        outSmall.reset();
        int id = routerSmall.match(nettyMixed, outSmall);
        blackhole.consume(outSmall.handler());
        return id;
    }

    public int matchNettyCatchAll(Blackhole blackhole) {
        outSmall.reset();
        int id = routerSmall.match(nettyCatchAll, outSmall);
        blackhole.consume(outSmall.handler());
        return id;
    }

    public int matchLiteralHeavy(Blackhole blackhole) {
        outSmall.reset();
        int id = routerLiteralHeavy.match(literalHeavyPath, outSmall);
        blackhole.consume(outSmall.handler());
        return id;
    }

    public int matchParamHeavy(Blackhole blackhole) {
        outSmall.reset();
        int id = routerParamHeavy.match(paramHeavyPath, outSmall);
        blackhole.consume(outSmall.handler());
        return id;
    }

    public int matchMixedHeavy(Blackhole blackhole) {
        outSmall.reset();
        int id = routerMixedHeavy.match(mixedHeavyPath, outSmall);
        blackhole.consume(outSmall.handler());
        return id;
    }

    public int matchCatchAllHeavy(Blackhole blackhole) {
        outSmall.reset();
        int id = routerCatchAll.match(catchAllPath, outSmall);
        blackhole.consume(outSmall.handler());
        return id;
    }

    public int matchLargeRouteSet(Blackhole blackhole) {
        outLarge.reset();
        int id = routerLarge.match(largePath, outLarge);
        blackhole.consume(outLarge.handler());
        return id;
    }

    public int matchVariety(Blackhole blackhole) {
        if (varietyIndex >= varietyPaths.length) {
            varietyIndex = 0;
        }
        ByteView view = varietyPaths[varietyIndex++];
        outSmall.reset();
        int id = routerSmall.match(view, outSmall);
        blackhole.consume(outSmall.handler());
        return id;
    }

    private FastPathRouter<ByteView, String> buildLiteralHeavy() {
        RouterBuilder<String> builder = new RouterBuilder<>();
        for (int i = 0; i < 200; i++) {
            builder.add(StringRouteParser.parse("/route-" + i), "L" + i);
        }
        return builder.compile();
    }

    private FastPathRouter<ByteView, String> buildParamHeavy() {
        RouterBuilder<String> builder = new RouterBuilder<>();
        for (int i = 0; i < 200; i++) {
            builder.add(StringRouteParser.parse("/p" + i + "/{id}"), "P" + i);
        }
        return builder.compile();
    }

    private FastPathRouter<ByteView, String> buildMixedHeavy() {
        RouterBuilder<String> builder = new RouterBuilder<>();
        for (int i = 0; i < 200; i++) {
            builder.add(StringRouteParser.parse("/m" + i + "/pre-{x}-suf"), "M" + i);
        }
        return builder.compile();
    }

    private FastPathRouter<ByteView, String> buildCatchAll() {
        RouterBuilder<String> builder = new RouterBuilder<>();
        builder.add(StringRouteParser.parse("/assets/**"), "CATCH");
        builder.add(StringRouteParser.parse("/assets/images/**"), "CATCH2");
        return builder.compile();
    }

    private FastPathRouter<ByteView, String> buildLarge() {
        RouterBuilder<String> builder = new RouterBuilder<>();
        for (int i = 0; i < 10_000; i++) {
            builder.add(StringRouteParser.parse("/r" + i), "R" + i);
        }
        return builder.compile();
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
