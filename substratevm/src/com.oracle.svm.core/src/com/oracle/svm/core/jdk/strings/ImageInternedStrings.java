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
package com.oracle.svm.core.jdk.strings;

import com.oracle.svm.core.BuildPhaseProvider.AfterHeapLayout;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.MultiLayer;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.util.Arrays;
import java.util.function.Consumer;

@AutomaticallyRegisteredImageSingleton
@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = MultiLayer.class)
public class ImageInternedStrings {

    /**
     * The native image contains a lot of interned strings. All Java String literals, and all class
     * names, are interned per Java specification. We don't want the memory overhead of a hash table
     * entry, so we store them (sorted) in this String[] array. When a string is interned at run
     * time, it is added to the real hash map, so we pay the (logarithmic) cost of the array access
     * only once per string.
     * <p>
     * The field is set late during image generation, so the value is not available during static
     * analysis and compilation.
     */
    @UnknownObjectField(availability = AfterHeapLayout.class) private String[] internedStringTable;

    @Platforms(Platform.HOSTED_ONLY.class)
    protected void setImageInternedStrings(String[] newImageInternedStrings) {
        assert internedStringTable == null : "Container objects already set";
        String[] sortedImageInternedStrings = Arrays.copyOf(newImageInternedStrings, newImageInternedStrings.length);
        Arrays.sort(sortedImageInternedStrings);
        this.internedStringTable = sortedImageInternedStrings;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void forEachContainerClass(Consumer<Class<?>> consumer) {
        consumer.accept(String[].class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void forEachContainerObject(Consumer<Object> consumer) {
        assert internedStringTable != null : "Container objects not set yet";
        consumer.accept(internedStringTable);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public Object getContainerRoot() {
        assert internedStringTable != null : "Container root not set yet";
        return internedStringTable;
    }

    public String find(String string) {
        int imageIdx = Arrays.binarySearch(internedStringTable, string);
        if (imageIdx >= 0) {
            return internedStringTable[imageIdx];
        }
        return null;
    }
}
