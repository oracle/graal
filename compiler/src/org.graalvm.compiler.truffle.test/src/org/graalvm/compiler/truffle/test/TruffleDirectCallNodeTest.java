/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;

public class TruffleDirectCallNodeTest {

    @Test
    public void testCanBeClonedWithoutParent() {
        final RootNode rootNode = new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return 42;
            }

            @Override
            public boolean isCloningAllowed() {
                return true;
            }
        };
        final CallTarget callTarget = rootNode.getCallTarget();
        final DirectCallNode callNode = Truffle.getRuntime().createDirectCallNode(callTarget);

        assertTrue(callNode.isCallTargetCloningAllowed());
        assertTrue(callNode.cloneCallTarget());
        assertSame(callNode.getCurrentCallTarget(), callNode.getClonedCallTarget());
        assertNotSame(callTarget, callNode.getClonedCallTarget());
    }
}
