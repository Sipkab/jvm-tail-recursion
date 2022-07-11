package testing.sipka.jvm.tailrec;

import java.util.Map;

import testing.saker.SakerTest;

@SakerTest
public class LookupSwitchReturnPathTest extends TailRecOptimizerTestCase {
	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		assertSuccessfulOptimization(TestMethods.class.getMethod("count", int.class, int.class), 10000000, 10);
	}

	public static class TestMethods {

		public static int count(int n, int sw) {
			if (n == 0) {
				return 0;
			}
			int v = count(n - 1, sw);
			switch (sw) {
				case Integer.MIN_VALUE:
					++sw;
					break;
				case 1:
					--sw;
					break;
				case Integer.MAX_VALUE:
					sw += 3;
					break;
				default:
					sw += 4;
					break;
			}
			return v;
		}

	}
}
