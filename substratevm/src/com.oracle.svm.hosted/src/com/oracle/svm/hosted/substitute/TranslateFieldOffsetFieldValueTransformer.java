/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.substitute;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.fieldvaluetransformer.FieldValueTransformerWithAvailability;
import com.oracle.svm.core.reflect.target.ReflectionSubstitutionSupport;
import com.oracle.svm.core.util.VMError;

import jdk.internal.misc.Unsafe;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Implements the field value transformation semantics of {@link Kind#TranslateFieldOffset}.
 */
public record TranslateFieldOffsetFieldValueTransformer(ResolvedJavaField original, Class<?> targetClass) implements FieldValueTransformerWithAvailability {

    @Override
    public boolean isAvailable() {
        return BuildPhaseProvider.isHostedUniverseBuilt();
    }

    @Override
    public Object transform(Object receiver, Object originalValue) {
        return translateFieldOffset(original, GraalAccess.getOriginalSnippetReflection().forObject(receiver), targetClass);
    }

    static Object translateFieldOffset(ResolvedJavaField original, JavaConstant receiver, Class<?> tclass) {
        long searchOffset = GraalAccess.getOriginalProviders().getConstantReflection().readFieldValue(original, receiver).asLong();
        /* Search the declared fields for a field with a matching offset. */
        for (Field f : tclass.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) {
                long fieldOffset = Unsafe.getUnsafe().objectFieldOffset(f);
                if (fieldOffset == searchOffset) {
                    int location = ImageSingletons.lookup(ReflectionSubstitutionSupport.class).getFieldOffset(f, true);
                    VMError.guarantee(location > 0, "Location is missing for field whose offset is stored: %s.", f);
                    return Long.valueOf(location);
                }
            }
        }
        throw shouldNotReachHere("unknown field offset class: " + tclass + ", offset = " + searchOffset);
    }
}
