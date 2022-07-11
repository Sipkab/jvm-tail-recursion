/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package sipka.jvm.tailrec;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import sipka.jvm.tailrec.thirdparty.org.objectweb.asm.ClassReader;
import sipka.jvm.tailrec.thirdparty.org.objectweb.asm.ClassWriter;
import sipka.jvm.tailrec.thirdparty.org.objectweb.asm.Label;
import sipka.jvm.tailrec.thirdparty.org.objectweb.asm.Opcodes;
import sipka.jvm.tailrec.thirdparty.org.objectweb.asm.Type;
import sipka.jvm.tailrec.thirdparty.org.objectweb.asm.tree.AbstractInsnNode;
import sipka.jvm.tailrec.thirdparty.org.objectweb.asm.tree.ClassNode;
import sipka.jvm.tailrec.thirdparty.org.objectweb.asm.tree.FieldInsnNode;
import sipka.jvm.tailrec.thirdparty.org.objectweb.asm.tree.FrameNode;
import sipka.jvm.tailrec.thirdparty.org.objectweb.asm.tree.IincInsnNode;
import sipka.jvm.tailrec.thirdparty.org.objectweb.asm.tree.JumpInsnNode;
import sipka.jvm.tailrec.thirdparty.org.objectweb.asm.tree.LabelNode;
import sipka.jvm.tailrec.thirdparty.org.objectweb.asm.tree.LineNumberNode;
import sipka.jvm.tailrec.thirdparty.org.objectweb.asm.tree.LocalVariableNode;
import sipka.jvm.tailrec.thirdparty.org.objectweb.asm.tree.LookupSwitchInsnNode;
import sipka.jvm.tailrec.thirdparty.org.objectweb.asm.tree.MethodInsnNode;
import sipka.jvm.tailrec.thirdparty.org.objectweb.asm.tree.MethodNode;
import sipka.jvm.tailrec.thirdparty.org.objectweb.asm.tree.MultiANewArrayInsnNode;
import sipka.jvm.tailrec.thirdparty.org.objectweb.asm.tree.TableSwitchInsnNode;
import sipka.jvm.tailrec.thirdparty.org.objectweb.asm.tree.TryCatchBlockNode;
import sipka.jvm.tailrec.thirdparty.org.objectweb.asm.tree.VarInsnNode;

public class TailRecursionOptimizer {
	//turns out proguard already did this to some extent: 
	//    https://sourceforge.net/p/proguard/code/ci/default/tree/core/src/proguard/optimize/TailRecursionSimplifier.java

	private static final int ASM_API = Opcodes.ASM8;

	/**
	 * Optimizes out the possible tail recursive calls in the argument Java class bytecode.
	 * 
	 * @param classbytes
	 *            The Java class bytecode to optimize.
	 * @param offset
	 *            The offset in the array at which the class bytes start.
	 * @param length
	 *            The number of bytes the class bytecode consists of starting at offset.
	 * @return The optimized bytecode. The result is <code>null</code> if and only if the argument is <code>null</code>.
	 */
	public static byte[] optimizeMethods(byte[] classbytes, int offset, int length) {
		if (classbytes == null) {
			return null;
		}
		ClassWriter cw = optimizeMethods(new ClassReader(classbytes, offset, length));
		if (cw == null) {
			return Arrays.copyOfRange(classbytes, offset, offset + length);
		}
		return cw.toByteArray();
	}

	/**
	 * Optimizes out the possible tail recursive calls in the argument Java class bytecode.
	 * 
	 * @param classbytes
	 *            The Java class bytecode to optimize.
	 * @return The optimized bytecode. The return value will equal by identity (<code>==</code>) if there were no
	 *             optimizations performed. The result is <code>null</code> if and only if the argument is
	 *             <code>null</code>.
	 */
	public static byte[] optimizeMethods(byte[] classbytes) {
		if (classbytes == null) {
			return null;
		}
		ClassWriter cw = optimizeMethods(new ClassReader(classbytes));
		if (cw == null) {
			return classbytes;
		}
		return cw.toByteArray();
	}

	private static ClassWriter optimizeMethods(ClassReader cr) {
		ClassNode cn = new ClassNode(ASM_API);
		cr.accept(cn, ClassReader.EXPAND_FRAMES);
		boolean optimized = false;
		for (MethodNode mn : cn.methods) {
			boolean methodoptimized = optimizeMethod(cn, ((cn.access & Opcodes.ACC_INTERFACE) == Opcodes.ACC_INTERFACE),
					mn);
			optimized = optimized || methodoptimized;
		}
		if (!optimized) {
			return null;
		}

		ClassWriter cw = new ClassWriter(cr, 0);

		cn.accept(cw);
		return cw;
	}

	private static enum VarContents {
		RESULT,
		THIS,
		UNKNOWN,

		;
	}

	private static class VisitedLabelState {
		protected LabelNode labelNode;
		protected NavigableMap<Integer, Object> variableContents;

