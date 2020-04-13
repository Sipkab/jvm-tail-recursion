package testing.sipka.jvm.tailrec;

import java.util.Map;

import testing.saker.SakerTest;

@SakerTest
public class NewArrayReturnPathTest extends TailRecOptimizerTestCase {
	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		assertSuccessfulOptimizationWithException(TestMethods.class.getMethod("count", int.class),
				OutOfMemoryError.class, 1);
		assertSuccessfulOptimization(TestMethods.class.getMethod("count", int.class), 10000000);
		assertSuccessfulOptimization(TestMethods.class.getMethod("count", long.class), 10000000);
	}

	public static class Fielded {
		public static Fielded o;
	}

	public static class TestMethods {

		public static int count(int n) {
			if (n == 0) {
				return 0;
			}
			int v = count(n - 1);
			Object a = new long[Integer.MAX_VALUE];
			Object b = new int[10][20];
			Object c = new Object[10];
			Object d = new Object[10][20];
			return v;
		}

		public static long count(long n) {
			if (n == 0) {
				return 0;
			}
			long v = count(n - 1);
			Object a = new long[Integer.MAX_VALUE];
			Object b = new int[10][20];
			Object c = new Object[10];
			Object d = new Object[10][20];
			return v;
		}
	}
}
