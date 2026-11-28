/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replaycomp;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaField;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.TriState;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.riscv64.RISCV64;

/**
 * Helpers for handling {@link Class} and {@link EnumSet} values used by the replay codecs.
 */
final class ReplayTypeSupport {
    /**
     * Placeholder used when a replay file refers to a {@link Class} that is not materialized from
     * the known-class table.
     * <p>
     * The surrogate can still participate in argument matching for recorded operations because its
     * equality compares the recorded class name against a local {@link Class} object.
     *
     * @param name the name of the class as returned by {@link Class#getName()}
     */
    record ClassSurrogate(String name) {
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Class<?> clazz) {
                return name.equals(clazz.getName());
            }
            return false;
        }
    }

    /**
     * Represents an unknown {@link Enum} type. Since the type system does not allow creating an
     * {@link EnumSet} without specifying the exact enum type, we can use this type as a generic
     * type parameter (which is erased anyway).
     */
    enum UnknownEnum {
    }

    /**
     * Class constants mapped by name used during deserialization. Classes not present in this map
     * are deserialized as {@link ClassSurrogate}.
     */
    private static final Map<String, Class<?>> KNOWN_CLASSES = createKnownClasses();

    private ReplayTypeSupport() {
    }

    /**
     * Builds the lookup table of class objects that can be reconstructed directly from replay data.
     *
     * @return the known classes keyed by their binary names
     */
    private static Map<String, Class<?>> createKnownClasses() {
        Class<?>[] classes = {
                        // Needed to deserialize enum constants
                        AMD64.CPUFeature.class, AArch64.CPUFeature.class, RISCV64.CPUFeature.class, UnknownEnum.class,
                        ConstantPool.Tag.class, DeoptimizationReason.class, JavaKind.class, MethodHandleAccessProvider.IntrinsicMethod.class,
                        // Needed to deserialize array component types
                        HotSpotResolvedJavaMethod.class, HotSpotResolvedJavaField.class, HotSpotResolvedObjectType.class,
                        Object.class, ExceptionHandler.class, TriState.class, AllocatableValue.class, Value.class,
                        ResolvedJavaMethod.class,
                        // Needed to deserialize Field objects
                        String.class,
        };
        Map<String, Class<?>> map = new EconomicHashMap<>();
        for (Class<?> clazz : classes) {
            map.put(clazz.getName(), clazz);
        }
        return map;
    }

    /**
     * Decodes a serialized class name to either a {@link Class} object or a {@link ClassSurrogate}.
     *
     * @param name the serialized class name
     * @return the resolved {@link Class} object or a {@link ClassSurrogate} if the class cannot be
     *         materialized directly
     */
    static Object decodeClass(String name) {
        Class<?> primitiveClass = Class.forPrimitiveName(name);
        if (primitiveClass != null) {
            return primitiveClass;
        }
        Class<?> knownClass = KNOWN_CLASSES.get(name);
        if (knownClass != null) {
            return knownClass;
        }
        return new ClassSurrogate(name);
    }

    /**
     * Casts a deserialized object to a {@link Class}.
     *
     * @param clazz the deserialized class object or surrogate
     * @return the class object cast to the requested type
     */
    @SuppressWarnings("unchecked")
    static <C> Class<C> classCast(Object clazz) {
        if (clazz instanceof ClassSurrogate(String name)) {
            throw new GraalError(String.format("Failed to find a Class object for %s. This can be fixed by adding %s.class to ReplayTypeSupport#KNOWN_CLASSES.", name, name));
        }
        return (Class<C>) clazz;
    }

    /**
     * Determines the enum element type that should be recorded for an {@link EnumSet}.
     *
     * @param enumSet the enum set whose element type should be determined
     * @return the element type to serialize for the enum set
     */
    static Class<?> enumSetElementType(EnumSet<?> enumSet) {
        Optional<?> maybeElementType = enumSet.stream().findAny();
        if (maybeElementType.isPresent()) {
            return ((Enum<?>) maybeElementType.get()).getDeclaringClass();
        }
        maybeElementType = EnumSet.complementOf(enumSet).stream().findAny();
        if (maybeElementType.isPresent()) {
            return ((Enum<?>) maybeElementType.get()).getDeclaringClass();
        }
        return UnknownEnum.class;
    }

    /**
     * Reconstructs an {@link EnumSet} from serialized enum ordinals.
     *
     * @param clazz the enum class of the set elements
     * @param ordinals the serialized ordinals to populate
     * @return the reconstructed enum set
     */
    static <E extends Enum<E>> EnumSet<E> asEnumSet(Class<E> clazz, Collection<Object> ordinals) {
        EnumSet<E> enumSet = EnumSet.noneOf(clazz);
        E[] enumConstants = clazz.getEnumConstants();
        for (Object ordinal : ordinals) {
            enumSet.add(enumConstants[((Number) ordinal).intValue()]);
        }
        return enumSet;
    }
}
