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

/**
 * Enumerates the different kinds of traits that can be associated with a singleton via
 * {@link SingletonTraits}. See {@link SingletonTraits} for more details.
 */
public enum SingletonTraitKind {

    /**
     * Describes when this singleton can be accessed (e.g., during the native image generator
     * process and/or from within the generated code at runtime).
     *
     * <p>
     * In cases where this SingletonTrait is not defined the singleton will have the equivalent
     * behavior as {@link BuiltinTraits#ALL_ACCESS}.
     */
    ACCESS(SingletonAccess.Supplier.class),

    /**
     * Links to layered-build specific callbacks for this singleton.
     *
     * <p>
     * In cases where this SingletonTrait is not defined then no layered-build specific callbacks
     * will be triggered for this singleton.
     */
    LAYERED_CALLBACKS(SingletonLayeredCallbacks.class),

    /**
     * Describes any special layered-specific behavior associated with this singleton. See
     * {@link SingletonLayeredInstallationKind} for more details about the various behaviors.
     *
     * <p>
     * In cases where this SingletonTrait is not defined the singleton will have the equivalent
     * behavior as {@link SingletonLayeredInstallationKind.Independent}.
     */
    LAYERED_INSTALLATION_KIND(SingletonLayeredInstallationKind.class),

    /**
     * Used as a marker to indicate this singleton's behavior should be made layer-aware in the
     * future.
     *
     * <p>
     * In detail, a duplicable singleton can have multiple instances of the object installed in the
     * Image Heap (at most one per a layer). The specific instance referred to from a given piece of
     * code is dependent on the layer in which the code was installed in.
     *
     * <p>
     * It is expected that either the installed objects (1) have no instance fields or (2) have
     * instance fields which have been made layer-aware through other means (e.g. using a layered
     * ImageHeapMap).
     *
     * <p>
     * Note this is a temporary marker and eventually all instances of this trait should be removed.
     * This trait should only be used when there is not a correctness issue with installing multiple
     * instances of the singleton. Instead, the trait indicates there is merely a performance/memory
     * overhead due to having multiple copies of this singleton installed (via different layers)
     * within the image heap.
     */
    DUPLICABLE(EmptyMetadata.class);

    private final Class<?> metadataClass;

    public Class<?> getMetadataClass() {
        return metadataClass;
    }

    SingletonTraitKind(Class<?> metadataClass) {
        this.metadataClass = metadataClass;
    }

    /**
     * Determines whether {@link SingletonTraits} of a given kind should be linked during this
     * build. Presently many traits are specific to layered build.
     */
    public boolean isInConfiguration(boolean layeredBuild) {
        return switch (this) {
            case ACCESS -> true;
            case LAYERED_CALLBACKS, DUPLICABLE, LAYERED_INSTALLATION_KIND -> layeredBuild;
        };
    }

}
