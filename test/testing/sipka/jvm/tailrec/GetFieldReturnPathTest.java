package testing.sipka.jvm.tailrec;

import java.util.Map;

import testing.saker.SakerTest;

@SakerTest
public class GetFieldReturnPathTest extends TailRecOptimizerTestCase {
	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		optimizeClass(Fielded.class);
		//the getfield instruction has been optimized away
		assertSuccessfulOptimizationWithException(TestMethods.class.getMethod("count", int.class, Fielded.class),
				NullPointerException.class, 1, null);
		assertSuccessfulOptimization(TestMethods.class.getMethod("count", int.class, Fielded.class), 10000000, null);
		assertSuccessfulOptimization(TestMethods.class.getMethod("count", long.class, Fielded.class), 10000000, null);
	}

	public static class Fielded {
		public Fielded o;
	}

	public static class TestMethods {

		public static int count(int n, Fielded m) {
			if (n == 0) {
				return 0;
			}
			int v = count(n - 1, m);
			Object x = m.o;
			return v;
		}

		public static long count(long n, Fielded m) {
			if (n == 0) {
				return 0;
			}
			long v = count(n - 1, m);
			Object x = m.o;
			return v;
		}
	}
}
