# Architecture

FastPathRouter is split into a small public API and an internal compiler/matcher pipeline.

1) Builder
Routes are added to `RouterBuilder` using structured `RoutePattern` segments. The builder assigns parameter key ids and collects handler bindings.

2) Compile / freeze
Routes are compiled into a graph and then frozen into immutable arrays. The compile pipeline lives in `dev.relism.fpr.core.internal.compile`:
- `RouteCompiler` orchestrates the process.
- `RouteGraph` is the intermediate state/edge graph.
- `FreezeWriter` emits primitive arrays and the blob.
- `IndexBuilder` builds per-state first-byte indexes.

3) Match
Matching walks the input path by segments, compares bytes, and records param spans in a reusable `MatchResult` container. Runtime pieces live in `dev.relism.fpr.core.internal.runtime`:
- `FrozenRouter` is the match loop and state transitions.
- `EdgeDispatch` selects candidate edges for a state.
- `SegmentMatcher` evaluates a single segment (literal/mixed/param/wild).
- `ByteCompare` performs bulk byte comparisons.

No Strings or Maps are created during matching.
If you need params as name/value pairs, `MatchResult.forEachParam` can stream spans via `ParamConsumer` without allocations.
