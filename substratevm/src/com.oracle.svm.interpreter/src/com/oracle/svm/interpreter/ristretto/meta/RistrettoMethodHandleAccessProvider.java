/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto.meta;

import java.lang.invoke.MethodHandle;

import com.oracle.svm.core.invoke.Target_java_lang_invoke_MemberName;
import com.oracle.svm.core.methodhandles.MethodHandleInterpreterUtils;
import com.oracle.svm.espresso.shared.meta.SignaturePolymorphicIntrinsic;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Runtime counterpart of the hosted method-handle access provider for Ristretto metadata.
 *
 * Ristretto parses runtime-loaded bytecode against {@link RistrettoMethod}s, not HotSpot or hosted
 * JVMCI methods. The normal SVM runtime constant-reflection provider intentionally has no
 * method-handle access, so runtime parsing needs to classify Ristretto signature-polymorphic
 * intrinsics and resolve constant MethodHandle/MemberName objects through the same metadata path
 * used by the interpreter.
 */
public final class RistrettoMethodHandleAccessProvider implements MethodHandleAccessProvider {
    private final SnippetReflectionProvider snippetReflectionProvider;

    public RistrettoMethodHandleAccessProvider(SnippetReflectionProvider snippetReflectionProvider) {
        this.snippetReflectionProvider = snippetReflectionProvider;
    }

    @Override
    public IntrinsicMethod lookupMethodHandleIntrinsic(ResolvedJavaMethod method) {
        if (method instanceof RistrettoMethod ristrettoMethod) {
            return toJVMCIIntrinsic(ristrettoMethod.getInterpreterMethod().getSignaturePolymorphicIntrinsic());
        }
        return null;
    }

    @Override
    public ResolvedJavaMethod resolveInvokeBasicTarget(JavaConstant methodHandle, boolean forceBytecodeGeneration) {
        Object value = snippetReflectionProvider.asObject(Object.class, methodHandle);
        if (value instanceof MethodHandle handle) {
            return asRistrettoMethod(MethodHandleInterpreterUtils.extractVMEntry(handle));
        }
        return null;
    }

    @Override
    public ResolvedJavaMethod resolveLinkToTarget(JavaConstant memberName) {
        Object value = snippetReflectionProvider.asObject(Object.class, memberName);
        if (value instanceof Target_java_lang_invoke_MemberName targetMemberName) {
            return asRistrettoMethod(targetMemberName);
        }
        return null;
    }

    private static RistrettoMethod asRistrettoMethod(Target_java_lang_invoke_MemberName memberName) {
        InterpreterResolvedJavaMethod target = InterpreterResolvedJavaMethod.fromMemberName(memberName);
        return RistrettoMethod.getOrCreate(target);
    }

    /**
     * Maps the interpreter metadata enum used by runtime-loaded methods to the JVMCI enum expected
     * by Graal's method-handle plugins.
     */
    private static IntrinsicMethod toJVMCIIntrinsic(SignaturePolymorphicIntrinsic intrinsic) {
        if (intrinsic == null) {
            return null;
        }
        return switch (intrinsic) {
            case InvokeBasic -> IntrinsicMethod.INVOKE_BASIC;
            case LinkToStatic -> IntrinsicMethod.LINK_TO_STATIC;
            case LinkToSpecial -> IntrinsicMethod.LINK_TO_SPECIAL;
            case LinkToVirtual -> IntrinsicMethod.LINK_TO_VIRTUAL;
            case LinkToInterface -> IntrinsicMethod.LINK_TO_INTERFACE;
            case LinkToNative -> IntrinsicMethod.LINK_TO_NATIVE;
            case InvokeGeneric -> null;
        };
    }
}
