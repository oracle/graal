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
import com.sun.cri.ci.*;

/**
 * The {@code UnsafeGetObject} instruction represents an unsafe read.
 *
 * @author Ben L. Titzer
 */
public final class UnsafeGetObject extends UnsafeObjectOp {

    /**
     * Constructs a new UnsafeGetObject operation.
     * @param opKind the kind of the operation
     * @param object the instruction generating the object
     * @param offset the instruction generating the offset
     * @param isVolatile {@code true} if this operation is volatile
     */
    public UnsafeGetObject(CiKind opKind, Value object, Value offset, boolean isVolatile) {
        super(opKind, object, offset, false, isVolatile);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitUnsafeGetObject(this);
    }

    @Override
    public void print(LogStream out) {
        out.print("UnsafeGetObject.(").print(object()).print(", ").print(offset()).print(')');
    }
}
