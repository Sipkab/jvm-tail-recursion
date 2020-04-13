package testing.sipka.jvm.tailrec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import sipka.jvm.tailrec.TailRecursionOptimizer;
import testing.saker.SakerTestCase;

public abstract class TailRecOptimizerTestCase extends SakerTestCase {
	protected DefiningClassLoader definingClassLoader = new DefiningClassLoader();

	protected Class<?> optimizeClass(Class<?> c) {
		String cname = c.getName();
		Class<?> defined = definingClassLoader.getDefinedClass(cname);
		if (defined != null) {
			return defined;
		}
		byte[] cbytes = getResourceBytes(c.getClassLoader(), cname.replace(".", "/") + ".class");
		byte[] optimizedbytes = TailRecursionOptimizer.optimizeMethods(cbytes);
		return definingClassLoader.defineUserClass(c.getName(), optimizedbytes);
	}

	private static byte[] getResourceBytes(ClassLoader cl, String resourcename) {
		try (InputStream is = cl.getResourceAsStream(resourcename)) {
			if (is == null) {
				return null;
			}
			try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
				byte[] buffer = new byte[2048];
				for (int read; (read = is.read(buffer)) > 0;) {
					baos.write(buffer, 0, read);
				}
				return baos.toByteArray();
			}
		} catch (IOException e) {
		}
		return null;
	}

	public static byte[] getClassBytesUsingClassLoader(Class<?> clazz) {
		return getResourceBytes(clazz.getClassLoader(), clazz.getName().replace('.', '/') + ".class");
	}

	public void assertSuccessfulOptimization(Method unoptimizedmethod, Object... args) throws Throwable {
		assertSuccessfulOptimizationWithException(unoptimizedmethod, StackOverflowError.class, args);
	}

	public void assertSuccessfulOptimizationWithException(Method unoptimizedmethod,
			Class<? extends Throwable> exceptiontype, Object... args) throws ClassNotFoundException,
			NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
		Class<?> optimizedclass = optimizeClass(unoptimizedmethod.getDeclaringClass());
		Class<?>[] paramtypes = unoptimizedmethod.getParameterTypes();
		for (int i = 0; i < paramtypes.length; i++) {
			if (!paramtypes[i].isPrimitive()) {
				paramtypes[i] = Class.forName(paramtypes[i].getName(), false, definingClassLoader);
			}
		}
		Method optimizedmethod = optimizedclass.getMethod(unoptimizedmethod.getName(), paramtypes);
		assertSuccessfulMethodsOptimizationWithException(unoptimizedmethod, optimizedmethod, exceptiontype, args);
	}

	private static void assertSuccessfulMethodsOptimization(Method unoptimized, Method optimized, Object... args)
			throws Throwable {
		assertSuccessfulMethodsOptimizationWithException(unoptimized, optimized, StackOverflowError.class, args);
	}

	private static void assertSuccessfulMethodsOptimizationWithException(Method unoptimized, Method optimized,
			Class<? extends Throwable> expectedexceptiontype, Object... args)
			throws IllegalAccessException, InvocationTargetException, InstantiationException, NoSuchMethodException {
		assertOptimizationException(expectedexceptiontype,
				() -> unoptimized.invoke(createMethodCallArgument(unoptimized), args));
		optimized.invoke(createMethodCallArgument(optimized), args);
	}

	private static <T extends Throwable> T assertOptimizationException(Class<T> exceptionclass,
			ExceptionAssertion runner) {
		return assertException(exceptionclass, () -> {
			try {
				runner.run();
			} catch (InvocationTargetException e) {
				throw e.getCause();
			}
		});
	}

	private static Object createMethodCallArgument(Method method)
			throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		return Modifier.isStatic(method.getModifiers()) ? null
				: method.getDeclaringClass().getConstructor().newInstance();
	}

	public static class DefiningClassLoader extends ClassLoader {
		public final Class<?> defineUserClass(String name, byte[] b, int off, int len) {
			Class<?> loaded = findLoadedClass(name);
			if (loaded != null) {
				return loaded;
			}
			return defineClass(name, b, off, len);
		}

		public final Class<?> defineUserClass(String name, byte[] b) {
			Class<?> loaded = findLoadedClass(name);
			if (loaded != null) {
				return loaded;
			}
			return defineClass(name, b, 0, b.length);
		}

		public Class<?> getDefinedClass(String name) {
			return findLoadedClass(name);
		}
	}
}
