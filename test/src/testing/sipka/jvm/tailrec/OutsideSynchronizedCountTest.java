package testing.sipka.jvm.tailrec;

import java.util.Map;

import testing.saker.SakerTest;

@SakerTest
public class OutsideSynchronizedCountTest extends TailRecOptimizerTestCase {
	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		assertSuccessfulOptimization(TestMethods.class.getMethod("outsynchCount", int.class), 10000000);
		assertSuccessfulOptimization(TestMethods.class.getMethod("outsynchCount", long.class), 10000000);
	}

	public static class TestMethods {
		public static void outsynchCount(int n) {
			synchronized (TestMethods.class) {
				if (n == 0) {
					return;
				}
			}
			outsynchCount(n - 1);
		}

		public static void outsynchCount(long n) {
			synchronized (TestMethods.class) {
				if (n == 0) {
					return;
				}
			}
			outsynchCount(n - 1);
		}
	}
}
