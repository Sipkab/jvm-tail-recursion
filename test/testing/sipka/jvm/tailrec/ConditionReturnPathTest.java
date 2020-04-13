package testing.sipka.jvm.tailrec;

import java.util.Map;

import testing.saker.SakerTest;

@SakerTest
public class ConditionReturnPathTest extends TailRecOptimizerTestCase {
	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		assertSuccessfulOptimization(TestMethods.class.getMethod("count", int.class), 10000000);
		assertSuccessfulOptimization(TestMethods.class.getMethod("count", long.class), 10000000);
	}

	public static class TestMethods {
		public static int count(int n) {
			if (n == 0) {
				return 0;
			}
			int v = count(n - 1);
			int k = 3;
			if (v == 0) {
				k += 3;
			} else {
				k *= 2;
			}
			return v;
		}

		public static long count(long n) {
			if (n == 0) {
				return 0;
			}
			long v = count(n - 1);
			long k = 3;
			if (v == 0) {
				k += 3;
			} else {
				k *= 2;
			}
			return v;
		}
	}
}
