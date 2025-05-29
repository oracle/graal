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
import java.util.function.Function;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import com.oracle.graal.pointsto.infrastructure.OriginalFieldProvider;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.fieldvaluetransformer.ObjectToConstantFieldValueTransformer;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;

public class FieldValueTransformation {
    protected final FieldValueTransformer fieldValueTransformer;
    protected final Class<?> transformedValueAllowedType;

    private final EconomicMap<JavaConstant, JavaConstant> valueCache = EconomicMap.create();
    private final ReentrantReadWriteLock valueCacheLock = new ReentrantReadWriteLock();

    public FieldValueTransformation(Class<?> transformedValueAllowedType, FieldValueTransformer fieldValueTransformer) {
        this.fieldValueTransformer = fieldValueTransformer;
        this.transformedValueAllowedType = transformedValueAllowedType;
    }

    public FieldValueTransformer getFieldValueTransformer() {
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
        Object receiverValue = receiver == null ? null : GraalAccess.getOriginalSnippetReflection().asObject(Object.class, receiver);
        Object originalValue = fetchOriginalValue(field, receiver);

        Function<Object, JavaConstant> constantConverter = (obj) -> {
            checkValue(obj, field);
            return GraalAccess.getOriginalSnippetReflection().forBoxed(field.getJavaKind(), obj);
        };

        JavaConstant result;
        if (fieldValueTransformer instanceof ObjectToConstantFieldValueTransformer objectToConstantFieldValueTransformer) {
            result = objectToConstantFieldValueTransformer.transformToConstant(field, receiverValue, originalValue, constantConverter);
        } else {
            Object newValue = fieldValueTransformer.transform(receiverValue, originalValue);
            result = constantConverter.apply(newValue);
        }

        assert result.getJavaKind() == field.getJavaKind();
        return result;
    }

    private void checkValue(Object newValue, AnalysisField field) {
        boolean primitive = transformedValueAllowedType.isPrimitive();
        if (newValue == null) {
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
        Class<?> actualType = primitive ? SubstrateUtil.toUnboxedClass(newValue.getClass()) : newValue.getClass();
        if (!transformedValueAllowedType.isAssignableFrom(actualType)) {
            throw UserError.abort("Field value transformer returned value of type `%s` that is not assignable to declared type `%s` of %s",
                            actualType.getTypeName(), transformedValueAllowedType.getTypeName(), field.format("%H.%n"));
        }
    }

    protected Object fetchOriginalValue(AnalysisField aField, JavaConstant receiver) {
        ResolvedJavaField oField = OriginalFieldProvider.getOriginalField(aField);
        if (oField == null) {
            return null;
        }
        JavaConstant originalValueConstant = GraalAccess.getOriginalProviders().getConstantReflection().readFieldValue(oField, receiver);
        if (originalValueConstant == null) {
            /*
             * The class is still uninitialized, so static fields cannot be read. Or it is an
             * instance field in a substitution class, i.e., a field that does not exist in the
             * hosted object.
             */
            return null;
        } else if (originalValueConstant.getJavaKind().isPrimitive()) {
            return originalValueConstant.asBoxedPrimitive();
        } else {
            return GraalAccess.getOriginalSnippetReflection().asObject(Object.class, originalValueConstant);
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
