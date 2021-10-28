/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.nodes.quick.interop;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

public final class Utils {
    public static boolean isBufferLike(InteropLibrary interop, Object foreignObject) {
        assert !(foreignObject instanceof StaticObject);
        return interop.hasBufferElements(foreignObject);
    }

    public static boolean isArrayLike(InteropLibrary interop, Object foreignObject) {
        assert !(foreignObject instanceof StaticObject);
        return interop.hasArrayElements(foreignObject);
    }

    public static boolean isByteArray(EspressoContext context, StaticObject array) {
        return array.getKlass() == context.getMeta()._byte_array;
    }

    public static boolean isBufferLikeByteArray(EspressoContext context, InteropLibrary interop, StaticObject array) {
        assert !StaticObject.isNull(array);
        assert array.isForeignObject();
        return isByteArray(context, array) && isBufferLike(interop, array.rawForeignObject());
    }
}
