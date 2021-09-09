/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.runtime.dispatch;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.runtime.StaticObject;

@ExportLibrary(value = InteropLibrary.class, receiverType = StaticObject.class)
public class IterableInterop extends EspressoInterop {
    // region ### Iterable

    @ExportMessage
    public static boolean hasIterator(@SuppressWarnings("unused") StaticObject receiver) {
        return true;
    }

    @ExportMessage
    abstract static class GetIterator {

        static final int LIMIT = 3;

        @SuppressWarnings("unused")
        @Specialization(guards = {"receiver.getKlass() == cachedKlass"}, limit = "LIMIT")
        static Object doCached(StaticObject receiver,
                        @Cached("receiver.getKlass()") Klass cachedKlass,
                        @Cached("doIteratorLookup(receiver)") Method method,
                        @Cached("create(method.getCallTarget())") DirectCallNode callNode) {
            return callNode.call(receiver);
        }

        @Specialization(replaces = "doCached")
        static Object doUncached(StaticObject receiver,
                        @Cached.Exclusive @Cached IndirectCallNode invoke) {
            Method iterator = doIteratorLookup(receiver);
            return invoke.call(iterator.getCallTarget(), receiver);
        }

        static Method doIteratorLookup(StaticObject receiver) {
            return receiver.getKlass().lookupMethod(Symbol.Name.iterator, Symbol.Signature.java_util_Iterator);
        }
    }

    // endregion ### Iterable
}
