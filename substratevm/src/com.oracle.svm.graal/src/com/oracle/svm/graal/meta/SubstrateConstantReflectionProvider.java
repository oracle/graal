/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.meta;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.graal.meta.SharedConstantReflectionProvider;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.snippets.KnownIntrinsics;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public class SubstrateConstantReflectionProvider extends SharedConstantReflectionProvider {
    private final MetaAccessProvider metaAccess;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateConstantReflectionProvider(SubstrateMetaAccess metaAccess) {
        this.metaAccess = metaAccess;
    }

    @Override
    public MemoryAccessProvider getMemoryAccessProvider() {
        return SubstrateMemoryAccessProviderImpl.SINGLETON;
    }

    @Override
    public ResolvedJavaType asJavaType(Constant constant) {
        if (constant instanceof SubstrateObjectConstant) {
            Object obj = KnownIntrinsics.convertUnknownValue(SubstrateObjectConstant.asObject(constant), Object.class);
            if (obj instanceof DynamicHub) {
                return ((SubstrateMetaAccess) metaAccess).lookupJavaTypeFromHub(((DynamicHub) obj));
            }
        }
        return null;
    }

    @Override
    public JavaConstant asJavaClass(ResolvedJavaType type) {
        return SubstrateObjectConstant.forObject(((SubstrateType) type).getHub());
    }

    @Override
    public JavaConstant readFieldValue(ResolvedJavaField field, JavaConstant receiver) {
        return readFieldValue((SubstrateField) field, receiver);
    }

    @Override
    public JavaConstant boxPrimitive(JavaConstant source) {
        if (!canBoxPrimitive(source)) {
            return null;
        }
        return super.boxPrimitive(source);
    }

    protected boolean canBoxPrimitive(JavaConstant source) {
        boolean result = source.getJavaKind().isPrimitive() && isCachedPrimitive(source);
        assert !result || source.asBoxedPrimitive() == source.asBoxedPrimitive() : "value must be cached";
        return result;
    }

    /**
     * Check if the constant is a boxed value that is guaranteed to be cached by the platform.
     * Otherwise the generated code might be the only reference to the boxed value and since object
     * references from code are weak this can cause invalidation problems.
     */
    private static boolean isCachedPrimitive(JavaConstant source) {
        switch (source.getJavaKind()) {
            case Boolean:
                return true;
            case Char:
                return source.asInt() <= 127;
            case Byte:
            case Short:
                return source.asInt() >= -128 && source.asInt() <= 127;
            case Int:
                return source.asInt() >= -128 && source.asInt() <= Target_java_lang_Integer_IntegerCache.high;
            case Long:
                return source.asLong() >= -128 && source.asLong() <= 127;
            case Float:
            case Double:
                return false;
            default:
                throw new IllegalArgumentException("unexpected kind " + source.getJavaKind());
        }
    }

    public static JavaConstant readFieldValue(SubstrateField field, JavaConstant receiver) {
        if (field.constantValue != null) {
            return field.constantValue;
        }
        if (field.location < 0) {
            return null;
        }

        JavaConstant base;
        if (receiver == null) {
            assert field.isStatic();
            if (field.type.getStorageKind() == JavaKind.Object) {
                base = SubstrateObjectConstant.forObject(StaticFieldsSupport.getStaticObjectFields());
            } else {
                base = SubstrateObjectConstant.forObject(StaticFieldsSupport.getStaticPrimitiveFields());
            }
        } else {
            assert !field.isStatic();
            base = receiver;
        }

        assert SubstrateObjectConstant.asObject(base) != null;
        try {
            return SubstrateMemoryAccessProviderImpl.readUnsafeConstant(field.type.getStorageKind(), base, field.location, field.isVolatile());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

@TargetClass(className = "java.lang.Integer$IntegerCache")
final class Target_java_lang_Integer_IntegerCache {
    @Alias //
    static int high;
}
