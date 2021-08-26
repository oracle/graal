/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.jdk;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.configure.ResourcesRegistry;
import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import com.oracle.svm.core.jdk.NativeLibrarySupport;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.core.jni.JNIRuntimeAccess;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.c.NativeLibraries;

import jdk.vm.ci.services.Services;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.InternalPlatform;

import java.awt.GraphicsEnvironment;
import java.util.Optional;

@Platforms({InternalPlatform.PLATFORM_JNI.class})
@AutomaticFeature
@SuppressWarnings({"unused"})
public class JNIRegistrationAwt extends JNIRegistrationUtil implements Feature {

    private static final int JDK_UPDATE = GraalServices.getJavaUpdateVersion();
    private static final boolean IS_OPENJDK = Optional.ofNullable(Services.getSavedProperties().get("java.vm.name")).orElse("").startsWith("OpenJDK");

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (JavaVersionUtil.JAVA_SPEC >= 11 && Platform.includedIn(Platform.LINUX.class)) {
            access.registerReachabilityHandler(JNIRegistrationAwt::handlePreferencesClassReachable,
                            clazz(access, "java.awt.Toolkit"),
                            clazz(access, "sun.java2d.cmm.lcms.LCMS"),
                            clazz(access, "java.awt.event.NativeLibLoader"),
                            clazz(access, "sun.awt.NativeLibLoader"),
                            clazz(access, "sun.awt.image.NativeLibLoader"),
                            clazz(access, "java.awt.image.ColorModel"),
                            clazz(access, "sun.awt.X11GraphicsEnvironment"),
                            clazz(access, "sun.font.FontManagerNativeLibrary"),
                            clazz(access, "sun.java2d.Disposer"));
            PlatformNativeLibrarySupport.singleton().addBuiltinPkgNativePrefix("java_awt");
            PlatformNativeLibrarySupport.singleton().addBuiltinPkgNativePrefix("sun_awt");
            PlatformNativeLibrarySupport.singleton().addBuiltinPkgNativePrefix("sun_java2d");

            access.registerReachabilityHandler(JNIRegistrationAwt::registerFreeType,
                            clazz(access, "sun.font.FontManagerNativeLibrary"));
            PlatformNativeLibrarySupport.singleton().addBuiltinPkgNativePrefix("sun_font");

            access.registerReachabilityHandler(JNIRegistrationAwt::registerLCMS,
                            clazz(access, "sun.java2d.cmm.lcms.LCMS"));

            access.registerReachabilityHandler(JNIRegistrationAwt::registerImagingLib,
                            clazz(access, "sun.awt.image.ImagingLib"));

            access.registerReachabilityHandler(JNIRegistrationAwt::registerJPEG,
                            clazz(access, "sun.awt.image.JPEGImageDecoder"),
                            clazz(access, "com.sun.imageio.plugins.jpeg.JPEGImageReader"),
                            clazz(access, "com.sun.imageio.plugins.jpeg.JPEGImageWriter"));
            PlatformNativeLibrarySupport.singleton().addBuiltinPkgNativePrefix("com_sun_imageio_plugins_jpeg");

            access.registerReachabilityHandler(JNIRegistrationAwt::registerColorProfiles,
                            clazz(access, "java.awt.color.ICC_Profile"));

            access.registerReachabilityHandler(JNIRegistrationAwt::registerFlavorMapProps,
                            clazz(access, "java.awt.datatransfer.SystemFlavorMap"));

            access.registerReachabilityHandler(JNIRegistrationAwt::registerRTFReaderCharsets,
                            clazz(access, "javax.swing.text.rtf.RTFReader"));

            access.registerReachabilityHandler(JNIRegistrationAwt::registerOceanThemeIcons,
                            clazz(access, "javax.swing.plaf.metal.OceanTheme"));
        }
    }

    private static void handlePreferencesClassReachable(DuringAnalysisAccess access) {

        JNIRuntimeAccess.register(method(access, "java.lang.System", "setProperty", String.class, String.class));
        JNIRuntimeAccess.register(method(access, "java.lang.System", "loadLibrary", String.class));

        JNIRuntimeAccess.register(java.awt.GraphicsEnvironment.class);
        JNIRuntimeAccess.register(method(access, "java.awt.GraphicsEnvironment", "isHeadless"));

        NativeLibraries nativeLibraries = getNativeLibraries(access);

        NativeLibrarySupport.singleton().preregisterUninitializedBuiltinLibrary("awt");
        nativeLibraries.addStaticJniLibrary("awt");

        if (isHeadless()) {
            NativeLibrarySupport.singleton().preregisterUninitializedBuiltinLibrary("awt_headless");
            nativeLibraries.addStaticJniLibrary("awt_headless", "awt");
        } else {
            NativeLibrarySupport.singleton().preregisterUninitializedBuiltinLibrary("awt_xawt");
            nativeLibraries.addStaticJniLibrary("awt_xawt", "awt");

            nativeLibraries.addDynamicNonJniLibrary("X11");
            nativeLibraries.addDynamicNonJniLibrary("Xrender");
            nativeLibraries.addDynamicNonJniLibrary("Xext");
            nativeLibraries.addDynamicNonJniLibrary("Xi");
        }

        nativeLibraries.addDynamicNonJniLibrary("stdc++");
        nativeLibraries.addDynamicNonJniLibrary("m");

        access.registerReachabilityHandler(JNIRegistrationAwt::registerHtml32bdtd,
                        clazz(access, "javax.swing.text.html.HTMLEditorKit"));
    }

    private static void registerJPEG(DuringAnalysisAccess access) {
        NativeLibraries nativeLibraries = getNativeLibraries(access);

        NativeLibrarySupport.singleton().preregisterUninitializedBuiltinLibrary("javajpeg");
        nativeLibraries.addStaticJniLibrary("javajpeg");
    }

    private static void registerImagingLib(DuringAnalysisAccess access) {
        NativeLibraries nativeLibraries = getNativeLibraries(access);

        NativeLibrarySupport.singleton().preregisterUninitializedBuiltinLibrary("mlib_image");
        nativeLibraries.addStaticJniLibrary("mlib_image");
    }

    private static void registerLCMS(DuringAnalysisAccess access) {
        NativeLibraries nativeLibraries = getNativeLibraries(access);

        NativeLibrarySupport.singleton().preregisterUninitializedBuiltinLibrary("lcms");
        nativeLibraries.addStaticJniLibrary("lcms");
    }

    private static void registerFreeType(DuringAnalysisAccess access) {
        if (SubstrateOptions.StaticExecutable.getValue()) {
            /*
             * Freetype uses fontconfig through dlsym. This may not work in a statically linked
             * executable
             */
            return;
        }
        NativeLibraries nativeLibraries = getNativeLibraries(access);

        NativeLibrarySupport.singleton().preregisterUninitializedBuiltinLibrary("fontmanager");
        nativeLibraries.addStaticJniLibrary("fontmanager", isHeadless() ? "awt_headless" : "awt_xawt");
        // harfbuzz became a separate library in OpenJDK 16/11.0.10 (JDK-8249821) and then went back
        // to be included in fontmanager library in OpenJDK 17/11.0.13 (or 11.0.12 for OracleJDK).
        // See JDK-8255790.
        int jdk11HbUpdateBound = IS_OPENJDK ? 12 : 11;
        if (JavaVersionUtil.JAVA_SPEC == 16 || (JavaVersionUtil.JAVA_SPEC == 11 && JDK_UPDATE >= 10 && JDK_UPDATE <= jdk11HbUpdateBound)) {
            nativeLibraries.addStaticJniLibrary("harfbuzz");
        }

        nativeLibraries.addDynamicNonJniLibrary("freetype");

        JNIRuntimeAccess.register(clazz(access, "sun.font.FontConfigManager$FontConfigInfo"));
        JNIRuntimeAccess.register(fields(access, "sun.font.FontConfigManager$FontConfigInfo", "fcVersion", "cacheDirs"));
        JNIRuntimeAccess.register(clazz(access, "sun.font.FontConfigManager$FcCompFont"));
        JNIRuntimeAccess.register(fields(access, "sun.font.FontConfigManager$FcCompFont", "fcName", "firstFont", "allFonts"));
        JNIRuntimeAccess.register(clazz(access, "sun.font.FontConfigManager$FontConfigFont"));
        JNIRuntimeAccess.register(constructor(access, "sun.font.FontConfigManager$FontConfigFont"));
        JNIRuntimeAccess.register(fields(access, "sun.font.FontConfigManager$FontConfigFont", "familyName", "styleStr", "fullName", "fontFile"));
    }

    private static void registerColorProfiles(DuringAnalysisAccess duringAnalysisAccess) {
        ResourcesRegistry resourcesRegistry = ImageSingletons.lookup(ResourcesRegistry.class);
        resourcesRegistry.addResources(ConfigurationCondition.alwaysTrue(), "sun.java2d.cmm.profiles.*");
    }

    private static void registerFlavorMapProps(DuringAnalysisAccess duringAnalysisAccess) {
        ResourcesRegistry resourcesRegistry = ImageSingletons.lookup(ResourcesRegistry.class);
        resourcesRegistry.addResources(ConfigurationCondition.alwaysTrue(), "sun.datatransfer.resources.flavormap.properties");
    }

    private static void registerRTFReaderCharsets(DuringAnalysisAccess duringAnalysisAccess) {
        ResourcesRegistry resourcesRegistry = ImageSingletons.lookup(ResourcesRegistry.class);
        resourcesRegistry.addResources(ConfigurationCondition.alwaysTrue(), "javax.swing.text.rtf.charsets.*");
    }

    private static void registerOceanThemeIcons(DuringAnalysisAccess duringAnalysisAccess) {
        ResourcesRegistry resourcesRegistry = ImageSingletons.lookup(ResourcesRegistry.class);
        resourcesRegistry.addResources(ConfigurationCondition.alwaysTrue(), "javax.swing.plaf.metal.icons.*");
        resourcesRegistry.addResources(ConfigurationCondition.alwaysTrue(), "javax.swing.plaf.basic.icons.*");
    }

    private static void registerHtml32bdtd(DuringAnalysisAccess duringAnalysisAccess) {
        ResourcesRegistry resourcesRegistry = ImageSingletons.lookup(ResourcesRegistry.class);
        resourcesRegistry.addResources(ConfigurationCondition.alwaysTrue(), "javax.swing.text.html.parser.html32.bdtd");
    }

    private static void registerDefaultCSS(DuringAnalysisAccess duringAnalysisAccess) {
        ResourcesRegistry resourcesRegistry = ImageSingletons.lookup(ResourcesRegistry.class);
        resourcesRegistry.addResources(ConfigurationCondition.alwaysTrue(), "javax.swing.text.html.default.css");
    }

    private static NativeLibraries getNativeLibraries(DuringAnalysisAccess access) {
        FeatureImpl.DuringAnalysisAccessImpl a = (FeatureImpl.DuringAnalysisAccessImpl) access;
        return a.getNativeLibraries();
    }

    private static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }
}
