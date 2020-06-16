/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jni.access;

// Checkstyle: allow reflection

import java.lang.reflect.Modifier;

import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;

import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.CompilationAccessImpl;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.meta.MethodPointer;
import com.oracle.svm.jni.hosted.JNIJavaCallWrapperMethod;
import com.oracle.svm.jni.hosted.JNIJavaCallWrapperMethod.CallVariant;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Information on a method that can be looked up and called via JNI.
 */
public final class JNIAccessibleMethod extends JNIAccessibleMember {

    public static ResolvedJavaField getCallWrapperField(MetaAccessProvider metaAccess, CallVariant variant, boolean nonVirtual) {
        StringBuilder name = new StringBuilder(32);
        if (variant == CallVariant.VARARGS) {
            name.append("varargs");
        } else if (variant == CallVariant.ARRAY) {
            name.append("array");
        } else if (variant == CallVariant.VA_LIST) {
            name.append("valist");
        } else {
            throw VMError.shouldNotReachHere();
        }
        if (nonVirtual) {
            name.append("Nonvirtual");
        }
        name.append("CallWrapper");
        try {
            return metaAccess.lookupJavaField(JNIAccessibleMethod.class.getDeclaredField(name.toString()));
        } catch (NoSuchFieldException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    @Platforms(HOSTED_ONLY.class) private final JNIAccessibleMethodDescriptor descriptor;
    private final int modifiers;
    @SuppressWarnings("unused") private CFunctionPointer varargsCallWrapper;
    @SuppressWarnings("unused") private CFunctionPointer arrayCallWrapper;
    @SuppressWarnings("unused") private CFunctionPointer valistCallWrapper;
    @SuppressWarnings("unused") private CFunctionPointer varargsNonvirtualCallWrapper;
    @SuppressWarnings("unused") private CFunctionPointer arrayNonvirtualCallWrapper;
    @SuppressWarnings("unused") private CFunctionPointer valistNonvirtualCallWrapper;
    @Platforms(HOSTED_ONLY.class) private final JNIJavaCallWrapperMethod varargsCallWrapperMethod;
    @Platforms(HOSTED_ONLY.class) private final JNIJavaCallWrapperMethod arrayCallWrapperMethod;
    @Platforms(HOSTED_ONLY.class) private final JNIJavaCallWrapperMethod valistCallWrapperMethod;
    @Platforms(HOSTED_ONLY.class) private final JNIJavaCallWrapperMethod varargsNonvirtualCallWrapperMethod;
    @Platforms(HOSTED_ONLY.class) private final JNIJavaCallWrapperMethod arrayNonvirtualCallWrapperMethod;
    @Platforms(HOSTED_ONLY.class) private final JNIJavaCallWrapperMethod valistNonvirtualCallWrapperMethod;

    JNIAccessibleMethod(JNIAccessibleMethodDescriptor descriptor,
                    int modifiers,
                    JNIAccessibleClass declaringClass,
                    JNIJavaCallWrapperMethod varargsCallWrapper,
                    JNIJavaCallWrapperMethod arrayCallWrapper,
                    JNIJavaCallWrapperMethod valistCallWrapper,
                    JNIJavaCallWrapperMethod varargsNonvirtualCallWrapperMethod,
                    JNIJavaCallWrapperMethod arrayNonvirtualCallWrapperMethod,
                    JNIJavaCallWrapperMethod valistNonvirtualCallWrapperMethod) {
        super(declaringClass);

        assert varargsCallWrapper != null && arrayCallWrapper != null && valistCallWrapper != null;
        assert (Modifier.isStatic(modifiers) || Modifier.isAbstract(modifiers)) //
                        ? (varargsNonvirtualCallWrapperMethod == null && arrayNonvirtualCallWrapperMethod == null && valistNonvirtualCallWrapperMethod == null)
                        : (varargsNonvirtualCallWrapperMethod != null & arrayNonvirtualCallWrapperMethod != null && valistNonvirtualCallWrapperMethod != null);
        this.descriptor = descriptor;
        this.modifiers = modifiers;
        this.varargsCallWrapperMethod = varargsCallWrapper;
        this.arrayCallWrapperMethod = arrayCallWrapper;
        this.valistCallWrapperMethod = valistCallWrapper;
        this.varargsNonvirtualCallWrapperMethod = varargsNonvirtualCallWrapperMethod;
        this.arrayNonvirtualCallWrapperMethod = arrayNonvirtualCallWrapperMethod;
        this.valistNonvirtualCallWrapperMethod = valistNonvirtualCallWrapperMethod;
    }

    public boolean isPublic() {
        return Modifier.isPublic(modifiers);
    }

    public boolean isStatic() {
        return Modifier.isStatic(modifiers);
    }

    @Platforms(HOSTED_ONLY.class)
    void finishBeforeCompilation(CompilationAccessImpl access) {
        HostedUniverse hUniverse = access.getUniverse();
        AnalysisUniverse aUniverse = access.getUniverse().getBigBang().getUniverse();
        varargsCallWrapper = MethodPointer.factory(hUniverse.lookup(aUniverse.lookup(varargsCallWrapperMethod)));
        arrayCallWrapper = MethodPointer.factory(hUniverse.lookup(aUniverse.lookup(arrayCallWrapperMethod)));
        valistCallWrapper = MethodPointer.factory(hUniverse.lookup(aUniverse.lookup(valistCallWrapperMethod)));
        if (!Modifier.isStatic(modifiers) && !Modifier.isAbstract(modifiers)) {
            varargsNonvirtualCallWrapper = MethodPointer.factory(hUniverse.lookup(aUniverse.lookup(varargsNonvirtualCallWrapperMethod)));
            arrayNonvirtualCallWrapper = MethodPointer.factory(hUniverse.lookup(aUniverse.lookup(arrayNonvirtualCallWrapperMethod)));
            valistNonvirtualCallWrapper = MethodPointer.factory(hUniverse.lookup(aUniverse.lookup(valistNonvirtualCallWrapperMethod)));
        }
        setHidingSubclasses(access.getMetaAccess(), this::anyMatchIgnoreReturnType);
    }

    private boolean anyMatchIgnoreReturnType(ResolvedJavaType sub) {
        for (ResolvedJavaMethod method : sub.getDeclaredMethods()) {
            if (descriptor.matchesIgnoreReturnType(method)) {
                return true;
            }
        }
        return false;
    }
}
