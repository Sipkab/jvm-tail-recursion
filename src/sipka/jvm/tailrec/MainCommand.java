package sipka.jvm.tailrec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.CopyOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Iterator;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import sipka.cmdline.api.Command;
import sipka.cmdline.api.CommonConverter;
import sipka.cmdline.api.Flag;
import sipka.cmdline.api.Parameter;
import sipka.cmdline.api.PositionalParameter;

/**
 * <pre>
 * Tail recursion optimizer for Java bytecode.
 * 
 *     https://github.com/Sipkab/jvm-tail-recursion
 * 
 * The program takes Java class files as its input and optimizes tail recursive calls 
 * in a way that it won't overflow the stack.
 * 
 * An example for a tail recursive call is:
 * 
 * 	public static void count(int n) {
 * 		if (n == 0) {
 * 			return;
 * 		}
 * 		count(n - 1);
 * 	}
 * 
 * In which the last instruction of a given function is a call to the same function.
 * The above function will be transformed to the following:
 * 
 * 	public static void count(int n) {
 * 		while (true) {
 * 			if (n == 0) {
 * 				return;
 * 			}
 * 			n = n - 1;
 * 		}
 * 	}
 * 
 * 
 * The program may perform optimizations related to removing instructions that have no 
 * side-effect and are after a tail recursive call. E.g.:
 * 
 * 	public static void count(int n) {
 * 		if (n == 0) {
 * 			return;
 * 		}
 * 		count(n - 1);
 * 		int k = n * 2;
 * 	}
 * 
 * In the above, the int k = n * 2; line will be removed as part of the optimizations.
 * </pre>
 */
@Command(className = ".Main", main = true, helpCommand = { "-h", "help", "-help", "--help", "?", "/?" })
@CommonConverter(type = Path.class, converter = MainCommand.class, method = "toPath")
public class MainCommand {
	/**
	 * <pre>
	 * Sets the output path where the optimized class files
	 * should be written.
	 * 
	 * The output will have the same format as the input.
	 * That is:
	 * 	JAR -&gt; JAR
	 * 	Class directory -&gt; Class directory
	 * 	Class file -&gt; Class file
	 * </pre>
	 */
	@Parameter(value = "-output")
	public Path output;

	/**
	 * <pre>
	 * Flag that specifies that the output files can be overwritten.
	 * 
	 * If the -output parameter is not specified, this flag sets
	 * that the input files should be overwritten.
	 * 
	 * If the -output parameter is set, this flag causes the process
	 * to overwrite any existing file at the output location. If the
	 * overwrite flag is not set, an exception may be thrown if a
	 * file at the output location already exists.
	 * </pre>
	 */
	@Parameter(value = "-overwrite")
	@Flag
	public Boolean overwrite;

	/**
	 * <pre>
	 * Path to the input class directory, ZIP archive, or 
	 * class file that should be optimized.
	 * 
	 * All files which have the .class extension will be
	 * subject to optimization.
	 * </pre>
	 */
	@Parameter(value = "input", required = true)
	@PositionalParameter(-1)
	public Path input;

	private CopyOption[] copyOptions;

	public void call() throws IOException {
		if (output == null && overwrite == null) {
			throw new IllegalArgumentException("No output specified. Use -output or -overwrite parameters.");
		}
		if (Boolean.TRUE.equals(overwrite)) {
			if (output == null) {
				output = input;
			}
			copyOptions = new CopyOption[] { StandardCopyOption.REPLACE_EXISTING };
		} else {
			copyOptions = new CopyOption[0];
		}
		input = input.toAbsolutePath().normalize();
		output = output.toAbsolutePath().normalize();
		BasicFileAttributes attrs = Files.readAttributes(input, BasicFileAttributes.class);
		if (attrs.isRegularFile()) {
			optimizeFile();
		} else if (attrs.isDirectory()) {
			optimizeDirectory();
		} else {
			throw new IOException("Unrecognized input file type: " + input);
		}
	}

