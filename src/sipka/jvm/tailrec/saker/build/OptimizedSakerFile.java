package sipka.jvm.tailrec.saker.build;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import saker.build.exception.InvalidPathFormatException;
import saker.build.file.SakerFile;
import saker.build.file.SakerFileBase;
import saker.build.file.content.ContentDescriptor;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import sipka.jvm.tailrec.TailRecursionOptimizer;

public class OptimizedSakerFile extends SakerFileBase {
	private SakerFile subject;

	public OptimizedSakerFile(SakerFile subject) throws NullPointerException, InvalidPathFormatException {
		super(subject.getName());
		this.subject = subject;
	}

	@Override
	public ContentDescriptor getContentDescriptor() {
		return new OptimizedContentDescriptor(subject.getContentDescriptor());
	}

	@Override
	public void writeToStreamImpl(OutputStream os) throws IOException, NullPointerException {
		byte[] optimized = getOptimizedBytes();
		os.write(optimized);
	}

	@Override
	public ByteArrayRegion getBytesImpl() throws IOException {
		byte[] optimized = getOptimizedBytes();
		return ByteArrayRegion.wrap(optimized);
	}

	private byte[] getOptimizedBytes() throws IOException {
		ByteArrayRegion inbytes = subject.getBytes();
		byte[] inarray = inbytes.getArray();
		byte[] optimized = TailRecursionOptimizer.optimizeMethods(inarray, inbytes.getOffset(), inbytes.getLength());
		return optimized;
	}

	@Override
	public InputStream openInputStreamImpl() throws IOException {
		return new UnsyncByteArrayInputStream(getOptimizedBytes());
	}

	@Override
	public ByteSource openByteSourceImpl() throws IOException {
		return new UnsyncByteArrayInputStream(getOptimizedBytes());
	}

}
