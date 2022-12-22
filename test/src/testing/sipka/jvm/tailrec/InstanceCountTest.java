package testing.sipka.jvm.tailrec;

import java.util.Map;

import testing.saker.SakerTest;

@SakerTest
public class InstanceCountTest extends TailRecOptimizerTestCase {
	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		assertSuccessfulOptimization(TestMethods.class.getMethod("countInstance", int.class), 10000000);
		assertSuccessfulOptimization(TestMethods.class.getMethod("countInstance", long.class), 10000000);
		
		assertSuccessfulOptimization(InstanceCounter.class.getMethod("countViaInstance", int.class), 10000000);
	}

	public static class TestMethods {
		public void countInstance(int n) {
			countInstanceImpl(n);
		}

		private void countInstanceImpl(int n) {
			if (n == 0) {
				return;
			}
			new TestMethods().countInstanceImpl(n - 1);
		}

		public void countInstance(long n) {
			countInstanceImpl(n);
		}

		private void countInstanceImpl(long n) {
			if (n == 0) {
				return;
			}
			new TestMethods().countInstanceImpl(n - 1);
		}
	}

	public static class InstanceCounter {
		private final int n;

		public InstanceCounter(int n) {
			this.n = n;
		}

		private void count() {
			if (n == 0) {
				return;
			}
			new InstanceCounter(n - 1).count();
		}

		public static void countViaInstance(int n) {
			new InstanceCounter(n).count();
		}
	}
}
