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
package com.oracle.max.criutils;

import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * A implementation of {@link RiMethod} for an unresolved method.
 */
public class BaseUnresolvedMethod implements RiMethod {

    public final String name;
    public final RiType holder;
    public final RiSignature signature;

    public BaseUnresolvedMethod(RiType holder, String name, RiSignature signature) {
        this.name = name;
        this.holder = holder;
        this.signature = signature;
    }

    public String name() {
        return name;
    }

    public RiType holder() {
        return holder;
    }

    public RiSignature signature() {
        return signature;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object o) {
        return o == this;
    }

    public StackTraceElement toStackTraceElement(int bci) {
        return new StackTraceElement(CiUtil.toJavaName(holder), name, null, -1);
    }

    @Override
    public String toString() {
        return CiUtil.format("%H.%n(%p) [unresolved]", this);
    }

    @Override
    public boolean canBePermanentlyLinked() {
        return false;
    }
}
