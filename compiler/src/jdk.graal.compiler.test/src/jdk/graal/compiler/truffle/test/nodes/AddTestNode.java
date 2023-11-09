/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test.nodes;

import jdk.graal.compiler.api.directives.GraalDirectives;

import com.oracle.truffle.api.frame.VirtualFrame;

public class AddTestNode extends AbstractTestNode {

    @Child private AbstractTestNode left;
    @Child private AbstractTestNode right;
    private final boolean cfgAnchorBeforeReturn;

    public AddTestNode(AbstractTestNode left, AbstractTestNode right, boolean cfgAnchorBeforeReturn) {
        this.left = left;
        this.right = right;
        this.cfgAnchorBeforeReturn = cfgAnchorBeforeReturn;
    }

    public AddTestNode(AbstractTestNode left, AbstractTestNode right) {
        this(left, right, false);
    }

    @Override
    public int execute(VirtualFrame frame) {
        int res = left.execute(frame) + right.execute(frame);
        if (cfgAnchorBeforeReturn) {
            GraalDirectives.controlFlowAnchor();
        }
        return res;
    }
}
