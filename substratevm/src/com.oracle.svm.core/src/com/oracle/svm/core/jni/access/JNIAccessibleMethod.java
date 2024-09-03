/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jni.access;

import java.lang.reflect.Modifier;

import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.BuildPhaseProvider.ReadyForCompilation;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.code.RuntimeMetadataDecoderImpl;
import com.oracle.svm.core.graal.nodes.LoadOpenTypeWorldDispatchTableStartingOffset;
import com.oracle.svm.core.heap.UnknownPrimitiveField;
import com.oracle.svm.core.jni.CallVariant;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.word.BarrieredAccess;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Information on a method that can be looked up and called via JNI.
 */
public final class JNIAccessibleMethod extends JNIAccessibleMember {
    public static final int STATICALLY_BOUND_METHOD = -1;
    public static final int VTABLE_OFFSET_NOT_YET_COMPUTED = -2;
    public static final int INTERFACE_TYPEID_CLASS_TABLE = -1;
    public static final int INTERFACE_TYPEID_NOT_YET_COMPUTED = -2;
    public static final int INTERFACE_TYPEID_UNNEEDED = -3;
    public static final int NEW_OBJECT_INVALID_FOR_ABSTRACT_TYPE = -1;

    public static JNIAccessibleMethod negativeMethodQuery(JNIAccessibleClass jniClass) {
        return new JNIAccessibleMethod(jniClass, RuntimeMetadataDecoderImpl.NEGATIVE_FLAG_MASK);
    }

    @Platforms(HOSTED_ONLY.class)
    public static ResolvedJavaField getCallVariantWrapperField(MetaAccessProvider metaAccess, CallVariant variant, boolean nonVirtual) {
        StringBuilder name = new StringBuilder(32);
        if (variant == CallVariant.VARARGS) {
            name.append("varargs");
        } else if (variant == CallVariant.ARRAY) {
            name.append("array");
        } else if (variant == CallVariant.VA_LIST) {
            name.append("valist");
        } else {
            throw VMError.shouldNotReachHereUnexpectedInput(variant); // ExcludeFromJacocoGeneratedReport
        }
        if (nonVirtual) {
            name.append("Nonvirtual");
        }
        name.append("Wrapper");
        return metaAccess.lookupJavaField(ReflectionUtil.lookupField(JNIAccessibleMethod.class, name.toString()));
    }

    private final int modifiers;
    @UnknownPrimitiveField(availability = ReadyForCompilation.class)//
    private int vtableOffset = VTABLE_OFFSET_NOT_YET_COMPUTED;
    @UnknownPrimitiveField(availability = ReadyForCompilation.class)//
    private int interfaceTypeID = INTERFACE_TYPEID_NOT_YET_COMPUTED;
    @UnknownPrimitiveField(availability = ReadyForCompilation.class)//
    private CodePointer nonvirtualTarget;
    @UnknownPrimitiveField(availability = ReadyForCompilation.class)//
    private PointerBase newObjectTarget; // for constructors
    @UnknownPrimitiveField(availability = ReadyForCompilation.class)//
    private CodePointer callWrapper;
    @UnknownPrimitiveField(availability = ReadyForCompilation.class)//
    @SuppressWarnings("unused") private CodePointer varargsWrapper;
    @UnknownPrimitiveField(availability = ReadyForCompilation.class)//
    @SuppressWarnings("unused") private CodePointer arrayWrapper;
    @UnknownPrimitiveField(availability = ReadyForCompilation.class)//
    @SuppressWarnings("unused") private CodePointer valistWrapper;
    @UnknownPrimitiveField(availability = ReadyForCompilation.class)//
    @SuppressWarnings("unused") private CodePointer varargsNonvirtualWrapper;
    @UnknownPrimitiveField(availability = ReadyForCompilation.class)//
    @SuppressWarnings("unused") private CodePointer arrayNonvirtualWrapper;
    @UnknownPrimitiveField(availability = ReadyForCompilation.class)//
    @SuppressWarnings("unused") private CodePointer valistNonvirtualWrapper;

