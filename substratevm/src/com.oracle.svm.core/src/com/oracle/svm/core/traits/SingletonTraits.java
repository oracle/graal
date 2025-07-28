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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

/**
 * Denotes behaviors associated with a singleton. Each singleton can have multiple
 * {@link SingletonTrait}s installed to describe different facets of its behavior. A given
 * {@link SingletonTrait} will be of a specific {@link SingletonTraitKind} and also may have
 * metadata associated with it. The type of metadata associated with each {@link SingletonTraitKind}
 * is specified in the enum.
 *
 * <p>
 * Note at most one {@link SingletonTrait} of each {@link SingletonTraitKind} can be associated with
 * a singleton. If a singleton does not have a given {@link SingletonTraitKind} defined, then it
 * will assume the default behavior for the trait as described in {@link SingletonTraitKind}.
 *
 * <p>
 * Note also that these traits are associated with a singleton object based on the singleton's
 * runtime class, not the singleton's key class. In other words,
 * <ul>
 * <li>If a singleton's key class is annotated with {@link SingletonTraits}, but the singleton's
 * runtime class does not, then no {@link SingletonTrait}s will be installed.</li>
 * <li>Even if a singleton whose runtime class implements {@link SingletonTraits} is installed under
 * multiple keys, it will not cause multiple instances of the {@link SingletonTrait}s to be
 * created.</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Platforms(Platform.HOSTED_ONLY.class)
public @interface SingletonTraits {

    /**
     * Access kind allowed by this singleton. See {@link SingletonTraitKind#ACCESS} for more
     * details.
     */
    Class<? extends SingletonAccessSupplier> access();

    /**
     * Layered callback hooks linked to this singleton. See
     * {@link SingletonTraitKind#LAYERED_CALLBACKS} for more details.
     */
    Class<? extends SingletonLayeredCallbacksSupplier> layeredCallbacks();

    /**
     * Special layered-specific behavior associated with this singleton.
     * {@link SingletonTraitKind#LAYERED_INSTALLATION_KIND} for more details.
     */
    Class<? extends SingletonLayeredInstallationKindSupplier> layeredInstallationKind();

    /**
     * Other {@link SingletonTraitsSupplier} classes used to create {@link SingletonTrait}s
     * associated with singletons installed of this type.
     */
    Class<? extends SingletonTraitsSupplier>[] other() default {};
}
