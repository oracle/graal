/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.ir;

import com.sun.c1x.debug.*;
import com.sun.c1x.value.*;
import com.sun.cri.ci.*;

/**
 * Performs a bounds check on the index variable given a length. Deoptimizes on index < 0 || index >= length.
 *
 * @author Thomas Wuerthinger
 *
 */
public final class BoundsCheck extends Guard {

    Value index;
    Value length;

    public BoundsCheck(Value index, Value length, FrameState stateBefore, Condition condition) {
        super(condition, stateBefore);
        this.index = index;
        this.length = length;
        assert index.kind == CiKind.Int;
        assert length.kind == CiKind.Int;
    }

    public Value index() {
        return index;
    }

    public Value length() {
        return length;
    }

    @Override
    public void inputValuesDo(ValueClosure closure) {
        index = closure.apply(index);
        length = closure.apply(length);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitBoundsCheck(this);
    }

    @Override
    public void print(LogStream out) {
        out.print("boundsCheck ").print(index).print(" ").print(length);
    }
}
