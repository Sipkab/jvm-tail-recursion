package testing.sipka.jvm.tailrec;

import java.util.Map;

import testing.saker.SakerTest;

@SakerTest
public class AfterTryCatchTest extends TailRecOptimizerTestCase {

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		assertSuccessfulOptimization(TestMethods.class.getMethod("trying", int.class), 10000000);
	}

	public static class TestMethods {
		public static void trying(int n) {
			try {
				if (n == 0) {
					return;
				}
			} catch (Throwable e) {
				throw e;
			}
			trying(n - 1);
		}
	}
}
