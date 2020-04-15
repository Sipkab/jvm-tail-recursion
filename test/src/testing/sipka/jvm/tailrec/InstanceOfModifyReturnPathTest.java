package testing.sipka.jvm.tailrec;

import java.util.Map;

import testing.saker.SakerTest;

@SakerTest
public class InstanceOfModifyReturnPathTest extends TailRecOptimizerTestCase {
	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		assertNotOptimized(TestMethods.class.getMethod("count", int.class), 10000000);
		assertOptimizationResultEquals(TestMethods.class.getMethod("count", int.class), 0);
		assertOptimizationResultEquals(TestMethods.class.getMethod("count", int.class), 1);
		assertOptimizationResultEquals(TestMethods.class.getMethod("count", int.class), 10);
	}

	public static class TestMethods {
		public static Object count(int n) {
			if (n == 0) {
				return "123";
			}
			Object v = count(n - 1);
			if (v instanceof CharSequence) {
				v = "";
			}
			return v;
		}
	}
}
