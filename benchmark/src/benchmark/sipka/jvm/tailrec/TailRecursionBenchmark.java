package benchmark.sipka.jvm.tailrec;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.infra.Blackhole;

public class TailRecursionBenchmark {
	@Benchmark
	public void countTest(Blackhole bh) throws InterruptedException {
		count(1000, bh);
	}

	@Benchmark
	public void factTest(Blackhole bh) throws InterruptedException {
		bh.consume(fact(1000));
	}

	@Benchmark
	public void numbersTest(Blackhole bh) throws InterruptedException {
		bh.consume(numbers(1000, ""));
	}

	public static void count(int n, Blackhole bh) {
		bh.consume(n);
		if (n == 0) {
			return;
		}
		count(n - 1,bh);
	}

	public static int fact(int n) {
		return factImpl(n, 1);
	}

	private static int factImpl(int n, int acc) {
		if (n == 0) {
			return acc;
		}
		return factImpl(n - 1, acc * n);
	}

	public static String numbers(int n, String s) {
		if (n == 0) {
			return s + "0";
		}
		return numbers(n - 1, s + n + ",");
	}
}
