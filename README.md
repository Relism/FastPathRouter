# FastPathRouter (FPR)

FastPathRouter is a tiny, GC-friendly routing core designed to compile startup-time route definitions into immutable, array-based match tables. The hot path avoids String and Map allocations and works directly on byte views.

Modules:
- fpr-core: routing core and compiler
- fpr-netty: Netty adapters (ByteBuf view + path extraction)
- fpr-bench: JMH benchmarks

Philosophy:
- build routes at startup, compile into frozen tables
- match using byte-level comparisons
- avoid String/Map allocations in the hot path

Route syntax:
- params use `{name}` (e.g. `/users/{id}`)
- mixed segments are allowed (e.g. `/static/pre-{x}-suf`)
- `*` matches a single segment, `**` matches the rest of the path

Zero-copy param extraction:
```java
RouterBuilder<String> builder = new RouterBuilder<>();
builder.add(StringRouteParser.parse("/users/{id}/orders/{orderId}"), "H");
FastPathRouter<ByteView, String> router = builder.compile();
String[] paramNames = builder.paramNames();

ByteView view = ...;
MatchResult<String> out = new MatchResult<>(builder.maxParamCount());
router.match(view, out);
out.forEachParam(view, paramNames, (name, bytes, start, len) -> {
    // Use bytes + start/len as needed; convert only if required.
});
```

Build and test:
```
mvn -q -DskipTests=false test
```

Run benchmarks:
```
mvn -pl fpr-bench -DskipTests package
java -jar fpr-bench/target/fpr-bench-1.0-SNAPSHOT-shaded.jar -wi 5 -i 5
```

Or use the Maven exec profile:
```
mvn -pl fpr-bench -am -Pbench verify
```
