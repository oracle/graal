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

import java.lang.reflect.Modifier;

import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.word.BarrieredAccess;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.graal.meta.KnownOffsets;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.CompilationAccessImpl;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.jni.hosted.JNIJavaCallVariantWrapperMethod;
import com.oracle.svm.jni.hosted.JNIJavaCallVariantWrapperMethod.CallVariant;
import com.oracle.svm.jni.hosted.JNIJavaCallWrapperMethod;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Information on a method that can be looked up and called via JNI.
 */
public final class JNIAccessibleMethod extends JNIAccessibleMember {
    public static final int STATICALLY_BOUND_METHOD = -1;
    public static final int VTABLE_OFFSET_NOT_YET_COMPUTED = -2;
    public static final int NEW_OBJECT_INVALID_FOR_ABSTRACT_TYPE = -1;

    static ResolvedJavaField getCallVariantWrapperField(MetaAccessProvider metaAccess, CallVariant variant, boolean nonVirtual) {
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
        name.append("Wrapper");
        return metaAccess.lookupJavaField(ReflectionUtil.lookupField(JNIAccessibleMethod.class, name.toString()));
    }

    @Platforms(HOSTED_ONLY.class) private final JNIAccessibleMethodDescriptor descriptor;
    private final int modifiers;
    private int vtableOffset = VTABLE_OFFSET_NOT_YET_COMPUTED;
    private CodePointer nonvirtualTarget;
    private PointerBase newObjectTarget; // for constructors
    private CodePointer callWrapper;
    @SuppressWarnings("unused") private CFunctionPointer varargsWrapper;
    @SuppressWarnings("unused") private CFunctionPointer arrayWrapper;
    @SuppressWarnings("unused") private CFunctionPointer valistWrapper;
    @SuppressWarnings("unused") private CFunctionPointer varargsNonvirtualWrapper;
    @SuppressWarnings("unused") private CFunctionPointer arrayNonvirtualWrapper;
    @SuppressWarnings("unused") private CFunctionPointer valistNonvirtualWrapper;
    @Platforms(HOSTED_ONLY.class) private final ResolvedJavaMethod targetMethod;
    @Platforms(HOSTED_ONLY.class) private final ResolvedJavaMethod newObjectTargetMethod;
    @Platforms(HOSTED_ONLY.class) private final JNIJavaCallWrapperMethod callWrapperMethod;
    @Platforms(HOSTED_ONLY.class) private final JNIJavaCallVariantWrapperMethod varargsWrapperMethod;
    @Platforms(HOSTED_ONLY.class) private final JNIJavaCallVariantWrapperMethod arrayWrapperMethod;
    @Platforms(HOSTED_ONLY.class) private final JNIJavaCallVariantWrapperMethod valistWrapperMethod;
    @Platforms(HOSTED_ONLY.class) private final JNIJavaCallVariantWrapperMethod varargsNonvirtualWrapperMethod;
    @Platforms(HOSTED_ONLY.class) private final JNIJavaCallVariantWrapperMethod arrayNonvirtualWrapperMethod;
    @Platforms(HOSTED_ONLY.class) private final JNIJavaCallVariantWrapperMethod valistNonvirtualWrapperMethod;

    JNIAccessibleMethod(JNIAccessibleMethodDescriptor descriptor,
                    JNIAccessibleClass declaringClass,
                    ResolvedJavaMethod targetMethod,
                    ResolvedJavaMethod newObjectTargetMethod,
                    JNIJavaCallWrapperMethod callWrapperMethod,
                    JNIJavaCallVariantWrapperMethod varargsWrapper,
                    JNIJavaCallVariantWrapperMethod arrayWrapper,
                    JNIJavaCallVariantWrapperMethod valistWrapper,
                    JNIJavaCallVariantWrapperMethod varargsNonvirtualWrapper,
                    JNIJavaCallVariantWrapperMethod arrayNonvirtualWrapper,
                    JNIJavaCallVariantWrapperMethod valistNonvirtualWrapper) {
        super(declaringClass);
        assert callWrapperMethod != null && varargsWrapper != null && arrayWrapper != null && valistWrapper != null;
        assert (targetMethod.isStatic() || targetMethod.isAbstract()) //
                        ? (varargsNonvirtualWrapper == null && arrayNonvirtualWrapper == null && valistNonvirtualWrapper == null)
                        : (varargsNonvirtualWrapper != null & arrayNonvirtualWrapper != null && valistNonvirtualWrapper != null);
        this.descriptor = descriptor;
        this.modifiers = targetMethod.getModifiers();
        this.targetMethod = targetMethod;
        this.newObjectTargetMethod = newObjectTargetMethod;
        this.callWrapperMethod = callWrapperMethod;
        this.varargsWrapperMethod = varargsWrapper;
        this.arrayWrapperMethod = arrayWrapper;
        this.valistWrapperMethod = valistWrapper;
        this.varargsNonvirtualWrapperMethod = varargsNonvirtualWrapper;
        this.arrayNonvirtualWrapperMethod = arrayNonvirtualWrapper;
        this.valistNonvirtualWrapperMethod = valistNonvirtualWrapper;
    }

