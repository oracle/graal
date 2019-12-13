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

import static com.oracle.truffle.espresso.classfile.Constants.ACC_NATIVE;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_VARARGS;

import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.HandleIntrinsicNode;
import com.oracle.truffle.espresso.nodes.MHInvokeBasicNode;
import com.oracle.truffle.espresso.nodes.MHInvokeGenericNode;
import com.oracle.truffle.espresso.nodes.MHLinkToNode;

public final class MethodHandleIntrinsics implements ContextAccess {

    public HandleIntrinsicNode createIntrinsicNode(Method method, Klass accessingKlass, Symbol<Name> methodName, Symbol<Signature> signature) {
        PolySigIntrinsics id = getId(method);
        switch (id) {
            case InvokeBasic:
                return new MHInvokeBasicNode(method);
            case InvokeGeneric:
                return MHInvokeGenericNode.create(accessingKlass, method, methodName, signature, getMeta());
            case LinkToVirtual:
            case LinkToStatic:
            case LinkToSpecial:
            case LinkToInterface:
                return MHLinkToNode.create(method, id);
            default:
                throw EspressoError.shouldNotReachHere("unrecognized intrinsic polymorphic method: " + method);

        }
    }

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

    public static final int FIRST_STATIC_SIG_POLY = PolySigIntrinsics.LinkToVirtual.value;
    public static final int LAST_SIG_POLY = PolySigIntrinsics.LinkToInterface.value;

    private static final PolySigIntrinsics FIRST_MH_SIG_POLY = PolySigIntrinsics.InvokeGeneric;
    private static final PolySigIntrinsics LAST_MH_SIG_POLY = PolySigIntrinsics.LinkToInterface;

    private static boolean isSignaturePolymorphic(PolySigIntrinsics iid) {
        return (iid.value >= FIRST_MH_SIG_POLY.value &&
                        iid.value <= LAST_MH_SIG_POLY.value);
    }

    @SuppressWarnings("unused")
    private static boolean isSignaturePolymorphicIntrinsic(PolySigIntrinsics iid) {
        assert isSignaturePolymorphic(iid);
        // Most sig-poly methods are intrinsics which do not require an
        // appeal to Java for adapter code.
        return (iid != PolySigIntrinsics.InvokeGeneric);
    }

    public static boolean isMethodHandleIntrinsic(Method m, Meta meta) {
        if (m.getDeclaringKlass() == meta.MethodHandle) {
            PolySigIntrinsics id = getId(m);
            /*
             * Contrary to HotSpot implementation, Espresso pushes the MH.invoke_ frames on the
             * stack. Thus, we need to explicitly ignore them, and can't copy the HotSpot
             * implementation here.
             *
             * HotSpot: return isSignaturePolymorphic(id) && isSignaturePolymorphicIntrinsic(id);
             */
            return isSignaturePolymorphic(id);
        }
        return false;
    }

    public static PolySigIntrinsics getId(Method m) {
        assert m.getDeclaringKlass() == m.getMeta().MethodHandle;
        Symbol<Name> name = m.getName();
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
        if (name == Name.invoke || isIntrinsicInvoke(m)) {
            return PolySigIntrinsics.InvokeGeneric;
        }
        return PolySigIntrinsics.None;
    }

    private static boolean isIntrinsicInvoke(Method m) {
        // JVM 2.9 Special Methods:
        // A method is signature polymorphic if and only if all of the following conditions hold :
        // * It is declared in the java.lang.invoke.MethodHandle class.
        // * It has a single formal parameter of type Object[].
        // * It has a return type of Object.
        // * It has the ACC_VARARGS and ACC_NATIVE flags set.
        if (!Type.MethodHandle.equals(m.getDeclaringKlass().getType())) {
            return false;
        }
        Symbol<Signature> polySig = Signature.Object_ObjectArray;
        Method lookup = m.getDeclaringKlass().lookupMethod(m.getName(), polySig);
        if (lookup == null) {
            return false;
        }
        int required = ACC_NATIVE | ACC_VARARGS;
        int flags = m.getModifiers();
        return (flags & required) == required;
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

    public Method findIntrinsic(Method thisMethod, Symbol<Signature> signature, PolySigIntrinsics id) {
        ConcurrentHashMap<Symbol<Signature>, Method> intrinsics = getIntrinsicMap(id, thisMethod);
        Method method = intrinsics.get(signature);
        if (method != null) {
            return method;
        }
        CompilerAsserts.neverPartOfCompilation();
        method = thisMethod.createIntrinsic(signature);
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
