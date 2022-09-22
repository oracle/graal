/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;

/**
 * Abstracts the information about sealed classes, which are not available in Java 11 and Java 8.
 * This class provides all information about sealed classes without exposing any JDK types and
 * methods that are not yet present in the old JDKs.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public abstract class SealedClassSupport {

    public static SealedClassSupport singleton() {
        return ImageSingletons.lookup(SealedClassSupport.class);
    }

    /** Same as {@code Class.isSealed()}. */
    public abstract boolean isSealed(Class<?> clazz);

    /** Same as {@code Class.getPermittedSubclasses()}. */
    public abstract Class<?>[] getPermittedSubclasses(Class<?> clazz);
}

/**
 * Placeholder implementation for JDK versions that do not have sealed classes.
 */
@AutomaticallyRegisteredImageSingleton(value = SealedClassSupport.class, onlyWith = JDK11OrEarlier.class)
final class SealedClassSupportJDK11OrEarlier extends SealedClassSupport {

    @Override
    public boolean isSealed(Class<?> clazz) {
        return false;
    }

    @Override
    public Class<?>[] getPermittedSubclasses(Class<?> clazz) {
        return null;
    }
}
