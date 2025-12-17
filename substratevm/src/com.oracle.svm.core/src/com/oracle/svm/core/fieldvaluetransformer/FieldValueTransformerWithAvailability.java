/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.fieldvaluetransformer;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.GraalAccess;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.vmaccess.VMAccess;
import jdk.vm.ci.meta.JavaConstant;

/**
 * Temporary implementation of {@link JVMCIFieldValueTransformerWithAvailability}, that falls back
 * to {@link FieldValueTransformer}. Usages should be migrated to
 * {@link JVMCIFieldValueTransformerWithAvailability} (GR-72015).
 */
@Platforms(Platform.HOSTED_ONLY.class)
public interface FieldValueTransformerWithAvailability extends FieldValueTransformer, JVMCIFieldValueTransformerWithAvailability {

    /**
     * Returns true when the value for this custom computation is available.
     */
    @Override
    boolean isAvailable();

    @Override
    Object transform(Object receiver, Object originalValue);

    @Override
    default JavaConstant transform(JavaConstant receiver, JavaConstant originalValue) {
        return transformAndConvert(this, receiver, originalValue);
    }

    /**
     * Transform a field value using a {@linkplain FieldValueTransformer core reflection based field
     * value transformer}. The {@link JavaConstant} inputs are unwrapped, the returned
     * {@link Object} is wrapped. This is only a temporary helper. Eventually, core reflection based
     * field value transformers will be executed via {@link VMAccess}.
     */
    static JavaConstant transformAndConvert(FieldValueTransformer fieldValueTransformer, JavaConstant receiver, JavaConstant originalValue) {
        SnippetReflectionProvider originalSnippetReflection = GraalAccess.getOriginalSnippetReflection();
        VMError.guarantee(originalValue != null, "Original value should not be `null`. Use `JavaConstant.NULL_POINTER`.");
        VMError.guarantee(receiver == null || !receiver.isNull(), "Receiver should not be a boxed `null` (`JavaConstant.isNull()`) for static fields. Use `null`instead");
        Object reflectionReceiver = toObject(receiver);
        Object reflectionOriginalValue = toObject(originalValue);
        Object newObject = fieldValueTransformer.transform(reflectionReceiver, reflectionOriginalValue);
        if (newObject == null) {
            return JavaConstant.NULL_POINTER;
        }
        if (newObject instanceof JavaConstantWrapper constantWrapper) {
            return constantWrapper.constant();
        }
        return originalSnippetReflection.forObject(newObject);
    }

    private static Object toObject(JavaConstant javaConstant) {
        if (javaConstant == null || javaConstant.isNull()) {
            return null;
        }
        if (javaConstant.getJavaKind().isObject()) {
            SnippetReflectionProvider originalSnippetReflection = GraalAccess.getOriginalSnippetReflection();
            return originalSnippetReflection.asObject(Object.class, javaConstant);
        }
        return javaConstant.asBoxedPrimitive();
    }

}
