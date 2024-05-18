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

package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.vm.continuation.EspressoFrameDescriptor;
import com.oracle.truffle.espresso.vm.continuation.HostFrameRecord;

public class ContinuableMethodWithBytecode extends EspressoInstrumentableRootNodeImpl {
    @Child BytecodeNode bytecodeNode;
    @Child ResumeThisContinuationNode resumeThis;

    public ContinuableMethodWithBytecode(BytecodeNode bytecodeNode) {
        super(bytecodeNode.getMethodVersion());
        this.bytecodeNode = bytecodeNode;
        this.resumeThis = ContinuableMethodWithBytecodeFactory.ResumeThisContinuationNodeGen.create();
    }

    @Override
    Object execute(VirtualFrame frame) {
        HostFrameRecord frameRecords = getFrameRecords(frame);
        return resumeThis.execute(frame, frameRecords, bytecodeNode);
    }

    private HostFrameRecord getFrameRecords(VirtualFrame frame) {
        assert frame.getArguments().length == 1 && frame.getArguments()[0] instanceof HostFrameRecord;
        HostFrameRecord records = (HostFrameRecord) frame.getArguments()[0];
        assert records.methodVersion == getMethodVersion();
        return records;
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        if (tag == StandardTags.RootTag.class) {
            return true;
        }
        return false;
    }

    @Override
    public int getBci(Frame frame) {
        return EspressoFrame.getBCI(frame);
    }

    /**
     * Used to rewind a frame. At most one cache entry per invoke BCI in the method. Since BCIs need
     * to be constant during PE, a cache fail means no compilation can happen, so we are using a
     * larger-than-usual cache to prevent that from happening.
     */
    abstract static class ResumeThisContinuationNode extends EspressoNode {
        // Uncached calls cannot be PE'd, so use a large cache.
        static final int LARGE_LIMIT = 10;

        public abstract Object execute(VirtualFrame frame, HostFrameRecord records, BytecodeNode bytecodeNode);

        @Specialization(guards = {"sameBciInRecord(records, cachedBci)"}, limit = "LARGE_LIMIT")
        Object doCached(VirtualFrame frame, HostFrameRecord records, BytecodeNode bytecodeNode,
                        @Cached("records.bci") int cachedBci,
                        @Cached("records.frameDescriptor") EspressoFrameDescriptor cachedFD,
                        @Cached @Exclusive ResumeNextContinuationNode resumeNext) {
            assert cachedFD.equals(records.frameDescriptor);
            assert bytecodeNode.getMethodVersion() == records.methodVersion;
            return bytecodeNode.resumeContinuation(frame, records, cachedBci, cachedFD, resumeNext);
        }

        @Specialization(replaces = "doCached")
        Object doUncached(VirtualFrame frame, HostFrameRecord records, BytecodeNode bytecodeNode,
                        @Cached @Exclusive ResumeNextContinuationNode resumeNext) {
            MaterializedFrame materializedFrame = frame.materialize();
            return bytecodeNode.resumeContinuationBoundary(materializedFrame, records, records.bci, records.frameDescriptor, resumeNext);
        }

        static boolean sameBciInRecord(HostFrameRecord records, int cachedBci) {
            return records.bci == cachedBci;
        }
    }

    /**
     * Passed to the bytecode node, so it can rewind the next continuation in the record.
     */
    public abstract static class ResumeNextContinuationNode extends EspressoNode {
        static final int LIMIT = 3;

        public abstract Object execute(HostFrameRecord records);

        @Specialization(guards = "isLastRecord(records)")
        Object doLast(HostFrameRecord records) {
            assert records == null;
            assert ((EspressoRootNode) getRootNode()).getMethod() == getMeta().continuum.com_oracle_truffle_espresso_continuations_Continuation_suspend;
            return StaticObject.NULL;
        }

        @Specialization(guards = {"sameMethodInRecord(records, cachedMethod)"}, limit = "LIMIT")
        Object doCached(HostFrameRecord records,
                        @Cached("records.methodVersion") Method.MethodVersion cachedMethod,
                        @Cached("create(cachedMethod.getContinuableCallTarget())") DirectCallNode callNode) {
            assert records.methodVersion == cachedMethod;
            return callNode.call(records);
        }

        @Specialization(replaces = "doCached")
        Object doUncached(HostFrameRecord records,
                        @Cached IndirectCallNode callNode) {
            return callNode.call(records.methodVersion.getContinuableCallTarget(), records);
        }

        static boolean sameMethodInRecord(HostFrameRecord records, Method.MethodVersion method) {
            return records.methodVersion == method;
        }

        static boolean isLastRecord(HostFrameRecord records) {
            return records == null;
        }
    }
}
