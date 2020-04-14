package testing.sipka.jvm.tailrec;

import java.util.Map;

import testing.saker.SakerTest;

@SakerTest
public class FactorialTest extends TailRecOptimizerTestCase {

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		assertSuccessfulOptimization(TestMethods.class.getMethod("fact", int.class), 10000000);
		assertSuccessfulOptimization(TestMethods.class.getMethod("fact", long.class), 10000000);
	}

	public static class TestMethods {
		public static int fact(int n) {
			return factImpl(n, 1);
		}

		private static int factImpl(int n, int acc) {
			if (n == 0) {
				return acc;
			}
			return factImpl(n - 1, acc * n);
		}

		public static long fact(long n) {
			return factImpl(n, 1);
		}

		private static long factImpl(long n, long acc) {
			if (n == 0) {
				return acc;
			}
			return factImpl(n - 1, acc * n);
		}
	}
}
