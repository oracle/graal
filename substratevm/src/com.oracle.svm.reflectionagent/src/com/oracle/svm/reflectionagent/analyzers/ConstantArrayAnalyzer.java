/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.reflectionagent.analyzers;

import com.oracle.svm.reflectionagent.MethodCallUtils;
import com.oracle.svm.reflectionagent.cfg.ControlFlowGraphNode;
import com.oracle.svm.shaded.org.objectweb.asm.Opcodes;
import com.oracle.svm.shaded.org.objectweb.asm.Type;
import com.oracle.svm.shaded.org.objectweb.asm.tree.AbstractInsnNode;
import com.oracle.svm.shaded.org.objectweb.asm.tree.IntInsnNode;
import com.oracle.svm.shaded.org.objectweb.asm.tree.LdcInsnNode;
import com.oracle.svm.shaded.org.objectweb.asm.tree.MethodInsnNode;
import com.oracle.svm.shaded.org.objectweb.asm.tree.TypeInsnNode;
import com.oracle.svm.shaded.org.objectweb.asm.tree.VarInsnNode;
import com.oracle.svm.shaded.org.objectweb.asm.tree.analysis.Frame;
import com.oracle.svm.shaded.org.objectweb.asm.tree.analysis.SourceValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.oracle.svm.shaded.org.objectweb.asm.Opcodes.AASTORE;
import static com.oracle.svm.shaded.org.objectweb.asm.Opcodes.ALOAD;
import static com.oracle.svm.shaded.org.objectweb.asm.Opcodes.ANEWARRAY;
import static com.oracle.svm.shaded.org.objectweb.asm.Opcodes.ASTORE;
import static com.oracle.svm.shaded.org.objectweb.asm.Opcodes.BIPUSH;
import static com.oracle.svm.shaded.org.objectweb.asm.Opcodes.DUP;
import static com.oracle.svm.shaded.org.objectweb.asm.Opcodes.ICONST_0;
import static com.oracle.svm.shaded.org.objectweb.asm.Opcodes.ICONST_1;
import static com.oracle.svm.shaded.org.objectweb.asm.Opcodes.ICONST_2;
import static com.oracle.svm.shaded.org.objectweb.asm.Opcodes.ICONST_3;
import static com.oracle.svm.shaded.org.objectweb.asm.Opcodes.ICONST_4;
import static com.oracle.svm.shaded.org.objectweb.asm.Opcodes.ICONST_5;
import static com.oracle.svm.shaded.org.objectweb.asm.Opcodes.ICONST_M1;
import static com.oracle.svm.shaded.org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static com.oracle.svm.shaded.org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static com.oracle.svm.shaded.org.objectweb.asm.Opcodes.INVOKESTATIC;
import static com.oracle.svm.shaded.org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static com.oracle.svm.shaded.org.objectweb.asm.Opcodes.LDC;
import static com.oracle.svm.shaded.org.objectweb.asm.Opcodes.PUTFIELD;
import static com.oracle.svm.shaded.org.objectweb.asm.Opcodes.PUTSTATIC;
import static com.oracle.svm.shaded.org.objectweb.asm.Opcodes.SIPUSH;

public class ConstantArrayAnalyzer {

    private final AbstractInsnNode[] instructions;
    private final ControlFlowGraphNode<SourceValue>[] frames;
    private final Set<MethodCallUtils.Signature> safeMethods;
    private final ConstantValueAnalyzer valueAnalyzer;

    public ConstantArrayAnalyzer(AbstractInsnNode[] instructions, ControlFlowGraphNode<SourceValue>[] frames, Set<MethodCallUtils.Signature> safeMethods, ConstantValueAnalyzer valueAnalyzer) {
        this.instructions = instructions;
        this.frames = frames;
        this.safeMethods = safeMethods;
        this.valueAnalyzer = valueAnalyzer;
    }

    public boolean isConstant(SourceValue value, AbstractInsnNode callSite) {
        return referenceIsConstant(value, callSite) && elementsAreConstant(value, callSite);
    }

    private boolean referenceIsConstant(SourceValue value, AbstractInsnNode callSite) {
        if (value.insns.size() != 1) {
            return false;
        }

        AbstractInsnNode sourceInstruction = value.insns.iterator().next();
        int sourceInstructionIndex = Arrays.asList(instructions).indexOf(sourceInstruction);
        Frame<SourceValue> sourceInstructionFrame = frames[sourceInstructionIndex];

        return switch (sourceInstruction.getOpcode()) {
            case ANEWARRAY -> {
                Optional<Integer> arraySize = extractConstInt(sourceInstructionFrame.getStack(sourceInstructionFrame.getStackSize() - 1));
                yield arraySize.isPresent();
            }
            case ALOAD -> {
                SourceValue sourceValue = sourceInstructionFrame.getLocal(((VarInsnNode) sourceInstruction).var);
                yield referenceIsConstant(sourceValue, callSite) && noForbiddenUsages(sourceValue.insns.iterator().next(), callSite);
            }
            case ASTORE -> {
                SourceValue sourceValue = sourceInstructionFrame.getStack(sourceInstructionFrame.getStackSize() - 1);
                yield referenceIsConstant(sourceValue, callSite) && sourceValue.insns.iterator().next().getOpcode() == ANEWARRAY;
            }
            default -> false;
        };
    }

