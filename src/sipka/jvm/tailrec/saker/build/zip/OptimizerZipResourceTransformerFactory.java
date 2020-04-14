package sipka.jvm.tailrec.saker.build.zip;

import java.io.Externalizable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;

import saker.build.file.path.SakerPath;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.zip.api.create.ZipResourceTransformationContext;
import saker.zip.api.create.ZipResourceTransformer;
import saker.zip.api.create.ZipResourceTransformerFactory;
import sipka.jvm.tailrec.TailRecursionOptimizer;

public class OptimizerZipResourceTransformerFactory implements ZipResourceTransformerFactory, Externalizable {
	private static final long serialVersionUID = 1L;
	
	public static final OptimizerZipResourceTransformerFactory INSTANCE = new OptimizerZipResourceTransformerFactory();

	/**
	 * For {@link Externalizable}.
	 */
	public OptimizerZipResourceTransformerFactory() {
	}

	@Override
	public ZipResourceTransformer createTransformer() {
		return new ZipResourceTransformer() {
			@Override
			public boolean process(ZipResourceTransformationContext context, SakerPath resourcepath,
					InputStream resourceinput) throws IOException {
				if (!resourcepath.getFileName().endsWith(".class")) {
					return false;
				}
				ByteArrayRegion contents = StreamUtils.readStreamFully(resourceinput);
				byte[] array = contents.getArray();
				byte[] optimized = TailRecursionOptimizer.optimizeMethods(array, contents.getOffset(),
						contents.getLength());
				if (array == optimized) {
					//not optimized
					return false;
				}
				try (OutputStream out = context.appendFile(resourcepath, null)) {
					out.write(optimized);
				}
				return true;
			}
		};
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	}

	@Override
	public int hashCode() {
		return getClass().getName().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return ObjectUtils.isSameClass(this, obj);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[]";
	}

}
