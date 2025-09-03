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
package com.oracle.graal.pointsto.infrastructure;

import java.lang.reflect.Executable;

import com.oracle.graal.pointsto.meta.BaseLayerMethod;
import com.oracle.graal.pointsto.util.GraalAccess;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * A wrapper method that can be unwrapped to an original host VM method.
 */
public interface OriginalMethodProvider {

    /**
     * Provides a mapping back from a {@link ResolvedJavaMethod} to the original method provided by
     * the JVMCI implementation of the VM that runs the image generator. This is a best-effort
     * operation, all callers must be aware that the return value can be null.
     */
    static ResolvedJavaMethod getOriginalMethod(ResolvedJavaMethod method) {
        ResolvedJavaMethod cur = method;
        while (cur instanceof OriginalMethodProvider originalMethodProvider) {
            cur = originalMethodProvider.unwrapTowardsOriginalMethod();
        }
        return cur;
    }

    /**
     * Provides a mapping back from a {@link ResolvedJavaMethod} to a {@link Executable}, i.e., a
     * mapping from JVMCI back to Java reflection. This is a best-effort operation, all callers must
     * be aware that the return value can be null.
     *
     * A null return value means that there is 1) no reflection representation at all - the provided
     * JVMCI method is a synthetic method without any class/bytecode backing, or 2) that looking up
     * the reflection object is not possible due to linking errors.
     */
    static Executable getJavaMethod(ResolvedJavaMethod method) {
        ResolvedJavaMethod originalMethod = getOriginalMethod(method);
        if (originalMethod instanceof BaseLayerMethod) {
            /* We don't know corresponding java.lang.reflect.Method. */
            return null;
        }
        if (originalMethod != null) {
            try {
                return GraalAccess.getOriginalSnippetReflection().originalMethod(originalMethod);
            } catch (LinkageError ignored) {
                /*
                 * Ignore any linking problems and incompatible class change errors. Looking up a
                 * reflective representation of a JVMCI method is always a best-effort operation.
                 */
            }
        }
        return null;
    }

    /**
     * Do not invoke directly, it is only invoked by static methods from this class. Must be
     * implemented by all {@link ResolvedJavaMethod} implementations in the native image code base.
     */
    ResolvedJavaMethod unwrapTowardsOriginalMethod();
}