    private static Optional<Integer> extractConstInt(SourceValue value) {
        if (value.insns.size() != 1) {
            return Optional.empty();
        }

        AbstractInsnNode sourceInstruction = value.insns.iterator().next();

        return switch (sourceInstruction.getOpcode()) {
            case ICONST_M1 -> Optional.of(-1);
            case ICONST_0 -> Optional.of(0);
            case ICONST_1 -> Optional.of(1);
            case ICONST_2 -> Optional.of(2);
            case ICONST_3 -> Optional.of(3);
            case ICONST_4 -> Optional.of(4);
            case ICONST_5 -> Optional.of(5);
            case BIPUSH, SIPUSH -> Optional.of(((IntInsnNode) sourceInstruction).operand);
            case LDC -> {
                LdcInsnNode ldc = (LdcInsnNode) sourceInstruction;
                if (ldc.cst instanceof Integer intValue) {
                    yield Optional.of(intValue);
                }
                yield Optional.empty();
            }
            default -> Optional.empty();
        };
    }

    private boolean noForbiddenUsages(AbstractInsnNode originalStoreInstruction, AbstractInsnNode callSite) {
        int callSiteInstructionIndex = Arrays.asList(instructions).indexOf(callSite);

        List<Integer> nodeIndices = new ArrayList<>();
        nodeIndices.add(callSiteInstructionIndex);

        /*
         * Run a BFS in the reversed CFG from the call site, looking for any potential forbidden
         * operations for constant arrays.
         */
        boolean[] visited = new boolean[frames.length];
        while (!nodeIndices.isEmpty()) {
            Integer currentNodeIndex = nodeIndices.removeLast();
            visited[currentNodeIndex] = true;
            if (isForbiddenStore(currentNodeIndex, originalStoreInstruction) || isForbiddenMethodCall(currentNodeIndex, originalStoreInstruction)) {
                return false;
            }

            ControlFlowGraphNode<SourceValue> currentNode = frames[currentNodeIndex];
            for (int adjacent : currentNode.predecessors) {
                if (!visited[adjacent]) {
                    nodeIndices.add(adjacent);
                }
            }
        }

        return true;
    }

    /**
     * Checks if the instruction at {@code instructionIndex} is an assignment instruction (to a
     * variable or a field) and its argument's value can be traced to
     * {@code originalStoreInstruction} which represents the initial assignment of an array
     * reference to a variable.
     * <p>
     * We want to avoid these cases when marking an array as constant because it could potentially
     * be modified through a field or variable which we aren't tracking.
     * <p>
     * In the following example, we want to avoid marking the params array as constant when
     * attempting to fold the second {@code getMethod} call:
     *
     * <pre>
     * {@code
     * Class<?>[] params = new Class<?>[2];
     * params[0] = String.class;
     * params[1] = int.class;
     * Method m1 = Integer.class.getMethod("parseInt", params); // This call is foldable
     * someField = params; // params could now be modified through someField
     * // ...
     * Method m2 = Integer.class.getMethod("parseInt", params); // We must not fold this
     * }
     * </pre>
     */
    private boolean isForbiddenStore(int instructionIndex, AbstractInsnNode originalStoreInstruction) {
        AbstractInsnNode instruction = instructions[instructionIndex];
        Frame<SourceValue> frame = frames[instructionIndex];

        if (Stream.of(Opcodes.ASTORE, PUTFIELD, PUTSTATIC).noneMatch(opc -> opc == instruction.getOpcode())) {
            return false;
        }

        SourceValue storeValue = frame.getStack(frame.getStackSize() - 1);
        return loadedValueTracesToStore(storeValue, originalStoreInstruction);
    }

