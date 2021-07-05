/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.nodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Interface for Truffle bytecode nodes which can be on-stack replaced (OSR). Bytecode OSR nodes
 * must extend {@link Node} or a subclass of {@link Node}.
 *
 * @since 21.3 TODO update
 */
public interface BytecodeOSRNode extends NodeInterface {

    /**
     * Entrypoint for invoking this node through OSR. Typically, this method will:
     * <ul>
     * <li>transfer state from the {@code parentFrame} into the {@code innerFrame} (if necessary)
     * <li>execute this node from the {@code target} location
     * <li>transfer state from the {@code innerFrame} back to the {@code parentFrame} (if necessary)
     * </ul>
     *
     * NOTE: The result of {@link Frame#getArguments()} for {@code innerFrame} is undefined and
     * should not be used. Additionally, since the parent frame could also come from an OSR call (in
     * the situation where an OSR call deoptimizes), the arguments of {@code parentFrame} are also
     * undefined.
     *
     * @param innerFrame the frame to use for OSR.
     * @param parentFrame the frame of the previous invocation (which may itself be an OSR frame).
     * @param target the target location to execute from (e.g., bytecode index).
     * @return the result of execution.
     */
    Object executeOSR(VirtualFrame innerFrame, Frame parentFrame, int target);

    /*
     * OSRMetadata is a virtual field representing the {@link TruffleRuntime runtime}-specific
     * metadata required for OSR compilation.
     * 
     * Since interfaces cannot declare fields, a class implementing this interface should declare a
     * field for the metadata and proxy accesses through these accessors.
     *
     * NOTE: this field should be volatile for thread-safety.
     */

    /**
     * Gets the OSR metadata for this instance.
     * 
     * @return the OSR metadata.
     */
    Object getOSRMetadata();

    /**
     * Sets the OSR metadata for this instance.
     * 
     * @param osrMetadata the OSR metadata.
     */
    void setOSRMetadata(Object osrMetadata);

    /**
     * Reports a back edge to the target location. This information can be used to trigger on-stack
     * replacement (OSR).
     *
     * @param osrNode the node for which to report a back-edge.
     * @param parentFrame frame at current point of execution.
     * @param target target location of the jump (e.g., bytecode index).
     * @return result if OSR was performed, or {@code null} otherwise.
     */
    static Object reportOSRBackEdge(BytecodeOSRNode osrNode, VirtualFrame parentFrame, int target) {
        if (!CompilerDirectives.inInterpreter()) {
            return null;
        }
        return NodeAccessor.RUNTIME.onOSRBackEdge(osrNode, parentFrame, target);
    }
}
