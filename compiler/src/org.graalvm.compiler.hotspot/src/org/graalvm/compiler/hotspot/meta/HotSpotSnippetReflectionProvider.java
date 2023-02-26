/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.meta;

import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.SnippetObjectConstant;
import org.graalvm.compiler.word.WordTypes;

import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.services.Services;

public class HotSpotSnippetReflectionProvider implements SnippetReflectionProvider {

    private final HotSpotGraalRuntimeProvider runtime;
    private final HotSpotConstantReflectionProvider constantReflection;
    private final WordTypes wordTypes;

    /*
     * GR-41976: JVMCI currently does not have public API to convert methods and fields back to
     * reflection objects. So we do it via reflective invocation of JVMCI internals.
     *
     * These fields are intentionally not static, because we do not want libgraal to run with the
     * state initialized at image build time.
     */
    private final Method hotSpotJDKReflectionGetMethod;
    private final Method hotSpotJDKReflectionGetField;

    public HotSpotSnippetReflectionProvider(HotSpotGraalRuntimeProvider runtime, HotSpotConstantReflectionProvider constantReflection, WordTypes wordTypes) {
        this.runtime = runtime;
        this.constantReflection = constantReflection;
        this.wordTypes = wordTypes;

        if (Services.IS_IN_NATIVE_IMAGE) {
            /* No access to method/field mirrors when running in libgraal. */
            hotSpotJDKReflectionGetMethod = null;
            hotSpotJDKReflectionGetField = null;
        } else {
            try {
                Class<?> hsJDKReflection = Class.forName("jdk.vm.ci.hotspot.HotSpotJDKReflection");
                hotSpotJDKReflectionGetMethod = lookupMethod(hsJDKReflection, "getMethod", Class.forName("jdk.vm.ci.hotspot.HotSpotResolvedJavaMethodImpl"));
                hotSpotJDKReflectionGetField = lookupMethod(hsJDKReflection, "getField", Class.forName("jdk.vm.ci.hotspot.HotSpotResolvedJavaFieldImpl"));
            } catch (ReflectiveOperationException ex) {
                /*
                 * Note that older JVMCI versions do not have those methods even when running in JDK
                 * mode and not in libgraal mode. But that affects only OpenJDK 11, and we no longer
                 * support JDK 11 at all. OpenJDK 17 already has the necessary methods.
                 */
                throw GraalError.shouldNotReachHere(ex);
            }
        }
    }

    @Override
    public JavaConstant forObject(Object object) {
        return constantReflection.forObject(object);
    }

    @Override
    public <T> T asObject(Class<T> type, JavaConstant constant) {
        if (constant.isNull()) {
            return null;
        }
        if (constant instanceof HotSpotObjectConstant) {
            HotSpotObjectConstant hsConstant = (HotSpotObjectConstant) constant;
            return hsConstant.asObject(type);
        }
        if (constant instanceof SnippetObjectConstant) {
            SnippetObjectConstant snippetObject = (SnippetObjectConstant) constant;
            return snippetObject.asObject(type);
        }
        return null;
    }

    @Override
    public JavaConstant forBoxed(JavaKind kind, Object value) {
        if (kind == JavaKind.Object) {
            return forObject(value);
        } else {
            return JavaConstant.forBoxedPrimitive(value);
        }
    }

    // Lazily initialized
    private Class<?> wordTypesType;
    private Class<?> runtimeType;
    private Class<?> configType;

    @Override
    public <T> T getInjectedNodeIntrinsicParameter(Class<T> type) {
        // Need to test all fields since there no guarantee under the JMM
        // about the order in which these fields are written.
        GraalHotSpotVMConfig config = runtime.getVMConfig();
        if (configType == null || wordTypesType == null || runtimeType == null) {
            wordTypesType = wordTypes.getClass();
            runtimeType = runtime.getClass();
            configType = config.getClass();
        }

        if (type.isAssignableFrom(wordTypesType)) {
            return type.cast(wordTypes);
        }
        if (type.isAssignableFrom(runtimeType)) {
            return type.cast(runtime);
        }
        if (type.isAssignableFrom(configType)) {
            return type.cast(config);
        }
        return null;
    }

    @Override
    public Class<?> originalClass(ResolvedJavaType type) {
        return runtime().getMirror(type);
    }

    private static Method lookupMethod(Class<?> declaringClass, String methodName, Class<?>... parameterTypes) throws ReflectiveOperationException {
        Method result = declaringClass.getDeclaredMethod(methodName, parameterTypes);
        result.setAccessible(true);
        return result;
    }

    @Override
    public Executable originalMethod(ResolvedJavaMethod method) {
        if (method.isClassInitializer()) {
            /* <clinit> methods never have a corresponding java.lang.reflect.Method. */
            return null;
        }

        if (hotSpotJDKReflectionGetMethod == null) {
            return null;
        }
        try {
            return (Executable) hotSpotJDKReflectionGetMethod.invoke(null, method);
        } catch (ReflectiveOperationException ex) {
            throw rethrow(ex.getCause());
        }
    }

    @Override
    public Field originalField(ResolvedJavaField field) {
        if (hotSpotJDKReflectionGetField == null) {
            return null;
        }
        try {
            return (Field) hotSpotJDKReflectionGetField.invoke(null, field);
        } catch (ReflectiveOperationException ex) {
            if (ex.getCause() instanceof IllegalArgumentException) {
                /**
                 * GR-41974: A bug in JVMCI prevents the lookup of the java.lang.reflect.Field.
                 * Since even calling getName() on the ResolvedJavaField crashes for such fields, we
                 * also cannot use Class.getDeclaredField as a workaround for lookup. Our only
                 * option is to return null for now.
                 */
                return null;
            }
            throw rethrow(ex.getCause());
        }
    }

    @SuppressWarnings({"unchecked"})
    private static <E extends Throwable> RuntimeException rethrow(Throwable ex) throws E {
        throw (E) ex;
    }
}
