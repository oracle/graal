/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import com.oracle.graal.pointsto.heap.TypedConstant;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.svm.core.fieldvaluetransformer.JavaConstantWrapper;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ameta.FieldValueInterceptionSupport;
import com.oracle.svm.util.ClassUtil;
import com.oracle.svm.util.GraalAccess;
import com.oracle.svm.util.JVMCIFieldValueTransformer;
import com.oracle.svm.util.JVMCIReflectionUtil;
import com.oracle.svm.util.OriginalClassProvider;
import com.oracle.svm.util.OriginalFieldProvider;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public class FieldValueTransformation {
    protected final JVMCIFieldValueTransformer fieldValueTransformer;
    protected final ResolvedJavaType transformedValueAllowedType;

    private final EconomicMap<JavaConstant, JavaConstant> valueCache = EconomicMap.create();
    private final ReentrantReadWriteLock valueCacheLock = new ReentrantReadWriteLock();

    public FieldValueTransformation(ResolvedJavaType transformedValueAllowedType, JVMCIFieldValueTransformer fieldValueTransformer) {
        this.fieldValueTransformer = fieldValueTransformer;
        this.transformedValueAllowedType = transformedValueAllowedType;
    }

    public JVMCIFieldValueTransformer getFieldValueTransformer() {
        return fieldValueTransformer;
    }

    public JavaConstant readValue(AnalysisField field, JavaConstant receiver) {
        ReadLock readLock = valueCacheLock.readLock();
        try {
            readLock.lock();
            JavaConstant result = getCached(receiver);
            if (result != null) {
                return result;
            }
        } finally {
            readLock.unlock();
        }

        WriteLock writeLock = valueCacheLock.writeLock();
        try {
            writeLock.lock();
            /*
             * Check the cache again, now that we are holding the write-lock, i.e., we know that no
             * other thread is computing a value right now.
             */
            JavaConstant result = getCached(receiver);
            if (result != null) {
                return result;
            }
            /*
             * Note that the value computation must be inside the lock, because we want to guarantee
             * that field-value computers are only executed once per unique receiver.
             */
            result = computeValue(field, receiver);
            putCached(receiver, result);
            return result;
        } finally {
            writeLock.unlock();
        }
    }

    private JavaConstant computeValue(AnalysisField field, JavaConstant receiver) {
        var originalValue = fetchOriginalValue(field, receiver);

        VMError.guarantee(originalValue != null, "Original value must not be `null`. Use `JavaConstant.NULL_POINTER`instead");
        VMError.guarantee(receiver == null || !receiver.isNull(), "Receiver should not be a boxed `null` (`JavaConstant.isNull()`) for static fields. Use `null`instead");
        JavaConstant result = getUnboxedConstant(fieldValueTransformer.transform(receiver, originalValue));
        checkValue(result, field);

        assert result.getJavaKind() == field.getJavaKind() : result.getJavaKind() + " vs " + field.getJavaKind();
        return result;
    }

    private void checkValue(JavaConstant newValue, AnalysisField field) {
        if (newValue == null) {
            throw UserError.abort("JVMCIFieldValueTransformer must not return `null`. Use `JavaConstant.NULL_POINTER`instead");
        }
        boolean primitive = transformedValueAllowedType.isPrimitive();
        if (newValue.isNull()) {
            if (primitive) {
                throw UserError.abort("Field value transformer returned null for primitive %s", field.format("%H.%n"));
            } else {
                /* Null is always allowed for reference fields. */
                return;
            }
        }
        /*
         * The compute/transform methods autobox primitive values. We unbox them here, but only if
         * the original field is primitive.
         */
        ResolvedJavaType actualType = getActualType(newValue);
        VMError.guarantee(actualType != null);
        if (actualType.equals(GraalAccess.lookupType(JavaConstantWrapper.class))) {
            throw UserError.abort("Field value transformer %s returned %s value instead of returning the JavaConstant directly.",
                            fieldValueTransformer.getClass().getName(), JavaConstantWrapper.class.getSimpleName(), JVMCIReflectionUtil.getTypeName(transformedValueAllowedType), field.format("%H.%n"));
        }
        if (!transformedValueAllowedType.isAssignableFrom(actualType)) {
            throw UserError.abort("Field value transformer returned value of type `%s` that is not assignable to declared type `%s` of %s",
                            JVMCIReflectionUtil.getTypeName(actualType), JVMCIReflectionUtil.getTypeName(transformedValueAllowedType), field.format("%H.%n"));
        }
    }

    /**
     * Gets the {@linkplain OriginalClassProvider#getOriginalType(JavaType) original} type of a
     * constant.
     */
    private static ResolvedJavaType getActualType(JavaConstant newValue) {
        if (newValue.getJavaKind().isPrimitive()) {
            VMError.guarantee(newValue.getJavaKind().isPrimitive());
            return GraalAccess.lookupType(newValue.getJavaKind().toJavaClass());
        }
        if (newValue instanceof TypedConstant typedConstant) {
            return OriginalClassProvider.getOriginalType(typedConstant.getType());
        }
        return GraalAccess.getOriginalProviders().getMetaAccess().lookupJavaType(newValue);
    }

    private JavaConstant getUnboxedConstant(JavaConstant newValue) {
        if (transformedValueAllowedType.isPrimitive() && !newValue.getJavaKind().isPrimitive()) {
            if (fieldValueTransformer instanceof FieldValueTransformer || fieldValueTransformer instanceof FieldValueInterceptionSupport.WrappedFieldValueTransformer) {
                /* Legacy transformer that needs to return boxed values. Try to unbox. */
                JavaConstant unboxed = GraalAccess.getOriginalProviders().getConstantReflection().unboxPrimitive(newValue);
                if (unboxed != null) {
                    return unboxed;
                }
            }
            throw VMError.shouldNotReachHere("Type %s is primitive but new value not %s (transformer: %s)",
                            JVMCIReflectionUtil.getTypeName(transformedValueAllowedType),
                            JVMCIReflectionUtil.getTypeName(GraalAccess.getOriginalProviders().getMetaAccess().lookupJavaType(newValue)),
                            ClassUtil.getUnqualifiedName(fieldValueTransformer.getClass()));
        }
        return newValue;
    }

    protected JavaConstant fetchOriginalValue(AnalysisField aField, JavaConstant receiver) {
        ResolvedJavaField oField = OriginalFieldProvider.getOriginalField(aField);
        if (oField == null) {
            return JavaConstant.NULL_POINTER;
        }
        JavaConstant originalValueConstant = GraalAccess.getOriginalProviders().getConstantReflection().readFieldValue(oField, receiver);
        if (originalValueConstant == null) {
            /*
             * The class is still uninitialized, so static fields cannot be read. Or it is an
             * instance field in a substitution class, i.e., a field that does not exist in the
             * hosted object.
             */
            return JavaConstant.NULL_POINTER;
        } else {
            return originalValueConstant;
        }
    }

    private void putCached(JavaConstant receiver, JavaConstant result) {
        JavaConstant key = receiver == null ? JavaConstant.NULL_POINTER : receiver;
        valueCache.put(key, result);
    }

    private JavaConstant getCached(JavaConstant receiver) {
        JavaConstant key = receiver == null ? JavaConstant.NULL_POINTER : receiver;
        return valueCache.get(key);
    }
}
