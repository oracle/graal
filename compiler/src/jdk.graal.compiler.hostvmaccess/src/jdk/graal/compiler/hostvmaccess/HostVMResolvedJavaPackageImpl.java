/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hostvmaccess;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.Function;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.vmaccess.ModuleSupport;
import jdk.graal.compiler.vmaccess.ResolvedJavaModule;
import jdk.graal.compiler.vmaccess.ResolvedJavaPackage;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.annotation.Annotated;
import jdk.vm.ci.meta.annotation.AnnotationsInfo;

/**
 * Fallback implementation of {@link ResolvedJavaPackage} based on {@link Package}.
 */
final class HostVMResolvedJavaPackageImpl implements ResolvedJavaPackage {
    private static final Field MODULE_FIELD;

    static {
        try {
            Class<?> namedPackageClass = Class.forName("java.lang.NamedPackage", false, HostVMResolvedJavaPackageImpl.class.getClassLoader());
            Field moduleField = namedPackageClass.getDeclaredField("module");
            ModuleSupport.addOpens(HostVMResolvedJavaPackageImpl.class.getModule(), namedPackageClass.getModule(), namedPackageClass.getPackageName());
            moduleField.setAccessible(true);
            MODULE_FIELD = moduleField;
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static final Method GET_PACKAGE_INFO_METHOD;

    static {
        try {
            Method getPackageInfoMethod = Package.class.getDeclaredMethod("getPackageInfo");
            ModuleSupport.addOpens(HostVMResolvedJavaPackageImpl.class.getModule(), Package.class.getModule(), Package.class.getPackageName());
            getPackageInfoMethod.setAccessible(true);
            GET_PACKAGE_INFO_METHOD = getPackageInfoMethod;
        } catch (ReflectiveOperationException | LinkageError ex) {
            throw new RuntimeException(ex);
        }
    }

    private final Package originalPackage;
    private final MetaAccessProvider metaAccess;

    HostVMResolvedJavaPackageImpl(MetaAccessProvider metaAccess, Package pkg) {
        this.originalPackage = Objects.requireNonNull(pkg);
        this.metaAccess = metaAccess;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        HostVMResolvedJavaPackageImpl that = (HostVMResolvedJavaPackageImpl) o;
        return originalPackage.equals(that.originalPackage);
    }

    @Override
    public int hashCode() {
        return originalPackage.hashCode();
    }

    @Override
    public String toString() {
        return originalPackage.toString();
    }

    @Override
    public String getImplementationVersion() {
        return originalPackage.getImplementationVersion();
    }

    @Override
    public <T> T getDeclaredAnnotationInfo(Function<AnnotationsInfo, T> parser) {
        if (parser == null) {
            return null;
        }
        Annotated packageInfo = getAnnotatedPackageInfo();
        return packageInfo == null ? parser.apply(null) : packageInfo.getDeclaredAnnotationInfo(parser);
    }

    private Annotated getAnnotatedPackageInfo() {
        try {
            Class<?> c = (Class<?>) GET_PACKAGE_INFO_METHOD.invoke(originalPackage);
            if (c.getName().endsWith(".package-info")) {
                return metaAccess.lookupJavaType(c);
            }
            return null;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public AnnotationsInfo getTypeAnnotationInfo() {
        Annotated packageInfo = getAnnotatedPackageInfo();
        return packageInfo == null ? null : packageInfo.getTypeAnnotationInfo();
    }

    @Override
    public String getName() {
        return originalPackage.getName();
    }

    @Override
    public ResolvedJavaModule module() {
        try {
            return new HostVMResolvedJavaModuleImpl(((Module) MODULE_FIELD.get(originalPackage)));
        } catch (IllegalAccessException e) {
            throw GraalError.shouldNotReachHere(e);
        }
    }
}
