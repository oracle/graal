/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.nodes.interop.InteropUnwrapNode;
import com.oracle.truffle.espresso.nodes.interop.InvokeEspressoNode;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

@ExportLibrary(InteropLibrary.class)
public final class EspressoFunction implements TruffleObject {
    private final Method m;
    private final StaticObject receiver;

    private EspressoFunction(Method m, StaticObject receiver) {
        this.m = m;
        this.receiver = receiver;
    }

    public static EspressoFunction createStaticInvocable(Method m) {
        return new EspressoFunction(m, null);
    }

    public static EspressoFunction createInstanceInvocable(Method m, StaticObject receiver) {
        assert receiver != null;
        return new EspressoFunction(m, receiver);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean isExecutable() {
        return true;
    }

    @ExportMessage
    public Object execute(Object[] args,
                    @Cached InvokeEspressoNode invoke,
                    @Cached InteropUnwrapNode unwrapNode) throws ArityException, UnsupportedTypeException {
        return invoke.execute(m, receiver, args, unwrapNode);
    }
}
