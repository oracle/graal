/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.hotspot.meta.HotSpotForeignCallDescriptor;
import jdk.graal.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition;
import jdk.graal.compiler.hotspot.replacements.HotSpotAllocationSnippets;
import jdk.graal.compiler.hotspot.replacements.MonitorSnippets;
import jdk.graal.compiler.hotspot.stubs.AbstractForeignCallStub;
import jdk.graal.compiler.hotspot.stubs.ForeignCallSnippets;
import jdk.graal.compiler.hotspot.stubs.Stub;
import jdk.graal.compiler.nodes.DeoptimizingNode.DeoptAfter;
import jdk.graal.compiler.nodes.DeoptimizingNode.DeoptBefore;
import jdk.graal.compiler.nodes.DeoptimizingNode.DeoptDuring;
import jdk.graal.compiler.nodes.FrameState.StackState;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.java.MonitorEnterNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.vm.ci.meta.InvokeTarget;

/**
 * The details required to link a HotSpot runtime or stub call.
 *
 * HotSpot runtime methods can be categorized as leaf methods and non-leaf methods, based on whether
 * the runtime methods may interfere with safepoints. Leaf runtime methods are annotated with
 * JRT_LEAF, and must comply with the following rules from {@code interfaceSupport.inline.hpp}:
 *
 * <pre>
 * JRT_LEAF rules:
 * A JRT_LEAF method may not interfere with safepointing by
 *   1) acquiring or blocking on a Mutex or JavaLock - checked
 *   2) allocating heap memory - checked
 *   3) executing a VM operation - checked
 *   4) executing a system call (including malloc) that could block or grab a lock
 *   5) invoking GC
 *   6) reaching a safepoint
 *   7) running too long
 * Nor may any method it calls.
 * </pre>
 *
 * Non-leaf runtime methods are of 4 forms:
 *
 * <pre>
 *   1) JRT_ENTRY, basic ENTRY routine that may lock, GC, and throw exceptions;
 *   2) JRT_ENTRY_NO_ASYNC, same as JRT_ENTRY but does not install pending async exceptions;
 *   3) JRT_BLOCK_ENTRY + JRT_BLOCK, same as JRT_ENTRY but allows for return value after the
 *      safepoint to get back into Java from the VM;
 *   4) JRT_BLOCK_ENTRY + JRT_BLOCK_NO_ASYNC, same as JRT_BLOCK_ENTRY + JRT_BLOCK but does not
 *      install pending async exceptions.
 * </pre>
 *
 * All of the above forms insert thread state transition from/to Java.
 *
 * When making a foreign call to the non-leaf runtime methods, we must specify the foreign call as
 * {@link Transition#SAFEPOINT}. Graal then generates a stub adapting the compiled code and the
 * target runtime method. This stub does three additional things besides making the actual call:
 *
 * <pre>
 *   1) save/restore all allocatable registers if the call linkage register effect is
 *      {@link RegisterEffect#COMPUTES_REGISTERS_KILLED}. This allows register allocator to avoid
 *      spilling around the call to this stub. In such case, Graal describes the save area to
 *      the HotSpot runtime through a callee save layout, which permits GC to find these values
 *      since they are described as being in registers but are actually saved in the stub frame.
 *   2) handle pending exception, i.e., exception thrown from the runtime.
 *   3) propagate return result if the return type is object.
 * </pre>
 *
 * Note that the pending exception and return result are stored in JavaThread, e.g., [r15+0x8] and
 * [r15+0x438] on AMD64. See {@link AbstractForeignCallStub#getGraph} for pseudo code of the
 * following stub.
 *
 * <pre>
 * 0x0000 push   rbp
 * 0x0001 mov    rbp,rsp
 * 0x0004 sub    rsp,0x70
 * 0x0008 mov    QWORD PTR [rsp+0x60],r10
 * 0x000d mov    QWORD PTR [rsp+0x8],r11
 * 0x0012 mov    QWORD PTR [rsp+0x10],r8
 * 0x0017 mov    QWORD PTR [rsp+0x18],r9
 * 0x001c mov    QWORD PTR [rsp+0x20],rcx
 * 0x0021 mov    QWORD PTR [rsp+0x28],rbx
 * 0x0026 mov    QWORD PTR [rsp+0x30],rdi
 * 0x002b mov    QWORD PTR [rsp+0x38],rdx
 * 0x0030 mov    QWORD PTR [rsp+0x40],rsi
 * 0x0035 mov    QWORD PTR [rsp+0x50],r13
 * 0x003a mov    QWORD PTR [rsp+0x58],r14
 * 0x003f mov    QWORD PTR [r15+0x3e0],rsp
 * 0x0046 mov    rdi,r15
 * 0x0049 vzeroupper
 * 0x004c call   <JVMCIRuntime::new_array(JavaThread*, Klass*, int)>
 * 0x0051 nop    DWORD PTR [rax+rax*1+0x0]
 * 0x0059 mov    QWORD PTR [r15+0x3e0],0x0
 * 0x0064 mov    QWORD PTR [r15+0x3f0],0x0
 * 0x006f mov    QWORD PTR [r15+0x3e8],0x0
 * 0x007a mov    r10,QWORD PTR [rsp+0x60]
 * 0x007f mov    r11,QWORD PTR [rsp+0x8]
 * 0x0084 mov    r8,QWORD PTR [rsp+0x10]
 * 0x0089 mov    r9,QWORD PTR [rsp+0x18]
 * 0x008e mov    rcx,QWORD PTR [rsp+0x20]
 * 0x0093 mov    rbx,QWORD PTR [rsp+0x28]
 * 0x0098 mov    rdi,QWORD PTR [rsp+0x30]
 * 0x009d mov    rdx,QWORD PTR [rsp+0x38]
 * 0x00a2 mov    rsi,QWORD PTR [rsp+0x40]
 * 0x00a7 mov    r13,QWORD PTR [rsp+0x50]
 * 0x00ac mov    r14,QWORD PTR [rsp+0x58]
 * 0x00b1 mov    rax,QWORD PTR [r15+0x8]           <-- fetch thread->_pending_exception
 * 0x00b5 mov    QWORD PTR [r15+0x8],r12           <-- set thread->_pending_exception to null
 * 0x00b9 nop    DWORD PTR [rax+0x0]
 * 0x00c0 test   rax,rax
 * 0x00c3 je     0x00eb
 * 0x00c9 mov    QWORD PTR [r15+0x438],r12         <-- set thread->_vm_result to null
 * 0x00d0 mov    DWORD PTR [r15+0x4d4],0xffffff8f  <-- set deoptimization reason and action
 * 0x00db mov    QWORD PTR [r15+0x4e0],r12
 * 0x00e2 mov    rsp,rbp
 * 0x00e5 pop    rbp
 * 0x00e6 jmp    <SharedRuntime::deopt_blob()->uncommon_trap()>
 * 0x00eb mov    rax,QWORD PTR [r15+0x438]
 * 0x00f2 mov    QWORD PTR [r15+0x438],r12
 * 0x00f9 mov    rsp,rbp
 * 0x00fc pop    rbp
 * 0x00fd ret
 * </pre>
 *
 * The AMD64 version of SharedRuntime::deopt_blob()->uncommon_trap() is emitted at
 * {@code SharedRuntime::generate_deopt_blob} in {@code sharedRuntime_x86_64.cpp}. It will patch the
 * return address of the deopt blob to interpreter deopt entry, i.e.
 * {@code TemplateInterpreterGenerator::generate_deopt_entry_for} in
 * {@code templateInterpreterGenerator_x86.cpp}
 *
 * HotSpot runtime methods can also be categorized as {@link CallSideEffect#HAS_SIDE_EFFECT} and
 * {@link CallSideEffect#NO_SIDE_EFFECT} methods. The notion of side effect is more about observable
 * program state by the Java code. For instance, {@link HotSpotBackend#NEW_ARRAY_OR_NULL}, though it
 * modifies the heap, is identified as {@link CallSideEffect#NO_SIDE_EFFECT};
 * {@link MonitorSnippets#MONITORENTER} is identified as {@link CallSideEffect#HAS_SIDE_EFFECT},
 * because the corresponding lock is acquired by the current thread once
 * {@link MonitorSnippets#MONITORENTER} is completed. Note that for {@link Transition#SAFEPOINT}
 * foreign calls, other threads may deoptimize compiled frames in the current thread, e.g.,
 * {@code EnterInterpOnlyModeClosure::do_thread} in {@code jvmtiEventController.cpp}. Thus, for
 * {@link CallSideEffect#NO_SIDE_EFFECT} foreign calls, we can deoptimize to a preceding or current
 * bytecode; for {@link CallSideEffect#HAS_SIDE_EFFECT} foreign calls, we must deoptimze to the
 * immediate succeeding bytecode, i.e., with a ScopeDesc whose bci set to the succeeding bytecode
 * and reexecute bit set to true, or a ScopeDesc whose bci set to the current bytecode and reexecute
 * bit set to false, see {@link StackState#AfterPop}.
 *
 * In both {@link CallSideEffect#NO_SIDE_EFFECT} and {@link CallSideEffect#HAS_SIDE_EFFECT} foreign
 * calls, Graal deoptimizes upon pending exception, see
 * {@link ForeignCallSnippets#handlePendingException}. Since Graal uses imprecise frame states, to
 * avoid dispatching to the incorrect exception handler, Graal should not propagate the exception.
 * For {@link CallSideEffect#NO_SIDE_EFFECT} runtime methods, this is done by swallowing the
 * exception and deoptimizing to a preceding BCI, assuming that re-execution will re-trigger the
 * same exception. For {@link CallSideEffect#HAS_SIDE_EFFECT} runtime methods, Graal does not
 * swallow the exception, the interpreter deopt entry will rethrow the exception regardless of the
 * _rethrow_exception bit in ScopeDesc, see
 * {@code TemplateInterpreterGenerator::generate_deopt_entry_for} in
 * {@code templateInterpreterGenerator_x86.cpp}. This means, to allow exception for
 * {@link Transition#SAFEPOINT} + {@link CallSideEffect#HAS_SIDE_EFFECT} foreign calls, we need to
 * use a precise frame state with {@link StackState#AfterPop}. For a node expanding to a snippet
 * with such a foreign call, it needs to implement {@link DeoptDuring}, see
 * {@link SnippetTemplate#rewireFrameStatesAfterFSA}.
 *
 * For instance, {@link NewArrayNode} implements {@link DeoptBefore}, and will expand to the snippet
 * {@link HotSpotAllocationSnippets#allocateArray} with a {@link Transition#SAFEPOINT} +
 * {@link CallSideEffect#NO_SIDE_EFFECT} foreign call to {@link HotSpotBackend#NEW_ARRAY_OR_NULL}.
 * This foreign call will use {@link NewArrayNode#stateBefore} as its
 * {@link ForeignCallNode#stateDuring}, and will deoptimize to normal interpretation of a preceding
 * BCI upon exception. {@link MonitorEnterNode} implements {@link DeoptBefore} and
 * {@link DeoptAfter}, and will expand to the snippet {@link MonitorSnippets#monitorenter} with a
 * {@link Transition#SAFEPOINT} + {@link CallSideEffect#HAS_SIDE_EFFECT} foreign call to
 * {@link MonitorSnippets#MONITORENTER}. This foreign call will use
 * {@link MonitorEnterNode#stateAfter} to compute its {@link ForeignCallNode#stateDuring}. In the
 * compiled code, it has a ScopeDesc pointing to next BCI. Upon exception, it will rethrow the
 * exception at the next BCI, which may be dispatched to an incorrect exception handler. Currently
 * {@code SharedRuntime::monitor_enter_helper} is wrapped with {@code JRT_BLOCK_NO_ASYNC} and ends
 * with {@code assert(!HAS_PENDING_EXCEPTION, "Should have no exception here");}, meaning that it is
 * impossible to have an exception thrown from the foreign call to
 * {@link MonitorSnippets#MONITORENTER}. Should there be a change to this invariant, we should adapt
 * {@link MonitorEnterNode} as well.
 */
