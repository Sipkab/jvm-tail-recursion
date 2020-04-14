package testing.sipka.jvm.tailrec;

import java.util.Map;

import testing.saker.SakerTest;

@SakerTest
public class BeforeTryCatchTest extends TailRecOptimizerTestCase {

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		assertSuccessfulOptimization(TestMethods.class.getMethod("trying", int.class), 10000000);
	}

	public static class TestMethods {
		public static void trying(int n) {
			if (n != 0) {
				trying(n - 1);
				return;
			}
			try {
			} catch (Throwable e) {
				throw e;
			}
		}
	}
}
