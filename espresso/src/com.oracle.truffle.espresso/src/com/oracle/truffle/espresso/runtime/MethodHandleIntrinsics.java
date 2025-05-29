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

import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Names;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Types;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.methodhandle.MHInvokeBasicNodeGen;
import com.oracle.truffle.espresso.nodes.methodhandle.MHInvokeGenericNode;
import com.oracle.truffle.espresso.nodes.methodhandle.MHInvokeGenericNode.MethodHandleInvoker;
import com.oracle.truffle.espresso.nodes.methodhandle.MHLinkToNativeNode;
import com.oracle.truffle.espresso.nodes.methodhandle.MHLinkToNodeGen;
import com.oracle.truffle.espresso.nodes.methodhandle.MethodHandleIntrinsicNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.InvokeHandleNode;

/**
 * This class manages MethodHandle polymorphic methods dispatch. It creates and records dummy
 * espresso Method instances every time a new signature is seen. This is the only place that keeps
 * track of these, as the dummy methods are not present in klasses, since they are merely internal
 * constructs.
 * <p>
 * Since the whole method handle machinery is a pretty opaque black box, here is a quick summary of
 * what's happening under espresso's hood.
 *
 * <li>Each time a {@link java.lang.invoke.MethodHandle} PolymorphicSignature method is resolved
 * with a signature that was never seen before by the context, espresso creates a dummy placeholder
 * method and keeps track of it.
 * <li>When a call site needs to link against a polymorphic signatures, it obtains the dummy method.
 * It then calls {@link Method#spawnIntrinsicNode(MethodHandleInvoker)} which gives a truffle node
 * implementing the behavior of the MethodHandle intrinsics (ie: extracting the call target from the
 * arguments, appending an appendix to the arguments, etc...)
 * <li>This node is then fed to a {@link InvokeHandleNode} whose role is exactly like the other
 * invoke nodes: extracting arguments from the stack and passing it to its child.
 */
public final class MethodHandleIntrinsics {

    private final ConcurrentHashMap<MethodKey, Method> intrinsics;

    MethodHandleIntrinsics() {
        this.intrinsics = new ConcurrentHashMap<>();
    }

    public static MethodHandleIntrinsicNode createIntrinsicNode(Meta meta, Method method, MethodHandleInvoker invoker) {
        PolySigIntrinsics id = getId(method);
        assert (invoker != null) == (id == PolySigIntrinsics.InvokeGeneric);
        return switch (id) {
            case InvokeBasic -> MHInvokeBasicNodeGen.create(method);
            case InvokeGeneric -> MHInvokeGenericNode.create(method, invoker);
            case LinkToVirtual, LinkToStatic, LinkToSpecial, LinkToInterface -> MHLinkToNodeGen.create(method, id);
            case LinkToNative -> MHLinkToNativeNode.create(method, meta);
            case None -> throw EspressoError.shouldNotReachHere();
        };
    }

    public Method findIntrinsic(Method thisMethod, Symbol<Signature> signature) {
        return findIntrinsic(thisMethod, new MethodKey(thisMethod, signature));
    }

    public static boolean isMethodHandleIntrinsic(Method m) {
        PolySigIntrinsics id = getId(m);
        /*
         * Contrary to HotSpot implementation, Espresso pushes the MH.invoke_ frames on the stack.
         * Thus, we need to explicitly ignore them, and can't copy the HotSpot implementation here.
         *
         * HotSpot: return isSignaturePolymorphic(id) && isSignaturePolymorphicIntrinsic(id);
         */
        return id.isSignaturePolymorphic();
    }

    public static PolySigIntrinsics getId(Method m) {
        return getId(m.getName(), m.getDeclaringKlass());
    }

    public static PolySigIntrinsics getId(Symbol<Name> name, Klass declaringKlass) {
        if (!Meta.isSignaturePolymorphicHolderType(declaringKlass.getType())) {
            return PolySigIntrinsics.None;
        }
        if (Types.java_lang_invoke_MethodHandle.equals(declaringKlass.getType())) {
            if (name == Names.linkToStatic) {
                return PolySigIntrinsics.LinkToStatic;
            }
            if (name == Names.linkToVirtual) {
                return PolySigIntrinsics.LinkToVirtual;
            }
            if (name == Names.linkToSpecial) {
                return PolySigIntrinsics.LinkToSpecial;
            }
            if (name == Names.linkToInterface) {
                return PolySigIntrinsics.LinkToInterface;
            }
            if (name == Names.linkToNative) {
                return PolySigIntrinsics.LinkToNative;
            }
            if (name == Names.invokeBasic) {
                return PolySigIntrinsics.InvokeBasic;
            }
        }
        if (declaringKlass.lookupPolysignatureDeclaredMethod(name, Klass.LookupMode.INSTANCE_ONLY) != null) {
            return PolySigIntrinsics.InvokeGeneric;
        }
        return PolySigIntrinsics.None;
    }

    private Method findIntrinsic(Method m, MethodKey methodRef) {
        Method method = getIntrinsic(methodRef);
        if (method != null) {
            return method;
        }
        CompilerAsserts.neverPartOfCompilation();
        method = m.createIntrinsic(methodRef.getSignature());
        Method previous = putIntrinsic(methodRef, method);
        if (previous != null) {
            return previous;
        }
        return method;
    }

    private Method getIntrinsic(MethodKey methodRef) {
        return intrinsics.get(methodRef);
    }

    private Method putIntrinsic(MethodKey methodRef, Method m) {
        return intrinsics.putIfAbsent(methodRef, m);
    }

    public enum PolySigIntrinsics {
        None(false, false),
        InvokeGeneric(false, false),
        InvokeBasic(false, true),
        LinkToVirtual(true, true),
        LinkToStatic(true, true),
        LinkToSpecial(true, true),
        LinkToInterface(true, true),
        LinkToNative(true, true);

        public final boolean isSignaturePolymorphicIntrinsic;
        public final boolean isStatic;

        PolySigIntrinsics(boolean isStatic, boolean isSignaturePolymorphic) {
            this.isStatic = isStatic;
            this.isSignaturePolymorphicIntrinsic = isSignaturePolymorphic;
        }

        /**
         * Indicates that the given ID represent a static polymorphic signature method. As of Java
         * 11, there exists only 4 such methods.
         *
         * @see "java.lang.invoke.MethodHandle.linkToInterface(Object...)"
         * @see "java.lang.invoke.MethodHandle.linkToSpecial(Object...)"
         * @see "java.lang.invoke.MethodHandle.linkToStatic(Object...)"
         * @see "java.lang.invoke.MethodHandle.linkToVirtual(Object...)"
         */
        public final boolean isStaticPolymorphicSignature() {
            return isStatic;
        }

        /**
         * Indicates whether or not a given PolymorphicSignature ID has its behavior entirely
         * implemented in the VM.
         * <p>
         * For example, invokeBasic's behavior is implemented in the VM (see
         * {@link com.oracle.truffle.espresso.nodes.methodhandle.MHInvokeBasicNode}). In particular,
         * target extraction and payload invocation is entirely done in Espresso.
         * <p>
         * On the contrary, methods with IDs represented by {@link PolySigIntrinsics#InvokeGeneric}
         * are managed by the VM, their behavior is implemented through Java code, which is then
         * simply called by the VM.
         */
        public final boolean isSignaturePolymorphicIntrinsic() {
            return isSignaturePolymorphicIntrinsic;
        }

        private boolean isSignaturePolymorphic() {
            return this != None;
        }
    }

}
