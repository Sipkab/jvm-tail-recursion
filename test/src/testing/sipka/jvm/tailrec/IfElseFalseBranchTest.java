package testing.sipka.jvm.tailrec;

import java.util.Map;

import testing.saker.SakerTest;

@SakerTest
public class IfElseFalseBranchTest extends TailRecOptimizerTestCase {

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		assertSuccessfulOptimization(TestMethods.class.getMethod("counter", int.class), 10000000);
		assertSuccessfulOptimization(TestMethods.class.getMethod("counter", long.class), 10000000);
	}

	public static class TestMethods {
		public static void counter(int n) {
			if (n == 0) {
			} else {
				counter(n - 1);
			}
		}

		public static void counter(long n) {
			if (n == 0) {
			} else {
				counter(n - 1);
			}
		}
	}
}
