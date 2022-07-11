package testing.sipka.jvm.tailrec;

import java.util.Map;

import testing.saker.SakerTest;

@SakerTest
public class LambdaCountTest extends TailRecOptimizerTestCase {
	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		optimizeClass(CounterInt.class);
		optimizeClass(CounterLong.class);

		assertSuccessfulOptimization(TestMethods.class.getMethod("countViaLambda", int.class), 10000000);
		assertSuccessfulOptimization(TestMethods.class.getMethod("countViaLambda", long.class), 10000000);
	}

	public interface CounterInt {
		public void lambdacount(int n);
	}

	public interface CounterLong {
		public void lambdacount(long n);
	}

	public static class TestMethods {
		public static void countViaLambda(int startn) {
			CounterInt c = (n) -> {
				count(n);
			};
			c.lambdacount(startn);
		}

		public static void countViaLambda(long startn) {
			CounterLong c = (n) -> {
				count(n);
			};
			c.lambdacount(startn);
		}

		public static void count(int n) {
			if (n == 0) {
				return;
			}
			count(n - 1);
		}

		public static void count(long n) {
			if (n == 0) {
				return;
			}
			count(n - 1);
		}
	}
}
