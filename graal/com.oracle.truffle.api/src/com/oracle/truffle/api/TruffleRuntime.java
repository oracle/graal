/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

/**
 * Interface representing a Truffle runtime object. The runtime is responsible for creating call
 * targets and performing optimizations for them.
 */
public interface TruffleRuntime {

    /**
     * Name describing this runtime implementation for debugging purposes.
     *
     * @return the name as a String
     */
    String getName();

    /**
     * Creates a new call target for a given root node.
     *
     * @param rootNode the root node whose
     *            {@link RootNode#execute(com.oracle.truffle.api.frame.VirtualFrame)} method
     *            represents the entry point
     * @return the new call target object
     */
    RootCallTarget createCallTarget(RootNode rootNode);

    /**
     * Creates a new runtime specific version of {@link DirectCallNode}.
     *
     * @param target the direct {@link CallTarget} to call
     * @return the new call node
     */
    DirectCallNode createDirectCallNode(CallTarget target);

    /**
     * Creates a new runtime specific version of {@link IndirectCallNode}.
     *
     * @return the new call node
     */
    IndirectCallNode createIndirectCallNode();

    /**
     * Creates a new assumption object that can be checked and invalidated.
     *
     * @return the newly created assumption object
     */
    Assumption createAssumption();

    /**
     * Creates a new assumption object with a given name that can be checked and invalidated.
     *
     * @param name the name for the new assumption
     * @return the newly created assumption object
     */
    Assumption createAssumption(String name);

    /**
     * Creates a new virtual frame object that can be used to store values and is potentially
     * optimizable by the runtime.
     *
     * @return the newly created virtual frame object
     */
    VirtualFrame createVirtualFrame(Object[] arguments, FrameDescriptor frameDescriptor);

    /**
     * Creates a new materialized frame object that can be used to store values.
     *
     * @return the newly created materialized frame object
     */
    MaterializedFrame createMaterializedFrame(Object[] arguments);

    /**
     * Creates a new materialized frame object with the given frame descriptor that can be used to
     * store values.
     *
     * @param frameDescriptor the frame descriptor describing this frame's values
     * @return the newly created materialized frame object
     */
    MaterializedFrame createMaterializedFrame(Object[] arguments, FrameDescriptor frameDescriptor);

    /**
     * Accesses the current stack, i.e., the contents of the {@link Frame}s and the associated
     * {@link CallTarget}s.
     *
     * @return a lazy collection of {@link FrameInstance}.
     */
    Iterable<FrameInstance> getStackTrace();

    /**
     * Accesses the current frame, i.e., the frame of the closest {@link CallTarget}. It is
     * important to note that this {@link FrameInstance} supports only slow path access.
     */
    FrameInstance getCurrentFrame();

}
