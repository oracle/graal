/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.GraphicsEnvironment;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.RuntimeJNIAccess;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.nativeimage.impl.RuntimeResourceSupport;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import com.oracle.svm.core.jdk.NativeLibrarySupport;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.c.NativeLibraries;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;

import java.util.stream.Stream;

@Platforms({InternalPlatform.PLATFORM_JNI.class})
@AutomaticallyRegisteredFeature
@SuppressWarnings({"unused"})
public class JNIRegistrationAwt extends JNIRegistrationUtil implements InternalFeature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (Platform.includedIn(Platform.LINUX.class)) {
            access.registerReachabilityHandler(JNIRegistrationAwt::handlePreferencesClassReachable,
                            clazz(access, "java.awt.Toolkit"),
                            clazz(access, "sun.java2d.cmm.lcms.LCMS"),
                            clazz(access, "java.awt.event.NativeLibLoader"),
                            clazz(access, "sun.awt.NativeLibLoader"),
                            clazz(access, "sun.awt.image.NativeLibLoader"),
                            clazz(access, "java.awt.image.ColorModel"),
                            clazz(access, "sun.awt.X11GraphicsEnvironment"),
                            clazz(access, "sun.font.FontManagerNativeLibrary"),
                            clazz(access, "sun.print.CUPSPrinter"),
                            clazz(access, "sun.java2d.Disposer"));
            PlatformNativeLibrarySupport.singleton().addBuiltinPkgNativePrefix("java_awt");
            PlatformNativeLibrarySupport.singleton().addBuiltinPkgNativePrefix("sun_awt");
            PlatformNativeLibrarySupport.singleton().addBuiltinPkgNativePrefix("sun_java2d");
            PlatformNativeLibrarySupport.singleton().addBuiltinPkgNativePrefix("sun_print");

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

            access.registerReachabilityHandler(JNIRegistrationAwt::registerFontManager,
                    clazz(access, "sun.font.SunFontManager"));

            access.registerReachabilityHandler(JNIRegistrationAwt::registerFontManagerFactory,
                    clazz(access, "sun.font.FontManagerFactory"));

            access.registerReachabilityHandler(JNIRegistrationAwt::registerSurfaceData,
                    clazz(access, "sun.java2d.SurfaceData"));

            access.registerReachabilityHandler(JNIRegistrationAwt::registerGraphicsPrimitiveMgr,
                    clazz(access, "sun.java2d.loops.GraphicsPrimitiveMgr"));

            access.registerReachabilityHandler(JNIRegistrationAwt::registerColorModel,
                    clazz(access, "java.awt.image.ColorModel"));

            access.registerReachabilityHandler(JNIRegistrationAwt::registerComponent,
                    clazz(access, "java.awt.Component"));

            access.registerReachabilityHandler(JNIRegistrationAwt::registerKeyCodes,
                    clazz(access, "java.awt.event.KeyEvent"));

            access.registerReachabilityHandler(JNIRegistrationAwt::registerDataTransferer,
                    clazz(access, "sun.awt.datatransfer.DataTransferer"));

            access.registerReachabilityHandler(JNIRegistrationAwt::registerDndIcons,
                    clazz(access, "java.awt.dnd.DragSource"));

            access.registerReachabilityHandler(JNIRegistrationAwt::registerDisposer,
                    clazz(access, "sun.java2d.SurfaceData"));

