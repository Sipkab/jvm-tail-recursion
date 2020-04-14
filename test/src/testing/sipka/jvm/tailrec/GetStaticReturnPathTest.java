package testing.sipka.jvm.tailrec;

import java.util.Map;

import testing.saker.SakerTest;

@SakerTest
public class GetStaticReturnPathTest extends TailRecOptimizerTestCase {
	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		//the getstatic instruction has been optimized away, or else NoClassDefFoundError would be thrown
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
			Fielded x = Fielded.o;
			return v;
		}

		public static long count(long n) {
			if (n == 0) {
				return 0;
			}
			long v = count(n - 1);
			Fielded x = Fielded.o;
			return v;
		}
	}
}
