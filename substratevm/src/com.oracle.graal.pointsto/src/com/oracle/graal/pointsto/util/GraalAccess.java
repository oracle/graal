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
package com.oracle.graal.pointsto.util;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.api.runtime.GraalJVMCICompiler;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.runtime.RuntimeProvider;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.runtime.JVMCI;

public final class GraalAccess {

    private GraalAccess() {
    }

    public static TargetDescription getOriginalTarget() {
        return getGraalCapability(RuntimeProvider.class).getHostBackend().getTarget();
    }

    public static Providers getOriginalProviders() {
        return getGraalCapability(RuntimeProvider.class).getHostBackend().getProviders();
    }

    public static SnippetReflectionProvider getOriginalSnippetReflection() {
        return getGraalCapability(SnippetReflectionProvider.class);
    }

    public static <T> T getGraalCapability(Class<T> clazz) {
        GraalJVMCICompiler compiler = (GraalJVMCICompiler) JVMCI.getRuntime().getCompiler();
        return compiler.getGraalRuntime().getCapability(clazz);
    }
}
