package sipka.jvm.tailrec.saker.build.zip;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.NavigableSet;
import java.util.Set;

import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.trace.BuildTrace;
import saker.nest.scriptinfo.reflection.annot.NestInformation;

@NestInformation(
		value = "Creates a ZIP resource transformer that performs tail recursion optimization on Java class files.\n"
				+ "The output of this task can be used as the Transformer inputs to ZIP archive creation tasks. "
				+ "(saker.zip.create and related tasks.)")
public class OptimizerZipTransformerTaskFactory
		implements TaskFactory<OptimizerZipResourceTransformerFactory>, Externalizable {
	private static final long serialVersionUID = 1L;

	private static final NavigableSet<String> CAPABILITIES = ImmutableUtils
			.singletonNavigableSet(CAPABILITY_SHORT_TASK);

	public static final String TASK_NAME = "sipka.jvm.tailrec.zip.transformer";

	@Override
	public Task<? extends OptimizerZipResourceTransformerFactory> createTask(ExecutionContext executioncontext) {
		return new Task<OptimizerZipResourceTransformerFactory>() {
			@Override
			public OptimizerZipResourceTransformerFactory run(TaskContext taskcontext) throws Exception {
				if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_006) {
					BuildTrace.classifyTask(BuildTrace.CLASSIFICATION_CONFIGURATION);
				}
				return OptimizerZipResourceTransformerFactory.INSTANCE;
			}
		};
	}

	@Override
	public Set<String> getCapabilities() {
		return CAPABILITIES;
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