    @AlwaysInline("Work around an issue with the LLVM backend with which the return value was accessed incorrectly.")
    @Uninterruptible(reason = "Allow inlining from call wrappers, which are uninterruptible.", mayBeInlined = true)
    public CodePointer getCallWrapperAddress() {
        return callWrapper;
    }

    @AlwaysInline("Work around an issue with the LLVM backend with which the return value was accessed incorrectly.")
    public CodePointer getJavaCallAddress(Object instance, boolean nonVirtual) {
        if (!nonVirtual) {
            assert vtableOffset != JNIAccessibleMethod.VTABLE_OFFSET_NOT_YET_COMPUTED;
            if (vtableOffset != JNIAccessibleMethod.STATICALLY_BOUND_METHOD) {
                return BarrieredAccess.readWord(instance.getClass(), vtableOffset, NamedLocationIdentity.FINAL_LOCATION);
            }
        }
        return nonvirtualTarget;
    }

    public PointerBase getNewObjectAddress() {
        return newObjectTarget;
    }

    public Class<?> getDeclaringClassObject() {
        return getDeclaringClass().getClassObject();
    }

    boolean isPublic() {
        return Modifier.isPublic(modifiers);
    }

    boolean isStatic() {
        return Modifier.isStatic(modifiers);
    }

    @Platforms(HOSTED_ONLY.class)
    void finishBeforeCompilation(CompilationAccessImpl access) {
        HostedUniverse hUniverse = access.getUniverse();
        AnalysisUniverse aUniverse = access.getUniverse().getBigBang().getUniverse();
        HostedMethod hTarget = hUniverse.lookup(aUniverse.lookup(targetMethod));
        if (hTarget.canBeStaticallyBound()) {
            vtableOffset = STATICALLY_BOUND_METHOD;
        } else {
            vtableOffset = KnownOffsets.singleton().getVTableOffset(hTarget.getVTableIndex());
        }
        nonvirtualTarget = new MethodPointer(hTarget);
        if (newObjectTargetMethod != null) {
            newObjectTarget = new MethodPointer(hUniverse.lookup(aUniverse.lookup(newObjectTargetMethod)));
        } else if (targetMethod.isConstructor()) {
            assert targetMethod.getDeclaringClass().isAbstract();
            newObjectTarget = WordFactory.signed(NEW_OBJECT_INVALID_FOR_ABSTRACT_TYPE);
        }
        callWrapper = new MethodPointer(hUniverse.lookup(aUniverse.lookup(callWrapperMethod)));
        varargsWrapper = new MethodPointer(hUniverse.lookup(aUniverse.lookup(varargsWrapperMethod)));
        arrayWrapper = new MethodPointer(hUniverse.lookup(aUniverse.lookup(arrayWrapperMethod)));
        valistWrapper = new MethodPointer(hUniverse.lookup(aUniverse.lookup(valistWrapperMethod)));
        if (!Modifier.isStatic(modifiers) && !Modifier.isAbstract(modifiers)) {
            varargsNonvirtualWrapper = new MethodPointer(hUniverse.lookup(aUniverse.lookup(varargsNonvirtualWrapperMethod)));
            arrayNonvirtualWrapper = new MethodPointer(hUniverse.lookup(aUniverse.lookup(arrayNonvirtualWrapperMethod)));
            valistNonvirtualWrapper = new MethodPointer(hUniverse.lookup(aUniverse.lookup(valistNonvirtualWrapperMethod)));
        }
        setHidingSubclasses(access.getMetaAccess(), this::anyMatchIgnoreReturnType);
    }

    private boolean anyMatchIgnoreReturnType(ResolvedJavaType sub) {
        try {
            for (ResolvedJavaMethod method : sub.getDeclaredMethods()) {
                if (descriptor.matchesIgnoreReturnType(method)) {
                    return true;
                }
            }
            return false;

        } catch (LinkageError ex) {
            /*
             * Ignore any linkage errors due to looking up the declared methods. Unfortunately, it
             * is not possible to look up methods (even a single declared method with a known
             * signature using reflection) if any other method of the class references a missing
             * type. In this case, we have to assume that the subclass does not have a matching
             * method.
             */
            return false;
        }
    }
}
