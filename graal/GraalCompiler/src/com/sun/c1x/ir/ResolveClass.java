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
 * An instruction that represents the runtime resolution of a Java class object. For example, an
 * ldc of a class constant that is unresolved.
 *
 * @author Ben L. Titzer
 * @author Thomas Wuerthinger
 */
public final class ResolveClass extends StateSplit {

    public final RiType type;
    public final RiType.Representation portion;

    public ResolveClass(RiType type, RiType.Representation r, FrameState stateBefore) {
        super(type.getRepresentationKind(r), stateBefore);
        this.portion = r;
        this.type = type;
        setFlag(Flag.NonNull);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitResolveClass(this);
    }

    @Override
    public boolean canTrap() {
        return true;
    }

    @Override
    public int valueNumber() {
        return 0x50000000 | type.hashCode();
    }

    @Override
    public boolean valueEqual(Instruction x) {
        if (x instanceof ResolveClass) {
            ResolveClass r = (ResolveClass) x;
            return r.portion == portion && r.type.equals(type);
        }
        return false;
    }

    @Override
    public String toString() {
        return super.toString() + "(type: " + type + ", portion: " + portion + ")";
    }

    @Override
    public void print(LogStream out) {
        out.print("resolve[").print(CiUtil.toJavaName(type)).print("-" + portion + "]");
    }

}
