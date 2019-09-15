/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.graalvm.compiler.truffle.runtime.FrameWithoutBoxing;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.test.nodes.AbstractTestNode;
import org.graalvm.compiler.truffle.test.nodes.RootTestNode;
import org.junit.Assert;
import org.junit.Test;

@AddExports("jdk.internal.vm.compiler/org.graalvm.compiler.truffle.runtime")
public class NewFrameNodeTest extends PartialEvaluationTest {

    @Test
    public void newFrameNodeFrameSizeIsCorrect() {
        final FrameDescriptor fd = new FrameDescriptor();
        /*
         * Make sure there are FrameSlots with higher index then is the default size (10) of inner
         * array of ArrayList storing FrameSlots in FrameDescriptor
         */
        for (int i = 0; i < 15; i++) {
            fd.addFrameSlot("b", FrameSlotKind.Boolean);
            fd.removeFrameSlot("b");
        }
        fd.addFrameSlot("b", FrameSlotKind.Boolean);
        fd.addFrameSlot("i", FrameSlotKind.Int);
        fd.removeFrameSlot("b");
        final Assumption version = fd.getVersion();

        Assert.assertEquals(17, fd.getSize());

        final RootTestNode rootNode = new RootTestNode(fd, "newFrameNodeFrameSizeIsCorrect", new AbstractTestNode() {
            @Override
            public int execute(VirtualFrame frame) {
                final MaterializedFrame materializedFrame = frame.materialize();
                if (materializedFrame instanceof FrameWithoutBoxing) {
                    FrameWithoutBoxing frameWithoutBoxing = (FrameWithoutBoxing) materializedFrame;
                    try {
                        final long[] primitiveLocals = (long[]) getFrameField(frameWithoutBoxing, "primitiveLocals");
                        final Object[] locals = (Object[]) getFrameField(frameWithoutBoxing, "locals");
                        final byte[] tags = (byte[]) getFrameField(frameWithoutBoxing, "tags");
                        return 10000 * tags.length + 100 * primitiveLocals.length + locals.length;
                    } catch (IllegalAccessException e) {
                        return -2;
                    } catch (NoSuchFieldException e) {
                        return -3;
                    }
                }
                return -1;
            }

            @CompilerDirectives.TruffleBoundary
            private Object getFrameField(FrameWithoutBoxing materializedFrame, String fieldName) throws IllegalAccessException, NoSuchFieldException {
                final Field field = FrameWithoutBoxing.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(materializedFrame);
            }
        });

        final OptimizedCallTarget callTarget = compileHelper("frameDescriptorKindIsCorrect", rootNode, new Object[]{});

        Assert.assertTrue(callTarget.isValid());
        Assert.assertEquals(171717, callTarget.call());
        Assert.assertEquals(version, fd.getVersion());
        Assert.assertTrue(version.isValid());
    }
}
