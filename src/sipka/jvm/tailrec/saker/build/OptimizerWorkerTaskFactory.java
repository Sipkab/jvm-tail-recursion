package sipka.jvm.tailrec.saker.build;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;

import saker.build.file.DelegateSakerFile;
import saker.build.file.SakerDirectory;
import saker.build.file.SakerFile;
import saker.build.file.content.ContentDescriptor;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.CommonTaskContentDescriptors;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.utils.dependencies.RecursiveFileCollectionStrategy;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.trace.BuildTrace;

public class OptimizerWorkerTaskFactory implements TaskFactory<SakerPath>, Task<SakerPath>, Externalizable {
	private static final long serialVersionUID = 1L;

	private SakerPath input;

	/**
	 * For {@link Externalizable}.
	 */
	public OptimizerWorkerTaskFactory() {
	}

	public OptimizerWorkerTaskFactory(SakerPath input) {
		this.input = input;
	}

	@Override
	public Task<? extends SakerPath> createTask(ExecutionContext executioncontext) {
		return this;
	}

	@Override
	public SakerPath run(TaskContext taskcontext) throws Exception {
		if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
			BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_WORKER);
			BuildTrace.setDisplayInformation("opt.tailrec", OptimizerTaskFactory.TASK_NAME + ":" + input.getFileName());
		}
		OptimizerWorkerTaskIdentifier workertaskid = (OptimizerWorkerTaskIdentifier) taskcontext.getTaskId();

		NavigableMap<SakerPath, SakerFile> inputfiles = taskcontext.getTaskUtilities()
				.collectFilesReportInputFileAndAdditionDependency(null, RecursiveFileCollectionStrategy.create(input));
		if (inputfiles.isEmpty()) {
			//check that it is a directory. if not, throw an appropriate exception
			SakerFile inputfile = taskcontext.getTaskUtilities().resolveAtPath(input);
			if (inputfile == null) {
				taskcontext.reportInputFileDependency(null, input, CommonTaskContentDescriptors.NOT_PRESENT);
				taskcontext.abortExecution(new NoSuchFileException(input.toString()));
				return null;
			}
			if (!(inputfile instanceof SakerDirectory)) {
				taskcontext.reportInputFileDependency(null, input, CommonTaskContentDescriptors.IS_NOT_DIRECTORY);
				taskcontext.abortExecution(new NotDirectoryException(input.toString()));
				return null;
			}
		}

		SakerDirectory outputbuilddir = taskcontext.getTaskUtilities().resolveDirectoryAtRelativePathCreate(
				taskcontext.getTaskBuildDirectory(),
				SakerPath.valueOf(OptimizerTaskFactory.TASK_NAME).append(workertaskid.getOutputRelativePath()));
		SakerPath outputdirpath = outputbuilddir.getSakerPath();

		NavigableMap<SakerPath, SakerFile> relativeinputfiles = SakerPathFiles.relativizeSubPath(inputfiles, input);
		NavigableMap<SakerPath, ContentDescriptor> outputcontents = new TreeMap<>();

		//TODO make incremental (although the directory synchronization provides necessary incrementality)
		outputbuilddir.clear();
		for (Entry<SakerPath, SakerFile> entry : relativeinputfiles.entrySet()) {
			SakerFile f = entry.getValue();
			if (f instanceof SakerDirectory) {
				//just resolve the path to have the directory created
				taskcontext.getTaskUtilities().resolveDirectoryAtRelativePathCreate(outputbuilddir, entry.getKey());
				continue;
			}
			SakerDirectory outdir = taskcontext.getTaskUtilities().resolveDirectoryAtRelativePathCreate(outputbuilddir,
					entry.getKey().getParent());
			SakerFile outfile;
			if (f.getName().endsWith(".class")) {
				outfile = new OptimizedSakerFile(f);
			} else {
				outfile = new DelegateSakerFile(f);
			}
			outdir.add(outfile);
			outputcontents.put(outputdirpath.resolve(entry.getKey()), outfile.getContentDescriptor());
		}

		outputbuilddir.synchronize();
		taskcontext.getTaskUtilities().reportOutputFileDependency(null, outputcontents);

		return outputdirpath;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(input);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		input = SerialUtils.readExternalObject(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((input == null) ? 0 : input.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		OptimizerWorkerTaskFactory other = (OptimizerWorkerTaskFactory) obj;
		if (input == null) {
			if (other.input != null)
				return false;
		} else if (!input.equals(other.input))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "OptimizerWorkerTaskFactory[input=" + input + "]";
	}

}
