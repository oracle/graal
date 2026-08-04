/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import java.lang.reflect.Method;
import java.nio.ByteOrder;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.impl.AnnotationExtractor;

import com.oracle.svm.core.GuestImageSingletonSupport;
import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.core.layeredimagesingleton.LoadedLayeredImageSingletonInfo;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.guest.staging.ArgsSupport;
import com.oracle.svm.guest.staging.JavaMainSupport;
import com.oracle.svm.guest.staging.config.SubstrateGuestTarget;
import com.oracle.svm.hosted.imagelayer.HostedImageLayerBuildingSupport;
import com.oracle.svm.shared.ImageLayerBuildingSupportProvider;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.GuestAccess;
import com.oracle.svm.util.JVMCIReflectionUtil;

import jdk.graal.compiler.vmaccess.InvocationException;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Creates and registers guest-context state needed while setting up an image build.
 */
final class GuestImageGeneratorSupport {
    private static final String WINDOWS_ARGS_SUPPORT_CLASS_NAME = "com.oracle.svm.core.windows.WindowsJavaMainWrapperArgsSupport";

    /** Prevents instantiation. */
    private GuestImageGeneratorSupport() {
    }

    /**
     * Installs the guest-staging argument support singleton after the image singleton registries
     * have been installed.
     * <p>
     * {@link ArgsSupport} is used by runtime code in guest staging, so fully isolated builds need
     * the singleton instance in the guest registry. The implementation object is therefore created
     * in the guest context and registered through {@link GuestImageSingletonSupport}. The singleton
     * keeps the {@code InitialLayerOnly} layer contract by following the same loaded-key check as
     * automatic singleton registration: if layer loading already handled the key, setup does not
     * create or register a replacement object. GR-76716 tracks direct guest-staging support for
     * automatic singleton registration, which would let this become an ordinary guest-staging
     * registration. GR-76886 tracks moving the Windows-specific implementation into guest staging
     * once the required platform bindings and platform-specific registration selection are
     * guest-staging owned.
     */
    static void installArgsSupport() {
        if (ImageSingletons.lookup(LoadedLayeredImageSingletonInfo.class).handledDuringLoading(ArgsSupport.class)) {
            return;
        }

        GuestAccess access = GuestAccess.get();
        ResolvedJavaType argsSupportType = access.lookupType(ArgsSupport.class);
        ResolvedJavaType implementationType = getArgsSupportImplementationType(access);
        ResolvedJavaMethod ctor = JVMCIReflectionUtil.getDeclaredConstructor(implementationType);
        JavaConstant argsSupport;
        try {
            argsSupport = access.invoke(ctor, null);
        } catch (InvocationException ex) {
            throw VMError.shouldNotReachHere("Error creating Java argument support in the guest context", ex);
        }
        GuestImageSingletonSupport.add(argsSupportType, argsSupport);
    }

    /**
     * Returns the guest type that should implement the {@link ArgsSupport} singleton for the target
     * platform.
     */
    private static ResolvedJavaType getArgsSupportImplementationType(GuestAccess access) {
        if (Platform.includedIn(Platform.WINDOWS.class)) {
            return access.lookupType(WINDOWS_ARGS_SUPPORT_CLASS_NAME);
        }
        return access.lookupType(ArgsSupport.class);
    }

