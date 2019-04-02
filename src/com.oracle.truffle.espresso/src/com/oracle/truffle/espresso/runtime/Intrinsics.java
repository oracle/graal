/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.nodes.EspressoBaseNode;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class Intrinsics implements ContextAccess {
    public enum PolySigIntrinsics {
        None(0),
        InvokeGeneric(1),
        InvokeBasic(2),
        LinkToVirtual(3),
        LinkToStatic(4),
        LinkToSpecial(5),
        LinkToInterface(6);

        public final int value;

        PolySigIntrinsics(int value) {
            this.value = value;
        }
    }

    public static int firstStaticSigPoly = PolySigIntrinsics.LinkToVirtual.value;
    public static int lastSigPoly = PolySigIntrinsics.LinkToInterface.value;

    private EspressoContext context;

    private ConcurrentHashMap<Symbol<Symbol.Signature>, Method> invokeGenericIntrinsics;
    private ConcurrentHashMap<Symbol<Symbol.Signature>, Method> invokeBasicIntrinsics;
    private ConcurrentHashMap<Symbol<Symbol.Signature>, Method> linkToStaticIntrinsics;
    private ConcurrentHashMap<Symbol<Symbol.Signature>, Method> linkToVirtualIntrinsics;
    private ConcurrentHashMap<Symbol<Symbol.Signature>, Method> linkToSpecialIntrinsics;
    private ConcurrentHashMap<Symbol<Symbol.Signature>, Method> linkToInterfaceIntrinsics;

    Intrinsics(EspressoContext context) {
        this.context = context;
        invokeGenericIntrinsics = new ConcurrentHashMap<>();
        invokeBasicIntrinsics = new ConcurrentHashMap<>();
        linkToStaticIntrinsics = new ConcurrentHashMap<>();
        linkToVirtualIntrinsics = new ConcurrentHashMap<>();
        linkToSpecialIntrinsics = new ConcurrentHashMap<>();
        linkToInterfaceIntrinsics = new ConcurrentHashMap<>();
    }

    @Override
    public EspressoContext getContext() {
        return context;
    }

    public Method findIntrinsic(Method thisMethod, Symbol<Symbol.Signature> signature, Function<Method, EspressoBaseNode> baseNodeFactory, PolySigIntrinsics id) {
        ConcurrentHashMap<Symbol<Symbol.Signature>, Method> intrinsics = getIntrinsicMap(id);
        Method method = intrinsics.get(signature);
        if (method != null) {
            return method;
        }
        CompilerAsserts.neverPartOfCompilation();
        method = Method.createIntrinsic(thisMethod, signature, baseNodeFactory);
        intrinsics.put(signature, method);
        return method;
    }

    private ConcurrentHashMap<Symbol<Symbol.Signature>, Method> getIntrinsicMap(PolySigIntrinsics id) {
        switch (id) {
            case InvokeBasic:
                return invokeBasicIntrinsics;
            case InvokeGeneric:
                return invokeGenericIntrinsics;
            case LinkToVirtual:
                return linkToVirtualIntrinsics;
            case LinkToStatic:
                return linkToStaticIntrinsics;
            case LinkToSpecial:
                return linkToSpecialIntrinsics;
            case LinkToInterface:
                return linkToInterfaceIntrinsics;
            default:
                throw EspressoError.shouldNotReachHere("unrecognized intrinsic polymorphic method: " + id);
        }
    }
}
