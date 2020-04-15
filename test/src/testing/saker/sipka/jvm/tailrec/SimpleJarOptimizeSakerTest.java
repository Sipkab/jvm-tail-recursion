package testing.saker.sipka.jvm.tailrec;

import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderDataFinder;
import saker.build.thirdparty.saker.util.classloader.MultiDataClassLoader;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.TestUtils;
import testing.saker.nest.util.NestRepositoryCachingEnvironmentTestCase;

@SakerTest
public class SimpleJarOptimizeSakerTest extends NestRepositoryCachingEnvironmentTestCase {

	@Override
	protected void runNestTaskTestImpl() throws Throwable {
		CombinedTargetTaskResult res = runScriptTask("build");
		SakerPath outpath = (SakerPath) res.getTargetTaskResult("path");

		Map<String, ByteArrayRegion> resourceBytes = new TreeMap<>();
		try (InputStream is = ByteSource.toInputStream(files.openInput(outpath));
				ZipInputStream zis = new ZipInputStream(is)) {
			for (ZipEntry entry; (entry = zis.getNextEntry()) != null;) {
				resourceBytes.put(entry.getName(), StreamUtils.readStreamFully(zis));
			}
		}

		try (ClassLoaderDataFinder jarfinder = new TestUtils.MemoryClassLoaderDataFinder(resourceBytes)) {
			MultiDataClassLoader cl = new MultiDataClassLoader(jarfinder);
			Class<?> c = Class.forName("test.Main", false, cl);
			//this should succeed, as it was optimized
			c.getMethod("count", int.class).invoke(null, 10000000);
		}

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());
	}

}
