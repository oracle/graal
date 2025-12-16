/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.vmaccess.ResolvedJavaModule;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.api.runtime.GraalJVMCICompiler;
import jdk.graal.compiler.api.runtime.GraalRuntime;
import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.runtime.RuntimeProvider;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaRecordComponent;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.annotation.Annotated;
import jdk.vm.ci.runtime.JVMCI;

/**
 * This class provides methods for converting core reflection objects into their JVMCI counterparts
 * using the host VM implementation of JVMCI (e.g. converts {@link Class} to
 * {@code HotSpotResolvedObjectTypeImpl} when running on HotSpot). There are methods for going in
 * the opposite direction in {@link OriginalClassProvider}, {@link OriginalMethodProvider} and
 * {@link OriginalFieldProvider}.
 * <p>
 * This class is also used to access the JVMCI and {@linkplain #getOriginalProviders compiler
 * providers}.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class GraalAccess {

    private static final GraalRuntime graalRuntime;
    private static final TargetDescription originalTarget;
    private static Providers originalProviders;
    private static SnippetReflectionProvider originalSnippetReflection;

    private static volatile String providersInit;

    static {
        graalRuntime = ((GraalJVMCICompiler) JVMCI.getRuntime().getCompiler()).getGraalRuntime();
        Backend hostBackend = getGraalCapability(RuntimeProvider.class).getHostBackend();
        originalTarget = Objects.requireNonNull(hostBackend.getTarget());
    }

    private GraalAccess() {
    }

    /**
     * Plant a custom set of providers to configure {@link GraalAccess}. This method should be
     * called <b>early in the lifecycle of svm</b> to make sure the whole system receives the same
     * set of providers. For the same reason, it should be called <b>only once</b>.
     */
    public static synchronized void plantProviders(Providers providers) {
        GraalError.guarantee(providersInit == null, "Providers have already been planted: %s", providersInit);
        originalProviders = providers;
        originalSnippetReflection = originalProviders.getSnippetReflection();

        StringWriter sw = new StringWriter();
        new Exception("providers previously planted here:").printStackTrace(new PrintWriter(sw));
        providersInit = sw.toString();
    }

    private static void init() {
        if (providersInit == null) {
            Backend hostBackend = getGraalCapability(RuntimeProvider.class).getHostBackend();
            plantProviders(Objects.requireNonNull(hostBackend.getProviders()));
        }
    }

    public static TargetDescription getOriginalTarget() {
        init();
        return originalTarget;
    }

    public static Providers getOriginalProviders() {
        init();
        return originalProviders;
    }

    private static final Map<Class<?>, ResolvedJavaType> typeCache = new ConcurrentHashMap<>();
    private static final Map<Executable, ResolvedJavaMethod> methodCache = new ConcurrentHashMap<>();
    private static final Map<Field, ResolvedJavaField> fieldCache = new ConcurrentHashMap<>();
    private static final Map<RecordComponent, ResolvedJavaRecordComponent> recordCache = new ConcurrentHashMap<>();

    /**
     * Gets the {@link Annotated} equivalent value for element.
     *
     * @return {@code null} if element is a {@link Package} that has no annotations
     */
    public static Annotated toAnnotated(AnnotatedElement element) {
        return switch (element) {
            case Class<?> clazz -> lookupType(clazz);
            case Method method -> lookupMethod(method);
            case Constructor<?> cons -> lookupMethod(cons);
            case Package pkg -> new ResolvedJavaPackageImpl(pkg);
            case Field field -> lookupField(field);
            case RecordComponent rc -> lookupRecordComponent(rc);
            default -> throw new IllegalArgumentException(String.valueOf(element));
        };
    }

    public static ResolvedJavaType lookupType(Class<?> cls) {
        return typeCache.computeIfAbsent(cls, c -> getOriginalProviders().getMetaAccess().lookupJavaType(cls));
    }

    public static ResolvedJavaMethod lookupMethod(Executable exe) {
        return methodCache.computeIfAbsent(exe, e -> getOriginalProviders().getMetaAccess().lookupJavaMethod(e));
    }

    public static ResolvedJavaField lookupField(Field field) {
        return fieldCache.computeIfAbsent(field, f -> getOriginalProviders().getMetaAccess().lookupJavaField(f));
    }

    public static ResolvedJavaRecordComponent lookupRecordComponent(RecordComponent rc) {
        return recordCache.computeIfAbsent(rc, r -> getOriginalProviders().getMetaAccess().lookupJavaRecordComponent(rc));
    }

    public static SnippetReflectionProvider getOriginalSnippetReflection() {
        init();
        return originalSnippetReflection;
    }

    public static <T> T getGraalCapability(Class<T> clazz) {
        return graalRuntime.getCapability(clazz);
    }

    public static ResolvedJavaModule lookupModule(Module module) {
        return new ResolvedJavaModuleImpl(module);
    }
}