		public VisitedLabelState(LabelNode labelNode, NavigableMap<Integer, Object> variableContents) {
			this.labelNode = labelNode;
			this.variableContents = new TreeMap<>(variableContents);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((labelNode == null) ? 0 : labelNode.hashCode());
			result = prime * result + ((variableContents == null) ? 0 : variableContents.hashCode());
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
			VisitedLabelState other = (VisitedLabelState) obj;
			if (labelNode == null) {
				if (other.labelNode != null)
					return false;
			} else if (!labelNode.equals(other.labelNode))
				return false;
			if (variableContents == null) {
				if (other.variableContents != null)
					return false;
			} else if (!variableContents.equals(other.variableContents))
				return false;
			return true;
		}
	}

	private static boolean isTailOptimizable(AbstractInsnNode startnode, Object returntypeframetype,
			MethodInsnNode mins) {
		//we need to keep track of visited labels, so we dont get into an infinite loop if the bytecode represents an infinite loop
		//e.g.:
		//public static void infloop() {
		//	infloop();
		//	while (true) {
		//	}
		//}

		NavigableMap<Integer, Object> varnumcontents = new TreeMap<>();
		Set<? super VisitedLabelState> visitedlabelcache = new HashSet<>();
		LinkedList<Object> currentstack = new LinkedList<>();
		if (returntypeframetype != null) {
			if (returntypeframetype.equals(Opcodes.LONG) || returntypeframetype.equals(Opcodes.DOUBLE)) {
				currentstack.push(VarContents.RESULT);
			}
			currentstack.push(VarContents.RESULT);
		} //else no return value of the method, stack doesnt contain the return value

		return isTailOptimizableImpl(startnode, returntypeframetype, varnumcontents, visitedlabelcache, currentstack);
	}

