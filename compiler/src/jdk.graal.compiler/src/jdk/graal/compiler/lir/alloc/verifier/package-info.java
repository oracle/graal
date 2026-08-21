/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/**
 * <h1>Register Allocation Verifier</h1>
 *
 * This package verifies the output of the
 * {@link jdk.graal.compiler.lir.alloc.RegisterAllocationPhase register allocator} by checking that
 * the values flowing into instructions are the same as in the program before allocation. It is
 * implemented as a phase in {@link jdk.graal.compiler.lir.alloc.verifier.RegAllocVerifierPhase},
 * wrapping around both the register and stack allocators to gain access to both pre- and
 * post-allocation LIR.
 *
 * <h2>The principle</h2>
 *
 * {@link jdk.graal.compiler.lir.Variable Variables} and
 * {@link jdk.graal.compiler.lir.ConstantValue} from pre-allocation LIR act as symbols that are
 * tracked in concrete locations during the verification process, implemented in
 * {@link jdk.graal.compiler.lir.alloc.verifier.RegAllocVerifier} and
 * {@link jdk.graal.compiler.lir.alloc.verifier.BlockVerifierState}.
 *
 * <p>
 * The core algorithm performs symbolic execution on abstract instructions that boil down the
 * functionality of LIR instruction to only symbols and locations they read and write, as follows:
 * <ul>
 * <li><code>[(output_symbol1, output_location1), ...] = Op [(input_symbol1, input_location1), ...]</code></li>
 * <li><code>dst_location = Move src_location</code></li>
 * </ul>
 *
 * Where {@link jdk.graal.compiler.lir.alloc.verifier.RAVInstruction.Op Op} is an operation that was
 * present before allocation and only had its operands changed. The allocator has to make sure that
 * symbols read by the instruction are present at those selected locations. Output symbols are
 * written at the output locations, when being symbolically executed.
 * {@link jdk.graal.compiler.lir.alloc.verifier.RAVInstruction.LocationMove Move} is an
 * allocator-inserted instruction that transfers symbols, created by Op, from source location to the
 * destination, keeping the source unchanged.
 * </p>
 *
 * <p>
 * The execution of these instructions goes as follows:
 * <ul>
 * <li>Op puts output_symbol(s) to the output_location(s) set by the allocator</li>
 * <li>Move puts the symbol from src_location to dst_location</li>
 * </ul>
 * </p>
 *
 * <p>
 * Instead of only maintaining a symbol for a location, an allocation state is stored instead:
 * <ul>
 * <li>{@link jdk.graal.compiler.lir.alloc.verifier.UnknownAllocationState Unknown} - no information
 * about the contents</li>
 * <li>{@link jdk.graal.compiler.lir.alloc.verifier.ValueAllocationState Value(s)} - symbol v is
 * stored at this location</li>
 * <li>{@link jdk.graal.compiler.lir.alloc.verifier.ConflictedAllocationState Conflict} -
 * approximation, multiple symbols can be present</li>
 * </ul>
 *
 * Every block has its own {@link jdk.graal.compiler.lir.alloc.verifier.AllocationStateMap
 * allocation map}: location -> state. Where states are stored for every location. If blocks meet at
 * their common successor, two different symbols can be present at the same location. This is
 * handled by a {@link jdk.graal.compiler.lir.alloc.verifier.AllocationState#meet} function. The
 * logic of the two-state meeting can be described in this way:
 * 
 * <pre>
 * meet(x, x) = x
 * meet(x, y) = Conflicted, if x != y
 * </pre>
 *
 * Where both states need to be equal to be passed to the merge block, otherwise a conflict is
 * created. Conflict does not mean an error state, rather that it is forbidden to read from this
 * location. Conflict can be overwritten by Op generating a new symbol to the same location.
 * </p>
 *
 * <p>
 * The algorithm works in two stages:
 * <ol>
 * <li>Compute the initial state of every block until a fixed point is reached, only generates
 * symbols</li>
 * <li>Verify that read symbols are present at their locations</li>
 * </ol>
 * </p>
 *
 * <p>
 * {@link jdk.graal.compiler.lir.alloc.verifier.RegAllocVerifier#computeEntryStates} runs a queue of
 * basic blocks, starting from the start block. Every block takes its current entry state and runs
 * symbolic execution on its instructions. The new state is then passed to its successors. Successor
 * is enqueued only if a change was made to its entry state.
 * </p>
 *
 * <p>
 * When every block has a fixed entry state (the allocation map), then we can linearly iterate over
 * every block, take its entry state, and for every instruction, first check that read symbols are
 * at read locations in the state map, second generate or move symbols. Implemented in
 * {@link jdk.graal.compiler.lir.alloc.verifier.RegAllocVerifier#verifyInstructionInputs}.
 * </p>
 *
 * <h2>Preprocessing stage and the IR</h2>
 *
 * Before the validation process can begin, the intermediate representation needs to be created.
 * Pre-allocation instructions are processed and stored in a map: LIRInstruction -> Op. Before
 * allocation, we also map symbols to fixed registers, as in this example:
 *
 * <pre>
 * rdi|DWORD = MOVE input: int[1|0x1]
 * rsi|QWORD = MOVE input: v7|QWORD moveKind: QWORD
 * rax = CALL parameters: [rdi|DWORD, rsi|QWORD] temps: [rax|ILLEGAL, ...]
 * v8 = MOVE input: rax|QWORD
 * </pre>
 *
 * Maps the symbols 0x1 and v7 to the rdi and rsi in the CALL, so if these moves are coalesced,
 * information is not lost. It also maps v8 to the rax output of the CALL.
 *
 * <p>
 * After allocation is performed, the IR can now be completed. Every existing lir instruction has a
 * {@link jdk.graal.compiler.lir.alloc.verifier.RAVInstruction.Base} counterpart. If it was present
 * before allocation, an {@link jdk.graal.compiler.lir.alloc.verifier.RAVInstruction.Op} otherwise a
 * {@link jdk.graal.compiler.lir.alloc.verifier.RAVInstruction.LocationMove}.
 * </p>
 *
 * <p>
 * Variable to variable moves, e.g. <code>v2|DWORD = MOVE v1|DWORD</code> can be removed by the
 * allocator. If this happens, every occurrence of the output variable is replaced with the source
 * variable. {@link jdk.graal.compiler.lir.alloc.verifier.VariableSynonymMap} also handles chained
 * coalesced moves, always uses the root source variable created by an existing instruction for
 * substitution.
 * </p>
 *
 * <h3>Constants</h3>
 *
 * Constants are created by {@link jdk.graal.compiler.lir.StandardOp.LoadConstantOp}, where the
 * output before allocation can be a variable. They can also be used as operands before allocation
 * and be put into a register by the allocator. For example, JUMP [int[0|0x0]] as the initial
 * condition of a for loop.
 *
 * <p>
 * To normalize this behavior, variables defined by
 * {@link jdk.graal.compiler.lir.StandardOp.LoadConstantOp} are substituted for their constant
 * values inside the verifier IR. Furthermore, every
 * {@link jdk.graal.compiler.lir.StandardOp.LoadConstantOp} has its own instruction
 * {@link jdk.graal.compiler.lir.alloc.verifier.RAVInstruction.ValueMove} in the IR, which puts the
 * constant symbol into specified location. Doing this also handles constants re-materialized by the
 * allocator.
 * </p>
 *
 * <h3>Inferring label-defined variable locations</h2>
 *
 * The allocator strips information about locations of label-defined variables, as well as locations
 * of symbols in the preceding jumps. This information is necessary to correctly verify symbol
 * reads.
 *
 * <p>
 * This information is reconstructed from the verifier's IR by finding the first usage of the
 * label-defined variable and going back through the CFG to the label. Handling any
 * allocator-inserted moves that might have moved the symbol.
 * </p>
 *
 * <p>
 * The preceding jumps then assert the result of this reconstruction. Their location matches the one
 * in the label. If the symbols flowing through the JUMP into the LABEL are not contained in the
 * selected location, for every predecessor, then a failure occurs.
 * </p>
 *
 * <p>
 * Implemented in {@link jdk.graal.compiler.lir.alloc.verifier.FromUsageResolverGlobal}, uses a
 * worklist, walking from every end block to the start, resolving label variable locations.
 * </p>
 *
 * <h2>Exceptions</h2>
 *
 * Exceptions are in the package {@link jdk.graal.compiler.lir.alloc.verifier.exceptions}. Each of
 * them stores debugging information about the fault that was detected. Block, instruction where it
 * occurred, and other exception-specific information.
 *
 * <p>
 * {@link jdk.graal.compiler.lir.alloc.verifier.AllocationState} maintains debug information.
 * {@link jdk.graal.compiler.lir.alloc.verifier.ValueAllocationState} contains information about the
 * instruction that created the symbol.
 * {@link jdk.graal.compiler.lir.alloc.verifier.ConflictedAllocationState} contains all the value
 * allocation states that are in conflict. New information added to the set of conflicting value
 * states is not propagated through the CFG.
 * </p>
 *
 * <p>
 * When an exception is thrown, a debug file with the extension ".rax.txt" is created in the
 * graal_dumps directory. The file contains the CFG and verifier's instructions in a text format.
 * Describing in which block and which instruction has the exception occurred. Optionally, the
 * process can gather multiple exceptions, always at most one per instruction, using
 * {@link jdk.graal.compiler.lir.alloc.verifier.exceptions.RAVFailedVerificationException}.
 * </p>
 *
 * <h2>Other checking</h2>
 *
 * <p>
 * Checking that other constraints, the register allocator needs to follow, were not violated.
 * <ul>
 * <li>Check kinds between the original symbol and stored symbol</li>
 * <li>Check if collected GC roots are actually references</li>
 * <li>Check that "alive" inputs live past the instruction</li>
 * <li>Check that only allocatable registers are used</li>
 * <li>Check if callee-saved registers are retrieved at exit</li>
 * <li>Check JavaKind and LIRKind correspondence in {@link jdk.vm.ci.code.BytecodeFrame}</li>
 * <li>Check for operand flags</li>
 * </ul>
 * </p>
 *
 * <h2>Options</h2>
 * <ul>
 * <li>{@link jdk.graal.compiler.lir.alloc.verifier.RegAllocVerifierPhase.Options#EnableRAVerifier}
 * - enable this process during compilation</li>
 * <li>{@link jdk.graal.compiler.lir.alloc.verifier.RegAllocVerifierPhase.Options#VerifyStackAllocator}
 * - verify the output of stack allocator</li>
 * <li>{@link jdk.graal.compiler.lir.alloc.verifier.RegAllocVerifierPhase.Options#CollectReferences}
 * - use {@link jdk.graal.compiler.lir.dfa.LocationMarker} interface to collect reference
 * information</li>
 * <li>{@link jdk.graal.compiler.lir.alloc.verifier.RegAllocVerifierPhase.Options#RAVFailOnFirst} -
 * if set to true, first failure is thrown; otherwise all failures are collected at thrown at once
 * using
 * {@link jdk.graal.compiler.lir.alloc.verifier.exceptions.RAVFailedVerificationException}</li>
 * </ul>
 *
 * <h3>Collect references</h3>
 *
 * The verifier uses {@link jdk.graal.compiler.lir.dfa.LocationMarker} to collect live object
 * reference information before {@link jdk.graal.compiler.lir.phases.FinalCodeAnalysisStage final
 * code analysis} runs. {@link jdk.graal.compiler.lir.alloc.verifier.RAVInstruction.Op Op} holds a
 * set of references. When being symbolically executed
 * ({@link jdk.graal.compiler.lir.alloc.verifier.BlockVerifierState#update} and the reference set is
 * not null, all references that are not part of the set are deleted from the
 * {@link jdk.graal.compiler.lir.alloc.verifier.AllocationStateMap allocation map}. Checking stage,
 * then verifies that all references in the set are actually references.
 */
package jdk.graal.compiler.lir.alloc.verifier;
