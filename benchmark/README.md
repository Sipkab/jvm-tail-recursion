# Benchmarks

This directory contains benchmarks for the library. \
You can run it by first building the `export` target in the `saker.build` file of this directory:

```plaintext
# in the project root directory
java -jar saker.build.jar -build-directory build export benchmark/saker.build
```

And then running the benchmarks with:

```plaintext
# in the project root directory
java -jar build\saker.jar.create\benchmark-optimized.jar
java -jar build\saker.jar.create\benchmark-unoptimized.jar
```

## Results

These are our benchmark results. See the [benchmarked class](src/benchmark/sipka/jvm/tailrec/TailRecursionBenchmark.java) for information about the cases.

**Higher values are better.**

### Optimized

```plaintext
Benchmark                            Mode  Cnt        Score      Error  Units
TailRecursionBenchmark.countTest    thrpt   25   436354,616 ? 2208,882  ops/s
TailRecursionBenchmark.factTest     thrpt   25  1201126,490 ? 8081,594  ops/s
TailRecursionBenchmark.numbersTest  thrpt   25     2183,977 ?   62,684  ops/s
```

### Unoptimized

```plaintext
Benchmark                            Mode  Cnt        Score      Error  Units
TailRecursionBenchmark.countTest    thrpt   25   257429,802 ? 1501,296  ops/s
TailRecursionBenchmark.factTest     thrpt   25   831008,693 ? 9108,785  ops/s
TailRecursionBenchmark.numbersTest  thrpt   25     2083,716 ?   14,563  ops/s
```

### Conclusion

We can see that the tail recursion optimization always comes with some advantage. However, as the workload for each call grows bigger, this advantage grows smaller. This is due to the fact that the cost of the extra function call starts to diminish when compared to the work that is performed by each method.

I think the advantage of the tail recursion optimization lies in avoiding stack overflows rather than the actual performance benefits of it.
