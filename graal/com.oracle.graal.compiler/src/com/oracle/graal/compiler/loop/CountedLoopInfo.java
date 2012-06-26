/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.loop;

import com.oracle.graal.compiler.loop.InductionVariable.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;

public class CountedLoopInfo {
    private final LoopEx loop;
    private InductionVariable iv;
    private ValueNode end;
    private boolean oneOff;

    CountedLoopInfo(LoopEx loop, InductionVariable iv, ValueNode end, boolean oneOff) {
        this.loop = loop;
        this.iv = iv;
        this.end = end;
        this.oneOff = oneOff;
    }

    public ValueNode maxTripCountNode() {
        //TODO (gd) stuarte and respect oneOff
        return IntegerArithmeticNode.div(IntegerArithmeticNode.sub(end, iv.initNode()), iv.strideNode());
    }

    public boolean isConstantMaxTripCount() {
        return end instanceof ConstantNode && iv.isConstantInit() && iv.isConstantStride();
    }

    public long constantMaxTripCount() {
        long off = oneOff ? iv.direction() == Direction.Up ? 1 : -1 : 0;
        long max = (((ConstantNode) end).asConstant().asLong() + off - iv.constantInit()) / iv.constantStride();
        return Math.max(0, max);
    }

    public boolean isExactTripCount() {
        return loop.loopBegin().loopExits().count() == 1;
    }

    public ValueNode exactTripCountNode() {
        assert isExactTripCount();
        return maxTripCountNode();
    }

    public boolean isConstantExactTripCount() {
        assert isExactTripCount();
        return isConstantMaxTripCount();
    }

    public long constantExactTripCount() {
        assert isExactTripCount();
        return constantMaxTripCount();
    }

    @Override
    public String toString() {
        return "iv=" + iv + " until " + end + (oneOff ? iv.direction() == Direction.Up ? "+1" : "-1" : "");
    }
}
