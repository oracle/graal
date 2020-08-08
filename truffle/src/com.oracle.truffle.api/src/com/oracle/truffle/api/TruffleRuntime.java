/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

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
     * To get possible asynchronous stack frames, use
     * {@link TruffleStackTrace#getAsynchronousStackTrace(CallTarget, Frame)} and provide call
     * target and frame from the last {@link FrameInstance}.
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
