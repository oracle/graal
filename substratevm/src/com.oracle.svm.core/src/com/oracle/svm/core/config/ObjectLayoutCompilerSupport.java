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
package com.oracle.svm.core.config;

import com.oracle.svm.shared.config.ObjectLayout;

import java.util.Objects;

import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.word.WordBase;

import com.oracle.svm.util.GuestAnnotationAccess;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;

/**
 * Provides JVMCI-based layout queries and call-signature classification for compiler, image-building,
 * and runtime implementation code.
 * <p>
 * These operations were extracted from {@code com.oracle.svm.core.config.ObjectLayout} when the
 * layout model moved to shared code. Keeping the JVMCI types and guest annotation queries here lets
 * {@link ObjectLayout} remain usable without those dependencies.
 * <p>
 * The separation remains useful while callers need these JVMCI-facing operations; moving the layout
 * model does not by itself make this adapter obsolete.
 */
public final class ObjectLayoutCompilerSupport {

    private ObjectLayoutCompilerSupport() {
    }

    public static int sizeInBytes(ObjectLayout layout, JavaKind kind) {
        Objects.requireNonNull(layout);
        return layout.sizeInBytes(toGuestJavaKind(kind));
    }

    /**
     * Maps a JVMCI kind to the guest-facing enum representation in the current context; it does not
     * transfer a value to another VM.
     */
    public static com.oracle.svm.espresso.classfile.JavaKind toGuestJavaKind(JavaKind kind) {
        return switch (kind) {
            case Boolean -> com.oracle.svm.espresso.classfile.JavaKind.Boolean;
            case Byte -> com.oracle.svm.espresso.classfile.JavaKind.Byte;
            case Char -> com.oracle.svm.espresso.classfile.JavaKind.Char;
            case Short -> com.oracle.svm.espresso.classfile.JavaKind.Short;
            case Int -> com.oracle.svm.espresso.classfile.JavaKind.Int;
            case Long -> com.oracle.svm.espresso.classfile.JavaKind.Long;
            case Float -> com.oracle.svm.espresso.classfile.JavaKind.Float;
            case Double -> com.oracle.svm.espresso.classfile.JavaKind.Double;
            case Object -> com.oracle.svm.espresso.classfile.JavaKind.Object;
            case Void -> com.oracle.svm.espresso.classfile.JavaKind.Void;
            case Illegal -> com.oracle.svm.espresso.classfile.JavaKind.Illegal;
        };
    }

    public static int getArrayIndexScale(ObjectLayout layout, JavaKind kind) {
        Objects.requireNonNull(layout);
        return layout.getArrayIndexScale(toGuestJavaKind(kind));
    }

    public static int getArrayIndexShift(ObjectLayout layout, JavaKind kind) {
        Objects.requireNonNull(layout);
        return layout.getArrayIndexShift(toGuestJavaKind(kind));
    }

    public static int getArrayBaseOffset(ObjectLayout layout, JavaKind kind) {
        Objects.requireNonNull(layout);
        return layout.getArrayBaseOffset(toGuestJavaKind(kind));
    }

    public static long getArrayElementOffset(ObjectLayout layout, JavaKind kind, int index) {
        Objects.requireNonNull(layout);
        return layout.getArrayElementOffset(toGuestJavaKind(kind), index);
    }

    public static long getArraySize(ObjectLayout layout, JavaKind kind, int length, boolean withOptionalIdHashField) {
        Objects.requireNonNull(layout);
        assert length >= 0 : length;
        return layout.getArraySize(toGuestJavaKind(kind), length, withOptionalIdHashField);
    }

    public static long getArrayIdentityHashOffset(ObjectLayout layout, JavaKind kind, int length) {
        Objects.requireNonNull(layout);
        assert length >= 0 : length;
        return layout.getArrayIdentityHashOffset(toGuestJavaKind(kind), length);
    }

    public static JavaKind getCallSignatureKind(boolean isEntryPoint, JavaType type, MetaAccessProvider metaAccess, TargetDescription target) {
        if (!(type instanceof ResolvedJavaType resolvedJavaType)) {
            assert type instanceof UnresolvedJavaType : type;
            return JavaKind.Object;
        }

        if (metaAccess != null && metaAccess.lookupJavaType(WordBase.class).isAssignableFrom(resolvedJavaType)) {
            return target.wordJavaKind;
        }
        if (isEntryPoint && GuestAnnotationAccess.isAnnotationPresent(resolvedJavaType, CEnum.class)) {
            return JavaKind.Int;
        }
        return type.getJavaKind();
    }
}
