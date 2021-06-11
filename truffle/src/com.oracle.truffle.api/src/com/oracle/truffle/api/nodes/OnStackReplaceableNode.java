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
import com.oracle.truffle.api.ReplaceObserver;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Interface for Truffle bytecode nodes which can be on-stack replaced.
 *
 * @since 21.2 TODO update
 */
public abstract class OnStackReplaceableNode extends ExecutableNode implements ReplaceObserver {
    private Object osrState;

    protected OnStackReplaceableNode(TruffleLanguage<?> language) {
        super(language);
    }

    abstract public Object doOSR(VirtualFrame innerFrame, Frame parentFrame, int target);

    /**
     * Reports a back edge to the target location. This information could be used to trigger
     * on-stack replacement (OSR)
     *
     * @param parentFrame frame at current point of execution
     * @param target target location of the jump (e.g., bytecode index).
     * @return result if OSR was performed, or {@code null} otherwise.
     */
    public final Object reportOSRBackEdge(VirtualFrame parentFrame, int target) {
        // Report loop count for the standard compilation path.
        LoopNode.reportLoopCount(this, 1);
        if (!CompilerDirectives.inInterpreter()) {
            return null;
        }
        return NodeAccessor.RUNTIME.onOSRBackEdge(this, parentFrame, target, getLanguage());
    }

    @Override
    public final boolean nodeReplaced(Node oldNode, Node newNode, CharSequence reason) {
        NodeAccessor.RUNTIME.onOSRNodeReplaced(this, oldNode, newNode, reason);
        return false;
    }

    public final Object getOSRState() {
        return osrState;
    }

    public final void setOSRState(Object osrState) {
        this.osrState = osrState;
    }
}
