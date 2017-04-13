/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

import java.util.Collection;
import java.util.Collections;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Interface representing a Truffle runtime object. The runtime is responsible for creating call
 * targets and performing optimizations for them.
 *
 * @since 0.8 or earlier
 */
public interface TruffleRuntime {

    /**
     * Name describing this runtime implementation for debugging purposes.
     *
     * @return the name as a String
     * @since 0.8 or earlier
     */
    String getName();

    /**
     * Creates a new call target for a given root node.
     *
     * @param rootNode the root node whose
     *            {@link RootNode#execute(com.oracle.truffle.api.frame.VirtualFrame)} method
     *            represents the entry point
     * @return the new call target object
     * @since 0.8 or earlier
     */
    RootCallTarget createCallTarget(RootNode rootNode);

    /**
     * Creates a new runtime specific version of {@link DirectCallNode}.
     *
     * @param target the direct {@link CallTarget} to call
     * @return the new call node
     * @since 0.8 or earlier
     */
    DirectCallNode createDirectCallNode(CallTarget target);

    /**
     * Creates a new loop node with an implementation provided by a Truffle runtime implementation.
     * Using Truffle loop nodes allows the runtime to do additional optimizations such as on stack
     * replacement for loops.
     *
     * @see LoopNode usage example
     * @since 0.8 or earlier
     */
    LoopNode createLoopNode(RepeatingNode body);

    /**
     * Creates a new runtime specific version of {@link IndirectCallNode}.
     *
     * @return the new call node
     * @since 0.8 or earlier
     */
    IndirectCallNode createIndirectCallNode();

    /**
     * Creates a new assumption object that can be checked and invalidated.
     *
     * @return the newly created assumption object
     * @since 0.8 or earlier
     */
    Assumption createAssumption();

    /**
     * Creates a new assumption object with a given name that can be checked and invalidated.
     *
     * @param name the name for the new assumption
     * @return the newly created assumption object
     * @since 0.8 or earlier
     */
    Assumption createAssumption(String name);

    /**
     * Creates a new virtual frame object that can be used to store values and is potentially
     * optimizable by the runtime.
     *
     * @return the newly created virtual frame object
     * @since 0.8 or earlier
     */
    VirtualFrame createVirtualFrame(Object[] arguments, FrameDescriptor frameDescriptor);

    /**
     * Creates a new materialized frame object that can be used to store values.
     *
     * @return the newly created materialized frame object
     * @since 0.8 or earlier
     */
    MaterializedFrame createMaterializedFrame(Object[] arguments);

    /**
     * Creates a new materialized frame object with the given frame descriptor that can be used to
     * store values.
     *
     * @param frameDescriptor the frame descriptor describing this frame's values
     * @return the newly created materialized frame object
     * @since 0.8 or earlier
     */
    MaterializedFrame createMaterializedFrame(Object[] arguments, FrameDescriptor frameDescriptor);

    /**
     * Creates an object which allows you to test for support of and set options specific for this
     * runtime.
     *
     * @return the newly created compiler options object
     * @since 0.8 or earlier
     */
    CompilerOptions createCompilerOptions();

    /**
     * Accesses the current stack, i.e., the contents of the {@link Frame}s and the associated
     * {@link CallTarget}s. Iteration starts at the current frame.
     *
     * Iteration continues as long as {@link FrameInstanceVisitor#visitFrame}, which is invoked for
     * every {@link FrameInstance}, returns null. Any non-null result of the visitor indicates that
     * frame iteration should stop.
     *
     * @param visitor the visitor that is called for every matching frame.
     * @return the last result returned by the visitor (which is non-null to indicate that iteration
     *         should stop), or null if the whole stack was iterated.
     * @since 0.8 or earlier
     */
    <T> T iterateFrames(FrameInstanceVisitor<T> visitor);

    /**
     * Accesses the caller frame. This is a convenience method that returns the first frame that is
     * passed to the visitor of {@link #iterateFrames}.
     *
     * @since 0.8 or earlier
     */
    FrameInstance getCallerFrame();

    /**
     * Accesses the current frame, i.e., the frame of the closest {@link CallTarget}. It is
     * important to note that this {@link FrameInstance} supports only slow path access.
     *
     * @since 0.8 or earlier
     */
    FrameInstance getCurrentFrame();

    /**
     * Requests a capability from the runtime.
     *
     * @param capability the type of the interface representing the capability
     * @return an implementation of the capability or {@code null} if the runtime does not offer it
     * @since 0.8 or earlier
     */
    <T> T getCapability(Class<T> capability);

    /**
     * Returns a list of all still referenced {@link RootCallTarget} instances that were created
     * using {@link #createCallTarget(RootNode)}.
     *
     * @since 0.8 or earlier
     * @deprecated Loaded sources and source sections can be accessed using the Truffle
     *             instrumentation framework. Deprecated in Truffle 0.15.
     */
    @Deprecated
    default Collection<RootCallTarget> getCallTargets() {
        return Collections.emptyList();
    }

    /**
     * Internal API method. Do not use.
     *
     * @since 0.8 or earlier
     */
    void notifyTransferToInterpreter();

    /**
     * Whether or not the {@link TruffleRuntime} implementation can or wants to use gathered
     * profiling information Truffle compilation. If this method returns <code>false</code> then all
     * profiles in the {@link com.oracle.truffle.api.utilities} package are returning void
     * implementations. If it returns <code>true</code> then all implementations gather profilinig
     * information.
     *
     * @since 0.8 or earlier
     */
    boolean isProfilingEnabled();

}