    /**
     * Installs Java-main state after the image singleton registries have been installed.
     * <p>
     * {@link NativeImageGeneratorRunner} resolves the application Java main method. This method runs
     * in the generator setup phase that has active builder and guest singleton registries. The
     * {@link JavaMainSupport} object is constructed in the guest context because it owns method
     * handles for guest methods, then registered through {@link GuestImageSingletonSupport} so the
     * builder-side code path performs all guest singleton registration consistently.
     *
     * @param javaMainMethod the application Java main method resolved by
     *            {@link NativeImageGeneratorRunner}
     */
    static void installJavaMainSupport(ResolvedJavaMethod javaMainMethod) {
        GuestAccess access = GuestAccess.get();
        JavaConstant executable = access.asExecutableConstant(javaMainMethod);
        if (executable == null) {
            throw UserError.abort("Cannot install Java main support because no reflective executable is available for %s.", javaMainMethod.format("%H.%n(%p)"));
        }

        ResolvedJavaType javaMainSupportType = access.lookupType(JavaMainSupport.class);
        ResolvedJavaMethod ctor = JVMCIReflectionUtil.getDeclaredConstructor(access.getProviders().getMetaAccess(), javaMainSupportType, Method.class);
        JavaConstant javaMainSupport;
        try {
            javaMainSupport = access.invoke(ctor, null, executable);
        } catch (InvocationException ex) {
            if (ex.getCause() instanceof IllegalArgumentException iae) {
                throw UserError.abort(iae, "%s", iae.getMessage());
            }
            throw VMError.shouldNotReachHere("Error creating Java main support in the guest context", ex);
        }
        GuestImageSingletonSupport.add(javaMainSupportType, javaMainSupport);
    }

    /** Installs the guest singleton registry and its image-layer support proxy. */
    static void installSingletonRegistry(HostedImageLayerBuildingSupport imageLayerSupport) {
        GuestImageSingletonSupport.install();
        registerImageLayerBuildingSupport(imageLayerSupport);
    }

    /** Registers the builder's image-layer support as a guest host proxy. */
    private static void registerImageLayerBuildingSupport(HostedImageLayerBuildingSupport imageLayerSupport) {
        GuestAccess access = GuestAccess.get();
        ResolvedJavaType key = access.lookupType(ImageLayerBuildingSupportProvider.class);
        JavaConstant hostProxy = access.createHostProxy(imageLayerSupport, key);
        GuestImageSingletonSupport.add(key, hostProxy);
    }

    /** Registers the guest-context annotation extractor. */
    static void registerAnnotationExtractor() {
        GuestAccess access = GuestAccess.get();
        ResolvedJavaType key = access.lookupType(AnnotationExtractor.class);
        JavaConstant hostProxy = access.createHostProxy(new GuestAnnotationExtractorProxy(), key);
        GuestImageSingletonSupport.add(key, hostProxy);
    }

    /** Creates and registers the target description in the guest context. */
    static void setupTargetDescription(SubstrateTarget target) {
        GuestAccess access = GuestAccess.get();
        ResolvedJavaMethod ctor = JVMCIReflectionUtil.getDeclaredConstructor(access.getProviders().getMetaAccess(), SubstrateGuestTarget.class, JavaKind.class, int.class, ByteOrder.class);

        JavaConstant wordKind = asGuestEnum(target.wordJavaKind);
        JavaConstant wordSize = JavaConstant.forInt(target.wordSize);
        JavaConstant byteOrder = JVMCIReflectionUtil.readStaticField(access.elements.java_nio_ByteOrder, target.arch.getByteOrder().toString());
        JavaConstant guestTargetDescription = access.invoke(ctor, null, wordKind, wordSize, byteOrder);

        GuestImageSingletonSupport.add(SubstrateGuestTarget.class, guestTargetDescription);
    }

    /** Converts a builder enum constant to its corresponding guest enum constant. */
    private static JavaConstant asGuestEnum(Enum<?> kind) {
        GuestAccess access = GuestAccess.get();
        ResolvedJavaType enumType = access.getProviders().getMetaAccess().lookupJavaType(kind.getDeclaringClass());
        JavaConstant enumName = access.asGuestString(kind.name());
        ResolvedJavaMethod valueOf = JVMCIReflectionUtil.getUniqueDeclaredMethod(access.getProviders().getMetaAccess(), enumType, "valueOf", String.class);
        JavaKind.valueOf(kind.name());
        return access.invoke(valueOf, null, enumName);
    }
}
