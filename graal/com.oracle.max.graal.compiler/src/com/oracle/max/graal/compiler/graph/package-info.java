/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 * <H2>IR Graph building</H2>
 *
 * The {@link com.oracle.max.graal.compiler.graph.IR} class drives the generation of the HIR graph for a method, making use of other
 * utility classes in this package.
 *
 * The graph building is separated into a basic build phase ({@link com.oracle.max.graal.compiler.graph.IR#buildGraph} method)and
 * (currently) two optimization phases ({@link com.oracle.max.graal.compiler.graph.IR#optimize1} and
 * {@link com.oracle.max.graal.compiler.graph.IR#optimize2}) although the basic phase also does some (basic) optimizations.
 *
 * <H2>Basic Graph Build Phase</H2>
 *
 * {@code IR.buildGraph} creates an {@link com.oracle.max.graal.compiler.ir.IRScope topScope} object,
 * that represents a context for inlining, and then invokes the constructor for the
 * {@link com.oracle.max.graal.compiler.phases.GraphBuilderPhase} class, passing the {@link com.oracle.max.graal.compiler.C1XCompilation}, and {@code IR}
 * instances, which are cached. The following support objects are created in the constructor:
 *
 * <ul>
 * <li>{@code memoryMap}: an instance of {@link com.oracle.max.graal.compiler.graph.MemoryMap}
 * <li>{@code localValueMap}: an instance of {@link com.oracle.max.graal.compiler.opt.ValueMap}
 * <li>{@code canonicalizer}: an instance of {@link com.oracle.max.graal.compiler.opt.Canonicalizer}
 * </ul>
 *
 * Now the {@link com.oracle.max.graal.compiler.phases.GraphBuilderPhase#build} is invoked with {@code topScope} as argument.
 *
 * <H3>{@code GraphBuilder.build}</H3>
 *
 * <ol>
 * <li>The {@link com.oracle.max.graal.compiler.graph.IR#startBlock} field of the cached {@link com.oracle.max.graal.compiler.graph.IR} instance is set to a newly created
 * {@link com.oracle.max.graal.compiler.ir.BlockBegin} node, with bytecode index 0 and then the {@link com.oracle.max.graal.compiler.graph.BlockMap} is
 * constructed by calling {@link com.oracle.max.graal.compiler.C1XCompilation#getBlockMap}. This behaves slightly differently depending on
 * whether this is an OSR compilation. If so, a new {@link com.oracle.max.graal.compiler.ir.BlockBegin} node is added to the map at the OSR bytecode
 * index. The map is then built by the{@link com.oracle.max.graal.compiler.graph.BlockMap#build}, which takes a boolean argument that
 * controls whether a second pass is made over the bytecodes to compute stores in loops. This always false for an OSR
 * compilation (why?). Otherwise, it is only true if enabled by the {@link com.oracle.max.graal.compiler.C1XOptions#PhiLoopStores}
 * compilation option. FInally some unneeded state from the map is removed by the {@link com.oracle.max.graal.compiler.graph.BlockMap#cleanup} method, and
 * the statistics are updated.
 * </li>
 *
 * <li>Next the {@link com.oracle.max.graal.compiler.phases.GraphBuilderPhase#pushRootScope} method is called, with the passed-in {@link com.oracle.max.graal.compiler.ir.IRScope}
 * object, the {@link com.oracle.max.graal.compiler.graph.BlockMap} returned by build and the {@code startBlock}. (Note: Unlike
 * {@link com.oracle.max.graal.compiler.phases.GraphBuilderPhase#pushScope}, this method does not propagate the
 * {@link com.oracle.max.graal.compiler.graph.BlockMap#storesInLoops} field to the {@link com.oracle.max.graal.compiler.ir.IRScope} object, which means that
 * {@link com.oracle.max.graal.compiler.ir.BlockBegin#insertLoopPhis} will always get null for this value. Is this a bug?).
 * {@link com.oracle.max.graal.compiler.phases.GraphBuilderPhase#pushRootScope} initializes the {@link com.oracle.max.graal.compiler.phases.GraphBuilderPhase#scopeData} field with a
 * {@link com.oracle.max.graal.compiler.graph.ScopeData} instance, with null parent. The
 * {@link com.oracle.max.graal.compiler.phases.GraphBuilderPhase#compilation} instance is called to get an {@link com.sun.cri.ri.RiConstantPool}
 * , which is C1X's interface to constant pool information. The {@link com.oracle.max.graal.compiler.phases.GraphBuilderPhase#curBlock} field is
 * set to the {@code startBlock}.
 * <p>
 *
 * Now a {@link com.oracle.max.graal.compiler.value.FrameState initialState} object is created by
 * {@link com.oracle.max.graal.compiler.phases.GraphBuilderPhase#stateAtEntry}. If the method is not static, then a {@link com.oracle.max.graal.compiler.ir.Local}
 * instance is created at index 0. Since the receiver cannot be {@code null}, the
 * {@link com.oracle.max.graal.compiler.ir.Value.Flag#NonNull} flag is set. Additional {@link com.oracle.max.graal.compiler.ir.Local} instances are created for the
 * arguments to the method. The index is incremented by the number of slots occupied by the
 * {@link com.sun.cri.ci.CiKind} corresponding to the argument type. All the {@link com.oracle.max.graal.compiler.ir.Local} instances are stored in the
 * {@link com.oracle.max.graal.compiler.value.FrameState} using the {@link com.oracle.max.graal.compiler.value.FrameState#storeLocal} method. This {@link com.oracle.max.graal.compiler.value.FrameState} is then
 * merged into the {@link com.oracle.max.graal.compiler.ir.BlockBegin#stateBefore} for the {@code startBlock}, which just results in a
 * copy since {@code stateBefore} will be {@code null}.
 * </li>
 * <li>
 * This step sets up three instance fields: {@link com.oracle.max.graal.compiler.phases.GraphBuilderPhase#curBlock} and
 * {@link com.oracle.max.graal.compiler.phases.GraphBuilderPhase#lastInstr} to {@code startBlock} and
 * {@link com.oracle.max.graal.compiler.phases.GraphBuilderPhase#curState} to {@code initialState}. (N.B. the setting of {@code curBlock} is
 * redundant as it is done in {@link com.oracle.max.graal.compiler.phases.GraphBuilderPhase#pushRootScope}).
 * </li>
 * <li>
 * Step 4 contains special handling for synchronized methods (TBD), otherwise it calls
 * {@link com.oracle.max.graal.compiler.phases.GraphBuilderPhase#finishStartBlock} which adds a {@link com.oracle.max.graal.compiler.ir.Base} block as the end of
 * the {@code startBlock}. The {@link com.oracle.max.graal.compiler.ir.Base} block has one successor set to the (entry) block with flag
 * {@link com.oracle.max.graal.compiler.ir.BlockBegin.BlockFlag#StandardEntry}, that was created by {@link com.oracle.max.graal.compiler.graph.BlockMap#build} (and possibly a
 * successor to an OSREntry block).
 * </li>
 * <li>
 * Then the {@link com.oracle.max.graal.compiler.ir.IRScope#lockStackSize} is computed. (TBD)
 * </li>
 * <li>
 * Then the method is checked for being intrinsic, i.e., one that has a hard-wired implementation known to C1X. If so,
 * and {@link com.oracle.max.graal.compiler.C1XOptions#OptIntrinsify} is set, an attempt is made to inline it (TBD). Otherwise, or if the
 * intrinsification fails, normal processing continues by adding the entry block to the
 * {@link com.oracle.max.graal.compiler.graph.ScopeData} work list (kept topologically sorted) and calling
 * {@link com.oracle.max.graal.compiler.phases.GraphBuilderPhase#iterateAllBlocks}.
 * </li>
 * <li>
 * Finally there is some cleanup code for synchronized blocks and OSR compilations.
 * </li>
 * </ol>
 *
 * <H3>{@link com.oracle.max.graal.compiler.phases.GraphBuilderPhase#iterateAllBlocks}</H3>
 * {@link com.oracle.max.graal.compiler.graph#iterateAllBlocks} repeatedly removes a block from the work list and, if not already visited, marks it so,
 * kills the current memory map, sets {@link com.oracle.max.graal.compiler.phases.GraphBuilderPhase#curBlock}, {@link com.oracle.max.graal.compiler.phases.GraphBuilderPhase#curState}
 * and {@link com.oracle.max.graal.compiler.phases.GraphBuilderPhase#lastInstr} and then calls
 * {@link com.oracle.max.graal.compiler.phases.GraphBuilderPhase#iterateBytecodesForBlock}.
 *
 * This process continues until all the blocks have been visited (processed) after which control returns to {@code
 * build}.
 * <p>

 * <H3>{@link com.oracle.max.graal.compiler.phases.GraphBuilderPhase#iterateBytecodesForBlock}</H3>
 *
 * {@link com.oracle.max.graal.compiler.phases.GraphBuilderPhase#iterateBytecodesForBlock} performs an abstract interpretation of the bytecodes in the block, appending new
 * nodes as necessary, until the last added node is an instance of {@link com.oracle.max.graal.compiler.ir.BlockEnd}. (Note: It has an
 * explicit check for finding a new {@link com.oracle.max.graal.compiler.ir.BlockBegin} before a {@link com.oracle.max.graal.compiler.ir.BlockEnd} but
 * {@link com.oracle.max.graal.compiler.graph.BlockMap#moveSuccessorLists} has a similar check so this may be redundant). For example,
 * consider the following bytecodes:
 *
 * <pre>
 * <code>
 *         0: iconst_0
 *         1: istore_2
 *         2: goto 22
 * </code>
 * </pre>
 *
 * The {@code iconst_0} bytecode causes a {@link com.oracle.max.graal.compiler.ir.Constant} node representing zero to be pushed on the
 * {@link com.oracle.max.graal.compiler.phases.GraphBuilderPhase#curState} stack and the node to be appended to the {@link com.oracle.max.graal.compiler.ir.BlockBegin} (entry) node associated with index 0.
 * The {@code istore_2} causes the node to be popped of the stack and stored in the local slot 2. No IR node is
 * generated for the {@code istore_2}. The {@code goto} creates a {@link com.oracle.max.graal.compiler.ir.Goto} node which is a subclass
 * of {@link com.oracle.max.graal.compiler.ir.BlockEnd}, so this terminates the iteration. As part of termination the {@link com.oracle.max.graal.compiler.ir.Goto} node is marked as the
 * end node of the current block and the {@link com.oracle.max.graal.compiler.value.FrameState} is propagated to the successor node(s) by merging any
 * existing {@link com.oracle.max.graal.compiler.value.FrameState} with the current state. If the target is a loop header node this involves inserting
 * {@link com.oracle.max.graal.compiler.ir.Phi} nodes. Finally, the target node is added to the {@code scopeData} work list.
 * <p>
 *
 *
 * @author Ben Titzer
 * @author Mick Jordan
 *
 */
package com.oracle.max.graal.compiler.graph;
