/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.traits;

import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;

/**
 * Commonly used {@link SingletonTrait}s.
 */
public class BuiltinTraits {

    /**
     * Trait indicating this singleton should only be accessed from code executed at runtime.
     */
    public static final SingletonTrait RUNTIME_ONLY = new SingletonTrait(SingletonTraitKind.ACCESS,
                    (SingletonAccess.Supplier) () -> LayeredImageSingletonBuilderFlags.RUNTIME_ACCESS_ONLY);

    public static final class RuntimeAccessOnly extends SingletonAccessSupplier {
        @Override
        public SingletonTrait getAccessTrait() {
            return RUNTIME_ONLY;
        }
    }

    /**
     * Trait indicating this singleton should only be accessed from the native image generator
     * process and not at runtime.
     */
    public static final SingletonTrait BUILDTIME_ONLY = new SingletonTrait(SingletonTraitKind.ACCESS,
                    (SingletonAccess.Supplier) () -> LayeredImageSingletonBuilderFlags.BUILDTIME_ACCESS_ONLY);

    public static final class BuildtimeAccessOnly extends SingletonAccessSupplier {
        @Override
        public SingletonTrait getAccessTrait() {
            return BUILDTIME_ONLY;
        }
    }

    /**
     * Trait indicating this singleton can be freely accessed both from the native image generator
     * process and at runtime.
     */
    public static final SingletonTrait ALL_ACCESS = new SingletonTrait(SingletonTraitKind.ACCESS,
                    (SingletonAccess.Supplier) () -> LayeredImageSingletonBuilderFlags.ALL_ACCESS);

    public static final class AllAccess extends SingletonAccessSupplier {
        @Override
        public SingletonTrait getAccessTrait() {
            return ALL_ACCESS;
        }
    }

    /**
     * Trait indicating this singleton has no special callbacks needed during layered builds.
     */
    public static final SingletonTrait NO_LAYERED_CALLBACKS = new SingletonTrait(SingletonTraitKind.LAYERED_CALLBACKS, new SingletonLayeredCallbacks() {
        @Override
        public LayeredImageSingleton.PersistFlags doPersist(ImageSingletonWriter writer, Object singleton) {
            return LayeredImageSingleton.PersistFlags.NOTHING;
        }
    });

    public static class NoLayeredCallbacks extends SingletonLayeredCallbacksSupplier {
        @Override
        public SingletonTrait getLayeredCallbacksTrait() {
            return NO_LAYERED_CALLBACKS;
        }
    }

    /**
     * Trait indicating nothing should be persisted for this singleton and that a singleton cannot
     * be linked to this key in a subsequent image layer. This limits the singleton to being
     * installed in a single layer.
     */
    public static final SingletonTrait SINGLE_LAYER = new SingletonTrait(SingletonTraitKind.LAYERED_CALLBACKS, new SingletonLayeredCallbacks() {
        @Override
        public LayeredImageSingleton.PersistFlags doPersist(ImageSingletonWriter writer, Object singleton) {
            return LayeredImageSingleton.PersistFlags.FORBIDDEN;
        }
    });

    public static class SingleLayer extends SingletonLayeredCallbacksSupplier {
        @Override
        public SingletonTrait getLayeredCallbacksTrait() {
            return SINGLE_LAYER;
        }
    }

    /**
     * Trait indicating this singleton can be made more layer-aware in the future. See
     * {@link SingletonTraitKind#DUPLICABLE} for more information.
     */
    public static final SingletonTrait DUPLICABLE_TRAIT = new SingletonTrait(SingletonTraitKind.DUPLICABLE, EmptyMetadata.EMPTY);

    @SuppressWarnings("unused")
    public static class Duplicable extends SingletonTraitsSupplier {
        @Override
        public SingletonTrait getTrait() {
            return DUPLICABLE_TRAIT;
        }
    }
}