    @Platforms(HOSTED_ONLY.class)
    public JNIAccessibleMethod(JNIAccessibleClass declaringClass, int modifiers) {
        super(declaringClass);
        this.modifiers = modifiers;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isNegative() {
        return (modifiers & RuntimeMetadataDecoderImpl.NEGATIVE_FLAG_MASK) != 0;
    }

    @AlwaysInline("Work around an issue with the LLVM backend with which the return value was accessed incorrectly.")
    @Uninterruptible(reason = "Must not throw any exceptions.", callerMustBe = true)
    CodePointer getCallWrapperAddress() {
        return callWrapper;
    }

    @AlwaysInline("Work around an issue with the LLVM backend with which the return value was accessed incorrectly.")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    CodePointer getJavaCallAddress(Object instance, boolean nonVirtual) {
        if (!nonVirtual) {
            if (SubstrateOptions.closedTypeWorld()) {
                assert vtableOffset != JNIAccessibleMethod.VTABLE_OFFSET_NOT_YET_COMPUTED;
                if (vtableOffset != JNIAccessibleMethod.STATICALLY_BOUND_METHOD) {
                    return BarrieredAccess.readWord(instance.getClass(), vtableOffset, NamedLocationIdentity.FINAL_LOCATION);
                }
            } else {
                assert vtableOffset != JNIAccessibleMethod.VTABLE_OFFSET_NOT_YET_COMPUTED;
                if (vtableOffset != STATICALLY_BOUND_METHOD) {
                    long tableStartingOffset = LoadOpenTypeWorldDispatchTableStartingOffset.createOpenTypeWorldLoadDispatchTableStartingOffset(instance.getClass(), interfaceTypeID);

                    return BarrieredAccess.readWord(instance.getClass(), WordFactory.pointer(tableStartingOffset + vtableOffset), NamedLocationIdentity.FINAL_LOCATION);
                }
            }
        }
        return nonvirtualTarget;
    }

    PointerBase getNewObjectAddress() {
        return newObjectTarget;
    }

    Class<?> getDeclaringClassObject() {
        return getDeclaringClass().getClassObject();
    }

    boolean isPublic() {
        return Modifier.isPublic(modifiers);
    }

    boolean isStatic() {
        return Modifier.isStatic(modifiers);
    }

    @Platforms(HOSTED_ONLY.class)
    public void finishBeforeCompilation(EconomicSet<Class<?>> hidingSubclasses, int vtableOffsetEntry, int interfaceTypeIDEntry, CodePointer nonvirtualEntry, PointerBase newObjectEntry,
                    CodePointer callWrapperEntry, CodePointer varargs, CodePointer array, CodePointer valist, CodePointer varargsNonvirtual, CodePointer arrayNonvirtual,
                    CodePointer valistNonvirtual) {
        assert this.vtableOffset == VTABLE_OFFSET_NOT_YET_COMPUTED && (vtableOffsetEntry == STATICALLY_BOUND_METHOD || vtableOffsetEntry >= 0);
        assert this.interfaceTypeID == INTERFACE_TYPEID_NOT_YET_COMPUTED && interfaceTypeIDEntry != INTERFACE_TYPEID_NOT_YET_COMPUTED;

        this.vtableOffset = vtableOffsetEntry;
        this.interfaceTypeID = interfaceTypeIDEntry;
        this.nonvirtualTarget = nonvirtualEntry;
        this.newObjectTarget = newObjectEntry;
        this.callWrapper = callWrapperEntry;
        this.varargsWrapper = varargs;
        this.arrayWrapper = array;
        this.valistWrapper = valist;
        this.varargsNonvirtualWrapper = varargsNonvirtual;
        this.arrayNonvirtualWrapper = arrayNonvirtual;
        this.valistNonvirtualWrapper = valistNonvirtual;
        setHidingSubclasses(hidingSubclasses);
    }
}
