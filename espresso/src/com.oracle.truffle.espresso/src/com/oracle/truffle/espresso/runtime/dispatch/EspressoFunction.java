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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.utilities.TriState;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.nodes.interop.InvokeEspressoNode;

@ExportLibrary(InteropLibrary.class)
public class EspressoFunction implements TruffleObject {
    private final Method method;
    private final Object receiver;

    EspressoFunction(Method method, Object receiver) {
        this.method = method;
        this.receiver = receiver;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean isExecutable() {
        return true;
    }

    @ExportMessage
    Object execute(Object[] args,
                    @Cached InvokeEspressoNode invoke) throws UnsupportedTypeException, ArityException {
        return invoke.execute(method, receiver, args);
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean hasLanguage() {
        return true;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    Class<? extends TruffleLanguage<?>> getLanguage() {
        return EspressoLanguage.class;
    }

    @ExportMessage
    @SuppressWarnings("unused")
    static final class IsIdenticalOrUndefined {
        @Specialization
        static TriState doHostObject(EspressoFunction receiver, EspressoFunction other) {
            return (receiver.method == other.method && receiver.receiver == other.receiver) ? TriState.TRUE : TriState.FALSE;
        }

        @Fallback
        static TriState doOther(EspressoFunction receiver, Object other) {
            return TriState.UNDEFINED;
        }
    }

    @ExportMessage
    static int identityHashCode(EspressoFunction receiver) {
        return System.identityHashCode(receiver.method);
    }

    @ExportMessage
    @TruffleBoundary
    @SuppressWarnings("unused")
    String toDisplayString(boolean allowSideEffects) {
        return method.toString();
    }
}
