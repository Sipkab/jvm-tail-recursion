package testing.sipka.jvm.tailrec;

import java.util.Map;

import testing.saker.SakerTest;

@SakerTest
public class IfEndCountTest extends TailRecOptimizerTestCase {
	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		assertSuccessfulOptimization(TestMethods.class.getMethod("ifEndCount", int.class), 10000000);
		assertSuccessfulOptimization(TestMethods.class.getMethod("ifEndCount", long.class), 10000000);
	}

	public static class TestMethods {
		public static void ifEndCount(int n) {
			if (n != 0) {
				ifEndCount(n - 1);
			}
		}

		public static void ifEndCount(long n) {
			if (n != 0) {
				ifEndCount(n - 1);
			}
		}
	}
}
