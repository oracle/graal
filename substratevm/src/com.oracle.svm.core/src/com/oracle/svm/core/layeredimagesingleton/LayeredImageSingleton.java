/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.layeredimagesingleton;

import java.util.EnumSet;

public interface LayeredImageSingleton {

    enum PersistFlags {
        /**
         * Indicates nothing should be persisted for this singleton. A different singleton can be
         * linked to this key in a subsequent image layer.
         */
        NOTHING,
        /**
         * Indicates nothing should be persisted for this singleton and that a singleton cannot be
         * linked to this key in a subsequent image layer.
         */
        FORBIDDEN,
        /**
         * Indicates in a subsequent image a new singleton should be created and linked via calling
         * {@code Object createFromLoader(ImageSingletonLoader)}.
         */
        CREATE
    }

    enum ImageBuilderFlags {
        /**
         * This singleton can be accessed from the runtime.
         */
        RUNTIME_ACCESS,
        /**
         * This singleton can be accessed from the buildtime.
         */
        BUILDTIME_ACCESS,
        /**
         * This singleton should not have been created. Throw error if it is created.
         */
        UNSUPPORTED,
        /**
         * This singleton should only be created in the initial layer.
         */
        INITIAL_LAYER_ONLY,
        /**
         * Allow the singleton to be constant-folded within the generated code.
         */
        ALLOW_CONSTANT_FOLDING,
        /**
         * Prevent the singleton to be constant-folded within the generated code.
         */
        PREVENT_CONSTANT_FOLDING
    }

    default boolean verifyImageBuilderFlags() {
        EnumSet<ImageBuilderFlags> flags = getImageBuilderFlags();

        if (flags.contains(ImageBuilderFlags.UNSUPPORTED)) {
            assert flags.equals(EnumSet.of(ImageBuilderFlags.UNSUPPORTED)) : "Unsupported should be the only flag set " + flags;
        }

        if (flags.contains(ImageBuilderFlags.RUNTIME_ACCESS)) {
            assert !flags.containsAll(EnumSet.of(ImageBuilderFlags.PREVENT_CONSTANT_FOLDING, ImageBuilderFlags.ALLOW_CONSTANT_FOLDING)) : String.format("Must set one of %s or %s. flags: %s",
                            ImageBuilderFlags.PREVENT_CONSTANT_FOLDING, ImageBuilderFlags.ALLOW_CONSTANT_FOLDING, flags);
            assert flags.contains(ImageBuilderFlags.PREVENT_CONSTANT_FOLDING) || flags.contains(ImageBuilderFlags.ALLOW_CONSTANT_FOLDING) : String.format("Must set one of %s or %s. flags: %s",
                            ImageBuilderFlags.PREVENT_CONSTANT_FOLDING, ImageBuilderFlags.ALLOW_CONSTANT_FOLDING, flags);

        }

        return true;
    }

    EnumSet<ImageBuilderFlags> getImageBuilderFlags();

    PersistFlags preparePersist(ImageSingletonWriter writer);

}
