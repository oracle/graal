/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.meta;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredPersistFlags;
import com.oracle.svm.core.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.core.traits.SingletonLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.core.traits.SingletonTrait;
import com.oracle.svm.core.traits.SingletonTraitKind;
import com.oracle.svm.core.traits.SingletonTraits;

/**
 * Tracks fields with constant values which could be inlined, but which must exist in memory -- for
 * example, when they might be accessed via JNI.
 */
@AutomaticallyRegisteredFeature
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = MaterializedConstantFields.LayeredCallbacks.class, layeredInstallationKind = Independent.class)
public class MaterializedConstantFields implements InternalFeature {
    private final Set<Integer> fields = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private boolean sealed = false;

    public static MaterializedConstantFields singleton() {
        return ImageSingletons.lookup(MaterializedConstantFields.class);
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        sealed = true;
    }

    public void register(AnalysisField field) {
        assert field.isStatic() : "Only required for static final fields: " + field;
        assert field.isAccessed() : "Field must be accessed as read: " + field;
        assert !sealed : "Already sealed: " + field;
        fields.add(field.getId());
    }

    public boolean contains(AnalysisField field) {
        if (field.isStatic()) {
            return fields.contains(field.getId());
        }
        return false;
    }

    public static class LayeredCallbacks extends SingletonLayeredCallbacksSupplier {
        private static final String FIELDS = "fields";

        @Override
        public SingletonTrait getLayeredCallbacksTrait() {
            var action = new SingletonLayeredCallbacks<MaterializedConstantFields>() {
                @Override
                public LayeredPersistFlags doPersist(ImageSingletonWriter writer, MaterializedConstantFields singleton) {
                    writer.writeIntList(FIELDS, singleton.fields.stream().toList());
                    return LayeredPersistFlags.CALLBACK_ON_REGISTRATION;
                }

                @Override
                public void onSingletonRegistration(ImageSingletonLoader loader, MaterializedConstantFields singleton) {
                    singleton.fields.addAll(loader.readIntList(FIELDS));
                }
            };
            return new SingletonTrait(SingletonTraitKind.LAYERED_CALLBACKS, action);
        }
    }
}
