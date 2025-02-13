/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes.quick.invoke;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.analysis.frame.EspressoFrameDescriptor;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.ContinuableMethodWithBytecode;
import com.oracle.truffle.espresso.nodes.ContinuableMethodWithBytecodeFactory;
import com.oracle.truffle.espresso.nodes.EspressoFrame;
import com.oracle.truffle.espresso.vm.continuation.HostFrameRecord;
import com.oracle.truffle.espresso.vm.continuation.UnwindContinuationException;

@GenerateWrapper(resumeMethodPrefix = "resumeContinuation", //
                yieldExceptions = BytecodeNode.EspressoOSRReturnException.class)
public class InvokeContinuableNode extends InvokeQuickNode {
    @Child InvokeQuickNode original;
    @Child ContinuableMethodWithBytecode.ResumeNextContinuationNode resumeNext;

    public InvokeContinuableNode(int top, int curBCI, InvokeQuickNode original) {
        super(original.method, top, curBCI);
        this.original = original;
        this.resumeNext = ContinuableMethodWithBytecodeFactory.ResumeNextContinuationNodeGen.create();
    }

    InvokeContinuableNode(InvokeContinuableNode copy) {
        super(copy.method, copy.top, copy.getCallerBCI());
        this.original = copy.original;
    }

    // Normal execution path.
    // Note: this method is not instrumented. Instrumentation of the original node should be what is
    // important in the non-resume case.
    @Override
    public final int execute(VirtualFrame frame, boolean isContinuationResume) {
        if (isContinuationResume) {
            return resumeContinuation(frame);
        } else {
            return original.execute(frame, false);
        }
    }

    // Resuming execution path.
    protected int resumeContinuation(VirtualFrame frame) {
        ContinuableMethodWithBytecode continuableNode = getContinuableNode();
        HostFrameRecord hfr = continuableNode.getFrameRecords(frame);

        EspressoFrameDescriptor fd = continuableNode.getFD();
        CompilerAsserts.partialEvaluationConstant(fd);

        fd.exportToFrame(frame, hfr.objects, hfr.primitives);

        // Unlink records
        HostFrameRecord next = hfr.next;
        hfr.next = null;
        try {
            // Keep rewinding, then push the result.
            return pushResult(frame, resumeNext.execute(next));
        } catch (UnwindContinuationException unwind) {
            // Small optimization: we can re-use the previously computed frame, unless the frame
            // is tainted.
            if (EspressoFrame.isTainted(frame)) {
                fd.importFromFrame(frame, hfr.objects, hfr.primitives);
                hfr.untaint();
            }
            hfr.next = unwind.head;
            unwind.head = hfr;
            // Hijack the early return mechanism of OSR to prevent processing of this unwind in the
            // main bytecode loop.
            throw new BytecodeNode.EspressoOSRReturnException(unwind);
        }
    }

    @ExplodeLoop
    public final ContinuableMethodWithBytecode getContinuableNode() {
        Node parent = getParent();
        while (!(parent instanceof ContinuableMethodWithBytecode)) {
            parent = parent.getParent();
        }
        return (ContinuableMethodWithBytecode) parent;
    }

    @Override
    public WrapperNode createWrapper(ProbeNode probeNode) {
        return new InvokeContinuableNodeWrapper(this, this, probeNode);
    }
}
