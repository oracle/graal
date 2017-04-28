/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test.nodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.utilities.CyclicAssumption;

@NodeInfo
public class ConstantWithCyclicAssumptionTestNode extends AbstractTestNode {
    @CompilerDirectives.CompilationFinal private int value;
    private final CyclicAssumption assumption;

    public ConstantWithCyclicAssumptionTestNode(CyclicAssumption assumption, int value) {
        this.value = value;
        this.assumption = assumption;
    }

    @Override
    public int execute(VirtualFrame frame) {
        try {
            assumption.getAssumption().check();
            return value;
        } catch (InvalidAssumptionException e) {
            return invalidate();
        }
    }

    public int invalidate() {
        int v = ++value;
        assumption.invalidate();
        return v;
    }
}
