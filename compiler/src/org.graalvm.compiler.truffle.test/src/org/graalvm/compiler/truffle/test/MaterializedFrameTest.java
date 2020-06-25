/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import static org.hamcrest.CoreMatchers.either;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.ValueProfile;

public class MaterializedFrameTest extends PartialEvaluationTest {
    private static RootNode createRootNode() {
        FrameDescriptor fd = new FrameDescriptor();
        FrameSlot slot = fd.addFrameSlot("test");
        return new RootNode(null, fd) {
            private final ValueProfile frameClassProfile = ValueProfile.createClassProfile();

            @Override
            public Object execute(VirtualFrame frame) {
                MaterializedFrame mframe = frameClassProfile.profile(GraalDirectives.opaque(frame.materialize()));
                if (mframe.getFrameDescriptor().getFrameSlotKind(slot) != FrameSlotKind.Int) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    mframe.getFrameDescriptor().setFrameSlotKind(slot, FrameSlotKind.Int);
                }
                mframe.setInt(slot, 42);
                if (mframe.isInt(slot)) {
                    try {
                        return mframe.getInt(slot);
                    } catch (FrameSlotTypeException e) {
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new AssertionError();
            }
        };
    }

    @Test
    public void getFrameSlotKind() {
        RootNode rootNode = createRootNode();
        RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(rootNode);
        StructuredGraph graph = partialEval((OptimizedCallTarget) callTarget, new Object[]{}, CompilationIdentifier.INVALID_COMPILATION_ID);

        NodeIterable<MethodCallTargetNode> calls = graph.getNodes().filter(MethodCallTargetNode.class);
        assertTrue("Unexpected call(s): " + calls.snapshot(), calls.isEmpty());
        for (IsNullNode isNull : graph.getNodes().filter(IsNullNode.class)) {
            assertThat("Unexpected IsNull: " + isNull + "(" + isNull.getValue() + ")", isNull.getValue(), not(instanceOf(LoadFieldNode.class)));
        }
        for (LoadFieldNode loadField : graph.getNodes().filter(LoadFieldNode.class)) {
            assertThat("Unexpected LoadField: " + loadField, loadField.field().getName(), either(equalTo("tags")).or(equalTo("primitiveLocals")));
        }
    }
}
