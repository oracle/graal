/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.annotation;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

/**
 * This interface is used to mark the ahead-of-time allocated annotation proxy objects. These
 * objects have an optimized type which removes the overhead of storing the annotation values in a
 * HashMap. See AnnotationSupport#getSubstitution(ResolvedJavaType) for the logic where this
 * substitution is implemented. This is possible since the ahead-of-time allocated annotation proxy
 * objects are effectively constant.
 * 
 * The {@link ConstantAnnotationMarker} is removed before runtime. See
 * {@link AnnotationSubstitutionType#getInterfaces()}. This is also enforced through the HOSTED_ONLY
 * annotation below. No {@link ConstantAnnotationMarker} is allowed in the image heap.
 * 
 * The run-time allocated annotations use the default JDK implementation.
 * 
 * The downside of having a separate (more efficient) implementation for ahead-of-time allocated
 * annotations is that at run time there can be two proxy types for the same annotation type and an
 * equality check between them would fail.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public interface ConstantAnnotationMarker {
}