    /**
     * Similar as with {@code isForbiddenStore}, arrays could be modified in methods they are passed
     * to, so we want to avoid marking them as constant after that. An exception to this are the
     * reflective methods we're already tracking with our analysis, as we know they won't modify the
     * passed array in any way.
     */
    private boolean isForbiddenMethodCall(int instructionIndex, AbstractInsnNode originalStoreInstruction) {
        AbstractInsnNode instruction = instructions[instructionIndex];
        Frame<SourceValue> frame = frames[instructionIndex];

        if (Stream.of(INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC, INVOKEINTERFACE).noneMatch(opc -> opc == instruction.getOpcode())) {
            return false;
        }

        MethodInsnNode methodCall = (MethodInsnNode) instruction;
        if (safeMethods.contains(new MethodCallUtils.Signature(methodCall))) {
            return false;
        }

        int numOfArgs = Type.getArgumentTypes(methodCall.desc).length;
        return IntStream.range(0, numOfArgs)
                        .anyMatch(i -> loadedValueTracesToStore(MethodCallUtils.getCallArg(methodCall, i, frame), originalStoreInstruction));
    }

    private boolean loadedValueTracesToStore(SourceValue value, AbstractInsnNode originalStoreInstruction) {
        return value.insns.stream().anyMatch(insn -> {
            if (insn.getOpcode() != ALOAD) {
                return false;
            }

            int loadInstructionIndex = Arrays.asList(instructions).indexOf(insn);
            Frame<SourceValue> loadInstructionFrame = frames[loadInstructionIndex];
            SourceValue loadSourceValue = loadInstructionFrame.getLocal(((VarInsnNode) insn).var);

            return loadSourceValue.insns.stream().anyMatch(storeInsn -> storeInsn == originalStoreInstruction);
        });
    }

    private Optional<TypeInsnNode> traceArrayRefToOrigin(SourceValue value) {
        if (value.insns.size() != 1) {
            return Optional.empty();
        }

        AbstractInsnNode sourceInstruction = value.insns.iterator().next();
        int sourceInstructionIndex = Arrays.asList(instructions).indexOf(sourceInstruction);
        Frame<SourceValue> sourceInstructionFrame = frames[sourceInstructionIndex];

        return switch (sourceInstruction.getOpcode()) {
            case ANEWARRAY -> Optional.of((TypeInsnNode) sourceInstruction);
            case ALOAD -> {
                SourceValue sourceValue = sourceInstructionFrame.getLocal(((VarInsnNode) sourceInstruction).var);
                yield traceArrayRefToOrigin(sourceValue);
            }
            case ASTORE, DUP -> {
                SourceValue sourceValue = sourceInstructionFrame.getStack(sourceInstructionFrame.getStackSize() - 1);
                yield traceArrayRefToOrigin(sourceValue);
            }
            default -> Optional.empty();
        };
    }

    private boolean elementsAreConstant(SourceValue value, AbstractInsnNode callSite) {
        int callSiteInstructionIndex = Arrays.asList(instructions).indexOf(callSite);

        Optional<TypeInsnNode> arrayInitInsn = traceArrayRefToOrigin(value);
        if (arrayInitInsn.isEmpty()) {
            return false;
        }

        int arrayInitInstructionIndex = Arrays.asList(instructions).indexOf(arrayInitInsn.get());
        Frame<SourceValue> arrayInitInstructionFrame = frames[arrayInitInstructionIndex];

        SourceValue arraySizeValue = arrayInitInstructionFrame.getStack(arrayInitInstructionFrame.getStackSize() - 1);
        Optional<Integer> arraySize = extractConstInt(arraySizeValue);
        if (arraySize.isEmpty()) {
            return false;
        }

        Set<Integer> constantElements = new HashSet<>();

        for (int i = arrayInitInstructionIndex; i < callSiteInstructionIndex; i++) {
            AbstractInsnNode currentInstruction = instructions[i];
            ControlFlowGraphNode<SourceValue> currentInstructionFrame = frames[i];

            if (currentInstructionFrame.successors.size() != 1) {
                return false;
            }

            if (currentInstruction.getOpcode() == AASTORE) {
                SourceValue storedValue = currentInstructionFrame.getStack(currentInstructionFrame.getStackSize() - 1);
                SourceValue indexValue = currentInstructionFrame.getStack(currentInstructionFrame.getStackSize() - 2);
                SourceValue arrayRefValue = currentInstructionFrame.getStack(currentInstructionFrame.getStackSize() - 3);

                Optional<TypeInsnNode> arrayReference = traceArrayRefToOrigin(arrayRefValue);
                if (arrayReference.isEmpty() || arrayReference.get() != arrayInitInsn.get()) {
                    continue;
                }

                Optional<Integer> elementIndex = extractConstInt(indexValue);
                if (elementIndex.isEmpty() || !valueAnalyzer.isConstant(storedValue) || constantElements.contains(elementIndex.get())) {
                    return false;
                }

                constantElements.add(elementIndex.get());
            }
        }

        return constantElements.size() == arraySize.get();
    }

    public String typeDescriptor() {
        return "[" + valueAnalyzer.typeDescriptor();
    }
}
