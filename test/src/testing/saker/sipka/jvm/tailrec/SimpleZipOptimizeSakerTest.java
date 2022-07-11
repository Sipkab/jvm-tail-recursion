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
import saker.build.thirdparty.saker.util.io.ByteSourceInputStream;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.TestUtils;
import testing.saker.nest.util.NestRepositoryCachingEnvironmentTestCase;

@SakerTest
public class SimpleZipOptimizeSakerTest extends NestRepositoryCachingEnvironmentTestCase {

	@Override
	protected void runNestTaskTestImpl() throws Throwable {
		CombinedTargetTaskResult res = runScriptTask("build");
		SakerPath outpath = (SakerPath) res.getTargetTaskResult("path");

		Map<String, ByteArrayRegion> resourceBytes = new TreeMap<String, ByteArrayRegion>();
		InputStream is = null;
		try {
			is = new ByteSourceInputStream(files.openInput(outpath));
			ZipInputStream zis = null;
			try {
				zis = new ZipInputStream(is);
				for (ZipEntry entry; (entry = zis.getNextEntry()) != null;) {
					resourceBytes.put(entry.getName(), StreamUtils.readStreamFully(zis));
				}
			} finally {
				if (zis != null) {
					zis.close();
				}
			}
		} finally {
			if (is != null) {
				is.close();
			}
		}

		ClassLoaderDataFinder jarfinder = null;
		try {
			jarfinder = new TestUtils.MemoryClassLoaderDataFinder(resourceBytes);
			MultiDataClassLoader cl = new MultiDataClassLoader(jarfinder);
			Class<?> c = Class.forName("test.Main", false, cl);
			//this should succeed, as it was optimized
			c.getMethod("count", int.class).invoke(null, 10000000);
		} finally {
			if (jarfinder != null) {
				jarfinder.close();
			}
		}

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());
	}

}
