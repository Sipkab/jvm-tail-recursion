package testing.sipka.jvm.tailrec;

import java.util.Map;

import testing.saker.SakerTest;

@SakerTest
public class InterfaceDefaultMethodTest extends TailRecOptimizerTestCase {
	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		optimizeClass(TestMethods.class);
		Class<?> optimizedimpl = optimizeClass(Impl.class);
		assertInvocationException(StackOverflowError.class, () -> optimizedimpl.getMethod("count", int.class)
				.invoke(optimizedimpl.getConstructor().newInstance(), 100000));
	}

	public static interface TestMethods {
		public default void count(int n) {
			if (n == 0) {
				return;
			}
			count(n - 1);
		}
	}

	public static class Impl implements TestMethods {
	}
}
