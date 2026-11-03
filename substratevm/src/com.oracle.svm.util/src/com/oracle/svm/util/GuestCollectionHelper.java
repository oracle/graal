/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.util;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.shared.util.VMError;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/** Builder-side iteration over immediate elements of guest collection-like objects. */
@Platforms(Platform.HOSTED_ONLY.class)
public final class GuestCollectionHelper {

    private static final String COLLECTION_SUPPORT_CLASS_NAME = "com.oracle.svm.guest.staging.util.CollectionSupport";

    private GuestCollectionHelper() {
    }

    /**
     * Visits the immediate elements of a guest reference array.
     *
     * @param array guest reference array constant to inspect; null is ignored
     * @param action receives each element as a guest object constant, including guest nulls
     */
    public static void forEachArrayElement(@GuestType(Object[].class) JavaConstant array, Consumer<JavaConstant> action) {
        if (array == null || array.isNull()) {
            return;
        }

        ConstantReflectionProvider constantReflection = GuestAccess.get().getProviders().getConstantReflection();
        Integer length = constantReflection.readArrayLength(array);
        VMError.guarantee(length != null, "Unable to read guest object array length");
        for (int i = 0; i < length; i++) {
            JavaConstant element = constantReflection.readArrayElement(array, i);
            VMError.guarantee(element != null, "Unable to read guest object array element %s", i);
            action.accept(element);
        }
    }

    /** Visits the immediate elements of a guest {@link Collection}. */
    public static void forEachCollectionElement(@GuestType(Collection.class) JavaConstant collection, Consumer<JavaConstant> action) {
        if (collection == null || collection.isNull()) {
            return;
        }
        GuestAccess access = GuestAccess.get();
        ResolvedJavaMethod method = access.lookupMethod(access.lookupType(COLLECTION_SUPPORT_CLASS_NAME), "forEachCollectionElement", Collection.class, Consumer.class);
        JavaConstant proxy = access.createHostProxy(new ElementConsumer(action), access.lookupType(Consumer.class));
        access.invokeStatic(method, collection, proxy);
    }

    /** Visits key/value pairs of a guest {@link Map}. */
    public static void forEachMapEntry(@GuestType(Map.class) JavaConstant map, BiConsumer<JavaConstant, JavaConstant> action) {
        if (map == null || map.isNull()) {
            return;
        }
        GuestAccess access = GuestAccess.get();
        ResolvedJavaMethod method = access.lookupMethod(access.lookupType(COLLECTION_SUPPORT_CLASS_NAME), "forEachMapEntry", Map.class, BiConsumer.class);
        JavaConstant proxy = access.createHostProxy(new EntryConsumer(action), access.lookupType(BiConsumer.class));
        access.invokeStatic(method, map, proxy);
    }

    /** Visits key/value pairs of a guest {@link EconomicMap}. */
    public static void forEachEconomicMapEntry(@GuestType(EconomicMap.class) JavaConstant map, BiConsumer<JavaConstant, JavaConstant> action) {
        if (map == null || map.isNull()) {
            return;
        }
        GuestAccess access = GuestAccess.get();
        ResolvedJavaMethod method = access.lookupMethod(access.lookupType(COLLECTION_SUPPORT_CLASS_NAME), "forEachEconomicMapEntry", EconomicMap.class, BiConsumer.class);
        JavaConstant proxy = access.createHostProxy(new EntryConsumer(action), access.lookupType(BiConsumer.class));
        access.invokeStatic(method, map, proxy);
    }

    /** Host proxy adapter for a guest {@link Consumer}. */
    public static final class ElementConsumer {
        private final Consumer<JavaConstant> action;

        public ElementConsumer(Consumer<JavaConstant> action) {
            this.action = action;
        }

        /**
         * Receives one guest object delivered through {@link Consumer#accept(Object)}.
         *
         * @param element guest object argument delivered by the guest {@link Consumer#accept(Object)} call
         */
        @GuestInvoked
        public void accept(@GuestType(Object.class) JavaConstant element) {
            action.accept(element);
        }
    }

    /** Host proxy adapter for a guest {@link BiConsumer}. */
    public static final class EntryConsumer {
        private final BiConsumer<JavaConstant, JavaConstant> action;

        public EntryConsumer(BiConsumer<JavaConstant, JavaConstant> action) {
            this.action = action;
        }

        /**
         * Receives one guest map key and value delivered through {@link BiConsumer#accept(Object, Object)}.
         *
         * @param key guest map key argument delivered by the guest {@link BiConsumer#accept(Object, Object)} call
         * @param value guest map value argument delivered by the guest {@link BiConsumer#accept(Object, Object)} call
         */
        @GuestInvoked
        public void accept(@GuestType(Object.class) JavaConstant key, @GuestType(Object.class) JavaConstant value) {
            action.accept(key, value);
        }
    }
}
