/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.api.meta.MetaUtil.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.phases.GraalOptions.*;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.options.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.Snippet.SnippetInliningPolicy;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;

/**
 * Represents a field in a HotSpot type.
 */
public class HotSpotResolvedJavaField extends CompilerObject implements ResolvedJavaField, LocationIdentity {

    // Must not conflict with any fields flags used by the VM - the assertion in the constructor
    // checks this assumption
    private static final int FIELD_INTERNAL_FLAG = 0x80000000;

    private static final long serialVersionUID = 7692985878836955683L;
    private final HotSpotResolvedObjectType holder;
    private final String name;
    private final JavaType type;
    private final int offset;
    private final int flags;
    private Constant constant;

    public HotSpotResolvedJavaField(HotSpotResolvedObjectType holder, String name, JavaType type, int offset, int flags, boolean internal) {
        assert (flags & FIELD_INTERNAL_FLAG) == 0;
        this.holder = holder;
        this.name = name;
        this.type = type;
        assert offset != -1;
        this.offset = offset;
        if (internal) {
            this.flags = flags | FIELD_INTERNAL_FLAG;
        } else {
            this.flags = flags;
        }
    }

    @Override
    public int getModifiers() {
        return flags & Modifier.fieldModifiers();
    }

    @Override
    public boolean isInternal() {
        return (flags & FIELD_INTERNAL_FLAG) != 0;
    }

    /**
     * Compares two {@link StackTraceElement}s for equality, ignoring differences in
     * {@linkplain StackTraceElement#getLineNumber() line number}.
     */
    private static boolean equalsIgnoringLine(StackTraceElement left, StackTraceElement right) {
        return left.getClassName().equals(right.getClassName()) && left.getMethodName().equals(right.getMethodName()) && left.getFileName().equals(right.getFileName());
    }

    /**
     * If the compiler is configured for AOT mode, {@link #readConstantValue(Constant)} should be
     * only called for snippets or replacements.
     */
    private static boolean isCalledForSnippets() {
        HotSpotRuntime runtime = graalRuntime().getRuntime();

        ResolvedJavaMethod makeGraphMethod = null;
        ResolvedJavaMethod initMethod = null;
        try {
            Class<?> rjm = ResolvedJavaMethod.class;
            makeGraphMethod = runtime.lookupJavaMethod(ReplacementsImpl.class.getDeclaredMethod("makeGraph", rjm, rjm, SnippetInliningPolicy.class));
            initMethod = runtime.lookupJavaMethod(SnippetTemplate.AbstractTemplates.class.getDeclaredMethod("template", Arguments.class));
        } catch (NoSuchMethodException | SecurityException e) {
            throw new GraalInternalError(e);
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

    private static final Set<ResolvedJavaField> notEmbeddable = new HashSet<>();

    private static void addResolvedToSet(Field field) {
        HotSpotRuntime runtime = graalRuntime().getRuntime();
        notEmbeddable.add(runtime.lookupJavaField(field));
    }

    static {
        try {
            addResolvedToSet(Boolean.class.getDeclaredField("TRUE"));
            addResolvedToSet(Boolean.class.getDeclaredField("FALSE"));

            Class<?> characterCacheClass = Character.class.getDeclaredClasses()[0];
            assert "java.lang.Character$CharacterCache".equals(characterCacheClass.getName());
            addResolvedToSet(characterCacheClass.getDeclaredField("cache"));

            Class<?> byteCacheClass = Byte.class.getDeclaredClasses()[0];
            assert "java.lang.Byte$ByteCache".equals(byteCacheClass.getName());
            addResolvedToSet(byteCacheClass.getDeclaredField("cache"));

            Class<?> shortCacheClass = Short.class.getDeclaredClasses()[0];
            assert "java.lang.Short$ShortCache".equals(shortCacheClass.getName());
            addResolvedToSet(shortCacheClass.getDeclaredField("cache"));

            Class<?> integerCacheClass = Integer.class.getDeclaredClasses()[0];
            assert "java.lang.Integer$IntegerCache".equals(integerCacheClass.getName());
            addResolvedToSet(integerCacheClass.getDeclaredField("cache"));

            Class<?> longCacheClass = Long.class.getDeclaredClasses()[0];
            assert "java.lang.Long$LongCache".equals(longCacheClass.getName());
            addResolvedToSet(longCacheClass.getDeclaredField("cache"));

            addResolvedToSet(Throwable.class.getDeclaredField("UNASSIGNED_STACK"));
            addResolvedToSet(Throwable.class.getDeclaredField("SUPPRESSED_SENTINEL"));
        } catch (SecurityException | NoSuchFieldException e) {
            throw new GraalInternalError(e);
        }
    }

    /**
     * in AOT mode, some fields should never be embedded even for snippets/replacements.
     */
    private boolean isEmbeddable() {
        if (AOTCompilation.getValue() && notEmbeddable.contains(this)) {
            return false;
        }
        return true;
    }

    private static final String SystemClassName = "Ljava/lang/System;";

    @Override
    public Constant readConstantValue(Constant receiver) {
        assert !AOTCompilation.getValue() || isCalledForSnippets() : receiver;

        if (receiver == null) {
            assert Modifier.isStatic(flags);
            if (constant == null) {
                if (holder.isInitialized() && !holder.getName().equals(SystemClassName) && isEmbeddable()) {
                    if (Modifier.isFinal(getModifiers())) {
                        constant = readValue(receiver);
                    }
                }
            }
            return constant;
        } else {
            /*
             * for non-static final fields, we must assume that they are only initialized if they
             * have a non-default value.
             */
            assert !Modifier.isStatic(flags);
            Object object = receiver.asObject();
            if (Modifier.isFinal(getModifiers())) {
                Constant value = readValue(receiver);
                if (assumeNonStaticFinalFieldsAsFinal(object.getClass()) || !value.isDefaultForKind()) {
                    return value;
                }
            } else {
                Class<?> clazz = object.getClass();
                if (StableOptionValue.class.isAssignableFrom(clazz)) {
                    StableOptionValue<?> option = (StableOptionValue<?>) object;
                    return Constant.forObject(option.getValue());
                }
            }
        }
        return null;
    }

    @Override
    public Constant readValue(Constant receiver) {
        if (receiver == null) {
            assert Modifier.isStatic(flags);
            if (holder.isInitialized()) {
                return graalRuntime().getRuntime().readUnsafeConstant(getKind(), holder.mirror(), offset, getKind() == Kind.Object);
            }
            return null;
        } else {
            assert !Modifier.isStatic(flags);
            return graalRuntime().getRuntime().readUnsafeConstant(getKind(), receiver.asObject(), offset, getKind() == Kind.Object);
        }
    }

    private static boolean assumeNonStaticFinalFieldsAsFinal(Class<?> clazz) {
        return clazz == SnippetCounter.class;
    }

    @Override
    public HotSpotResolvedObjectType getDeclaringClass() {
        return holder;
    }

    @Override
    public Kind getKind() {
        return getType().getKind();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public JavaType getType() {
        return type;
    }

    public int offset() {
        return offset;
    }

    @Override
    public String toString() {
        return format("HotSpotField<%H.%n %t:", this) + offset + ">";
    }

    @Override
    public boolean isSynthetic() {
        Field javaField = toJava();
        if (javaField != null) {
            return javaField.isSynthetic();
        }
        return false;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        Field javaField = toJava();
        if (javaField != null) {
            return javaField.getAnnotation(annotationClass);
        }
        return null;
    }

    private Field toJava() {
        try {
            return holder.mirror().getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }
}
