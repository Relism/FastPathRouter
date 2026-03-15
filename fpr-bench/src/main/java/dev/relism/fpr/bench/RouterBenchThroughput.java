package dev.relism.fpr.bench;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class RouterBenchThroughput {
    @Benchmark
    public int matchArrayLiteral(RouterBenchState state, Blackhole blackhole) {
        return state.matchArrayLiteral(blackhole);
    }

    @Benchmark
    public int matchArrayParam(RouterBenchState state, Blackhole blackhole) {
        return state.matchArrayParam(blackhole);
    }

    @Benchmark
    public int matchArrayMixed(RouterBenchState state, Blackhole blackhole) {
        return state.matchArrayMixed(blackhole);
    }

    @Benchmark
    public int matchArrayCatchAll(RouterBenchState state, Blackhole blackhole) {
        return state.matchArrayCatchAll(blackhole);
    }

    @Benchmark
    public int matchNettyLiteral(RouterBenchState state, Blackhole blackhole) {
        return state.matchNettyLiteral(blackhole);
    }

    @Benchmark
    public int matchNettyParam(RouterBenchState state, Blackhole blackhole) {
        return state.matchNettyParam(blackhole);
    }

    @Benchmark
    public int matchNettyMixed(RouterBenchState state, Blackhole blackhole) {
        return state.matchNettyMixed(blackhole);
    }

    @Benchmark
    public int matchNettyCatchAll(RouterBenchState state, Blackhole blackhole) {
        return state.matchNettyCatchAll(blackhole);
    }

    @Benchmark
    public int matchLiteralHeavy(RouterBenchState state, Blackhole blackhole) {
        return state.matchLiteralHeavy(blackhole);
    }

    @Benchmark
    public int matchParamHeavy(RouterBenchState state, Blackhole blackhole) {
        return state.matchParamHeavy(blackhole);
    }

    @Benchmark
    public int matchMixedHeavy(RouterBenchState state, Blackhole blackhole) {
        return state.matchMixedHeavy(blackhole);
    }

    @Benchmark
    public int matchCatchAllHeavy(RouterBenchState state, Blackhole blackhole) {
        return state.matchCatchAllHeavy(blackhole);
    }

    @Benchmark
    public int matchLargeRouteSet(RouterBenchState state, Blackhole blackhole) {
        return state.matchLargeRouteSet(blackhole);
    }

    @Benchmark
    public int matchVariety(RouterBenchState state, Blackhole blackhole) {
        return state.matchVariety(blackhole);
    }
}
