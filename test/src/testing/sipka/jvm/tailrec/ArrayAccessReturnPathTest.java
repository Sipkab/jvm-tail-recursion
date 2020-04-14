package testing.sipka.jvm.tailrec;

import java.util.Map;

import testing.saker.SakerTest;

@SakerTest
public class ArrayAccessReturnPathTest extends TailRecOptimizerTestCase {
	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		optimizeClass(ArrayContainer.class);
		//the array access instruction has been optimized away
		assertSuccessfulOptimizationWithException(TestMethods.class.getMethod("count", int.class),
				NullPointerException.class, 1);
		assertSuccessfulOptimization(TestMethods.class.getMethod("count", int.class), 10000000);
		assertSuccessfulOptimization(TestMethods.class.getMethod("count", long.class), 10000000);
	}

	public static class ArrayContainer {
		public static Object[] o;
	}

	public static class TestMethods {

		public static int count(int n) {
			if (n == 0) {
				return 0;
			}
			int v = count(n - 1);
			Object x = ArrayContainer.o[3];
			return v;
		}

		public static long count(long n) {
			if (n == 0) {
				return 0;
			}
			long v = count(n - 1);
			Object x = ArrayContainer.o[3];
			return v;
		}
	}
}
