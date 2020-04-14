package testing.sipka.jvm.tailrec;

import java.util.Map;

import testing.saker.SakerTest;

@SakerTest
public class InterfaceDefaultMethodTest extends TailRecOptimizerTestCase {
	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		optimizeClass(TestMethods.class);
		assertSuccessfulOptimization(Impl.class, "count", new Class<?>[] { int.class }, 10000000);
	}

	public static interface TestMethods {
		public default void count(int n) {
			if (n == 0) {
				return;
			}
			count(n - 1);
		}
	}

	public static class Impl implements TestMethods {
	}
}
