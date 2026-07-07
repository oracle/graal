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
package com.oracle.svm.core.option;

import java.util.Objects;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.util.GuestAccess;
import com.oracle.svm.util.JVMCIReflectionUtil;

import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Builder-side bridge to an option key owned by a guest object stored in a static field.
 *
 * The bridge keeps the guest object as a {@link JavaConstant} and invokes its no-argument
 * {@code getValue()} method for every {@link #getValue()} call. This makes it suitable for
 * stateful option keys where the builder must not cache a stale value snapshot.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class GuestOptionKey<T> {
    private final JavaConstant receiver;
    private final Class<T> valueType;

    private GuestOptionKey(JavaConstant receiver, Class<T> valueType) {
        this.receiver = receiver;
        this.valueType = valueType;
    }

    /**
     * Creates a bridge for a guest runtime option stored in a static field.
     */
    public static <T> GuestOptionKey<T> forField(String declaringTypeName, String fieldName, Class<T> valueType) {
        Objects.requireNonNull(declaringTypeName, "declaringTypeName");
        Objects.requireNonNull(fieldName, "fieldName");
        Objects.requireNonNull(valueType, "valueType");

        GuestAccess access = GuestAccess.get();
        ResolvedJavaType declaringType = access.lookupType(declaringTypeName);
        if (declaringType == null) {
            throw new NoSuchFieldError(declaringTypeName + "." + fieldName);
        }
        JavaConstant receiver = JVMCIReflectionUtil.readStaticField(declaringType, fieldName);
        GraalError.guarantee(receiver != null && receiver.getJavaKind().isObject() && !receiver.isNull(), "Static field %s.%s does not contain a non-null guest object: %s", declaringTypeName,
                        fieldName, receiver);

        ResolvedJavaType receiverType = access.getProviders().getMetaAccess().lookupJavaType(receiver);
        GraalError.guarantee(access.elements.RuntimeOptionKey.isAssignableFrom(receiverType), "Static field %s.%s contains %s, not a RuntimeOptionKey", declaringTypeName, fieldName, receiverType
                        .toClassName());
        return new GuestOptionKey<>(receiver, valueType);
    }

    /**
     * Invokes {@code getValue()} in the guest and converts the result to the requested host type.
     */
    public T getValue() {
        GuestAccess access = GuestAccess.get();
        JavaConstant value = access.invoke(access.elements.RuntimeOptionKey_getValue, receiver);
        return access.asSafeHostObject(valueType, value);
    }
}
