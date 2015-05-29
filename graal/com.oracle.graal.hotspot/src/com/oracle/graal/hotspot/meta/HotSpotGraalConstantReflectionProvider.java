/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.meta;

import com.oracle.jvmci.meta.ResolvedJavaField;
import com.oracle.jvmci.meta.MetaAccessProvider;
import com.oracle.jvmci.meta.JavaConstant;
import com.oracle.jvmci.meta.JavaField;
import com.oracle.jvmci.meta.ResolvedJavaMethod;
import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.hotspot.meta.HotSpotGraalConstantReflectionProvider.ImmutableCodeLazy.*;
import static com.oracle.graal.hotspot.stubs.SnippetStub.*;

import java.util.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.jvmci.common.*;
import com.oracle.jvmci.hotspot.*;

/**
 * Extends {@link HotSpotConstantReflectionProvider} to override the implementation of
 * {@link #readConstantFieldValue(JavaField, JavaConstant)} with Graal specific semantics.
 */
public class HotSpotGraalConstantReflectionProvider extends HotSpotConstantReflectionProvider {

    public HotSpotGraalConstantReflectionProvider(HotSpotJVMCIRuntimeProvider runtime) {
        super(runtime);
    }

    @Override
    public JavaConstant readConstantFieldValue(JavaField field, JavaConstant receiver) {
        assert !ImmutableCode.getValue() || isCalledForSnippets() || SnippetGraphUnderConstruction.get() != null || FieldReadEnabledInImmutableCode.get() == Boolean.TRUE : receiver;
        return super.readConstantFieldValue(field, receiver);
    }

    /**
     * In AOT mode, some fields should never be embedded even for snippets/replacements.
     */
    @Override
    protected boolean isStaticFieldConstant(HotSpotResolvedJavaField field) {
        return super.isStaticFieldConstant(field) && (!ImmutableCode.getValue() || ImmutableCodeLazy.isEmbeddable(field));

    }

    @SuppressWarnings("all")
    private static boolean assertionsEnabled() {
        boolean enabled = false;
        assert enabled = true;
        return enabled;
    }

    public static final ThreadLocal<Boolean> FieldReadEnabledInImmutableCode = assertionsEnabled() ? new ThreadLocal<>() : null;

    /**
     * Compares two {@link StackTraceElement}s for equality, ignoring differences in
     * {@linkplain StackTraceElement#getLineNumber() line number}.
     */
    private static boolean equalsIgnoringLine(StackTraceElement left, StackTraceElement right) {
        return left.getClassName().equals(right.getClassName()) && left.getMethodName().equals(right.getMethodName()) && left.getFileName().equals(right.getFileName());
    }

    @Override
    protected boolean isFinalInstanceFieldValueConstant(JavaConstant value, Class<?> receiverClass) {
        return super.isFinalInstanceFieldValueConstant(value, receiverClass) || receiverClass == SnippetCounter.class || receiverClass == NodeClass.class;
    }

    /**
     * {@inheritDoc}
     *
     * {@link HotSpotVMConfig} has a lot of zero-value fields which we know are stable and want to
     * be considered as constants.
     */
    @Override
    protected boolean isStableInstanceFieldValueConstant(JavaConstant value, Class<? extends Object> receiverClass) {
        return super.isStableInstanceFieldValueConstant(value, receiverClass) || receiverClass == HotSpotVMConfig.class;
    }

    /**
     * Separate out the static initialization of
     * {@linkplain #isEmbeddable(HotSpotResolvedJavaField) embeddable fields} to eliminate cycles
     * between clinit and other locks that could lead to deadlock. Static code that doesn't call
     * back into type or field machinery is probably ok but anything else should be made lazy.
     */
    static class ImmutableCodeLazy {

        /**
         * If the compiler is configured for AOT mode,
         * {@link #readConstantFieldValue(JavaField, JavaConstant)} should be only called for
         * snippets or replacements.
         */
        static boolean isCalledForSnippets() {
            assert ImmutableCode.getValue();
            MetaAccessProvider metaAccess = runtime().getHostProviders().getMetaAccess();
            ResolvedJavaMethod makeGraphMethod = null;
            ResolvedJavaMethod initMethod = null;
            try {
                Class<?> rjm = ResolvedJavaMethod.class;
                makeGraphMethod = metaAccess.lookupJavaMethod(ReplacementsImpl.class.getDeclaredMethod("makeGraph", rjm, Object[].class, rjm));
                initMethod = metaAccess.lookupJavaMethod(SnippetTemplate.AbstractTemplates.class.getDeclaredMethod("template", Arguments.class));
            } catch (NoSuchMethodException | SecurityException e) {
                throw new JVMCIError(e);
            }
            StackTraceElement makeGraphSTE = makeGraphMethod.asStackTraceElement(0);
            StackTraceElement initSTE = initMethod.asStackTraceElement(0);

            StackTraceElement[] stackTrace = new Exception().getStackTrace();
            for (StackTraceElement element : stackTrace) {
                // Ignoring line numbers should not weaken this check too much while at
                // the same time making it more robust against source code changes
                if (equalsIgnoringLine(makeGraphSTE, element) || equalsIgnoringLine(initSTE, element)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Determine if it's ok to embed the value of {@code field}.
         */
        static boolean isEmbeddable(HotSpotResolvedJavaField field) {
            assert ImmutableCode.getValue();
            return !embeddableFields.contains(field);
        }

        private static final List<ResolvedJavaField> embeddableFields = new ArrayList<>();
        static {
            try {
                MetaAccessProvider metaAccess = runtime().getHostProviders().getMetaAccess();
                embeddableFields.add(metaAccess.lookupJavaField(Boolean.class.getDeclaredField("TRUE")));
                embeddableFields.add(metaAccess.lookupJavaField(Boolean.class.getDeclaredField("FALSE")));

                Class<?> characterCacheClass = Character.class.getDeclaredClasses()[0];
                assert "java.lang.Character$CharacterCache".equals(characterCacheClass.getName());
                embeddableFields.add(metaAccess.lookupJavaField(characterCacheClass.getDeclaredField("cache")));

                Class<?> byteCacheClass = Byte.class.getDeclaredClasses()[0];
                assert "java.lang.Byte$ByteCache".equals(byteCacheClass.getName());
                embeddableFields.add(metaAccess.lookupJavaField(byteCacheClass.getDeclaredField("cache")));

                Class<?> shortCacheClass = Short.class.getDeclaredClasses()[0];
                assert "java.lang.Short$ShortCache".equals(shortCacheClass.getName());
                embeddableFields.add(metaAccess.lookupJavaField(shortCacheClass.getDeclaredField("cache")));

                Class<?> integerCacheClass = Integer.class.getDeclaredClasses()[0];
                assert "java.lang.Integer$IntegerCache".equals(integerCacheClass.getName());
                embeddableFields.add(metaAccess.lookupJavaField(integerCacheClass.getDeclaredField("cache")));

                Class<?> longCacheClass = Long.class.getDeclaredClasses()[0];
                assert "java.lang.Long$LongCache".equals(longCacheClass.getName());
                embeddableFields.add(metaAccess.lookupJavaField(longCacheClass.getDeclaredField("cache")));

                embeddableFields.add(metaAccess.lookupJavaField(Throwable.class.getDeclaredField("UNASSIGNED_STACK")));
                embeddableFields.add(metaAccess.lookupJavaField(Throwable.class.getDeclaredField("SUPPRESSED_SENTINEL")));
            } catch (SecurityException | NoSuchFieldException e) {
                throw new JVMCIError(e);
            }
        }
    }

}
