package testing.sipka.jvm.tailrec;

import java.util.Map;

import testing.saker.SakerTest;

@SakerTest
public class CountTest extends TailRecOptimizerTestCase {
	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		assertSuccessfulOptimization(TestMethods.class.getMethod("count", int.class), 10000000);
		assertSuccessfulOptimization(TestMethods.class.getMethod("count", long.class), 10000000);
	}

	public static class TestMethods {
		public static void count(int n) {
			if (n == 0) {
				return;
			}
			count(n - 1);
		}

		public static void count(long n) {
			if (n == 0) {
				return;
			}
			count(n - 1);
		}
	}
}
