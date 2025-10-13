/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.shared.meta;

import com.oracle.truffle.espresso.classfile.ParserKlass;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.ParserSymbols.ParserNames;
import com.oracle.truffle.espresso.classfile.descriptors.ParserSymbols.ParserTypes;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;

/**
 * Classification of signature polymorphic methods found in the JDK based on how their behaviour is
 * to be implemented.
 */
public enum SignaturePolymorphicIntrinsic {
    InvokeGeneric(false, false),
    InvokeBasic(false, true),
    LinkToVirtual(true, true),
    LinkToStatic(true, true),
    LinkToSpecial(true, true),
    LinkToInterface(true, true),
    LinkToNative(true, true);

    private final boolean isStatic;
    private final boolean isSignaturePolymorphicIntrinsic;

    SignaturePolymorphicIntrinsic(boolean isStatic, boolean isSignaturePolymorphic) {
        this.isStatic = isStatic;
        this.isSignaturePolymorphicIntrinsic = isSignaturePolymorphic;
    }

    /**
     * Indicates that the given ID represent a static polymorphic signature method.
     */
    public final boolean isStaticSignaturePolymorphic() {
        return isStatic;
    }

    /**
     * Indicates whether a given PolymorphicSignature ID has its behavior entirely implemented in
     * the VM.
     * <p>
     * For example, invokeBasic's behavior is implemented in the VM. In particular, target
     * extraction and payload invocation is entirely done in Espresso.
     * <p>
     * On the contrary, methods with IDs represented by
     * {@link SignaturePolymorphicIntrinsic#InvokeGeneric} are managed by the VM, but their behavior
     * is implemented through Java code, which is then simply called by the VM.
     */
    public final boolean isSignaturePolymorphicIntrinsic() {
        return isSignaturePolymorphicIntrinsic;
    }

    /**
     * Find the classification of the given method. Returns null if this method is not a polymorphic
     * signature method.
     */
    public static <C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> SignaturePolymorphicIntrinsic getId(M m) {
        return getId(m.getSymbolicName(), m.getDeclaringClass());
    }

    /**
     * Find the classification of the given method. Returns null if this method is not a polymorphic
     * signature method.
     */
    public static <C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> SignaturePolymorphicIntrinsic getId(Symbol<Name> name, C declaringKlass) {
        if (!ParserKlass.isSignaturePolymorphicHolderType(declaringKlass.getSymbolicType())) {
            return null;
        }
        if (ParserTypes.java_lang_invoke_MethodHandle.equals(declaringKlass.getSymbolicType())) {
            if (name == ParserNames.linkToStatic) {
                return SignaturePolymorphicIntrinsic.LinkToStatic;
            }
            if (name == ParserNames.linkToVirtual) {
                return SignaturePolymorphicIntrinsic.LinkToVirtual;
            }
            if (name == ParserNames.linkToSpecial) {
                return SignaturePolymorphicIntrinsic.LinkToSpecial;
            }
            if (name == ParserNames.linkToInterface) {
                return SignaturePolymorphicIntrinsic.LinkToInterface;
            }
            if (name == ParserNames.linkToNative) {
                return SignaturePolymorphicIntrinsic.LinkToNative;
            }
            if (name == ParserNames.invokeBasic) {
                return SignaturePolymorphicIntrinsic.InvokeBasic;
            }
        }
        if (declaringKlass.lookupDeclaredSignaturePolymorphicMethod(name) != null) {
            return SignaturePolymorphicIntrinsic.InvokeGeneric;
        }
        return null;
    }
}