	private void optimizeDirectory() throws IOException {
		Files.walkFileTree(input, new FileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Path outputpath = output.resolve(input.relativize(file));
				if (file.getFileName().toString().endsWith(".class")) {
					optimizeClassFile(file, outputpath);
				}else {
					Files.createDirectories(outputpath.getParent());
					Files.copy(file, outputpath, copyOptions);
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
				throw exc;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				if (exc != null) {
					throw exc;
				}
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private void optimizeFile() throws IOException {
		if (input.getFileName().toString().endsWith(".class")) {
			optimizeClassFile();
			return;
		}
		optimizeJar();
	}

	private void optimizeJar() throws IOException {
		Path tempout = output.resolveSibling(output.getFileName() + "." + UUID.randomUUID());
		byte[] bytebuf = new byte[1024 * 8];
		ByteArrayOutputStream buffer = new ByteArrayOutputStream(bytebuf.length);
		try (InputStream in = Files.newInputStream(input);
				ZipInputStream zis = new ZipInputStream(in)) {
			Files.createDirectories(output.getParent());
			try (OutputStream outos = Files.newOutputStream(tempout)) {
				try (ZipOutputStream zos = new ZipOutputStream(outos)) {

					for (ZipEntry entry; (entry = zis.getNextEntry()) != null;) {
						if (entry.isDirectory()) {
							zos.putNextEntry(entry);
							zos.closeEntry();
							continue;
						}
						if (!entry.getName().endsWith(".class")) {
							zos.putNextEntry(entry);
							copyInputStream(bytebuf, zis, zos);
							zos.closeEntry();
							continue;
						}
						byte[] entrybytes = readInputStreamFully(buffer, bytebuf, zis);
						byte[] optimizedclassbytes = TailRecursionOptimizer.optimizeMethods(entrybytes);

						zos.putNextEntry(cloneEntry(entry));
						zos.write(optimizedclassbytes);
						zos.closeEntry();
					}
				}
			}
		} catch (Throwable e) {
			//clean up temporary output in case of exception
			Files.deleteIfExists(tempout);
			throw e;
		}
		try {
			Files.move(tempout, output, copyOptions);
		} finally {
			Files.deleteIfExists(tempout);
		}
	}

	private void optimizeClassFile() throws IOException {
		Path inputpath = input;
		Path outputpath = output;

		optimizeClassFile(inputpath, outputpath);
	}

	private void optimizeClassFile(Path inputpath, Path outputpath) throws IOException {
		Path tempout = outputpath.resolveSibling(outputpath.getFileName() + "." + UUID.randomUUID());
		byte[] classbytes = Files.readAllBytes(inputpath);
		byte[] optimizedbytes = TailRecursionOptimizer.optimizeMethods(classbytes);
		if (classbytes == optimizedbytes && inputpath.equals(outputpath)) {
			return;
		}
		Files.createDirectories(outputpath.getParent());
		try {
			Files.write(tempout, optimizedbytes);
		} catch (Throwable e) {
			//clean up temporary output in case of exception
			Files.deleteIfExists(tempout);
			throw e;
		}
		try {
			Files.move(tempout, outputpath, copyOptions);
		} finally {
			Files.deleteIfExists(tempout);
		}
	}

	private static ZipEntry cloneEntry(ZipEntry entry) {
		ZipEntry nentry = new ZipEntry(entry.getName());
		FileTime ctime = entry.getCreationTime();
		if (ctime != null) {
			nentry.setCreationTime(ctime);
		}
		FileTime latime = entry.getLastAccessTime();
		if (latime != null) {
			nentry.setLastAccessTime(latime);
		}
		FileTime lmtime = entry.getLastModifiedTime();
		if (lmtime != null) {
			nentry.setLastModifiedTime(lmtime);
		}
		return nentry;
	}

	private static void copyInputStream(byte[] bytebuf, InputStream in, OutputStream os) throws IOException {
		for (int read; (read = in.read(bytebuf)) > 0;) {
			os.write(bytebuf, 0, read);
		}
	}

	private static byte[] readInputStreamFully(ByteArrayOutputStream buffer, byte[] bytebuf, ZipInputStream zis)
			throws IOException {
		buffer.reset();
		copyInputStream(bytebuf, zis, buffer);
		return buffer.toByteArray();
	}

	public static Path toPath(Iterator<? extends String> it) {
		return Paths.get(it.next());
	}
}
