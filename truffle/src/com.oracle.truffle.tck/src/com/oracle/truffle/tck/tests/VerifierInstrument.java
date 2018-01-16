/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tck.tests;

import org.junit.Assert;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.Node;

/**
 * Verify constraints of Truffle languages.
 */
@TruffleInstrument.Registration(name = VerifierInstrument.ID, id = VerifierInstrument.ID, services = VerifierInstrument.class)
public class VerifierInstrument extends TruffleInstrument {

    static final String ID = "TckVerifierInstrument";

    @Override
    protected void onCreate(Env env) {
        env.registerService(this);
        env.getInstrumenter().attachListener(SourceSectionFilter.newBuilder().tagIs(RootTag.class).build(),
                        new ExecutionEventListener() {
                            @Override
                            public void onEnter(EventContext context, VirtualFrame frame) {
                                checkFrameIsEmpty(context, frame.materialize());
                            }

                            @TruffleBoundary
                            private void checkFrameIsEmpty(EventContext context, MaterializedFrame frame) {
                                if (!hasParentRootTag(context.getInstrumentedNode())) {
                                    // Top-most nodes tagged with RootTag should have clean frames.
                                    Object defaultValue = frame.getFrameDescriptor().getDefaultValue();
                                    for (FrameSlot slot : frame.getFrameDescriptor().getSlots()) {
                                        Assert.assertEquals(defaultValue, frame.getValue(slot));
                                    }
                                }
                            }

                            private boolean hasParentRootTag(Node node) {
                                Node parent = node.getParent();
                                if (parent == null) {
                                    return false;
                                }
                                if (TruffleTCKAccessor.nodesAccess().isTaggedWith(parent, RootTag.class)) {
                                    return true;
                                }
                                return hasParentRootTag(parent);
                            }

                            @Override
                            public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                            }

                            @Override
                            public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                            }
                        });
    }

    static final TruffleTCKAccessor ACCESSOR = new TruffleTCKAccessor();

    static final class TruffleTCKAccessor extends Accessor {

        static Accessor.Nodes nodesAccess() {
            return ACCESSOR.nodes();
        }

    }
}
