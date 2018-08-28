/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.truffle.api.test.host;

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

@MessageResolution(receiverType = UnboxableToInt.class)
final class UnboxableToInt implements TruffleObject {

    private final int value;

    UnboxableToInt(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return UnboxableToIntForeign.ACCESS;
    }

    static boolean isInstance(TruffleObject obj) {
        return obj instanceof UnboxableToInt;
    }

    @Resolve(message = "UNBOX")
    abstract static class UnboxINode extends Node {
        Object access(UnboxableToInt obj) {
            return obj.getValue();
        }
    }

    @Resolve(message = "IS_BOXED")
    abstract static class IsBoxedINode extends Node {
        @SuppressWarnings("unused")
        Object access(UnboxableToInt obj) {
            return true;
        }
    }
}
