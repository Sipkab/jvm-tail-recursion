package testing.sipka.jvm.tailrec;

import java.util.Map;

import testing.saker.SakerTest;

@SakerTest
public class MiscMethodsTest extends TailRecOptimizerTestCase {

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		assertSuccessfulOptimization(TestMethods.class.getMethod("doubleCount", int.class), 10000000);
		assertSuccessfulOptimization(TestMethods.class.getMethod("doubleCount", long.class), 10000000);
	}

	public static class TestMethods {
		public static void doubleCount(int n) {
			if (n == 0) {
				return;
			}
			for (int i = 0; i < 2; i++) {
				--n;
			}
			doubleCount(n);
		}

		public static void doubleCount(long n) {
			if (n == 0) {
				return;
			}
			for (long i = 0; i < 2; i++) {
				--n;
			}
			doubleCount(n);
		}
	}
}
