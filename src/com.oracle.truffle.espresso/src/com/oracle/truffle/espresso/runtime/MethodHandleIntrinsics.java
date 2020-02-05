/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.espresso.nodes.methodhandle.MHInvokeBasicNodeGen;
import com.oracle.truffle.espresso.nodes.methodhandle.MHInvokeGenericNode;
import com.oracle.truffle.espresso.nodes.methodhandle.MHLinkToNodeGen;
import com.oracle.truffle.espresso.nodes.methodhandle.MethodHandleIntrinsicNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.InvokeHandleNode;

/**
 * This class manages MethodHandle polymorphic methods dispatch. It creates and records dummy
 * espresso Method instances every time a new signature is seen. This is the only place that keeps
 * track of these, as the dummy methods are not present in klasses, since they are merely internal
 * constructs.
 * 
 * Since the whole method handle machinery is a pretty opaque black box, here is a quick summary of
 * what's happening under espresso's hood.
 * 
 * <li>Each time a {@link java.lang.invoke.MethodHandle} PolymorphicSignature method is resolved
 * with a signature that was never seen before by the context, espresso creates a dummy placeholder
 * method and keeps track of it.
 * <li>When a call site needs to link against a polymorphic signatures, it obtains the dummy method.
 * It then calls {@link Method#spawnIntrinsicNode(Klass, Symbol, Symbol)} which gives a truffle node
 * implementing the behavior of the MethodHandle intrinsics (ie: extracting the call target from the
 * arguments, appending an appendix to the erguments, etc...)
 * <li>This node is then fed to a {@link InvokeHandleNode} whose role is exactly like the other
 * invoke nodes: extracting arguments from the stack and passing it to its child.
 */
public final class MethodHandleIntrinsics implements ContextAccess {

    public MethodHandleIntrinsicNode createIntrinsicNode(Method method, Klass accessingKlass, Symbol<Name> methodName, Symbol<Signature> signature) {
        PolySigIntrinsics id = getId(method);
        switch (id) {
            case InvokeBasic:
                return MHInvokeBasicNodeGen.create(method);
            case InvokeGeneric:
                return MHInvokeGenericNode.create(accessingKlass, method, methodName, signature, getMeta());
            case LinkToVirtual:
            case LinkToStatic:
            case LinkToSpecial:
            case LinkToInterface:
                return MHLinkToNodeGen.create(method, id);
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
        if (m.getDeclaringKlass() == meta.java_lang_invoke_MethodHandle) {
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
        assert m.getDeclaringKlass() == m.getMeta().java_lang_invoke_MethodHandle;
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
        if (!Type.java_lang_invoke_MethodHandle.equals(m.getDeclaringKlass().getType())) {
            return false;
        }
        Symbol<Signature> polySig = Signature.Object_Object_array;
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
