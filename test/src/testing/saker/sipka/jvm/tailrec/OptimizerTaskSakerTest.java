package testing.saker.sipka.jvm.tailrec;

import java.lang.reflect.InvocationTargetException;

import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderDataFinder;
import saker.build.thirdparty.saker.util.classloader.MultiDataClassLoader;
import saker.build.util.classloader.SakerPathClassLoaderDataFinder;
import testing.saker.SakerTest;
import testing.saker.nest.util.NestRepositoryCachingEnvironmentTestCase;

@SakerTest
public class OptimizerTaskSakerTest extends NestRepositoryCachingEnvironmentTestCase {

	@Override
	protected void runNestTaskTestImpl() throws Throwable {
		SakerPath mainjavapath = PATH_WORKING_DIRECTORY.resolve("src/test/Main.java");

		CombinedTargetTaskResult res = runScriptTask("build");

		testOptimization((SakerPath) res.getTargetTaskResult("path"));

		res = runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());

		files.putFile(mainjavapath, files.getAllBytes(mainjavapath).toString().replace("n - 1", "n - 2"));
		res = runScriptTask("build");
		assertNotEmpty(getMetric().getRunTaskIdResults());
		testOptimization((SakerPath) res.getTargetTaskResult("path"));

		//make the file non-optimizable
		//test proper incremental operation
		files.putFile(mainjavapath, files.getAllBytes(mainjavapath).toString().replace("count(n - 2);",
				"count(n - 2);System.out.println();"));
		res = runScriptTask("build");
		assertNotEmpty(getMetric().getRunTaskIdResults());
		try {
			testOptimization((SakerPath) res.getTargetTaskResult("path"));
			fail("no stackoverflow caught");
		} catch (StackOverflowError e) {
			//this is expected
		}
	}

	private void testOptimization(SakerPath outpath) throws Throwable {
		ClassLoaderDataFinder finder = null;
		try {
			finder = new SakerPathClassLoaderDataFinder(files, outpath);
			MultiDataClassLoader cl = new MultiDataClassLoader(finder);
			Class<?> c = Class.forName("test.Main", false, cl);
			//this should succeed, as it was optimized
			c.getMethod("count", int.class).invoke(null, 10000000);
		} catch (InvocationTargetException e) {
			throw e.getCause();
		} finally {
			if (finder != null) {
				finder.close();
			}
		}
	}

}
