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
package com.oracle.svm.core.reflect.target;

import static com.oracle.svm.core.annotate.TargetElement.CONSTRUCTOR_NAME;

import java.lang.annotation.AnnotationFormatError;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import com.oracle.svm.configure.config.ConfigurationMemberInfo;
import com.oracle.svm.configure.config.SignatureUtil;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.configure.RuntimeConditionSet;
import com.oracle.svm.core.hub.ConstantPoolProvider;
import com.oracle.svm.core.imagelayer.DynamicImageLayerInfo;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.metadata.MetadataTracer;
import com.oracle.svm.core.reflect.MissingReflectionRegistrationUtils;

import jdk.internal.reflect.ConstantPool;
import sun.reflect.annotation.AnnotationParser;
import sun.reflect.annotation.AnnotationType;
import sun.reflect.annotation.ExceptionProxy;
import sun.reflect.annotation.TypeNotPresentExceptionProxy;
import sun.reflect.generics.repository.MethodRepository;

@TargetClass(value = Method.class)
public final class Target_java_lang_reflect_Method {
    /** Generic info is created on demand at run time. */
    @Alias @RecomputeFieldValue(kind = Kind.Reset) //
    private MethodRepository genericInfo;

    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = AnnotationsComputer.class)//
    byte[] annotations;

    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = ParameterAnnotationsComputer.class)//
    byte[] parameterAnnotations;

    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = AnnotationDefaultComputer.class)//
    byte[] annotationDefault;

    @Alias //
    @RecomputeFieldValue(kind = Kind.Custom, declClass = ExecutableAccessorComputer.class) //
    public Target_jdk_internal_reflect_MethodAccessor methodAccessor;

    @Alias //
    private Class<?> clazz;

    @Alias //
    private int slot;

    @Alias //
    private String name;

    @Alias //
    private Class<?> returnType;

    @Alias //
    private Class<?>[] parameterTypes;

    @Alias //
    private Class<?>[] exceptionTypes;

    @Alias //
    private int modifiers;

    @Alias //
    private transient String signature;

    @Alias //
    private Method root;

    /**
     * We need this indirection to use {@link #acquireMethodAccessor()} for checking if run-time
     * conditions for this method are satisfied.
     */
    @Inject //
    @RecomputeFieldValue(kind = Kind.Reset) //
    Target_jdk_internal_reflect_MethodAccessor methodAccessorFromMetadata;

    @Inject //
    @RecomputeFieldValue(kind = Kind.Custom, declClass = LayerIdComputer.class) //
    public int layerId;

    @Alias
    @TargetElement(name = CONSTRUCTOR_NAME)
    @SuppressWarnings("hiding")
    native void constructor(Class<?> declaringClass, String name, Class<?>[] parameterTypes, Class<?> returnType, Class<?>[] checkedExceptions, int modifiers, int slot, String signature,
                    byte[] annotations, byte[] parameterAnnotations, byte[] annotationDefault);

    @Alias
    public native Class<?> getReturnType();

    @Alias
    public native Class<?> getDeclaringClass();

    /**
     * Encoded field, will fetch the value from {@link #methodAccessorFromMetadata} and check the
     * conditions.
     */
    @Substitute
    public Target_jdk_internal_reflect_MethodAccessor acquireMethodAccessor() {
        RuntimeConditionSet conditions = SubstrateUtil.cast(this, Target_java_lang_reflect_AccessibleObject.class).conditions;
        if (MetadataTracer.enabled()) {
            MethodUtil.traceMethodAccess(SubstrateUtil.cast(this, Executable.class));
        }
        if (methodAccessorFromMetadata == null || !conditions.satisfied()) {
            throw MissingReflectionRegistrationUtils.reportInvokedExecutable(SubstrateUtil.cast(this, Executable.class));
        }
        return methodAccessorFromMetadata;
    }

    @Substitute
    public Object getDefaultValue() {
        if (annotationDefault == null) {
            return null;
        }
        Class<?> memberType = AnnotationType.invocationHandlerReturnType(getReturnType());
        /*
         * The layer id of the method is not necessarily the same as the declaring class, so the
         * constant pool used need to be chosen using the layer id of the method.
         */
        Object result = AnnotationParser.parseMemberValue(memberType, ByteBuffer.wrap(annotationDefault),
                        ImageLayerBuildingSupport.buildingImageLayer() ? SubstrateUtil.cast(ConstantPoolProvider.singletons()[layerId].getConstantPool(), ConstantPool.class) : null,
                        getDeclaringClass());
        if (result instanceof ExceptionProxy) {
            if (result instanceof TypeNotPresentExceptionProxy proxy) {
                throw new TypeNotPresentException(proxy.typeName(), proxy.getCause());
            }
            throw new AnnotationFormatError("Invalid default: " + this);
        }
        return result;
    }

    @Substitute
    Target_java_lang_reflect_Method copy() {
        if (this.root != null) {
            throw new IllegalArgumentException("Can not copy a non-root Method");
        }

        Target_java_lang_reflect_Method res = new Target_java_lang_reflect_Method();
        res.constructor(clazz, name, parameterTypes, returnType,
                        exceptionTypes, modifiers, slot, signature,
                        annotations, parameterAnnotations, annotationDefault);
        res.root = SubstrateUtil.cast(this, Method.class);
        // Propagate shared states
        res.methodAccessor = methodAccessor;
        res.genericInfo = genericInfo;
        /* Copy the layer id too */
        res.layerId = layerId;
        return res;
    }

    static class AnnotationsComputer extends ReflectionMetadataComputer {
        @Override
        public Object transform(Object receiver, Object originalValue) {
            return ImageSingletons.lookup(EncodedRuntimeMetadataSupplier.class).getAnnotationsEncoding((AccessibleObject) receiver);
        }
    }

    static class ParameterAnnotationsComputer extends ReflectionMetadataComputer {
        @Override
        public Object transform(Object receiver, Object originalValue) {
            return ImageSingletons.lookup(EncodedRuntimeMetadataSupplier.class).getParameterAnnotationsEncoding((Executable) receiver);
        }
    }

    static class AnnotationDefaultComputer extends ReflectionMetadataComputer {
        @Override
        public Object transform(Object receiver, Object originalValue) {
            return ImageSingletons.lookup(EncodedRuntimeMetadataSupplier.class).getAnnotationDefaultEncoding((Method) receiver);
        }
    }

    static class LayerIdComputer implements FieldValueTransformer {
        @Override
        public Object transform(Object receiver, Object originalValue) {
            if (ImageLayerBuildingSupport.buildingImageLayer()) {
                return DynamicImageLayerInfo.getCurrentLayerNumber();
            }
            return MultiLayeredImageSingleton.UNUSED_LAYER_NUMBER;
        }
    }
}

class MethodUtil {
    static void traceMethodAccess(Executable meth) {
        MetadataTracer.singleton().traceMethodAccess(meth.getDeclaringClass(), meth.getName(), SignatureUtil.toInternalSignature(meth.getParameterTypes()),
                        ConfigurationMemberInfo.ConfigurationMemberDeclaration.DECLARED);
    }
}
