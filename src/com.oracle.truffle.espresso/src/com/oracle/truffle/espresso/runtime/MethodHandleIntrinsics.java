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

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoBaseNode;

public final class MethodHandleIntrinsics implements ContextAccess {
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

    public static boolean isMethodHandleIntrinsic(Method m, Meta meta) {
        if (m.getDeclaringKlass() == meta.MethodHandle) {
            PolySigIntrinsics id = getId(m);
            return (id != PolySigIntrinsics.None);
        }
        return false;
    }

    public static PolySigIntrinsics getId(Method m) {
        Symbol<Name> name = m.getName();
        if (name == Name.invoke || name == Name.invokeExact) {
            return PolySigIntrinsics.InvokeGeneric;
        }
        if (name == Name.linkToStatic) {
            return PolySigIntrinsics.LinkToStatic;
        }
        if (name == Name.linkToVirtual) {
            return PolySigIntrinsics.LinkToVirtual;
        }
        if (name == Name.linkToSpecial) {
            return PolySigIntrinsics.LinkToSpecial;
        }
        if (name == Name.linkToInterface) {
            return PolySigIntrinsics.LinkToInterface;
        }
        if (name == Name.invokeBasic) {
            return PolySigIntrinsics.InvokeBasic;
        }
        return PolySigIntrinsics.None;
    }

    private final EspressoContext context;

    private final ConcurrentHashMap<Symbol<Signature>, Method> invokeIntrinsics;
    private final ConcurrentHashMap<Symbol<Signature>, Method> invokeExactIntrinsics;
    private final ConcurrentHashMap<Symbol<Signature>, Method> invokeBasicIntrinsics;
    private final ConcurrentHashMap<Symbol<Signature>, Method> linkToStaticIntrinsics;
    private final ConcurrentHashMap<Symbol<Signature>, Method> linkToVirtualIntrinsics;
    private final ConcurrentHashMap<Symbol<Signature>, Method> linkToSpecialIntrinsics;
    private final ConcurrentHashMap<Symbol<Signature>, Method> linkToInterfaceIntrinsics;

    MethodHandleIntrinsics(EspressoContext context) {
        this.context = context;
        this.invokeIntrinsics = new ConcurrentHashMap<>();
        this.invokeExactIntrinsics = new ConcurrentHashMap<>();
        this.invokeBasicIntrinsics = new ConcurrentHashMap<>();
        this.linkToStaticIntrinsics = new ConcurrentHashMap<>();
        this.linkToVirtualIntrinsics = new ConcurrentHashMap<>();
        this.linkToSpecialIntrinsics = new ConcurrentHashMap<>();
        this.linkToInterfaceIntrinsics = new ConcurrentHashMap<>();
    }

    @Override
    public EspressoContext getContext() {
        return context;
    }

    public Method findIntrinsic(Method thisMethod, Symbol<Signature> signature, Function<Method, EspressoBaseNode> baseNodeFactory, PolySigIntrinsics id) {
        ConcurrentHashMap<Symbol<Signature>, Method> intrinsics = getIntrinsicMap(id, thisMethod);
        Method method = intrinsics.get(signature);
        if (method != null) {
            return method;
        }
        CompilerAsserts.neverPartOfCompilation();
        method = thisMethod.createIntrinsic(signature, baseNodeFactory);
        Method previous = intrinsics.putIfAbsent(signature, method);
        if (previous != null) {
            return previous;
        }
        return method;
    }

    private ConcurrentHashMap<Symbol<Signature>, Method> getIntrinsicMap(PolySigIntrinsics id, Method thisMethod) {
        switch (id) {
            case InvokeBasic:
                return invokeBasicIntrinsics;
            case InvokeGeneric:
                return (thisMethod.getName() == Symbol.Name.invoke ? invokeIntrinsics : invokeExactIntrinsics);
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
