package testing.sipka.jvm.tailrec;

import java.util.Map;

import testing.saker.SakerTest;

/**
 * Just tests that the optimization doesn't crash for some edge cases.
 */
@SakerTest
public class OptimizationImplEdgeCasesTest extends TailRecOptimizerTestCase {

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		optimizeClass(TestMethods.class);
		assertNotOptimized(TestMethods.class.getMethod("whileStartCount", int.class), 1000000);
	}

	public static class TestMethods {
		public TestMethods() {
		}

		public TestMethods(int i) {
			this();
		}

		static {
			System.out.println("OptimizationImplEdgeCasesTest.TestMethods.<clinit>()");
		}

		public static void infloop() {
			infloop();
			while (true) {
			}
		}

		public static void trySynchronized() {
			try {
				synchronized (TestMethods.class) {
					System.out.println("trySynchronized()");
				}
			} catch (Exception e) {
			}
		}

		public static void mixedArguments(int i, long l, double d, boolean b, short s) {
			++i;
			++l;
			++d;
			b = !b;
			++s;
			mixedArguments(i, l, d, b, s);
		}

		public final void mixedInstanceArguments(int i, long l, double d, boolean b, short s) {
			++i;
			++l;
			++d;
			b = !b;
			++s;
			mixedArguments(i, l, d, b, s);
		}

		public static int whileStartCount(int n) {
			while (true) {
				if (n == 0) {
					break;
				}
				whileStartCount(n - 1);
			}
			if (n == 0) {
				return 0;
			}
			return whileStartCount(n - 1);
		}

		//TODO these methods could be optimizable, as the lock is loaded with LDC
		public static void insynchCount(int n) {
			synchronized (TestMethods.class) {
				if (n == 0) {
					return;
				}
				insynchCount(n - 1);
				return;
			}
		}

		public static void insynchCount(long n) {
			synchronized (TestMethods.class) {
				if (n == 0) {
					return;
				}
				insynchCount(n - 1);
				return;
			}
		}
	}
}
