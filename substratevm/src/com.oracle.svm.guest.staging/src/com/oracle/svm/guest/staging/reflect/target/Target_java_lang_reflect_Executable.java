/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.guest.staging.reflect.target;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.guest.staging.GuestStagingDependencyBridge;
import com.oracle.svm.guest.staging.layered.LayerIdComputer;
import com.oracle.svm.guest.staging.reflect.RuntimeMetadataDecoder;
import com.oracle.svm.shared.util.BasedOnJDKFile;
import com.oracle.svm.shared.util.SubstrateUtil;

import sun.reflect.annotation.AnnotationParser;

@TargetClass(value = Executable.class)
public final class Target_java_lang_reflect_Executable {

    @Alias @RecomputeFieldValue(kind = Kind.Reset)//
    Target_java_lang_reflect_Executable_ParameterData parameterData;

    @Alias @RecomputeFieldValue(kind = Kind.Reset)//
    Map<Class<? extends Annotation>, Annotation> declaredAnnotations;

    /**
     * Backing metadata for {@link Executable#getParameters()}.
     * <p>
     * For executables created at image-build-time this contains the encoded {@code byte[]} produced
     * at image build time. For run-time-loaded Crema executables this contains the
     * {@code MethodParametersAttribute}. A {@code null} value means there is no
     * {@code MethodParameters} attribute and reflection must synthesize parameter objects.
     */
    @Inject @RecomputeFieldValue(kind = Kind.Custom, declClass = RawParametersComputer.class)//
    Object parameterMetadata;

    @Inject @RecomputeFieldValue(kind = Kind.Custom, declClass = LayerIdComputer.class)//
    public int layerId;

    @Alias
    native byte[] getAnnotationBytes();

    /**
     * The JDK implementation obtains the constant pool from the declaring class. Reflection
     * metadata for a base-layer executable can be added by a later layer, so its annotation bytes
     * must instead be decoded using the executable's layer.
     */
    @Substitute
    @TargetElement(onlyWith = ReflectionSupportPredicates.BuildingImageLayer.class)
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jvmci-25.6-b27/src/java.base/share/classes/java/lang/reflect/Executable.java#L659-L680")
    private Map<Class<? extends Annotation>, Annotation> declaredAnnotations() {
        Map<Class<? extends Annotation>, Annotation> result = declaredAnnotations;
        if (result == null) {
            synchronized (this) {
                result = declaredAnnotations;
                if (result == null) {
                    AccessibleObject root = SubstrateUtil.cast(this, Target_java_lang_reflect_AccessibleObject.class).getRoot();
                    if (root != null) {
                        result = SubstrateUtil.cast(root, Target_java_lang_reflect_Executable.class).declaredAnnotations();
                    } else {
                        Executable executable = SubstrateUtil.cast(this, Executable.class);
                        Class<?> declaringClass = executable.getDeclaringClass();
                        result = AnnotationParser.parseAnnotations(getAnnotationBytes(), ReflectionHelper.getAnnotationConstantPool(declaringClass, layerId), declaringClass);
                    }
                    declaredAnnotations = result;
                }
            }
        }
        return result;
    }

    @Substitute
    private Parameter[] getParameters0() {
        if (parameterMetadata == null) {
            return null;
        }
        Executable executable = SubstrateUtil.cast(this, Executable.class);
        Class<?> declaringClass = executable.getDeclaringClass();
        if (GuestStagingDependencyBridge.singleton().isRuntimeLoadedClass(declaringClass)) {
            return GuestStagingDependencyBridge.singleton().getRuntimeReflectParameters(executable, parameterMetadata);
        }
        /*
         * Note that the image-time parameter metadata encoding can also encode a possible
         * IllegalArgumentException. We want the decoder to throw this exception. Our caller
         * Executable.parameterData catches it and converts it to a MalformedParametersException.
         */
        return ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseReflectParameters(executable, (byte[]) parameterMetadata, declaringClass);
    }

    @Substitute
    byte[] getTypeAnnotationBytes0() {
        return SubstrateUtil.cast(this, Target_java_lang_reflect_AccessibleObject.class).typeAnnotations;
    }

    static class RawParametersComputer extends ReflectionMetadataComputer {
        @Override
        public Object transform(Object receiver, Object originalValue) {
            return ImageSingletons.lookup(EncodedRuntimeMetadataSupplier.class).getReflectParametersEncoding((Executable) receiver);
        }
    }
}

@TargetClass(value = Executable.class, innerClass = "ParameterData")
final class Target_java_lang_reflect_Executable_ParameterData {
}
