package testing.sipka.jvm.tailrec;

import java.util.Map;

import testing.saker.SakerTest;

@SakerTest
public class IfElseTrueBranchTest extends TailRecOptimizerTestCase {

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		assertSuccessfulOptimization(TestMethods.class.getMethod("counter", int.class), 10000000);
		assertSuccessfulOptimization(TestMethods.class.getMethod("counter", long.class), 10000000);
	}

	public static class TestMethods {
		public static void counter(int n) {
			if (n != 0) {
				counter(n - 1);
			} else {
			}
		}

		public static void counter(long n) {
			if (n != 0) {
				counter(n - 1);
			} else {
			}
		}
	}
}
