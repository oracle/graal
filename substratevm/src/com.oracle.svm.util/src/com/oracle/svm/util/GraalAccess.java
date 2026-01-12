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

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.graal.compiler.vmaccess.ResolvedJavaModule;
import jdk.graal.compiler.vmaccess.VMAccess;
import jdk.graal.compiler.vmaccess.VMAccess.Builder;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaRecordComponent;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.annotation.Annotated;

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

    /**
     * The {@link VMAccess} used to implement the functionality in this class.
     */
    private static VMAccess vmAccess;

    /**
     * Guards against multiple calls to {@link #plantConfiguration(VMAccess)}. The value is a stack
     * trace of the first call.
     */
    private static volatile String providersInit;

    private GraalAccess() {
    }

    public static final String PROPERTY_NAME = ImageInfo.PROPERTY_NATIVE_IMAGE_PREFIX + "vmaccessname";

    /**
     * Gets a {@link VMAccess} builder whose {@linkplain Builder#getVMAccessName() name} is
     * specified by the {@value #PROPERTY_NAME} system property. If no name is specified, then
     * {@code "host"} is used.
     *
     * @throws GraalError if the requested builder cannot be found
     */
    public static VMAccess.Builder getVmAccessBuilder() {
        String requestedAccessName = GraalServices.getSavedProperty(PROPERTY_NAME);
        String accessName = requestedAccessName == null ? "host" : requestedAccessName;
        Module vmAccessModule = Builder.class.getModule();
        ModuleLayer vmAccessLayer = vmAccessModule.getLayer();
        ServiceLoader<VMAccess.Builder> loader;
        if (vmAccessLayer == null) {
            // VMAccess was loaded on the class path (as an unnamed module).
            // In this context, it's expected that all VMAccess providers
            // are also on the class path.
            loader = ServiceLoader.load(VMAccess.Builder.class);
        } else {
            loader = ServiceLoader.load(vmAccessLayer, VMAccess.Builder.class);
        }
        VMAccess.Builder selected = null;
        List<VMAccess.Builder> builders = new ArrayList<>();
        for (VMAccess.Builder builder : loader) {
            builders.add(builder);
            if (accessName.equals(builder.getVMAccessName())) {
                selected = builder;
                break;
            }
        }
        if (selected == null) {
            if (builders.isEmpty()) {
                throw new GraalError("No %s service providers found", VMAccess.Builder.class.getName());
            }
            String available = builders.stream().map(b -> "'" + b.getVMAccessName() + "'").collect(Collectors.joining(", "));
            String origin = requestedAccessName == null ? "" : "specified by system property %s ".formatted(PROPERTY_NAME);
            throw new GraalError("%s service provider '%s' %snot found. Available providers: %s",
                            VMAccess.Builder.class.getName(),
                            accessName,
                            origin,
                            available);
        }
        return selected;
    }

    /**
     * Configures {@link GraalAccess} based on {@code vmAccess}. This method must be called before
     * calling any other methods in this class and can only be called once to ensure the whole
     * system uses a stable configuration.
     *
     * @param access the {@link VMAccess} value to use for configuring {@link GraalAccess}. If
     *            {@code null}, then {@link #getVmAccessBuilder()} is used to create a VMAccess that
     *            reflects the host configuration.
     */
    public static synchronized void plantConfiguration(VMAccess access) {
        GraalError.guarantee(providersInit == null, "Providers have already been planted: %s", providersInit);
        if (access == null) {
            VMAccess.Builder builder = getVmAccessBuilder();
            String cp = System.getProperty("java.class.path");
            if (cp != null) {
                builder.classPath(Arrays.asList(cp.split(File.pathSeparator)));
            }
            vmAccess = builder.build();
        } else {
            vmAccess = access;
        }
        StringWriter sw = new StringWriter();
        new Exception("providers previously planted here:").printStackTrace(new PrintWriter(sw));
        providersInit = sw.toString();
    }

    private static void ensureInitialized() {
        if (providersInit == null) {
            synchronized (GraalAccess.class) {
                if (providersInit == null) {
                    plantConfiguration(null);
                }
            }
        }
    }

    public static TargetDescription getOriginalTarget() {
        return getOriginalProviders().getCodeCache().getTarget();
    }

    public static Providers getOriginalProviders() {
        return getVMAccess().getProviders();
    }

    /**
     * Gets the {@link VMAccess} used to configure this class.
     */
    public static VMAccess getVMAccess() {
        ensureInitialized();
        return vmAccess;
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
        return getOriginalProviders().getSnippetReflection();
    }

    public static ResolvedJavaModule lookupModule(Module module) {
        return new ResolvedJavaModuleImpl(module);
    }
}
