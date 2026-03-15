package dev.relism.fpr.netty;

import dev.relism.fpr.core.ByteView;
import dev.relism.fpr.core.FastPathRouter;
import dev.relism.fpr.core.MatchResult;
import dev.relism.fpr.core.RouterBuilder;
import dev.relism.fpr.core.dsl.StringRouteParser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class NettyAdaptersTest {
    @Test
    void byteBufViewRespectsOffsetAndLength() {
        ByteBuf buf = Unpooled.wrappedBuffer("abcdef".getBytes(StandardCharsets.US_ASCII));
        NettyByteBufView view = new NettyByteBufView(buf, 1, 3);

        assertThat(view.length()).isEqualTo(3);
        assertThat((char) view.byteAt(0)).isEqualTo('b');
        assertThat((char) view.byteAt(2)).isEqualTo('d');
    }

    @Test
    void extractPathFromRequestLine() {
        ByteBuf buf = Unpooled.wrappedBuffer("GET /alpha/beta?q=1 HTTP/1.1".getBytes(StandardCharsets.US_ASCII));
        NettyPathExtractor.PathSpan out = new NettyPathExtractor.PathSpan();

        NettyPathExtractor.extractFromRequestLine(buf, 0, buf.readableBytes(), out);

        String path = buf.toString(out.start(), out.len(), StandardCharsets.US_ASCII);
        assertThat(path).isEqualTo("/alpha/beta?q=1");
    }

    @Test
    void nettyIntegrationMatch() {
        RouterBuilder<String> builder = new RouterBuilder<>();
        builder.add(StringRouteParser.parse("/user/{id}"), "USER");
        FastPathRouter<ByteView, String> router = builder.compile();

        ByteBuf buf = Unpooled.wrappedBuffer("/user/42".getBytes(StandardCharsets.US_ASCII));
        NettyByteBufView view = new NettyByteBufView(buf, 0, buf.readableBytes());

        MatchResult<String> out = new MatchResult<>(builder.maxParamCount());
        assertThat(router.match(view, out)).isNotEqualTo(FastPathRouter.NO_MATCH);
        assertThat(out.handler()).isEqualTo("USER");
        assertThat(out.paramCount()).isEqualTo(1);
        assertThat(span(view, out, 0)).isEqualTo("42");
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
}
