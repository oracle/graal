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
package com.oracle.svm.reflect.target;

import static com.oracle.svm.core.annotate.TargetElement.CONSTRUCTOR_NAME;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.image.NativeImageCodeCache;
import com.oracle.svm.reflect.hosted.ExecutableAccessorComputer;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

@TargetClass(value = Constructor.class)
public final class Target_java_lang_reflect_Constructor {

    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = AnnotationsComputer.class)//
    @UnknownObjectField(types = {byte[].class}) byte[] annotations;

    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = ParameterAnnotationsComputer.class)//
    @UnknownObjectField(types = {byte[].class}) byte[] parameterAnnotations;

    @Alias //
    @RecomputeFieldValue(kind = Kind.Custom, declClass = ExecutableAccessorComputer.class) //
    Target_jdk_internal_reflect_ConstructorAccessor constructorAccessor;

    @Alias
    @TargetElement(name = CONSTRUCTOR_NAME)
    @SuppressWarnings("hiding")
    public native void constructor(Class<?> declaringClass, Class<?>[] parameterTypes, Class<?>[] checkedExceptions, int modifiers, int slot, String signature, byte[] annotations,
                    byte[] parameterAnnotations);

    @Alias
    native Target_java_lang_reflect_Constructor copy();

    @Substitute
    Target_jdk_internal_reflect_ConstructorAccessor acquireConstructorAccessor() {
        if (constructorAccessor == null) {
            throw VMError.unsupportedFeature("Runtime reflection is not supported for " + this);
        }
        return constructorAccessor;
    }

    static class AnnotationsComputer implements RecomputeFieldValue.CustomFieldValueComputer {

        @Override
        public RecomputeFieldValue.ValueAvailability valueAvailability() {
            return RecomputeFieldValue.ValueAvailability.AfterCompilation;
        }

        @Override
        public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
            return ImageSingletons.lookup(NativeImageCodeCache.MethodMetadataEncoder.class).getAnnotationsEncoding((AccessibleObject) receiver);
        }
    }

    static class ParameterAnnotationsComputer implements RecomputeFieldValue.CustomFieldValueComputer {

        @Override
        public RecomputeFieldValue.ValueAvailability valueAvailability() {
            return RecomputeFieldValue.ValueAvailability.AfterCompilation;
        }

        @Override
        public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
            return ImageSingletons.lookup(NativeImageCodeCache.MethodMetadataEncoder.class).getParameterAnnotationsEncoding((Executable) receiver);
        }
    }
}
