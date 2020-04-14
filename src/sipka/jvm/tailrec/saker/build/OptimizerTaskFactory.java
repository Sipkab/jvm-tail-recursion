package sipka.jvm.tailrec.saker.build;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskContext;
import saker.build.task.utils.SimpleStructuredObjectTaskResult;
import saker.build.task.utils.annot.SakerInput;
import saker.build.task.utils.dependencies.EqualityTaskOutputChangeDetector;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.trace.BuildTrace;
import saker.nest.scriptinfo.reflection.annot.NestInformation;
import saker.nest.scriptinfo.reflection.annot.NestParameterInformation;
import saker.nest.scriptinfo.reflection.annot.NestTaskInformation;
import saker.nest.scriptinfo.reflection.annot.NestTypeUsage;
import saker.nest.utils.FrontendTaskFactory;

@NestTaskInformation(returnType = @NestTypeUsage(SakerPath.class))
@NestInformation("Performs tail recursion optimizations on the specified class directory.\n"
		+ "The task will optimize all Java .class bytecode files for tail recursions and write the "
		+ "output to another build directory location with the same hierarchy.\n"
		+ "The Input for the task must be a class directory.\n" + "The output is the path to the output directory.")

@NestParameterInformation(value = "Directory",
		aliases = { "", "Input" },
		required = true,
		type = @NestTypeUsage(SakerPath.class),
		info = @NestInformation("The path to the input class directory."))
public class OptimizerTaskFactory extends FrontendTaskFactory<Object> {
	private static final long serialVersionUID = 1L;

	public static final String TASK_NAME = "sipka.jvm.tailrec.optimize";

	@Override
	public ParameterizableTask<? extends Object> createTask(ExecutionContext executioncontext) {
		return new ParameterizableTask<Object>() {

			@SakerInput(value = { "", "Directory", "Input" }, required = true)
			public SakerPath inputOption;

			@Override
			public Object run(TaskContext taskcontext) throws Exception {
				if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
					BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_FRONTEND);
				}
				SakerPath input = taskcontext.getTaskWorkingDirectoryPath().tryResolve(inputOption);

				SakerPath builddirpath = SakerPathFiles.requireBuildDirectoryPath(taskcontext);
				SakerPath outputrelpath;
				if (input.startsWith(builddirpath)) {
					outputrelpath = builddirpath.relativize(input);
				} else {
					outputrelpath = SakerPath.valueOf(StringUtils.toHexString(FileUtils.hashString(input.toString())));
				}

				OptimizerWorkerTaskIdentifier workertaskid = new OptimizerWorkerTaskIdentifier(outputrelpath);
				taskcontext.startTask(workertaskid, new OptimizerWorkerTaskFactory(input), null);

				SimpleStructuredObjectTaskResult result = new SimpleStructuredObjectTaskResult(workertaskid);
				taskcontext.reportSelfTaskOutputChangeDetector(new EqualityTaskOutputChangeDetector(result));
				return result;
			}
		};
	}

}
