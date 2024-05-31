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
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.espresso.analysis.frame.EspressoFrameDescriptor;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.vm.continuation.HostFrameRecord;

public class ContinuableMethodWithBytecode extends EspressoInstrumentableRootNodeImpl {
    @Child BytecodeNode bytecodeNode;
    private final int bci;
    private final EspressoFrameDescriptor fd;

    public ContinuableMethodWithBytecode(BytecodeNode bytecodeNode, int bci, EspressoFrameDescriptor fd) {
        super(bytecodeNode.getMethodVersion());
        this.bci = bci;
        this.fd = fd;
        this.bytecodeNode = bytecodeNode;
    }

    @Override
    Object execute(VirtualFrame frame) {
        return bytecodeNode.resumeContinuation(frame, bci, fd.top());
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

    public EspressoFrameDescriptor getFD() {
        return fd;
    }

    public HostFrameRecord getFrameRecords(VirtualFrame frame) {
        assert frame.getArguments().length == 1 && frame.getArguments()[0] instanceof HostFrameRecord;
        HostFrameRecord records = (HostFrameRecord) frame.getArguments()[0];
        assert records.methodVersion == getMethodVersion();
        return records;
    }

    @GenerateInline(false)
    public abstract static class ResumeNextContinuationNode extends EspressoNode {
        static final int LIMIT = 3;

        public abstract Object execute(HostFrameRecord records);

        @Specialization(guards = "isLastRecord(records)")
        Object doLast(HostFrameRecord records) {
            assert records == null;
            assert ((EspressoRootNode) getRootNode()).getMethod() == getMeta().continuum.org_graalvm_continuations_Continuation_suspend;
            // Was disabled in the call to Continuation.resume0().
            getLanguage().getThreadLocalState().enableSingleStepping();
            return StaticObject.NULL;
        }

        @Specialization(guards = {"sameCachedRecord(records, cachedMethod, cachedBci)"}, limit = "LIMIT")
        Object doCached(HostFrameRecord records,
                        @Cached("records.methodVersion") Method.MethodVersion cachedMethod,
                        @Cached("records.bci()") int cachedBci,
                        @Cached("create(cachedMethod.getContinuableCallTarget(cachedBci))") DirectCallNode callNode) {
            assert sameCachedRecord(records, cachedMethod, cachedBci);
            return callNode.call(records);
        }

        @Specialization(replaces = "doCached")
        Object doUncached(HostFrameRecord records,
                        @Cached IndirectCallNode callNode) {
            return callNode.call(records.methodVersion.getContinuableCallTarget(records.bci()), records);
        }

        static boolean sameCachedRecord(HostFrameRecord records, Method.MethodVersion method, int cachedBci) {
            return records.methodVersion == method && records.bci() == cachedBci;
        }

        static boolean isLastRecord(HostFrameRecord records) {
            return records == null;
        }
    }
}
