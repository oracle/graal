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

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.api.runtime.GraalJVMCICompiler;
import jdk.graal.compiler.api.runtime.GraalRuntime;
import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.runtime.RuntimeProvider;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaRecordComponent;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.runtime.JVMCI;

@Platforms(Platform.HOSTED_ONLY.class)
public final class GraalAccess {

    private static final GraalRuntime graalRuntime;
    private static final TargetDescription originalTarget;
    private static final Providers originalProviders;
    private static final SnippetReflectionProvider originalSnippetReflection;

    static {
        graalRuntime = ((GraalJVMCICompiler) JVMCI.getRuntime().getCompiler()).getGraalRuntime();
        Backend hostBackend = getGraalCapability(RuntimeProvider.class).getHostBackend();
        originalTarget = Objects.requireNonNull(hostBackend.getTarget());
        originalProviders = Objects.requireNonNull(hostBackend.getProviders());
        originalSnippetReflection = Objects.requireNonNull(getGraalCapability(SnippetReflectionProvider.class));
    }

    private GraalAccess() {
    }

    public static TargetDescription getOriginalTarget() {
        return originalTarget;
    }

    public static Providers getOriginalProviders() {
        return originalProviders;
    }

    private static final Map<Class<?>, ResolvedJavaType> typeCache = new ConcurrentHashMap<>();
    private static final Map<Executable, ResolvedJavaMethod> methodCache = new ConcurrentHashMap<>();
    private static final Map<Field, ResolvedJavaField> fieldCache = new ConcurrentHashMap<>();
    private static final Map<RecordComponent, ResolvedJavaRecordComponent> recordCache = new ConcurrentHashMap<>();

    public static ResolvedJavaType lookupType(Class<?> cls) {
        return typeCache.computeIfAbsent(cls, c -> originalProviders.getMetaAccess().lookupJavaType(cls));
    }

    public static ResolvedJavaMethod lookupMethod(Executable exe) {
        return methodCache.computeIfAbsent(exe, e -> originalProviders.getMetaAccess().lookupJavaMethod(e));
    }

    public static ResolvedJavaField lookupField(Field field) {
        return fieldCache.computeIfAbsent(field, f -> originalProviders.getMetaAccess().lookupJavaField(f));
    }

    public static ResolvedJavaRecordComponent lookupRecordComponent(RecordComponent rc) {
        return recordCache.computeIfAbsent(rc, r -> originalProviders.getMetaAccess().lookupJavaRecordComponent(rc));
    }

    public static SnippetReflectionProvider getOriginalSnippetReflection() {
        return originalSnippetReflection;
    }

    public static <T> T getGraalCapability(Class<T> clazz) {
        return graalRuntime.getCapability(clazz);
    }
}
