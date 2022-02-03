/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.reflect.hosted;

import java.lang.reflect.Executable;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.RecomputeFieldValue;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Computes new values for the accessor fields of {@link Executable} subclasses, to be used instead
 * of the value from the host VM. The new values are the ones that will be in the Native Image heap.
 *
 * @see RecomputeFieldValue
 */
public final class ExecutableAccessorComputer implements RecomputeFieldValue.CustomFieldValueComputer {
    @Override
    public RecomputeFieldValue.ValueAvailability valueAvailability() {
        return RecomputeFieldValue.ValueAvailability.BeforeAnalysis;
    }

    @Override
    public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
        return ImageSingletons.lookup(ReflectionFeature.class).getOrCreateAccessor((Executable) receiver);
    }
}