	private static boolean isTailOptimizableImpl(AbstractInsnNode startnode, Object returntypeframetype,
			NavigableMap<Integer, Object> varnumcontents, Set<? super VisitedLabelState> visitedlabelcache,
			LinkedList<Object> currentstack) throws AssertionError {
		for (AbstractInsnNode ins = startnode; (ins = ins.getNext()) != null;) {
			switch (ins.getType()) {
				case AbstractInsnNode.FRAME: {
					FrameNode fins = (FrameNode) ins;
					if (fins.type != Opcodes.F_NEW) {
						throw new AssertionError(fins.type);
					}
					int varidx = 0;
					for (Object locobj : fins.local) {
						boolean wide = locobj.equals(Opcodes.LONG) || locobj.equals(Opcodes.DOUBLE);
						if (!Objects.equals(returntypeframetype, locobj)) {
							varnumcontents.remove(varidx);
							if (wide) {
								varnumcontents.remove(varidx + 1);
							}
						}
						if (wide) {
							varidx += 2;
						} else {
							varidx += 1;
						}
					}
					varnumcontents.tailMap(varidx, true).clear();
					//we don't modify the stack in case of new frame. It is the job of the JVM to verify its validity
					break;
				}
				case AbstractInsnNode.LINE: {
					break;
				}
				case AbstractInsnNode.LABEL: {
					VisitedLabelState labelstate = new VisitedLabelState((LabelNode) ins, varnumcontents);
					if (!visitedlabelcache.add(labelstate)) {
						//already visited target label, and re-encountered. it is optimizable
						return true;
					}
					break;
				}
				case AbstractInsnNode.JUMP_INSN: {
					switch (ins.getOpcode()) {
						case Opcodes.GOTO: {
							JumpInsnNode jmp = (JumpInsnNode) ins;
							VisitedLabelState labelstate = new VisitedLabelState(jmp.label, varnumcontents);
							if (!visitedlabelcache.add(labelstate)) {
								//already visited target label, and re-encountered. it is optimizable
								return true;
							}
							ins = jmp.label; // .getNext() is called as part of the for loop
							break;
						}
						case Opcodes.IFNULL:
						case Opcodes.IFNONNULL:
						case Opcodes.IFEQ:
						case Opcodes.IFNE:
						case Opcodes.IFLT:
						case Opcodes.IFGE:
						case Opcodes.IFGT:
						case Opcodes.IFLE: {
//							..., value 
//							...
							JumpInsnNode jmp = (JumpInsnNode) ins;
							VisitedLabelState labelstate = new VisitedLabelState(jmp.label, varnumcontents);
							if (!visitedlabelcache.add(labelstate)) {
								//already visited target label, and re-encountered. it is optimizable
								return true;
							}
							currentstack.pollFirst();
							if (!isTailOptimizableImpl(jmp.label, returntypeframetype, new TreeMap<>(varnumcontents),
									new HashSet<>(visitedlabelcache), new LinkedList<>(currentstack))) {
								return false;
							}
							//continue checking this branch
							break;
						}
						case Opcodes.IF_ICMPEQ:
						case Opcodes.IF_ICMPNE:
						case Opcodes.IF_ICMPLT:
						case Opcodes.IF_ICMPGE:
						case Opcodes.IF_ICMPGT:
						case Opcodes.IF_ICMPLE:
						case Opcodes.IF_ACMPEQ:
						case Opcodes.IF_ACMPNE: {
//							..., value1, value2 
//							...
							JumpInsnNode jmp = (JumpInsnNode) ins;
							VisitedLabelState labelstate = new VisitedLabelState(jmp.label, varnumcontents);
							if (!visitedlabelcache.add(labelstate)) {
								//already visited target label, and re-encountered. it is optimizable
								return true;
							}
							currentstack.pollFirst();
							currentstack.pollFirst();
							if (!isTailOptimizableImpl(jmp.label, returntypeframetype, new TreeMap<>(varnumcontents),
									new HashSet<>(visitedlabelcache), new LinkedList<>(currentstack))) {
								return false;
							}
							//continue checking this branch
							break;
						}
						case Opcodes.JSR: {
							return false;
						}
						default: {
							//unknown instruction
							return false;
						}
					}
					break;
				}
				case AbstractInsnNode.FIELD_INSN: {
					switch (ins.getOpcode()) {
						case Opcodes.GETSTATIC: {
							FieldInsnNode fins = (FieldInsnNode) ins;
							if ("J".equals(fins.desc) || "D".equals(fins.desc)) {
								//2x size
								currentstack.addFirst(VarContents.UNKNOWN);
							}
							currentstack.addFirst(VarContents.UNKNOWN);
							break;
						}
						case Opcodes.GETFIELD: {
							//a field read can be optimized away, if later we don't do anything with it
							//nullpointer exception can occurr, but it is fine to optimize that away
							currentstack.pollFirst();
							FieldInsnNode fins = (FieldInsnNode) ins;
							if ("J".equals(fins.desc) || "D".equals(fins.desc)) {
								//2x size
								currentstack.addFirst(VarContents.UNKNOWN);
							}
							currentstack.addFirst(VarContents.UNKNOWN);
							break;
						}
						default: {
							//unknown instruction
							return false;
						}
					}
					break;
				}
				case AbstractInsnNode.IINC_INSN: {
					IincInsnNode iincn = (IincInsnNode) ins;
					//remove the holding var, as it has been modified
					varnumcontents.remove(iincn.var);
					//doesn't change the stack
					break;
				}
				case AbstractInsnNode.TYPE_INSN: {
					switch (ins.getOpcode()) {
						case Opcodes.INSTANCEOF: {
							currentstack.pollFirst();
							currentstack.addFirst(VarContents.UNKNOWN);
							break;
						}
						case Opcodes.CHECKCAST: {
							//does not modify the stack
							//XXX create tests which check for any errors if we don't apply the cast to the representation regarding frames
							//    the optimization might omit the actual casting
							break;
						}
						case Opcodes.NEW: {
							currentstack.add(VarContents.UNKNOWN);
							break;
						}
						case Opcodes.ANEWARRAY: {
//							..., count 
//							..., arrayref

							currentstack.pollFirst();
							currentstack.add(VarContents.UNKNOWN);
							break;
						}
						default: {
							//unknown instruction
							return false;
						}
					}
					break;
				}
				case AbstractInsnNode.TABLESWITCH_INSN: {
					TableSwitchInsnNode tsn = (TableSwitchInsnNode) ins;
					currentstack.pollFirst();
					if (tsn.dflt != null) {
						VisitedLabelState labelstate = new VisitedLabelState(tsn.dflt, varnumcontents);
						if (visitedlabelcache.add(labelstate)) {
							//already visited target label, and re-encountered. it is optimizable
							if (!isTailOptimizableImpl(tsn.dflt, returntypeframetype, new TreeMap<>(varnumcontents),
									new HashSet<>(visitedlabelcache), new LinkedList<>(currentstack))) {
								return false;
							}
						}
					}
					if (tsn.labels != null) {
						for (LabelNode lbl : tsn.labels) {
							VisitedLabelState labelstate = new VisitedLabelState(lbl, varnumcontents);
							if (visitedlabelcache.add(labelstate)) {
								//already visited target label, and re-encountered. it is optimizable
								if (!isTailOptimizableImpl(lbl, returntypeframetype, new TreeMap<>(varnumcontents),
										new HashSet<>(visitedlabelcache), new LinkedList<>(currentstack))) {
									return false;
								}
							}
						}
					}
					break;
				}
				case AbstractInsnNode.LOOKUPSWITCH_INSN: {
					LookupSwitchInsnNode lsn = (LookupSwitchInsnNode) ins;
					currentstack.pollFirst();
					if (lsn.dflt != null) {
						VisitedLabelState labelstate = new VisitedLabelState(lsn.dflt, varnumcontents);
						if (visitedlabelcache.add(labelstate)) {
							//already visited target label, and re-encountered. it is optimizable
							if (!isTailOptimizableImpl(lsn.dflt, returntypeframetype, new TreeMap<>(varnumcontents),
									new HashSet<>(visitedlabelcache), new LinkedList<>(currentstack))) {
								return false;
							}
						}
					}
					if (lsn.labels != null) {
						for (LabelNode lbl : lsn.labels) {
							VisitedLabelState labelstate = new VisitedLabelState(lbl, varnumcontents);
							if (visitedlabelcache.add(labelstate)) {
								//already visited target label, and re-encountered. it is optimizable
								if (!isTailOptimizableImpl(lbl, returntypeframetype, new TreeMap<>(varnumcontents),
										new HashSet<>(visitedlabelcache), new LinkedList<>(currentstack))) {
									return false;
								}
							}
						}
					}
					break;
				}
				case AbstractInsnNode.INSN: {
					switch (ins.getOpcode()) {
						case Opcodes.RETURN: {
							//no need to check the stack
							return true;
						}
						case Opcodes.ARETURN:
						case Opcodes.FRETURN:
						case Opcodes.IRETURN: {
							if (currentstack.peekFirst() == VarContents.RESULT) {
								return true;
							}
							return false;
						}
						case Opcodes.DRETURN:
						case Opcodes.LRETURN: {
							Iterator<?> it = currentstack.iterator();
							if (it.next() == VarContents.RESULT && it.next() == VarContents.RESULT) {
								return true;
							}
							return false;
						}
						case Opcodes.ATHROW: {
							//XXX we could optimize throwing as well, but it is probably rarely used, and messes with the stack trace. 
							//    Could be toggleable with an optimization flag
							return false;
						}

						case Opcodes.DUP: {
							Object f = currentstack.peekFirst();
							if (f != null) {
								currentstack.addFirst(f);
							}
							break;
						}
						case Opcodes.DUP_X1: {
//							..., value2, value1 
//							..., value1, value2, value1
							Object f = currentstack.peekFirst();
							if (f != null) {
								ListIterator<Object> it = currentstack.listIterator();
								//first
								it.next();
								addStackElementIfDoesntExist(it);
								//add the first element
								it.add(f);
							}
							break;
						}
						case Opcodes.DUP_X2: {
//							..., value3, value2, value1 
//							..., value1, value3, value2, value1
							Object f = currentstack.peekFirst();
							if (f != null) {
								ListIterator<Object> it = currentstack.listIterator();
								//first
								it.next();
								addStackElementIfDoesntExist(it);
								addStackElementIfDoesntExist(it);
								it.add(f);
							}
							break;
						}
						case Opcodes.DUP2: {
//							..., value2, value1 
//							..., value2, value1, value2, value1
							Object f = currentstack.peekFirst();
							if (f != null) {
								ListIterator<Object> it = currentstack.listIterator();
								it.next();
								Object s = addStackElementIfDoesntExist(it);
								it.add(f);
								it.add(s);
							}
							break;
						}
						case Opcodes.DUP2_X1: {
//							..., value3, value2, value1 
//							..., value2, value1, value3, value2, value1
							Object f = currentstack.peekFirst();
							if (f != null) {
								ListIterator<Object> it = currentstack.listIterator();
								it.next();
								Object s = addStackElementIfDoesntExist(it);
								addStackElementIfDoesntExist(it);
								it.add(f);
								it.add(s);
							}
							break;
						}
						case Opcodes.DUP2_X2: {
//							..., value4, value3, value2, value1 
//							..., value2, value1, value4, value3, value2, value1
							Object f = currentstack.peekFirst();
							if (f != null) {
								ListIterator<Object> it = currentstack.listIterator();
								it.next();
								Object s = addStackElementIfDoesntExist(it);
								addStackElementIfDoesntExist(it);
								addStackElementIfDoesntExist(it);
								it.add(f);
								it.add(s);
							}
							break;
						}
						case Opcodes.POP: {
							currentstack.pollFirst();
							break;
						}
						case Opcodes.POP2: {
							currentstack.pollFirst();
							currentstack.pollFirst();
							break;
						}
						case Opcodes.SWAP: {
							switch (currentstack.size()) {
								case 0: {
									//ignore
									break;
								}
								case 1: {
									//anything that is first, will be second
									currentstack.addFirst(VarContents.UNKNOWN);
									break;
								}
								default: {
									//swap the first 2 values
									Object first = currentstack.removeFirst();
									Object second = currentstack.removeFirst();
									currentstack.addFirst(first);
									currentstack.addFirst(second);
									break;
								}
							}
							break;
						}
						case Opcodes.ARRAYLENGTH: {
							//we can optimize array length reading away.
							//it could trigger a nullpointer exception if the array is null, but users really should rely on that
							currentstack.pollFirst();
							currentstack.addFirst(VarContents.UNKNOWN);
							break;
						}
						case Opcodes.ICONST_M1:
						case Opcodes.ICONST_0:
						case Opcodes.ICONST_1:
						case Opcodes.ICONST_2:
						case Opcodes.ICONST_3:
						case Opcodes.ICONST_4:
						case Opcodes.ICONST_5:
						case Opcodes.FCONST_0:
						case Opcodes.FCONST_1:
						case Opcodes.FCONST_2:
						case Opcodes.ACONST_NULL: {
							currentstack.addFirst(VarContents.UNKNOWN);
							break;
						}
						case Opcodes.LCONST_0:
						case Opcodes.LCONST_1:
						case Opcodes.DCONST_0:
						case Opcodes.DCONST_1: {
							currentstack.addFirst(VarContents.UNKNOWN);
							currentstack.addFirst(VarContents.UNKNOWN);
							break;
						}
						case Opcodes.IALOAD:
						case Opcodes.FALOAD:
						case Opcodes.AALOAD:
						case Opcodes.BALOAD:
						case Opcodes.CALOAD:
						case Opcodes.SALOAD: {
							//we can optimize array reading away.
							//it could trigger a nullpointer exception if the array is null, but users really should rely on that
//							..., arrayref, index 
//							..., value
							currentstack.pollFirst();
							currentstack.pollFirst();
							currentstack.addFirst(VarContents.UNKNOWN);
							break;
						}
						case Opcodes.LALOAD:
						case Opcodes.DALOAD: {
							currentstack.pollFirst();
							currentstack.pollFirst();
							currentstack.addFirst(VarContents.UNKNOWN);
							currentstack.addFirst(VarContents.UNKNOWN);
							break;
						}
						case Opcodes.IASTORE:
						case Opcodes.LASTORE:
						case Opcodes.FASTORE:
						case Opcodes.DASTORE:
						case Opcodes.AASTORE:
						case Opcodes.BASTORE:
						case Opcodes.CASTORE:
						case Opcodes.SASTORE: {
							//we cannot optimize array storings away, as they are not side effect free
							//if an array was loaded previously from a field, storing an object to it cannot be omitted
							return false;
						}

						case Opcodes.LSHL:
						case Opcodes.LUSHR:
						case Opcodes.LSHR: {
							//size 2, size 1 -> 2
							currentstack.pollFirst();
							currentstack.pollFirst();
							currentstack.pollFirst();
							currentstack.addFirst(VarContents.UNKNOWN);
							currentstack.addFirst(VarContents.UNKNOWN);
							break;
						}
						case Opcodes.IADD:
						case Opcodes.ISUB:
						case Opcodes.IMUL:
						case Opcodes.IDIV:
						case Opcodes.IREM:
						case Opcodes.ISHL:
						case Opcodes.ISHR:
						case Opcodes.IUSHR:
						case Opcodes.IAND:
						case Opcodes.IOR:
						case Opcodes.IXOR:
						case Opcodes.FADD:
						case Opcodes.FSUB:
						case Opcodes.FMUL:
						case Opcodes.FDIV:
						case Opcodes.FREM:
						case Opcodes.FCMPL:
						case Opcodes.FCMPG: {
							//size 1, size 1 -> 1
							currentstack.pollFirst();
							currentstack.pollFirst();
							currentstack.addFirst(VarContents.UNKNOWN);
							break;
						}

						case Opcodes.LDIV:
						case Opcodes.LADD:
						case Opcodes.LSUB:
						case Opcodes.LMUL:
						case Opcodes.LREM:
						case Opcodes.LAND:
						case Opcodes.LOR:
						case Opcodes.DDIV:
						case Opcodes.DADD:
						case Opcodes.DSUB:
						case Opcodes.DMUL:
						case Opcodes.DREM: {
							//size 2, size 2 -> 2
							currentstack.pollFirst();
							currentstack.pollFirst();
							currentstack.pollFirst();
							currentstack.pollFirst();
							currentstack.addFirst(VarContents.UNKNOWN);
							currentstack.addFirst(VarContents.UNKNOWN);
							break;
						}

						case Opcodes.LCMP:
						case Opcodes.DCMPL:
						case Opcodes.DCMPG: {
							//size 2, size 2 -> 1
							currentstack.pollFirst();
							currentstack.pollFirst();
							currentstack.pollFirst();
							currentstack.pollFirst();
							currentstack.addFirst(VarContents.UNKNOWN);
							break;
						}

						case Opcodes.D2I:
						case Opcodes.D2F:
						case Opcodes.L2I:
						case Opcodes.L2F: {
							//size 2 -> 1
							currentstack.pollFirst();
							currentstack.pollFirst();
							currentstack.addFirst(VarContents.UNKNOWN);
							break;
						}
						case Opcodes.I2F:
						case Opcodes.I2B:
						case Opcodes.I2C:
						case Opcodes.I2S:
						case Opcodes.INEG:
						case Opcodes.FNEG:
						case Opcodes.F2I: {
							//size 1 -> 1
							currentstack.pollFirst();
							currentstack.addFirst(VarContents.UNKNOWN);
							break;
						}
						case Opcodes.LXOR:
						case Opcodes.L2D:
						case Opcodes.LNEG:
						case Opcodes.D2L:
						case Opcodes.DNEG: {
							//size 2 -> 2
							currentstack.pollFirst();
							currentstack.pollFirst();
							currentstack.addFirst(VarContents.UNKNOWN);
							currentstack.addFirst(VarContents.UNKNOWN);
							break;
						}

						case Opcodes.I2L:
						case Opcodes.I2D:
						case Opcodes.F2L:
						case Opcodes.F2D: {
							//size 1 -> 2
							currentstack.pollFirst();
							currentstack.addFirst(VarContents.UNKNOWN);
							currentstack.addFirst(VarContents.UNKNOWN);
							break;
						}

						//unary bitwise not is xorring by -1 
						case Opcodes.NOP: {
							break;
						}
						//TODO include additional operators and instructions
						case Opcodes.MONITORENTER:
						case Opcodes.MONITOREXIT: {
							//if there are monitor instructions to the return path, it is not optimizable
							return false;
						}
						default: {
							//unrecognized instruction, invalid path
							return false;
						}
					}
					break;
				}
				case AbstractInsnNode.INT_INSN: {
					switch (ins.getOpcode()) {
						case Opcodes.BIPUSH:
						case Opcodes.SIPUSH: {
							currentstack.addFirst(VarContents.UNKNOWN);
							break;
						}
						case Opcodes.NEWARRAY: {
//							..., count 
//							..., arrayref
							currentstack.pollFirst();
							currentstack.addFirst(VarContents.UNKNOWN);
							break;
						}
						default: {
							//unknown instruction
							return false;
						}
					}
					break;
				}
				case AbstractInsnNode.MULTIANEWARRAY_INSN: {
//					..., count1, [count2, ...] 
//					..., arrayref
					MultiANewArrayInsnNode mnanode = (MultiANewArrayInsnNode) ins;
					for (int i = 0; i < mnanode.dims; i++) {
						currentstack.pollFirst();
					}
					currentstack.addFirst(VarContents.UNKNOWN);
					break;
				}
				case AbstractInsnNode.VAR_INSN: {
					switch (ins.getOpcode()) {
						case Opcodes.ALOAD:
						case Opcodes.FLOAD:
						case Opcodes.ILOAD: {
							VarInsnNode vins = (VarInsnNode) ins;
							currentstack.push(varnumcontents.getOrDefault(vins.var, VarContents.UNKNOWN));
							break;
						}
						case Opcodes.DLOAD:
						case Opcodes.LLOAD: {
							VarInsnNode vins = (VarInsnNode) ins;
							Object holding = varnumcontents.getOrDefault(vins.var, VarContents.UNKNOWN);
							currentstack.push(holding);
							currentstack.push(holding);
							break;
						}
						case Opcodes.ASTORE:
						case Opcodes.FSTORE:
						case Opcodes.ISTORE: {
							VarInsnNode vins = (VarInsnNode) ins;
							Object f = currentstack.pollFirst();
							if (f == null) {
								f = VarContents.UNKNOWN;
							}
							varnumcontents.put(vins.var, f);
							break;
						}
						case Opcodes.DSTORE:
						case Opcodes.LSTORE: {
							VarInsnNode vins = (VarInsnNode) ins;
							Object f = currentstack.pollFirst();
							Object f2 = currentstack.pollFirst();
							if (f == null) {
								f = VarContents.UNKNOWN;
							}
							if (f2 == null) {
								f2 = VarContents.UNKNOWN;
							}
							varnumcontents.put(vins.var, f);
							varnumcontents.put(vins.var + 1, f);
							break;
						}
						default: {
							//unrecognized instruction, not found
							return false;
						}
					}
					break;
				}
				case AbstractInsnNode.LDC_INSN: {
					currentstack.push(VarContents.UNKNOWN);
					break;
				}
				default: {
					//unrecognized node type
					return false;
				}
			}
		}
		return false;
	}

