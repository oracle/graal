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

import java.lang.reflect.Modifier;

import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.fieldvaluetransformer.FieldValueTransformerWithAvailability;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Implements the field value transformation semantics of {@link Kind#AtomicFieldUpdaterOffset}.
 */
public record AtomicFieldUpdaterOffsetFieldValueTransformer(ResolvedJavaField original, Class<?> updaterClass) implements FieldValueTransformerWithAvailability {

    @Override
    public boolean isAvailable() {
        return BuildPhaseProvider.isHostedUniverseBuilt();
    }

    @Override
    public Object transform(Object receiver, Object originalValue) {
        assert !Modifier.isStatic(original.getModifiers());

        /*
         * AtomicXxxFieldUpdater implementation objects cache the offset of some specified field. We
         * have to search the declaring class for a field that has the same "unsafe" offset as the
         * cached offset in this atomic updater object.
         */
        ResolvedJavaField tclassField = findField(original.getDeclaringClass(), "tclass");
        SnippetReflectionProvider originalSnippetReflection = GraalAccess.getOriginalSnippetReflection();
        JavaConstant receiverConstant = GraalAccess.getOriginalSnippetReflection().forObject(receiver);
        Class<?> tclass = originalSnippetReflection.asObject(Class.class, GraalAccess.getOriginalProviders().getConstantReflection().readFieldValue(tclassField, receiverConstant));

        return TranslateFieldOffsetFieldValueTransformer.translateFieldOffset(original, receiverConstant, tclass);
    }

    private static ResolvedJavaField findField(ResolvedJavaType declaringClass, String name) {
        for (ResolvedJavaField field : declaringClass.getInstanceFields(false)) {
            if (field.getName().equals(name)) {
                return field;
            }
        }
        throw shouldNotReachHere("Field not found: " + declaringClass.toJavaName(true) + "." + name);
    }
}
