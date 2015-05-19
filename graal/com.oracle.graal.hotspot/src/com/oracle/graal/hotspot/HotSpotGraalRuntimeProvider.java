/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.stack.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.hotspot.jvmci.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.runtime.*;

//JaCoCo Exclude

/**
 * Configuration information for the HotSpot Graal runtime.
 */
public interface HotSpotGraalRuntimeProvider extends GraalRuntime, RuntimeProvider, StackIntrospection {

    HotSpotVMConfig getConfig();

    TargetDescription getTarget();

    CompilerToVM getCompilerToVM();

    /**
     * Converts a name to a Java type. This method attempts to resolve {@code name} to a
     * {@link ResolvedJavaType}.
     *
     * @param name a well formed Java type in {@linkplain JavaType#getName() internal} format
     * @param accessingType the context of resolution which must be non-null
     * @param resolve specifies whether resolution failure results in an unresolved type being
     *            return or a {@link LinkageError} being thrown
     * @return a Java type for {@code name} which is guaranteed to be of type
     *         {@link ResolvedJavaType} if {@code resolve == true}
     * @throws LinkageError if {@code resolve == true} and the resolution failed
     * @throws NullPointerException if {@code accessingClass} is {@code null}
     */
    JavaType lookupType(String name, HotSpotResolvedObjectType accessingType, boolean resolve);

    HotSpotProviders getHostProviders();

    default String getName() {
        return getClass().getSimpleName();
    }

    HotSpotBackend getHostBackend();

    /**
     * The offset from the origin of an array to the first element.
     *
     * @return the offset in bytes
     */
    int getArrayBaseOffset(Kind kind);

    /**
     * The scale used for the index when accessing elements of an array of this kind.
     *
     * @return the scale in order to convert the index into a byte offset
     */
    int getArrayIndexScale(Kind kind);

    /**
     * Gets the Graal mirror for a {@link Class} object.
     *
     * @return the {@link ResolvedJavaType} corresponding to {@code javaClass}
     */
    ResolvedJavaType fromClass(Class<?> clazz);
}
