package testing.sipka.jvm.tailrec;

import java.util.Map;

import testing.saker.SakerTest;

@SakerTest
public class TypeCastCountTest extends TailRecOptimizerTestCase {
	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		assertSuccessfulOptimization(TestMethods.class.getMethod("castingCount", int.class), 10000000);
	}

	public static class TestMethods {
		public static CharSequence castingCount(int n) {
			if (n == 0) {
				return "";
			}
			String v = (String) castingCount(n - 1);
			return v;
		}
	}
}
