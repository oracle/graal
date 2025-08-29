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

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.graal.nodes.LoadImageSingletonNode;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredAllowNullEntries;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.util.VMError;

/**
 * {@link SingletonTrait} which describes how this singleton acts in layered builds.
 */
public record SingletonLayeredInstallationKind(InstallationKind kind, Object metadata) {
    public enum InstallationKind {
        /**
         * This singleton cannot be installed in a layered build.
         */
        DISALLOWED(EmptyMetadata.class),

        /**
         * A different version of this singleton can be installed in each layer. If a given layer X
         * generates compiled code referring to an independent singleton with key K, then it will
         * refer to the singleton installed in the layer X and the singleton will be installed in
         * layer X's image heap. If a later layer Y generates compiled code referring to the same
         * singleton key K, it will not be linked to the same singleton as installed in layer X, but
         * instead will refer to the singleton installed in layer Y that will be installed in layer
         * Y's image heap.
         */
        INDEPENDENT(EmptyMetadata.class),

        /**
         * This singleton can only be installed in the initial layer. All references to this
         * singleton in all code compiled across all layers refer to the singleton installed in the
         * base layer.
         */
        INITIAL_LAYER_ONLY(EmptyMetadata.class),

        /**
         * This singleton can only be installed in the app layer. All references to this singleton
         * in all code compiled across all layers refer to the singleton installed in the app layer.
         * <p>
         * When this trait is linked to a singleton, the singleton's key class is expected to match
         * the singleton's implementation key. In addition, a singleton with this trait can only be
         * installed in the application layer.
         * <p>
         * Referring to fields of an app layer singleton from a code compiled in a shared layer,
         * i.e., even before the value of the field can be known, is safe. This is because, instead
         * of being constant-folded in a shared layer, it is instead implemented via
         * {@link LoadImageSingletonNode} and lowered into a singleton table read.
         */
        APP_LAYER_ONLY(EmptyMetadata.class),

        /*
         * GR-66794: switch from using interfaces to traits for implementing the following
         * behaviors.
         */

        /**
         * A different version of this singleton can be installed in each layer. For these
         * singletons, {@link ImageSingletons#lookup} should no longer be used. Instead, there is
         * the method {@link MultiLayeredImageSingleton#getAllLayers} which returns an array with
         * the image singletons corresponding to this key for all layers. The length of this array
         * will always be the total number of layers. If a singleton corresponding to this key was
         * not installed in a given layer (and this is allowed), then the array will contain null
         * for the given index. See {@link MultiLayeredAllowNullEntries} for more details. Within
         * the array, the singletons will be arranged so that index [0] corresponds to the singleton
         * originating from the initial layer and index [length - 1] holds the singleton from the
         * application layer. See {@link ImageLayerBuildingSupport} for a description of the
         * different layer names.
         *
         * <p>
         * Calling {@link MultiLayeredImageSingleton#getAllLayers} during a traditional build
         * requires the singleton to be installed in the build and will return an array of length 1
         * * containing that singleton.
         */
        MULTI_LAYER(EmptyMetadata.class);

        InstallationKind(Class<?> metadataClass) {
            this.metadataClass = metadataClass;
        }

        private final Class<?> metadataClass;
    }

    public SingletonLayeredInstallationKind(InstallationKind kind, Object metadata) {
        this.kind = kind;
        this.metadata = metadata;
        VMError.guarantee(kind.metadataClass.isInstance(metadata));
    }

    public static InstallationKind getInstallationKind(SingletonTrait trait) {
        VMError.guarantee(trait.kind() == SingletonTraitKind.LAYERED_INSTALLATION_KIND);
        return ((SingletonLayeredInstallationKind) trait.metadata()).kind;
    }

    static final SingletonTrait DISALLOWED_TRAIT = new SingletonTrait(SingletonTraitKind.LAYERED_INSTALLATION_KIND,
                    new SingletonLayeredInstallationKind(InstallationKind.DISALLOWED, EmptyMetadata.EMPTY));

    public static final class Disallowed extends SingletonLayeredInstallationKindSupplier {
        @Override
        public SingletonTrait getLayeredInstallationKindTrait() {
            return DISALLOWED_TRAIT;
        }
    }

    static final SingletonTrait INDEPENDENT_TRAIT = new SingletonTrait(SingletonTraitKind.LAYERED_INSTALLATION_KIND,
                    new SingletonLayeredInstallationKind(InstallationKind.INDEPENDENT, EmptyMetadata.EMPTY));

    public static final class Independent extends SingletonLayeredInstallationKindSupplier {
        @Override
        public SingletonTrait getLayeredInstallationKindTrait() {
            return INDEPENDENT_TRAIT;
        }
    }

    public static final SingletonTrait INITIAL_LAYER_ONLY = new SingletonTrait(SingletonTraitKind.LAYERED_INSTALLATION_KIND,
                    new SingletonLayeredInstallationKind(InstallationKind.INITIAL_LAYER_ONLY, EmptyMetadata.EMPTY));

    public static final class InitialLayerOnly extends SingletonLayeredInstallationKindSupplier {
        @Override
        public SingletonTrait getLayeredInstallationKindTrait() {
            return INITIAL_LAYER_ONLY;
        }
    }

    public static final SingletonTrait APP_LAYER_ONLY = new SingletonTrait(SingletonTraitKind.LAYERED_INSTALLATION_KIND,
                    new SingletonLayeredInstallationKind(InstallationKind.APP_LAYER_ONLY, EmptyMetadata.EMPTY));

    public static final class ApplicationLayerOnly extends SingletonLayeredInstallationKindSupplier {
        @Override
        public SingletonTrait getLayeredInstallationKindTrait() {
            return APP_LAYER_ONLY;
        }
    }
}