public interface HotSpotForeignCallLinkage extends ForeignCallLinkage, InvokeTarget {

    /**
     * Constants for specifying whether a foreign call destroys or preserves registers. A foreign
     * call will always destroy {@link HotSpotForeignCallLinkage#getOutgoingCallingConvention() its}
     * {@linkplain ForeignCallLinkage#getTemporaries() temporary} registers.
     */
    enum RegisterEffect {
        /**
         * Acts like a normal call.
         */
        DESTROYS_ALL_CALLER_SAVE_REGISTERS,

        /**
         * Compute the set of registers which are killed from the LIR and emits register save and
         * restore logic around any internal foreign calls to reduce the number of registers which
         * are killed.
         */
        COMPUTES_REGISTERS_KILLED,

        /**
         * Uses a stack based calling convention and all registers are treated as callee saved.
         */
        KILLS_NO_REGISTERS
    }

    @Override
    HotSpotForeignCallDescriptor getDescriptor();

    /**
     * Sentinel marker for a computed jump address.
     */
    long JUMP_ADDRESS = 0xDEADDEADBEEFBEEFL;

    void setCompiledStub(Stub stub);

    RegisterEffect getEffect();

    /**
     * Determines if this is a call to a compiled {@linkplain Stub stub}.
     */
    boolean isCompiledStub();

    /**
     * Gets the stub, if any, this foreign call links to.
     */
    Stub getStub();

    void finalizeAddress(Backend backend);

    long getAddress();

    /**
     * Determines if the runtime function or stub might use floating point registers. If the answer
     * is no, then no FPU state management prologue or epilogue needs to be emitted around the call.
     */
    boolean mayContainFP();

    /**
     * Determines if a {@code JavaFrameAnchor} needs to be set up and torn down around this call.
     */
    boolean needsJavaFrameAnchor();

    /**
     * Gets the VM symbol associated with the target {@linkplain #getAddress() address} of the call.
     */
    String getSymbol();
}
