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
 * The {@code NewTypeArray} class definition.
 *
 * @author Ben L. Titzer
 */
public final class NewTypeArray extends NewArray {

    final RiType elementType;

    public NewTypeArray(Value length, RiType elementType, FrameState stateBefore) {
        super(length, stateBefore);
        this.elementType = elementType;
    }

    public CiKind elementKind() {
        return elementType.kind();
    }

    @Override
    public RiType declaredType() {
        return elementType.arrayOf();
    }

    @Override
    public RiType exactType() {
        return elementType.arrayOf();
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitNewTypeArray(this);
    }

    @Override
    public void print(LogStream out) {
        out.print("new ").print(elementKind().name()).print(" array [").print(length()).print(']');
    }
}
