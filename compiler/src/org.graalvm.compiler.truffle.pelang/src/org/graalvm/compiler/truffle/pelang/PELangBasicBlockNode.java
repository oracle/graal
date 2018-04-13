/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.pelang;

import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class PELangBasicBlockNode extends PELangExpressionNode {

    public static final int NO_SUCCESSOR = -1;

    @Child protected PELangExpressionNode bodyNode;

    public PELangBasicBlockNode(PELangExpressionNode bodyNode) {
        this.bodyNode = bodyNode;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return executeBlock(frame);
    }

    public abstract Execution executeBlock(VirtualFrame frame);

    public static class Execution {
        private final Object result;
        private final int successor;

        public Execution(Object result, int successor) {
            this.result = result;
            this.successor = successor;
        }

        public Object getResult() {
            return result;
        }

        public int getSuccessor() {
            return successor;
        }

    }

}
