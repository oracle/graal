/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.bytecode.test;

import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.debug.BytecodeDebugListener;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;

public abstract class DebugBytecodeRootNode extends RootNode implements BytecodeRootNode, BytecodeDebugListener {

    static boolean traceQuickening = false;
    static boolean traceInstrumentation = false;
    @CompilationFinal static boolean traceInstructions = false;
    @CompilationFinal static boolean traceRoots = false;
    static RootInfo currentRoot;
    static final AtomicInteger ROOT_EXECUTION_INDEX = new AtomicInteger();

    protected DebugBytecodeRootNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    final AtomicInteger invalidateCount = new AtomicInteger();
    final AtomicInteger quickeningCount = new AtomicInteger();
    final AtomicInteger specializeCount = new AtomicInteger();

    @Override
    public void beforeRootExecute(Instruction instruction) {
        if (traceInstructions || traceRoots) {
            currentRoot = new RootInfo(currentRoot);
        }
        if (traceRoots) {
            traceRootEnter(instruction);
        }
    }

    @Override
    public void afterRootExecute(Instruction leaveInstruction, Object returnValue, Throwable t) {
        if (traceRoots) {
            traceRootLeave(leaveInstruction, returnValue, t);
        }
        if (traceInstructions || traceRoots) {
            currentRoot = currentRoot.prev;
        }
    }

    @TruffleBoundary
    private static void traceRootEnter(Instruction instruction) {
        if (instruction.getBytecodeIndex() == 0) {
            System.out.printf("Before-Root %6s %s %n", "[" + currentRoot.rootIndex + "]", instruction.getBytecodeNode());
        } else {
            System.out.printf("Before-Root %6s %s at %s%n", "[" + currentRoot.rootIndex + "]", instruction.getBytecodeNode(), instruction);
        }

    }

    @TruffleBoundary
    private static void traceRootLeave(Instruction instruction, Object returnValue, Throwable t) {
        System.out.printf("After-Root %7s %s with %s [instructionCount=%s] at %s %n",
                        "[" + currentRoot.rootIndex + "]",
                        instruction.getBytecodeNode(),
                        t == null ? returnValue : t,
                        currentRoot.instructionCount,
                        instruction);
    }

    public void beforeInstructionExecute(Instruction instruction) {
        if (traceInstructions) {
            traceInstruction(instruction);
        }
        if (traceInstructions || traceRoots) {
            currentRoot.instructionCount++;
        }
    }

    @TruffleBoundary
    private static void traceInstruction(Instruction instruction) {
        System.out.printf("  Instruction %4s %s%n", "[" + currentRoot.instructionCount + "]", instruction);
    }

    public void onBytecodeStackTransition(Instruction source, Instruction target) {
        if (traceInstrumentation) {
            System.out.printf("On stack transition: %s%n", source.getLocation().getBytecodeNode().getRootNode());
            System.out.printf("  Invalidated at: %s%n", source.getLocation().getBytecodeNode().dump(source.getLocation()));
            System.out.printf("  Continue at:    %s%n", target.getLocation().getBytecodeNode().dump(target.getLocation()));
        }
    }

    @Override
    public void onInvalidateInstruction(Instruction before, Instruction after) {
        if (traceQuickening) {
            System.out.printf("Invalidate %s: %n     %s%n  -> %s%n", before.getName(), before, after);
        }
        invalidateCount.incrementAndGet();
    }

    @Override
    public void onQuicken(Instruction before, Instruction after) {
        if (traceQuickening) {
            System.out.printf("Quicken %s: %n     %s%n  -> %s%n", before.getName(), before, after);
        }
        quickeningCount.incrementAndGet();
    }

    public void onQuickenOperand(Instruction base, int operandIndex, Instruction operandBefore, Instruction operandAfter) {
        if (traceQuickening) {
            System.out.printf("Quicken operand index %s for %s: %n     %s%n  -> %s%n", operandIndex, base.getName(),
                            operandBefore, operandAfter);
        }
        quickeningCount.incrementAndGet();
    }

    @Override
    public void onSpecialize(Instruction instruction, String specialization) {
        if (traceQuickening) {
            System.out.printf("Specialize %s: %n     %s%n", specialization, instruction);
        }
        specializeCount.incrementAndGet();
    }

    private static final class RootInfo {
        int instructionCount;
        final int rootIndex;
        final RootInfo prev;

        RootInfo(RootInfo prev) {
            this.prev = prev;
            this.rootIndex = ROOT_EXECUTION_INDEX.incrementAndGet();
        }
    }

}
