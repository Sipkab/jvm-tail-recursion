package testing.sipka.jvm.tailrec;

import java.util.Map;

import testing.saker.SakerTest;

@SakerTest
public class MultiVariableCounterFactorialTest extends TailRecOptimizerTestCase {

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		assertSuccessfulOptimization(TestMethods.class.getMethod("multiVarCount", int.class), 10000000);
		assertSuccessfulOptimization(TestMethods.class.getMethod("multiVarCountDiff", int.class), 10000000);

		assertSuccessfulOptimization(TestMethods.class.getMethod("multiVarCount", long.class), 10000000);
		assertSuccessfulOptimization(TestMethods.class.getMethod("multiVarCountDiff", long.class), 10000000);
	}

	public static class TestMethods {
		public static int multiVarCount(int n) {
			if (n == 0) {
				return 0;
			}
			int v1 = multiVarCount(n - 1);
			int v2 = v1;
			return v2;
		}

		public static int multiVarCountDiff(int n) {
			if (n == 0) {
				return 0;
			}
			int v1 = multiVarCountDiff(n - 1);
			int v2 = v1;
			return v1;
		}

		public static long multiVarCount(long n) {
			if (n == 0) {
				return 0;
			}
			long v1 = multiVarCount(n - 1);
			long v2 = v1;
			return v2;
		}

		public static long multiVarCountDiff(long n) {
			if (n == 0) {
				return 0;
			}
			long v1 = multiVarCountDiff(n - 1);
			long v2 = v1;
			return v1;
		}
	}
}
