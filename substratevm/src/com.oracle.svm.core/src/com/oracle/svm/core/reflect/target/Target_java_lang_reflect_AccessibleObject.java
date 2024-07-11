/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.reflect.target;

import java.lang.reflect.AccessibleObject;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.configure.RuntimeConditionSet;
import com.oracle.svm.core.fieldvaluetransformer.FieldValueTransformerWithAvailability;

@TargetClass(value = AccessibleObject.class)
public final class Target_java_lang_reflect_AccessibleObject {
    @Alias //
    public boolean override;

    /**
     * For objects in image heap the conditions are always satisfied.
     */
    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = SatisfiedConditionComputer.class) //
    public RuntimeConditionSet conditions;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    volatile Object accessCheckCache;

    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = TypeAnnotationsComputer.class) //
    byte[] typeAnnotations;

    @Alias
    native AccessibleObject getRoot();

    static class TypeAnnotationsComputer extends ReflectionMetadataComputer {
        @Override
        public Object transform(Object receiver, Object originalValue) {
            return ImageSingletons.lookup(EncodedRuntimeMetadataSupplier.class).getTypeAnnotationsEncoding((AccessibleObject) receiver);
        }
    }

    static class SatisfiedConditionComputer implements FieldValueTransformerWithAvailability {
        @Override
        public Object transform(Object receiver, Object originalValue) {
            return RuntimeConditionSet.unmodifiableEmptySet();
        }

        @Override
        public final ValueAvailability valueAvailability() {
            return ValueAvailability.BeforeAnalysis;
        }

    }
}
