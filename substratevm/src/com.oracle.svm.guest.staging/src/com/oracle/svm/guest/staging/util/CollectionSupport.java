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
package com.oracle.svm.guest.staging.util;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.shared.BuilderInvoked;

/** Guest-side collection iteration helpers for the image builder to invoke reflectively. */
@Platforms(Platform.HOSTED_ONLY.class)
public final class CollectionSupport {

    private CollectionSupport() {
    }

    /**
     * Delivers each immediate element of a guest collection.
     *
     * @param collection guest collection whose elements are visited
     * @param consumer receives each element, including {@code null}
     */
    @BuilderInvoked
    public static void forEachCollectionElement(Collection<?> collection, Consumer<Object> consumer) {
        collection.forEach(consumer);
    }

    /**
     * Delivers each immediate key and value pair in a guest map.
     *
     * @param map guest map whose entries are visited
     * @param consumer receives each entry's key and value, including {@code null}
     */
    @BuilderInvoked
    public static void forEachMapEntry(Map<?, ?> map, BiConsumer<Object, Object> consumer) {
        map.forEach(consumer);
    }

    /**
     * Delivers each immediate key and value pair in a guest {@link EconomicMap}.
     *
     * @param map guest map whose entries are visited
     * @param consumer receives each entry's key and value, including {@code null}
     */
    @BuilderInvoked
    public static void forEachEconomicMapEntry(EconomicMap<?, ?> map, BiConsumer<Object, Object> consumer) {
        MapCursor<?, ?> cursor = map.getEntries();
        while (cursor.advance()) {
            consumer.accept(cursor.getKey(), cursor.getValue());
        }
    }
}