	private static Object addStackElementIfDoesntExist(ListIterator<Object> it) {
		if (!it.hasNext()) {
			it.add(VarContents.UNKNOWN);
			return VarContents.UNKNOWN;
		}
		return it.next();
	}

	private static boolean isOptimizableMethod(MethodNode mn, boolean owneritf) {
		//Can we optimize methods which are not synchronized, but use monitorenter and monitorexit instructions? 
		//yes:
		//    only in the case if there is no monitorexit instruction until the return instruction after the method call
		//    if there is, the method call is not optimized
		//    if there is not, then we can optimize the method, as the monitors would never be exited
		//        a JVM implementation might enable not exiting a monitor after entering it in a method
		//        (no corresponding exit for the enter instruction)
		//        in this case if we optimize the recursive call, no semantic changes occurr
		//        as the unmatched monitorenter would not be exited anyway even if we don't optimize.

		//Virtual methods cannot be optimized.
		//   we can't optimize them, because subclasses can call these methods. the recursive
		//   calls would dispatch back to the subclass, so the optimization cannot be performed.

		int access = mn.access;
		if (((access & Opcodes.ACC_NATIVE) == Opcodes.ACC_NATIVE)
				|| ((access & Opcodes.ACC_ABSTRACT) == Opcodes.ACC_ABSTRACT)) {
			return false;
		}
		if ((access & Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC) {
			//static methods can be optimized even if they are declared as synchronized
			return true;
		}
		//method is an instance method
		//    it must be non overrideable, so either private or final
		//    it must not be synchronized, as the this variable might change during optimization
		if ((access & Opcodes.ACC_PRIVATE) == Opcodes.ACC_PRIVATE
				|| (access & Opcodes.ACC_FINAL) == Opcodes.ACC_FINAL) {
			if (((access & Opcodes.ACC_SYNCHRONIZED) == Opcodes.ACC_SYNCHRONIZED)) {
				//synchronized instance methods are not optimizable

				//TODO they could be optimizable if we keep track of the object reference
				//     and make sure it is called on this
				return false;
			}
			return true;
		}
		return false;
	}

	private static boolean isInstructionNodeType(int type) {
		switch (type) {
			case AbstractInsnNode.INSN:
			case AbstractInsnNode.INT_INSN:
			case AbstractInsnNode.VAR_INSN:
			case AbstractInsnNode.TYPE_INSN:
			case AbstractInsnNode.FIELD_INSN:
			case AbstractInsnNode.METHOD_INSN:
			case AbstractInsnNode.INVOKE_DYNAMIC_INSN:
			case AbstractInsnNode.JUMP_INSN:
			case AbstractInsnNode.LDC_INSN:
			case AbstractInsnNode.IINC_INSN:
			case AbstractInsnNode.TABLESWITCH_INSN:
			case AbstractInsnNode.LOOKUPSWITCH_INSN:
			case AbstractInsnNode.MULTIANEWARRAY_INSN: {
				return true;
			}
		}
		return false;
	}

	private static LabelNode insertStartGotoLabel(ClassNode classnode, MethodNode mn) {
		AbstractInsnNode firstinsn = mn.instructions.getFirst();
		LabelNode firstlabel = null;
		FrameNode firstframe = null;

		for (AbstractInsnNode n = firstinsn; (firstlabel == null || firstframe == null) && n != null;) {
			if (isInstructionNodeType(n.getType())) {
				break;
			}
			switch (n.getType()) {
				case AbstractInsnNode.LABEL: {
					firstlabel = (LabelNode) n;
					break;
				}
				case AbstractInsnNode.FRAME: {
					firstframe = (FrameNode) n;
					break;
				}
			}
			n = n.getNext();
		}

		if (firstlabel == null) {
			firstlabel = new LabelNode(new Label());
			mn.instructions.insert(firstlabel);
		}

		if (firstframe == null) {
			if (isUsingFrames(classnode)) {
				firstframe = new FrameNode(Opcodes.F_SAME, 0, null, 0, null);
				mn.instructions.insert(firstlabel, firstframe);
			}
		}
		return firstlabel;
	}

	private static boolean optimizeMethod(ClassNode classnode, boolean owneritf, MethodNode mn) {
		if ("<init>".equals(mn.name) || "<clinit>".equals(mn.name)) {
			//no optimizations for constructors and static initializers
			return false;
		}
		boolean methodoptimizable = isOptimizableMethod(mn, owneritf);
		if (!methodoptimizable) {
			return false;
		}

		LabelNode gotolabelnode = null;
		Type methodtype = Type.getMethodType(mn.desc);
		boolean staticcall = ((mn.access & Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC);
		int allsize = staticcall ? 0 : 1;
		Type[] argtypes = methodtype.getArgumentTypes();
		for (Type argtype : argtypes) {
			allsize += argtype.getSize();
		}

		//the tail-recursion doesnt work if the method call is in a try-catch, as the exception would need the stack trace, and 
		//    basically anything can happen in the catch block. This would semantically violate the optimization.
		Set<AbstractInsnNode> trycatchskipinstructions;
		if (mn.tryCatchBlocks.isEmpty()) {
			trycatchskipinstructions = Collections.emptySet();
		} else {
			trycatchskipinstructions = new HashSet<>();
			for (TryCatchBlockNode tcb : mn.tryCatchBlocks) {
				collectTryCatchBodyInstructions(trycatchskipinstructions, tcb);
			}
		}

		Object returntypeframetype;
		Type methodreturntype = methodtype.getReturnType();
		switch (methodreturntype.getSort()) {
			case Type.VOID: {
				returntypeframetype = null;
				break;
			}
			case Type.CHAR:
			case Type.BOOLEAN:
			case Type.BYTE:
			case Type.SHORT:
			case Type.INT: {
				returntypeframetype = Opcodes.INTEGER;
				break;
			}
			case Type.LONG: {
				returntypeframetype = Opcodes.LONG;
				break;
			}
			case Type.FLOAT: {
				returntypeframetype = Opcodes.FLOAT;
				break;
			}
			case Type.DOUBLE: {
				returntypeframetype = Opcodes.DOUBLE;
				break;
			}
			case Type.ARRAY:
			case Type.OBJECT: {
				returntypeframetype = methodreturntype.getInternalName();
				break;
			}
			default: {
				throw new AssertionError("Unknown sort: " + methodreturntype.getSort());
			}
		}

		boolean usingframes = isUsingFrames(classnode);

		for (AbstractInsnNode ins = mn.instructions.getFirst(); ins != null; ins = ins.getNext()) {
			if (trycatchskipinstructions.contains(ins)) {
				continue;
			}
			int instype = ins.getType();
			if (instype == AbstractInsnNode.METHOD_INSN) {
				MethodInsnNode mins = (MethodInsnNode) ins;
				if (classnode.name.equals(mins.owner) && mn.name.equals(mins.name) && mn.desc.equals(mins.desc)
						&& owneritf == mins.itf) {
					boolean optimizablecall = isTailOptimizable(mins, returntypeframetype, mins);
					if (optimizablecall) {
						if (gotolabelnode == null) {
							gotolabelnode = insertStartGotoLabel(classnode, mn);
						}
						JumpInsnNode gotojumpnode = new JumpInsnNode(Opcodes.GOTO, gotolabelnode);

						//the return instruction is directly after the method call, no frame changes or jumps present
						mn.instructions.insertBefore(mins, gotojumpnode);
						//remove the method call instruction
						mn.instructions.remove(mins);

						//the first instruction of the section that is reachable by other jump instructions
						//this is a frame instruction if we're using frames in this class version
						//or a label node that is possibly a target of another jump instruction
						AbstractInsnNode nextsection = null;
						remover:
						for (AbstractInsnNode n = gotojumpnode.getNext(); n != null;) {
							int ntype = n.getType();
							switch (ntype) {
								case AbstractInsnNode.FRAME: {
									nextsection = n;
									break remover;
								}
								case AbstractInsnNode.LABEL: {
									if (!usingframes) {
										nextsection = n;
										break remover;
									}
									break;
								}
								case AbstractInsnNode.LINE: {
									break;
								}
								case AbstractInsnNode.INSN:
								case AbstractInsnNode.INT_INSN:
								case AbstractInsnNode.VAR_INSN:
								case AbstractInsnNode.TYPE_INSN:
								case AbstractInsnNode.FIELD_INSN:
								case AbstractInsnNode.METHOD_INSN:
								case AbstractInsnNode.INVOKE_DYNAMIC_INSN:
								case AbstractInsnNode.JUMP_INSN:
								case AbstractInsnNode.LDC_INSN:
								case AbstractInsnNode.IINC_INSN:
								case AbstractInsnNode.TABLESWITCH_INSN:
								case AbstractInsnNode.LOOKUPSWITCH_INSN:
								case AbstractInsnNode.MULTIANEWARRAY_INSN: {
									AbstractInsnNode next = n.getNext();
									mn.instructions.remove(n);
									n = next;
									continue remover;
								}
								default: {
									break;
								}
							}
							n = n.getNext();
						}
						if (nextsection == null) {
							//remove every next node after the goto, as there are no more instructions
							for (AbstractInsnNode n = gotojumpnode.getNext(); n != null;) {
								AbstractInsnNode next = n.getNext();
								if (isInstructionNodeType(n.getType())) {
									mn.instructions.remove(n);
								}
								n = next;
							}
						}

						int cvar = allsize;
						for (int i = argtypes.length - 1; i >= 0; i--) {
							Type argtype = argtypes[i];

							cvar -= argtype.getSize();
							mn.instructions.insertBefore(gotojumpnode,
									new VarInsnNode(argtype.getOpcode(Opcodes.ISTORE), cvar));
						}
						if (!staticcall) {
							// replace the this variable as well in case if the method is getting called
							// on a different instance
							mn.instructions.insertBefore(gotojumpnode, new VarInsnNode(Opcodes.ASTORE, 0));
						}
					}

				}
			}
		}
		if (gotolabelnode != null) {
			//the method was optimized
			Set<LabelNode> localvarlabels = new HashSet<>();
			for (Iterator<LocalVariableNode> it = mn.localVariables.iterator(); it.hasNext();) {
				LocalVariableNode lvn = it.next();
				if (isLabelsNextToEachOther(lvn.start, lvn.end)) {
					//no actual scope for the variable
					it.remove();
					continue;
				}
				localvarlabels.add(lvn.start);
				localvarlabels.add(lvn.end);
			}
			for (AbstractInsnNode ins = mn.instructions.getFirst(); ins != null;) {
				if (ins.getType() == AbstractInsnNode.LINE) {
					if (findNextInstruction(ins) == null) {
						LineNumberNode lnn = (LineNumberNode) ins;
						if (!localvarlabels.contains(lnn.start)) {
							ins = ins.getNext();
							mn.instructions.remove(lnn);
							continue;
						}
					}
				}
				ins = ins.getNext();
			}
			for (Iterator<LocalVariableNode> it = mn.localVariables.iterator(); it.hasNext();) {
				LocalVariableNode lvn = it.next();
				if (lvn.start.getNext() == lvn.end) {
					//no scope for the variable
					it.remove();
				} else {
					boolean beginremoved = lvn.start.getNext() == null && lvn.start.getPrevious() == null;
					boolean endremoved = lvn.end.getNext() == null && lvn.end.getPrevious() == null;
					if (beginremoved) {
						if (endremoved) {
							//the scope was removed
							it.remove();
						}
					} else if (endremoved) {
//						lvn.end.resetLabel();
					}
				}
			}
			return true;
		}
		return false;
	}

	private static boolean isUsingFrames(ClassNode classnode) {
		return (classnode.version & 0xFFFF) > Opcodes.V1_5;
	}

	private static boolean isLabelsNextToEachOther(LabelNode first, LabelNode second) {
		AbstractInsnNode ins = first;
		while ((ins = ins.getNext()) != null) {
			if (ins == second) {
				return true;
			}
			if (isInstructionNodeType(ins.getType())) {
				return false;
			}
		}
		return true;
	}

	private static AbstractInsnNode findNextInstruction(AbstractInsnNode ins) {
		while ((ins = ins.getNext()) != null) {
			if (isInstructionNodeType(ins.getType())) {
				return ins;
			}
		}
		return null;
	}

	private static void collectTryCatchBodyInstructions(Set<AbstractInsnNode> trycatchskipinstructions,
			TryCatchBlockNode tcb) {
		for (AbstractInsnNode ins = tcb.start.getNext(); ins != null; ins = ins.getNext()) {
			if (ins == tcb.end) {
				break;
			}
			trycatchskipinstructions.add(ins);
		}
	}

}