            if (!isHeadless()) {
                access.registerReachabilityHandler(JNIRegistrationAwt::registerX11,
                        clazz(access, "sun.awt.X11GraphicsEnvironment"));
                PlatformNativeLibrarySupport.singleton().addBuiltinPkgNativePrefix("com_sun_java_swing_plaf_gtk");

                access.registerReachabilityHandler(JNIRegistrationAwt::registerGtkFileDialog,
                        clazz(access, "sun.awt.X11.GtkFileDialogPeer"));

                access.registerReachabilityHandler(JNIRegistrationAwt::registerShellFolderManager,
                        clazz(access, "sun.swing.FilePane"));

                access.registerReachabilityHandler(JNIRegistrationAwt::registerMetalLookAndFeel,
                        clazz(access, "javax.swing.plaf.metal.MetalLookAndFeel"));
            }
        }
    }

    private static void handlePreferencesClassReachable(DuringAnalysisAccess access) {

        RuntimeJNIAccess.register(method(access, "java.lang.System", "setProperty", String.class, String.class));
        RuntimeJNIAccess.register(method(access, "java.lang.System", "loadLibrary", String.class));

        RuntimeJNIAccess.register(GraphicsEnvironment.class);
        RuntimeJNIAccess.register(method(access, "java.awt.GraphicsEnvironment", "isHeadless"));

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

        RuntimeJNIAccess.register(clazz(access, "sun.awt.image.JPEGImageDecoder"));
        RuntimeJNIAccess.register(method(access, "sun.awt.image.JPEGImageDecoder", "sendHeaderInfo",
                int.class, int.class, boolean.class, boolean.class, boolean.class));
        RuntimeJNIAccess.register(method(access, "sun.awt.image.JPEGImageDecoder", "sendPixels",
                byte[].class, int.class));
        RuntimeJNIAccess.register(method(access, "sun.awt.image.JPEGImageDecoder", "sendPixels",
                int[].class, int.class));

        RuntimeJNIAccess.register(method(access, "java.io.InputStream", "available"));
        RuntimeJNIAccess.register(method(access, "java.io.InputStream", "read",
                byte[].class, int.class, int.class));
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
        nativeLibraries.addDynamicNonJniLibrary("freetype");

        RuntimeJNIAccess.register(clazz(access, "sun.font.FontConfigManager$FontConfigInfo"));
        RuntimeJNIAccess.register(fields(access, "sun.font.FontConfigManager$FontConfigInfo", "fcVersion", "cacheDirs"));
        RuntimeJNIAccess.register(clazz(access, "sun.font.FontConfigManager$FcCompFont"));
        RuntimeJNIAccess.register(fields(access, "sun.font.FontConfigManager$FcCompFont", "fcName", "firstFont", "allFonts"));
        RuntimeJNIAccess.register(clazz(access, "sun.font.FontConfigManager$FontConfigFont"));
        RuntimeJNIAccess.register(constructor(access, "sun.font.FontConfigManager$FontConfigFont"));
        RuntimeJNIAccess.register(fields(access, "sun.font.FontConfigManager$FontConfigFont", "familyName", "styleStr", "fullName", "fontFile"));
    }

    private static void registerColorProfiles(DuringAnalysisAccess duringAnalysisAccess) {
        ImageSingletons.lookup(RuntimeResourceSupport.class).addResources(ConfigurationCondition.alwaysTrue(), "sun.java2d.cmm.profiles.*");
    }

    private static void registerFlavorMapProps(DuringAnalysisAccess duringAnalysisAccess) {
        ImageSingletons.lookup(RuntimeResourceSupport.class).addResources(ConfigurationCondition.alwaysTrue(), "sun.datatransfer.resources.flavormap.properties");
    }

    private static void registerRTFReaderCharsets(DuringAnalysisAccess duringAnalysisAccess) {
        ImageSingletons.lookup(RuntimeResourceSupport.class).addResources(ConfigurationCondition.alwaysTrue(), "javax.swing.text.rtf.charsets.*");
    }

    private static void registerOceanThemeIcons(DuringAnalysisAccess duringAnalysisAccess) {
        ImageSingletons.lookup(RuntimeResourceSupport.class).addResources(ConfigurationCondition.alwaysTrue(), "javax.swing.plaf.metal.icons.*");
        ImageSingletons.lookup(RuntimeResourceSupport.class).addResources(ConfigurationCondition.alwaysTrue(), "javax.swing.plaf.basic.icons.*");
    }

    private static void registerHtml32bdtd(DuringAnalysisAccess duringAnalysisAccess) {
        ImageSingletons.lookup(RuntimeResourceSupport.class).addResources(ConfigurationCondition.alwaysTrue(), "javax.swing.text.html.parser.html32.bdtd");

        RuntimeReflection.register(clazz(duringAnalysisAccess, "javax.swing.text.html.HTMLEditorKit"));
        RuntimeReflection.register(constructor(duringAnalysisAccess, "javax.swing.text.html.HTMLEditorKit"));

        RuntimeReflection.register(clazz(duringAnalysisAccess, "javax.swing.text.rtf.RTFEditorKit"));
        RuntimeReflection.register(constructor(duringAnalysisAccess, "javax.swing.text.rtf.RTFEditorKit"));

        RuntimeReflection.register(clazz(duringAnalysisAccess, "javax.swing.JEditorPane$PlainEditorKit"));
        RuntimeReflection.register(constructor(duringAnalysisAccess, "javax.swing.JEditorPane$PlainEditorKit"));
    }

    private static void registerDefaultCSS(DuringAnalysisAccess duringAnalysisAccess) {
        ImageSingletons.lookup(RuntimeResourceSupport.class).addResources(ConfigurationCondition.alwaysTrue(), "javax.swing.text.html.default.css");
    }

    private static void registerGraphicsPrimitiveMgr(DuringAnalysisAccess access) {
        RuntimeJNIAccess.register(fields(access, "java.awt.AlphaComposite", "extraAlpha", "rule"));

        RuntimeJNIAccess.register(fields(access, "java.awt.Color", "value"));
        RuntimeJNIAccess.register(constructor(access, "java.awt.Color", int.class, int.class, int.class));
        RuntimeJNIAccess.register(method(access, "java.awt.Color", "getRGB"));

        RuntimeJNIAccess.register(fields(access, "sun.java2d.SunGraphics2D",
                "clipRegion", "composite", "eargb", "lcdTextContrast", "pixel", "strokeHint"));

        RuntimeJNIAccess.register(clazz(access, "sun.java2d.pipe.Region"));
        RuntimeJNIAccess.register(fields(access, "sun.java2d.pipe.Region",
                "bands", "endIndex", "hix", "hiy", "lox", "loy"));

        RuntimeJNIAccess.register(fields(access, "sun.java2d.pipe.RegionIterator",
                "curIndex", "numXbands", "region"));

        RuntimeJNIAccess.register(fields(access, "sun.java2d.pipe.ShapeSpanIterator", "pData"));

        RuntimeReflection.register(clazz(access, "sun.java2d.marlin.DMarlinRenderingEngine"));
        RuntimeReflection.register(constructor(access, "sun.java2d.marlin.DMarlinRenderingEngine"));

        RuntimeJNIAccess.register(fields(access, "java.awt.geom.AffineTransform",
                "m00", "m01", "m02", "m10", "m11", "m12"));

        RuntimeJNIAccess.register(constructor(access, "java.awt.geom.GeneralPath"));
        RuntimeJNIAccess.register(constructor(access, "java.awt.geom.GeneralPath",
                int.class, byte[].class, int.class, float[].class, int.class));

        RuntimeJNIAccess.register(fields(access, "java.awt.geom.Path2D", "numTypes", "pointTypes", "windingRule"));

        RuntimeJNIAccess.register(fields(access, "java.awt.geom.Path2D$Float", "floatCoords"));

        RuntimeJNIAccess.register(fields(access, "java.awt.geom.Point2D$Float", "x", "y"));
        RuntimeJNIAccess.register(constructor(access, "java.awt.geom.Point2D$Float", float.class, float.class));

        RuntimeJNIAccess.register(fields(access, "java.awt.geom.Rectangle2D$Float", "height", "width", "x", "y"));
        RuntimeJNIAccess.register(constructor(access, "java.awt.geom.Rectangle2D$Float"));
        RuntimeJNIAccess.register(constructor(access, "java.awt.geom.Rectangle2D$Float",
                float.class, float.class, float.class, float.class));

        RuntimeJNIAccess.register(fields(access, "sun.awt.SunHints", "INTVAL_STROKE_PURE"));

        RuntimeJNIAccess.register(fields(access, "sun.java2d.loops.CompositeType",
                "AnyAlpha", "Src", "SrcNoEa", "SrcOver", "SrcOverNoEa", "Xor"));

        RuntimeJNIAccess.register(fields(access, "sun.java2d.loops.GraphicsPrimitive", "pNativePrim"));
        RuntimeJNIAccess.register(method(access, "sun.java2d.loops.GraphicsPrimitiveMgr",
                "register", clazz(access, "[Lsun.java2d.loops.GraphicsPrimitive;")));
        RuntimeJNIAccess.register(clazz(access, "[Lsun.java2d.loops.GraphicsPrimitive;"));

        RuntimeJNIAccess.register(fields(access, "sun.java2d.loops.XORComposite",
                "alphaMask", "xorColor", "xorPixel"));

        RuntimeJNIAccess.register(fields(access, "sun.java2d.loops.SurfaceType",
                "Any3Byte", "Any4Byte", "AnyByte", "AnyColor", "AnyInt", "AnyShort",
                "ByteBinary1Bit", "ByteBinary2Bit", "ByteBinary4Bit", "ByteGray", "ByteIndexed",
                "ByteIndexedBm", "FourByteAbgr", "FourByteAbgrPre", "Index12Gray", "Index8Gray",
                "IntArgb", "IntArgbBm", "IntArgbPre", "IntBgr", "IntRgb", "IntRgbx", "OpaqueColor",
                "ThreeByteBgr", "Ushort4444Argb", "Ushort555Rgb", "Ushort555Rgbx", "Ushort565Rgb",
                "UshortGray", "UshortIndexed"));

        Stream.of("sun.java2d.loops.OpaqueCopyAnyToArgb",
                "sun.java2d.loops.OpaqueCopyArgbToAny",
                "sun.java2d.loops.XorCopyArgbToAny",
                "sun.java2d.loops.SetFillRectANY",
                "sun.java2d.loops.SetFillPathANY",
                "sun.java2d.loops.SetFillSpansANY",
                "sun.java2d.loops.SetDrawLineANY",
                "sun.java2d.loops.SetDrawPolygonsANY",
                "sun.java2d.loops.SetDrawPathANY",
                "sun.java2d.loops.SetDrawRectANY",
                "sun.java2d.loops.XorFillRectANY",
                "sun.java2d.loops.XorFillPathANY",
                "sun.java2d.loops.XorFillSpansANY",
                "sun.java2d.loops.XorDrawLineANY",
                "sun.java2d.loops.XorDrawPolygonsANY",
                "sun.java2d.loops.XorDrawPathANY",
                "sun.java2d.loops.XorDrawRectANY",
                "sun.java2d.loops.XorDrawGlyphListANY",
                "sun.java2d.loops.XorDrawGlyphListAAANY")
                .forEach(graphicsPrimitive -> {
                    RuntimeReflection.register(constructor(access, graphicsPrimitive));
                });

        Stream.of("sun.java2d.loops.Blit",
                "sun.java2d.loops.BlitBg",
                "sun.java2d.loops.DrawGlyphList",
                "sun.java2d.loops.DrawGlyphListAA",
                "sun.java2d.loops.DrawGlyphListLCD",
                "sun.java2d.loops.DrawLine",
                "sun.java2d.loops.DrawParallelogram",
                "sun.java2d.loops.DrawPath",
                "sun.java2d.loops.DrawPolygons",
                "sun.java2d.loops.DrawRect",
                "sun.java2d.loops.FillParallelogram",
                "sun.java2d.loops.FillPath",
                "sun.java2d.loops.FillRect",
                "sun.java2d.loops.FillSpans",
                "sun.java2d.loops.MaskBlit",
                "sun.java2d.loops.MaskFill",
                "sun.java2d.loops.ScaledBlit",
                "sun.java2d.loops.TransformHelper")
                .forEach(graphicsPrimitive -> {
                    RuntimeJNIAccess.register(constructor(access, graphicsPrimitive,
                            long.class,
                            clazz(access, "sun.java2d.loops.SurfaceType"),
                            clazz(access, "sun.java2d.loops.CompositeType"),
                            clazz(access, "sun.java2d.loops.SurfaceType")));

                });
    }

    private static void registerSurfaceData(DuringAnalysisAccess access) {
        RuntimeJNIAccess.register(clazz(access, "sun.java2d.InvalidPipeException"));
        RuntimeJNIAccess.register(clazz(access, "sun.java2d.NullSurfaceData"));

        RuntimeJNIAccess.register(clazz(access, "sun.java2d.SurfaceData"));
        RuntimeJNIAccess.register(fields(access, "sun.java2d.SurfaceData", "pData", "valid"));

        RuntimeJNIAccess.register(clazz(access, "sun.java2d.xr.XRSurfaceData"));
        RuntimeJNIAccess.register(fields(access, "sun.java2d.xr.XRSurfaceData", "picture", "xid"));

        RuntimeJNIAccess.register(clazz(access, "sun.java2d.xr.XRBackendNative"));
        RuntimeJNIAccess.register(fields(access, "sun.java2d.xr.XRBackendNative",
                "FMTPTR_A8", "FMTPTR_ARGB32", "MASK_XIMG"));

        RuntimeJNIAccess.register(java.awt.image.BufferedImage.class);
        RuntimeJNIAccess.register(fields(access, "java.awt.image.BufferedImage",
                "colorModel", "imageType", "raster"));
        RuntimeJNIAccess.register(method(access, "java.awt.image.BufferedImage",
                "getRGB", int.class, int.class, int.class, int.class, int[].class, int.class, int.class));
        RuntimeJNIAccess.register(method(access, "java.awt.image.BufferedImage",
                "setRGB", int.class, int.class, int.class, int.class, int[].class, int.class, int.class));

        RuntimeJNIAccess.register(java.awt.image.Raster.class);
        RuntimeJNIAccess.register(fields(access, "java.awt.image.Raster",
                "dataBuffer", "height", "minX", "minY", "numBands", "numDataElements",
                "sampleModel", "sampleModelTranslateX", "sampleModelTranslateY", "width"));

        RuntimeJNIAccess.register(java.awt.image.SampleModel.class);
        RuntimeJNIAccess.register(fields(access, "java.awt.image.SampleModel", "height", "width"));
        RuntimeJNIAccess.register(method(access, "java.awt.image.SampleModel",
                "getPixels", int.class, int.class, int.class, int.class, int[].class,
                java.awt.image.DataBuffer.class));
        RuntimeJNIAccess.register(method(access, "java.awt.image.SampleModel",
                "setPixels", int.class, int.class, int.class, int.class, int[].class,
                java.awt.image.DataBuffer.class));

        RuntimeJNIAccess.register(java.awt.image.SinglePixelPackedSampleModel.class);
        RuntimeJNIAccess.register(fields(access, "java.awt.image.SinglePixelPackedSampleModel",
                "bitMasks", "bitOffsets", "bitSizes", "maxBitSize"));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.image.BufImgSurfaceData$ICMColorData"));
        RuntimeJNIAccess.register(fields(access, "sun.awt.image.BufImgSurfaceData$ICMColorData", "pData"));
        RuntimeJNIAccess.register(constructor(access, "sun.awt.image.BufImgSurfaceData$ICMColorData", long.class));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.image.IntegerComponentRaster"));
        RuntimeJNIAccess.register(fields(access, "sun.awt.image.IntegerComponentRaster",
                "data", "dataOffsets", "pixelStride", "scanlineStride", "type"));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.image.ByteComponentRaster"));
        RuntimeJNIAccess.register(fields(access, "sun.awt.image.ByteComponentRaster",
                "data", "dataOffsets", "pixelStride", "scanlineStride", "type"));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.image.GifImageDecoder"));
        RuntimeJNIAccess.register(fields(access, "sun.awt.image.GifImageDecoder",
                "outCode", "prefix", "suffix"));
        RuntimeJNIAccess.register(method(access, "sun.awt.image.GifImageDecoder",
                "readBytes", byte[].class, int.class, int.class));
        RuntimeJNIAccess.register(method(access, "sun.awt.image.GifImageDecoder", "sendPixels",
                int.class, int.class, int.class, int.class,
                byte[].class, java.awt.image.ColorModel.class));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.image.ImageRepresentation"));
        RuntimeJNIAccess.register(fields(access, "sun.awt.image.ImageRepresentation",
                "numSrcLUT", "srcLUTtransIndex"));
    }

    private static void registerColorModel(DuringAnalysisAccess access) {
        RuntimeJNIAccess.register(java.awt.image.ColorModel.class);
        RuntimeJNIAccess.register(fields(access, "java.awt.image.ColorModel",
                "colorSpace", "colorSpaceType", "isAlphaPremultiplied", "is_sRGB", "nBits",
                "numComponents", "pData", "supportsAlpha", "transparency"));
        RuntimeJNIAccess.register(method(access, "java.awt.image.ColorModel", "getRGBdefault"));

        RuntimeJNIAccess.register(java.awt.image.IndexColorModel.class);
        RuntimeJNIAccess.register(fields(access, "java.awt.image.IndexColorModel",
                "allgrayopaque", "colorData", "map_size", "rgb", "transparent_index"));

        RuntimeJNIAccess.register(java.awt.image.DirectColorModel.class);
        RuntimeJNIAccess.register(constructor(access, "java.awt.image.DirectColorModel",
                int.class, int.class, int.class, int.class, int.class));
    }

    private static void registerX11(DuringAnalysisAccess access) {
        RuntimeJNIAccess.register(clazz(access, "sun.awt.UNIXToolkit"));
        RuntimeJNIAccess.register(method(access, "sun.awt.UNIXToolkit", "loadIconCallback",
                byte[].class, int.class, int.class, int.class, int.class, int.class, boolean.class));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.X11GraphicsEnvironment"));
        RuntimeJNIAccess.register(clazz(access, "sun.awt.X11.XWindow"));
        RuntimeJNIAccess.register(fields(access, "sun.awt.X11.XWindow", "drawState",
                "graphicsConfig", "target"));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.X11.XFramePeer"));
        RuntimeJNIAccess.register(clazz(access, "sun.awt.X11.XDialogPeer"));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.X11.XRootWindow"));
        RuntimeJNIAccess.register(method(access, "sun.awt.X11.XRootWindow", "getXRootWindow"));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.X11.XBaseWindow"));
        RuntimeJNIAccess.register(fields(access, "sun.awt.X11.XBaseWindow", "window"));
        RuntimeJNIAccess.register(method(access, "sun.awt.X11.XBaseWindow", "getWindow"));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.X11.XContentWindow"));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.X11.XErrorHandlerUtil"));
        RuntimeJNIAccess.register(method(access, "sun.awt.X11.XErrorHandlerUtil", "init", long.class));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.X11.XToolkit"));
        RuntimeJNIAccess.register(fields(access, "sun.awt.X11.XToolkit", "modLockIsShiftLock", "numLockMask"));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.X11GraphicsConfig"));
        RuntimeJNIAccess.register(fields(access, "sun.awt.X11GraphicsConfig", "aData", "bitsPerPixel"));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.X11GraphicsDevice"));
        RuntimeJNIAccess.register(fields(access, "sun.awt.X11GraphicsDevice", "screen"));
        RuntimeJNIAccess.register(method(access, "sun.awt.X11GraphicsDevice", "addDoubleBufferVisual", int.class));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.X11InputMethodBase"));
        RuntimeJNIAccess.register(fields(access, "sun.awt.X11InputMethodBase", "pData"));

        RuntimeJNIAccess.register(method(access, "sun.awt.X11.XErrorHandlerUtil", "globalErrorHandler",
                long.class, long.class));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.SunToolkit"));
        RuntimeJNIAccess.register(method(access, "sun.awt.SunToolkit", "isTouchKeyboardAutoShowEnabled"));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.SunToolkit"));
        RuntimeJNIAccess.register(method(access, "sun.awt.SunToolkit", "awtLock"));
        RuntimeJNIAccess.register(method(access, "sun.awt.SunToolkit", "awtLockNotify"));
        RuntimeJNIAccess.register(method(access, "sun.awt.SunToolkit", "awtLockNotifyAll"));
        RuntimeJNIAccess.register(method(access, "sun.awt.SunToolkit", "awtLockWait", long.class));
        RuntimeJNIAccess.register(method(access, "sun.awt.SunToolkit", "awtUnlock"));

        RuntimeJNIAccess.register(java.awt.Rectangle.class);
        RuntimeJNIAccess.register(constructor(access, "java.awt.Rectangle",
                int.class, int.class, int.class, int.class));

        RuntimeJNIAccess.register(java.awt.DisplayMode.class);
        RuntimeJNIAccess.register(constructor(access, "java.awt.DisplayMode", int.class, int.class, int.class, int.class));

        RuntimeJNIAccess.register(method(access, "java.lang.Thread", "yield"));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.AWTAutoShutdown"));
        RuntimeJNIAccess.register(method(access, "sun.awt.AWTAutoShutdown", "notifyToolkitThreadBusy"));
        RuntimeJNIAccess.register(method(access, "sun.awt.AWTAutoShutdown", "notifyToolkitThreadFree"));

        RuntimeJNIAccess.register(clazz(access, "java.awt.AWTError"));
        RuntimeJNIAccess.register(constructor(access, "java.awt.AWTError", java.lang.String.class));
    }

    private static void registerFontManager(DuringAnalysisAccess access) {
        RuntimeJNIAccess.register(clazz(access, "sun.font.GlyphLayout$GVData"));
        RuntimeJNIAccess.register(fields(access, "sun.font.GlyphLayout$GVData",
                "_count", "_flags", "_glyphs", "_indices", "_positions"));
        RuntimeJNIAccess.register(method(access, "sun.font.GlyphLayout$GVData",
                "grow"));

        RuntimeJNIAccess.register(clazz(access, "sun.font.CharToGlyphMapper"));
        RuntimeJNIAccess.register(method(access, "sun.font.CharToGlyphMapper", "charToGlyph", int.class));

        RuntimeJNIAccess.register(clazz(access, "sun.font.Font2D"));
        RuntimeJNIAccess.register(method(access, "sun.font.Font2D", "canDisplay", char.class));
        RuntimeJNIAccess.register(method(access, "sun.font.Font2D", "charToGlyph", int.class));
        RuntimeJNIAccess.register(method(access, "sun.font.Font2D", "charToVariationGlyph", int.class, int.class));
        RuntimeJNIAccess.register(method(access, "sun.font.Font2D", "getMapper"));
        RuntimeJNIAccess.register(method(access, "sun.font.Font2D", "getTableBytes", int.class));

        RuntimeJNIAccess.register(clazz(access, "sun.font.FontStrike"));
        RuntimeJNIAccess.register(method(access, "sun.font.FontStrike", "getGlyphMetrics", int.class));

        RuntimeJNIAccess.register(clazz(access, "sun.font.FreetypeFontScaler"));
        RuntimeJNIAccess.register(method(access, "sun.font.FreetypeFontScaler", "invalidateScaler"));

        RuntimeJNIAccess.register(clazz(access, "sun.font.GlyphList"));
        RuntimeJNIAccess.register(fields(access, "sun.font.GlyphList",
                "images", "lcdRGBOrder", "lcdSubPixPos", "len", "positions", "usePositions", "x", "y", "gposx", "gposy"));

        RuntimeJNIAccess.register(clazz(access, "sun.font.PhysicalStrike"));
        RuntimeJNIAccess.register(fields(access, "sun.font.PhysicalStrike", "pScalerContext"));
        RuntimeJNIAccess.register(method(access, "sun.font.PhysicalStrike", "adjustPoint",
                java.awt.geom.Point2D.Float.class));
        RuntimeJNIAccess.register(method(access, "sun.font.PhysicalStrike",
                "getGlyphPoint", int.class, int.class));

        RuntimeJNIAccess.register(clazz(access, "sun.font.StrikeMetrics"));
        RuntimeJNIAccess.register(constructor(access, "sun.font.StrikeMetrics",
                float.class, float.class, float.class, float.class, float.class,
                float.class, float.class, float.class, float.class, float.class));

        RuntimeJNIAccess.register(clazz(access, "sun.font.TrueTypeFont"));
        RuntimeJNIAccess.register(method(access, "sun.font.TrueTypeFont", "readBlock",
                java.nio.ByteBuffer.class, int.class, int.class));
        RuntimeJNIAccess.register(method(access, "sun.font.TrueTypeFont", "readBytes", int.class, int.class));

        RuntimeJNIAccess.register(clazz(access, "sun.font.Type1Font"));
        RuntimeJNIAccess.register(method(access, "sun.font.Type1Font", "readFile", java.nio.ByteBuffer.class));

        RuntimeJNIAccess.register(clazz(access, "sun.font.FontUtilities"));
        RuntimeJNIAccess.register(method(access, "sun.font.FontUtilities", "debugFonts"));

        RuntimeJNIAccess.register(method(access, "java.awt.GraphicsEnvironment",
                "getLocalGraphicsEnvironment"));
        RuntimeJNIAccess.register(clazz(access, "sun.java2d.SunGraphicsEnvironment"));
        RuntimeJNIAccess.register(method(access, "sun.java2d.SunGraphicsEnvironment",
                "isDisplayLocal"));

        RuntimeJNIAccess.register(java.lang.String.class);
        RuntimeJNIAccess.register(method(access, "java.lang.String", "toLowerCase", java.util.Locale.class));

        RuntimeJNIAccess.register(java.util.Locale.class);

        RuntimeJNIAccess.register(java.util.ArrayList.class);
        RuntimeJNIAccess.register(constructor(access, "java.util.ArrayList"));
        RuntimeJNIAccess.register(constructor(access, "java.util.ArrayList", int.class));
        RuntimeJNIAccess.register(method(access, "java.util.ArrayList", "add", java.lang.Object.class));
        RuntimeJNIAccess.register(method(access, "java.util.ArrayList", "contains", java.lang.Object.class));
    }

    private static void registerFontManagerFactory(DuringAnalysisAccess access) {
        if (JavaVersionUtil.JAVA_SPEC <= 17) {
            // JDK-8273581 Change the mechanism by which JDK loads the platform-specific FontManager class
            RuntimeReflection.register(clazz(access, "sun.awt.X11FontManager"));
            RuntimeReflection.register(constructor(access, "sun.awt.X11FontManager"));
        }
    }

    private static void registerComponent(DuringAnalysisAccess access) {
        RuntimeJNIAccess.register(java.awt.Component.class);

        RuntimeJNIAccess.register(fields(access, "java.awt.Component",
                "appContext", "background", "cursor", "enabled", "focusable",
                "foreground", "graphicsConfig", "height", "parent",
                "isPacked", "name", "peer", "visible", "width", "x", "y"));

        RuntimeJNIAccess.register(method(access, "java.awt.Component", "getLocationOnScreen_NoTreeLock"));
        RuntimeJNIAccess.register(method(access, "java.awt.Component", "getParent_NoClientCode"));

        RuntimeJNIAccess.register(java.awt.event.KeyEvent.class);
        RuntimeJNIAccess.register(fields(access, "java.awt.event.KeyEvent",
                "isProxyActive", "keyChar", "keyCode"));
    }

    private static void registerKeyCodes(DuringAnalysisAccess access) {
        RuntimeReflection.register(java.awt.event.KeyEvent.class);

        String[] keys = Stream.of(java.awt.event.KeyEvent.class
                .getDeclaredFields())
                .filter(f -> f.getType() == Integer.TYPE && f.getName().startsWith("VK_"))
                .map(f -> f.getName())
                .toArray(size -> new String[size]);

        RuntimeReflection.register(fields(access, "java.awt.event.KeyEvent", keys));
    }

    private static void registerDataTransferer(DuringAnalysisAccess access) {
        RuntimeReflection.register(java.lang.String.class);
        RuntimeReflection.register(java.util.List.class);

        RuntimeReflection.register(java.io.Reader.class);
        RuntimeReflection.register(java.io.InputStream.class);
        RuntimeReflection.register(java.io.Serializable.class);
        RuntimeReflection.register(java.nio.CharBuffer.class);
        RuntimeReflection.register(java.nio.ByteBuffer.class);

        RuntimeReflection.register(java.awt.Image.class);

        RuntimeReflection.register(byte[].class);
        RuntimeReflection.register(char[].class);

        RuntimeReflection.register(constructor(access, "java.rmi.MarshalledObject",
                java.lang.Object.class));
        RuntimeReflection.register(method(access, "java.rmi.MarshalledObject", "get"));

        RuntimeJNIAccess.register(method(access, "com.sun.imageio.plugins.jpeg.JPEGImageReader",
                "readInputData", byte[].class, int.class, int.class));
        RuntimeJNIAccess.register(method(access, "com.sun.imageio.plugins.jpeg.JPEGImageReader",
                "skipInputBytes", long.class));
        RuntimeJNIAccess.register(method(access, "com.sun.imageio.plugins.jpeg.JPEGImageReader",
                "warningOccurred", int.class));
        RuntimeJNIAccess.register(method(access, "com.sun.imageio.plugins.jpeg.JPEGImageReader",
                "warningWithMessage", String.class));
        RuntimeJNIAccess.register(method(access, "com.sun.imageio.plugins.jpeg.JPEGImageReader",
                "setImageData", int.class, int.class, int.class, int.class, int.class, byte[].class));
        RuntimeJNIAccess.register(method(access, "com.sun.imageio.plugins.jpeg.JPEGImageReader",
                "acceptPixels", int.class, boolean.class));
        RuntimeJNIAccess.register(method(access, "com.sun.imageio.plugins.jpeg.JPEGImageReader",
                "passStarted", int.class));
        RuntimeJNIAccess.register(method(access, "com.sun.imageio.plugins.jpeg.JPEGImageReader",
                "passComplete"));
        RuntimeJNIAccess.register(method(access, "com.sun.imageio.plugins.jpeg.JPEGImageReader",
                "pushBack", int.class));
        RuntimeJNIAccess.register(method(access, "com.sun.imageio.plugins.jpeg.JPEGImageReader",
                "skipPastImage", int.class));

        RuntimeJNIAccess.register(method(access, "com.sun.imageio.plugins.jpeg.JPEGImageWriter",
                "writeOutputData", byte[].class, int.class, int.class));
        RuntimeJNIAccess.register(method(access, "com.sun.imageio.plugins.jpeg.JPEGImageWriter",
                "warningOccurred", int.class));
        RuntimeJNIAccess.register(method(access, "com.sun.imageio.plugins.jpeg.JPEGImageWriter",
                "warningWithMessage", String.class));
        RuntimeJNIAccess.register(method(access, "com.sun.imageio.plugins.jpeg.JPEGImageWriter",
                "writeMetadata"));
        RuntimeJNIAccess.register(method(access, "com.sun.imageio.plugins.jpeg.JPEGImageWriter",
                "grabPixels", int.class));

        RuntimeJNIAccess.register(fields(access, "javax.imageio.plugins.jpeg.JPEGQTable", "qTable"));
        RuntimeJNIAccess.register(fields(access, "javax.imageio.plugins.jpeg.JPEGHuffmanTable",
                "lengths", "values"));
    }

    private static void registerDndIcons(DuringAnalysisAccess duringAnalysisAccess) {
        ImageSingletons.lookup(RuntimeResourceSupport.class).addResources(ConfigurationCondition.alwaysTrue(), "sun.awt.*");
    }

    private static void registerShellFolderManager(DuringAnalysisAccess access) {
        RuntimeReflection.register(clazz(access, "sun.awt.shell.ShellFolderManager"));
        RuntimeReflection.register(constructor(access, "sun.awt.shell.ShellFolderManager"));
    }

    private static void registerGtkFileDialog(DuringAnalysisAccess access) {
        RuntimeJNIAccess.register(fields(access, "sun.awt.X11.GtkFileDialogPeer", "widget"));
        RuntimeJNIAccess.register(method(access, "sun.awt.X11.GtkFileDialogPeer",
                "filenameFilterCallback", java.lang.String.class));
        RuntimeJNIAccess.register(method(access, "sun.awt.X11.GtkFileDialogPeer",
                "setFileInternal", java.lang.String.class, java.lang.String[].class));
        RuntimeJNIAccess.register(method(access, "sun.awt.X11.GtkFileDialogPeer",
                "setWindow", long.class));
    }

    private static void registerDisposer(DuringAnalysisAccess access) {
        RuntimeJNIAccess.register(clazz(access, "sun.java2d.Disposer"));
        RuntimeJNIAccess.register(method(access, "sun.java2d.Disposer", "addRecord",
                java.lang.Object.class, long.class, long.class));
    }

    private static void registerMetalLookAndFeel(DuringAnalysisAccess access) {
        // Basic L&F
        registerComponentUIs(access, "javax.swing.plaf.basic",
                "BasicButtonUI",
                "BasicCheckBoxUI",
                "BasicColorChooserUI",
                "BasicFormattedTextFieldUI",
                "BasicMenuBarUI",
                "BasicMenuUI",
                "BasicMenuItemUI",
                "BasicCheckBoxMenuItemUI",
                "BasicRadioButtonMenuItemUI",
                "BasicRadioButtonUI",
                "BasicToggleButtonUI",
                "BasicPopupMenuUI",
                "BasicProgressBarUI",
                "BasicScrollBarUI",
                "BasicScrollPaneUI",
                "BasicSplitPaneUI",
                "BasicSliderUI",
                "BasicSeparatorUI",
                "BasicSpinnerUI",
                "BasicToolBarSeparatorUI",
                "BasicPopupMenuSeparatorUI",
                "BasicTabbedPaneUI",
                "BasicTextAreaUI",
                "BasicTextFieldUI",
                "BasicPasswordFieldUI",
                "BasicTextPaneUI",
                "BasicEditorPaneUI",
                "BasicTreeUI",
                "BasicLabelUI",
                "BasicListUI",
                "BasicToolBarUI",
                "BasicToolTipUI",
                "BasicComboBoxUI",
                "BasicTableUI",
                "BasicTableHeaderUI",
                "BasicInternalFrameUI",
                "BasicDesktopPaneUI",
                "BasicDesktopIconUI",
                "BasicFileChooserUI",
                "BasicOptionPaneUI",
                "BasicPanelUI",
                "BasicViewportUI",
                "BasicRootPaneUI");

        ImageSingletons.lookup(RuntimeResourceSupport.class).addResourceBundles(ConfigurationCondition.alwaysTrue(), "com.sun.swing.internal.plaf.basic.resources.basic");

        // Metal L&F
        registerComponentUIs(access, "javax.swing.plaf.metal",

                "MetalButtonUI",
                "MetalCheckBoxUI",
                "MetalComboBoxUI",
                "MetalDesktopIconUI",
                "MetalFileChooserUI",
                "MetalInternalFrameUI",
                "MetalLabelUI",
                "MetalPopupMenuSeparatorUI",
                "MetalProgressBarUI",
                "MetalRadioButtonUI",
                "MetalScrollBarUI",
                "MetalScrollPaneUI",
                "MetalSeparatorUI",
                "MetalSliderUI",
                "MetalSplitPaneUI",
                "MetalTabbedPaneUI",
                "MetalTextFieldUI",
                "MetalToggleButtonUI",
                "MetalToolBarUI",
                "MetalToolTipUI",
                "MetalTreeUI",
                "MetalRootPaneUI",
                "MetalMenuBarUI");

        ImageSingletons.lookup(RuntimeResourceSupport.class).addResourceBundles(ConfigurationCondition.alwaysTrue(), "com.sun.swing.internal.plaf.metal.resources.metal");
    }

    private static void registerComponentUIs(DuringAnalysisAccess access, String packageName, String... componentUIs) {
        Stream.of(componentUIs)
                .map(name -> String.format("%s.%s", packageName, name))
                .forEach(componentUI -> {
                    RuntimeReflection.register(clazz(access, componentUI));
                    RuntimeReflection.register(method(access, componentUI, "createUI",
                            clazz(access, "javax.swing.JComponent")));
                });
    }

    private static NativeLibraries getNativeLibraries(DuringAnalysisAccess access) {
        FeatureImpl.DuringAnalysisAccessImpl a = (FeatureImpl.DuringAnalysisAccessImpl) access;
        return a.getNativeLibraries();
    }

    private static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }
}
