package testing.sipka.jvm.tailrec;

import java.util.Map;

import testing.saker.SakerTest;

@SakerTest
public class VariableCounterTest extends TailRecOptimizerTestCase {

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		callMethodsWithArgType(int.class);
		callMethodsWithArgType(long.class);
	}

	private void callMethodsWithArgType(Class<?> t) throws Throwable, NoSuchMethodException {
		assertSuccessfulOptimization(TestMethods.class.getMethod("varCount1", t), 10000000);
		assertSuccessfulOptimization(TestMethods.class.getMethod("varCount2", t), 10000000);
		assertSuccessfulOptimization(TestMethods.class.getMethod("varCount3", t), 10000000);
		assertSuccessfulOptimization(TestMethods.class.getMethod("varCount4", t), 10000000);
		assertSuccessfulOptimization(TestMethods.class.getMethod("varCount5", t), 10000000);
	}

	public static class TestMethods {
		public static int varCount1(int n) {
			if (n == 0) {
				return 0;
			}
			int v = varCount1(n - 1);
			return v;
		}

		public static int varCount2(int n) {
			int v = n == 0 ? 0 : varCount2(n - 1);
			return v;
		}

		public static int varCount3(int n) {
			int v = n != 0 ? varCount3(n - 1) : 0;
			return v;
		}

		public static int varCount4(int n) {
			int v;
			if (n == 0) {
				v = 0;
			} else {
				v = varCount4(n - 1);
			}
			return v;
		}

		public static int varCount5(int n) {
			int v;
			if (n != 0) {
				v = varCount5(n - 1);
			} else {
				v = 0;
			}
			return v;
		}

		public static long varCount1(long n) {
			if (n == 0) {
				return 0;
			}
			long v = varCount1(n - 1);
			return v;
		}

		public static long varCount2(long n) {
			long v = n == 0 ? 0 : varCount2(n - 1);
			return v;
		}

		public static long varCount3(long n) {
			long v = n != 0 ? varCount3(n - 1) : 0;
			return v;
		}

		public static long varCount4(long n) {
			long v;
			if (n == 0) {
				v = 0;
			} else {
				v = varCount4(n - 1);
			}
			return v;
		}

		public static long varCount5(long n) {
			long v;
			if (n != 0) {
				v = varCount5(n - 1);
			} else {
				v = 0;
			}
			return v;
		}
	}
}
