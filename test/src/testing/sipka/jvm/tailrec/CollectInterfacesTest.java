package testing.sipka.jvm.tailrec;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import testing.saker.SakerTest;

/**
 * As seen in the README.
 */
@SakerTest
public class CollectInterfacesTest extends TailRecOptimizerTestCase {

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		optimizeClass(MockClass.class);
		assertSuccessfulOptimization(TestMethods.class.getMethod("collect", int.class), 10000000);
	}

	public static class TestMethods {
		public static Set<MockClass> collect(int depth) {
			Set<MockClass> result = new HashSet<MockClass>();
			collectInterfaces(new MockClass(depth), result);
			return result;
		}

		public static void collectInterfaces(MockClass clazz, Set<MockClass> result) {

			for (MockClass itf : clazz.getInterfaces()) {
				if (result.add(itf))
					collectInterfaces(itf, result);
			}
			MockClass sclass = clazz.getSuperclass();
			if (sclass != null) {
				collectInterfaces(sclass, result);

			}

		}
	}

	public static class MockClass {
		private int count;

		public MockClass(int count) {
			this.count = count;
		}

		public MockClass[] getInterfaces() {
			if (count == 0) {
				return new MockClass[0];
			}
			MockClass[] result = new MockClass[count % 4];
			for (int i = 0; i < result.length; i++) {
				result[i] = new MockClass((count - 1) % result.length);
			}
			return result;
		}

		public MockClass getSuperclass() {
			if (count == 0) {
				return null;
			}
			return new MockClass(count - 1);
		}

		@Override
		public int hashCode() {
			return count;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			MockClass other = (MockClass) obj;
			if (count != other.count)
				return false;
			return true;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("MockClass[count=");
			builder.append(count);
			builder.append("]");
			return builder.toString();
		}
	}

}
