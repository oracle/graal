/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.cri.ri.*;

/**
 * Copies a sequence of elements from a source into a destination array.
 *
 * @author Thomas Wuerthinger
 *
 */
public class ArrayCopy extends StateSplit {

    private Value src;
    private Value srcPos;
    private Value dest;
    private Value destPos;
    private Value length;
    public final RiMethod arrayCopyMethod;

    public ArrayCopy(Value src, Value srcPos, Value dest, Value destPos, Value length, RiMethod arrayCopyMethod, FrameState stateBefore) {
        super(CiKind.Void, stateBefore);
        this.arrayCopyMethod = arrayCopyMethod;
        this.src = src;
        this.srcPos = srcPos;
        this.dest = dest;
        this.destPos = destPos;
        this.length = length;
    }

    public Value src() {
        return src;
    }

    public Value srcPos() {
        return srcPos;
    }

    public Value dest() {
        return dest;
    }

    public Value destPos() {
        return destPos;
    }

    public Value length() {
        return length;
    }

    @Override
    public void inputValuesDo(ValueClosure closure) {
        src = closure.apply(src);
        srcPos = closure.apply(srcPos);
        dest = closure.apply(dest);
        destPos = closure.apply(destPos);
        length = closure.apply(length);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitArrayCopy(this);
    }

    @Override
    public void print(LogStream out) {
        out.print("arrayCopy ").print(src).print(" ").print(srcPos).print(" ");
        out.print(dest).print(" ").print(destPos).print(" ").print(length);
    }
}
