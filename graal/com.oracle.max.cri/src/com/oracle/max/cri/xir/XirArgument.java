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
package com.oracle.max.cri.xir;

import com.oracle.max.cri.ci.*;

/**
 * Represents an argument to an {@link XirSnippet}.
 * Currently, this is a <i>union </i> type; it is either a {@link RiConstant} or an {@code Object}.
 */
public final class XirArgument {

    public final RiConstant constant;
    public final Object object;

    private XirArgument(RiConstant value) {
        this.constant = value;
        this.object = null;
    }

    private XirArgument(Object o) {
        this.constant = null;
        this.object = o;
    }

    public static XirArgument forInternalObject(Object o) {
        return new XirArgument(o);
    }

    public static XirArgument forInt(int x) {
        return new XirArgument(RiConstant.forInt(x));
    }

    public static XirArgument forLong(long x) {
        return new XirArgument(RiConstant.forLong(x));
    }

    public static XirArgument forObject(Object o) {
        return new XirArgument(RiConstant.forObject(o));
    }

    @Override
    public String toString() {
        if (constant != null) {
            return constant.toString();
        } else {
            return "" + object;
        }
    }
}
