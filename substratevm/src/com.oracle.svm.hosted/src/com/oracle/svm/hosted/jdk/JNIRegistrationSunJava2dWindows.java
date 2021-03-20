package com.oracle.svm.hosted.jdk;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jni.JNIRuntimeAccess;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

/**
 * @see JNIRegistrationJavaAwtWindows
 */
@Platforms(Platform.WINDOWS.class)
@AutomaticFeature
public class JNIRegistrationSunJava2dWindows extends JNIRegistrationAwtUtil implements Feature {

    @Override
    public void duringSetup(DuringSetupAccess a) {
        initializeAtRunTime(a,
                // from shared code
                // Disposer.c
                SunJava2d.DISPOSER,
                // SurfaceData.c
                SunJava2d.SURFACE_DATA,
                // D3DGraphicsDevice.cpp
                SunJava2d.D3D_GRAPHICS_DEVICE,
                // D3DMaskFill.cpp
                /* SunJava2d.D3D_MASK_FILL */
                // D3DRenderQueue.cpp
                SunJava2d.D3D_RENDER_QUEUE,
                // D3DRenderer.cpp
                /* SunJava2d.D3D_RENDERER */
                // D3DSurfaceData.cpp
                SunJava2d.D3D_SURFACE_DATA,
                // D3DTextRenderer.cpp - empty

                // from shared code
                // Blit.c
                SunJava2d.BLIT,
                // BlitBg.c
                SunJava2d.BLIT_BG,
                // DrawLine.c
                SunJava2d.DRAW_LINE,
                // DrawParallelogram.c
                SunJava2d.DRAW_PARALLELOGRAM,
                // DrawPath.c
                SunJava2d.DRAW_PATH,
                // DrawPolygons.c
                SunJava2d.DRAW_POLYGONS,
                // DrawRect.c
                SunJava2d.DRAW_RECT,
                // FillParallelogram.c
                SunJava2d.FILL_PARALLELOGRAM,
                // FillPath.c
                SunJava2d.FILL_PATH,
                // FillRect.c
                SunJava2d.FILL_RECT,
                // FillSpans.c
                SunJava2d.FILL_SPANS,
                // MaskBlit.c
                SunJava2d.MASK_BLIT,
                // MaskFill.c
                SunJava2d.MASK_FILL,
                // ScaledBlit.c
                SunJava2d.SCALED_BLIT,
                // this should cover the remaining graphics primitives
                SunJava2d.GRAPHICS_PRIMITIVE,
                // GraphicsPrimitiveMgr.c
                SunJava2d.GRAPHICS_PRIMITIVE_MGR,
                // OGLContext.c
                /* SunJava2d.OGL_CONTEXT, */
                // OGLMaskFill.c
                /* SunJava2d.OGL_MASK_FILL, */
                // OGLRenderQueue.c
                SunJava2d.OGL_RENDER_QUEUE,
                // OGLRenderer.c - empty
                /* SunJava2d.OGL_RENDERER */
                // OGLSurfaceData.c
                SunJava2d.OGL_SURFACE_DATA,
                // OGLTextRenderer.c - empty

                // from windows specific code
                // WGLGraphicsConfig.c
                SunJava2d.WGL_GRAPHICS_CONFIG,
                // WGLSurfaceData.c
                SunJava2d.WGL_SURFACE_DATA,

                // from shared code
                // BufferedMaskBlit.c
                // BufferedRenderPipe.c
                // Region.c
                SunJava2d.REGION,
                // ShapeSpanIterator.c
                SunJava2d.SHAPE_SPAN_ITERATOR,
                // SpanClipRenderer.c
                SunJava2d.SPAN_CLIP_RENDERER,

                // from windows specific code
                // GDIBlitLoops.cpp - empty
                // GDIRenderer.cpp - empty
                // GDIWindowSurfaceData.cpp
                SunJava2d.GDI_WINDOW_SURFACE_DATA,
                // WindowsFlags.cpp
                SunJava2d.WINDOWS_FLAGS
        );
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        registerForThrowNew(a,
                "sun.java2d.InvalidPipeException"
        );
        // from shared code
        // Disposer.c
        registerInitIDsHandler(a, JNIRegistrationSunJava2dWindows::registerDisposerInitIDs, SunJava2d.DISPOSER);
        // SurfaceData.c
        registerInitIDsHandler(a, JNIRegistrationSunJava2dWindows::registerSurfaceDataInitIDs, SunJava2d.SURFACE_DATA);

        // from windows specific code
        // D3DGraphicsDevice.cpp - empty
        // D3DMaskFill.cpp - empty
        // D3DRenderQueue.cpp
        a.registerReachabilityHandler(JNIRegistrationSunJava2dWindows::registerD3DRenderQueueFlushBuffer,
                method(a, SunJava2d.D3D_RENDER_QUEUE, "flushBuffer", long.class, int.class, clazz(a, "java.lang.Runnable")));
        // D3DRenderer.cpp - empty
        // D3DSurfaceData.cpp
        registerClassHandler(a, JNIRegistrationSunJava2dWindows::registerD3DSurfaceDataClass, SunJava2d.D3D_SURFACE_DATA);
        a.registerReachabilityHandler(JNIRegistrationSunJava2dWindows::registerD3DSurfaceDataInitOps,
                method(a, SunJava2d.D3D_SURFACE_DATA, "initOps", int.class, int.class, int.class));
        // D3DTextRenderer.cpp - empty

        // from shared code
        // Blit.c - empty
        // BlitBg.c - empty
        // DrawLine.c - empty
        // DrawParallelogram.c - empty
        // DrawPath.c - empty
        // DrawPolygons.c - empty
        // DrawRect.c - empty
        // FillParallelogram.c - empty
        // FillPath.c - empty
        // FillRect.c - empty
        // FillSpans.c - empty
        // MaskBlit.c - empty
        // MaskFill.c - empty
        // ScaledBlit.c - empty
        // GraphicsPrimitiveMgr.c
        registerInitIDsHandler(a, JNIRegistrationSunJava2dWindows::registerGraphicsPrimitiveMgrInitIDs, SunJava2d.GRAPHICS_PRIMITIVE_MGR,
                Class.class, Class.class, Class.class, Class.class, Class.class, Class.class,
                Class.class, Class.class, Class.class, Class.class, Class.class);

        // OGLContext.c
        registerClassHandler(a, JNIRegistrationSunJava2dWindows::registerOGLContextClass, SunJava2d.OGL_CONTEXT);
        // OGLMaskFill.c - empty
        // OGLRenderQueue.c - empty
        // OGLRenderer.c - empty
        // OGLSurfaceData.c
        registerClassHandler(a, JNIRegistrationSunJava2dWindows::registerOGLSurfaceDataClass, SunJava2d.OGL_SURFACE_DATA);
        // OGLTextRenderer.c - empty

        // from windows specific code
        // WGLGraphicsConfig.c - empty
        // WGLSurfaceData.c - empty

        // from shared code
        // BufferedMaskBlit.c
        // BufferedRenderPipe.c
        a.registerReachabilityHandler(JNIRegistrationSunJava2dWindows::registerBufferedRenderPipeFillSpans,
                method(a, SunJava2d.BUFFERED_RENDER_PIPE, "fillSpans",
                        clazz(a, "sun.java2d.pipe.RenderQueue"), long.class, int.class, int.class,
                        clazz(a, "sun.java2d.pipe.SpanIterator"), long.class, int.class, int.class));
        // Region.c
        registerInitIDsHandler(a, JNIRegistrationSunJava2dWindows::registerRegionInitIDs, SunJava2d.REGION);
        // ShapeSpanIterator.c
        registerInitIDsHandler(a, JNIRegistrationSunJava2dWindows::registerShapeSpanIteratorInitIDs, SunJava2d.SHAPE_SPAN_ITERATOR);
        // SpanClipRenderer.c
        registerInitIDsHandler(a, JNIRegistrationSunJava2dWindows::registerShapeSpanClipRendererInitIDs,
                SunJava2d.SPAN_CLIP_RENDERER, Class.class, Class.class);

        // from windows specific code
        // GDIBlitLoops.cpp - empty
        // GDIRenderer.cpp - empty
        registerInitIDsHandler(a, JNIRegistrationSunJava2dWindows::registerGDIWindowSurfaceDataInitIDs,
                SunJava2d.GDI_WINDOW_SURFACE_DATA, Class.class);
        // WindowsFlags.cpp
        a.registerReachabilityHandler(JNIRegistrationSunJava2dWindows::registerWindowsFlagsInitNativeFlags,
                method(a, SunJava2d.WINDOWS_FLAGS, "initNativeFlags"));
    }

    // Disposer.c

    private static void registerDisposerInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, SunJava2d.DISPOSER));
        JNIRuntimeAccess.register(method(a, SunJava2d.DISPOSER, "addRecord",
                Object.class, long.class, long.class));
    }

    // SurfaceData.c

    private static void registerSurfaceDataInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, SunJava2d.NULL_SURFACE_DATA));
        JNIRuntimeAccess.register(fields(a, SunJava2d.SURFACE_DATA, "pData", "valid"));
        JNIRuntimeAccess.register(clazz(a, JavaAwt.INDEX_COLOR_MODEL));
        JNIRuntimeAccess.register(fields(a, JavaAwt.INDEX_COLOR_MODEL, "allgrayopaque"));
    }

    // from windows specific code

    // D3DGraphicsDevice.cpp - empty

    // D3DMaskFill.cpp - empty

    // D3DRenderQueue.cpp - empty

    private static void registerD3DRenderQueueFlushBuffer(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(method(a, "java.lang.Runnable", "run"));
    }

    // D3DRenderer.cpp - empty

    // D3DSurfaceData.cpp

    private static void registerD3DSurfaceDataClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, SunJava2d.D3D_SURFACE_DATA, "nativeWidth", "nativeHeight"));
        JNIRuntimeAccess.register(method(a, SunJava2d.D3D_SURFACE_DATA, "setSurfaceLost", boolean.class));
    }

    private static void registerD3DSurfaceDataInitOps(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, SunJava2d.D3D_SURFACE_DATA));
        JNIRuntimeAccess.register(method(a, SunJava2d.D3D_SURFACE_DATA, "dispose", long.class));
    }


    // D3DTextRenderer.cpp - empty

    // from shared code

    // Blit.c - empty
    // BlitBg.c - empty
    // DrawLine.c - empty
    // DrawParallelogram.c - empty
    // DrawPath.c - empty
    // DrawPolygons.c - empty
    // DrawRect.c - empty
    // FillParallelogram.c - empty
    // FillPath.c - empty
    // FillRect.c - empty
    // FillSpans.c - empty
    // MaskBlit.c - empty
    // MaskFill.c - empty
    // ScaledBlit.c - empty

    // GraphicsPrimitiveMgr.c

    private static void registerGraphicsPrimitiveMgrInitIDs(DuringAnalysisAccess a) {
        Class<?>[] initSignature = {long.class, clazz(a, "sun.java2d.loops.SurfaceType"),
                clazz(a, "sun.java2d.loops.CompositeType"), clazz(a, "sun.java2d.loops.SurfaceType")};

        // InitPrimTypes
        String[] primitiveTypes = {
                SunJava2d.BLIT, SunJava2d.BLIT_BG, SunJava2d.SCALED_BLIT, SunJava2d.FILL_RECT, SunJava2d.FILL_SPANS,
                SunJava2d.FILL_PARALLELOGRAM, SunJava2d.DRAW_PARALLELOGRAM, SunJava2d.DRAW_LINE, SunJava2d.DRAW_RECT,
                SunJava2d.DRAW_POLYGONS, SunJava2d.DRAW_PATH, SunJava2d.FILL_PATH, SunJava2d.MASK_BLIT, SunJava2d.MASK_FILL,
                SunJava2d.DRAW_GLYPH_LIST, SunJava2d.DRAW_GLYPH_LIST_AA, SunJava2d.DRAW_GLYPH_LIST_LCD, SunJava2d.TRANSFORM_HELPER
        };
        for (String primitiveType : primitiveTypes) {
            JNIRuntimeAccess.register(clazz(a, primitiveType));
            JNIRuntimeAccess.register(constructor(a, primitiveType, initSignature));
        }

        // InitSurfaceTypes
        JNIRuntimeAccess.register(fields(a, "sun.java2d.loops.SurfaceType",
                "OpaqueColor", "AnyColor", "AnyByte", "ByteBinary1Bit", "ByteBinary2Bit", "ByteBinary4Bit",
                "ByteIndexed", "ByteIndexedBm", "ByteGray", "Index8Gray", "Index12Gray", "AnyShort", "Ushort555Rgb",
                "Ushort555Rgbx", "Ushort565Rgb", "Ushort4444Argb", "UshortGray", "UshortIndexed", "Any3Byte",
                "ThreeByteBgr", "AnyInt", "IntArgb", "IntArgbPre", "IntArgbBm", "IntRgb", "IntBgr", "IntRgbx",
                "Any4Byte", "FourByteAbgr", "FourByteAbgrPre"));

        // InitCompositeTypes
        JNIRuntimeAccess.register(fields(a, "sun.java2d.loops.CompositeType",
                "SrcNoEa", "SrcOverNoEa", "Src", "SrcOver", "Xor", "AnyAlpha"));

        // initIDs

        JNIRuntimeAccess.register(method(a, SunJava2d.GRAPHICS_PRIMITIVE_MGR, "register",
                clazz(a, array(SunJava2d.GRAPHICS_PRIMITIVE))));

        JNIRuntimeAccess.register(fields(a, SunJava2d.GRAPHICS_PRIMITIVE, "pNativePrim"));

        JNIRuntimeAccess.register(fields(a, "sun.java2d.SunGraphics2D",
                "pixel", "eargb", "clipRegion", "composite", "lcdTextContrast", "strokeHint"));

        JNIRuntimeAccess.register(method(a, JavaAwt.COLOR, "getRGB"));

        JNIRuntimeAccess.register(fields(a, "sun.java2d.loops.XORComposite",
                "xorPixel", "xorColor", "alphaMask"));

        JNIRuntimeAccess.register(fields(a, "java.awt.AlphaComposite",
                "rule", "extraAlpha"));

        JNIRuntimeAccess.register(fields(a, "java.awt.geom.AffineTransform",
                "m00", "m01", "m02", "m10", "m11", "m12"));

        JNIRuntimeAccess.register(fields(a, "java.awt.geom.Path2D",
                "pointTypes", "numTypes", "windingRule"));

        JNIRuntimeAccess.register(fields(a, "java.awt.geom.Path2D$Float",
                "floatCoords"));

        JNIRuntimeAccess.register(fields(a, "sun.awt.SunHints",
                "INTVAL_STROKE_PURE"));
    }

    // OGLContext.c

    private static void registerOGLContextClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, SunJava2d.OGL_SURFACE_DATA));
        JNIRuntimeAccess.register(fields(a, SunJava2d.OGL_SURFACE_DATA,
                "isFBObjectEnabled", "isLCDShaderEnabled", "isBIOpShaderEnabled", "isGradShaderEnabled"));
    }


    // OGLMaskFill.c - empty
    // OGLRenderQueue.c - empty
    // OGLRenderer.c - empty

    // OGLSurfaceData.c

    private static void registerOGLSurfaceDataClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, SunJava2d.OGL_SURFACE_DATA));
        JNIRuntimeAccess.register(fields(a, SunJava2d.OGL_SURFACE_DATA, "nativeWidth", "nativeHeight"));
        JNIRuntimeAccess.register(method(a, SunJava2d.OGL_SURFACE_DATA, "dispose",
                long.class, clazz(a, "sun.java2d.opengl.OGLGraphicsConfig")));
    }

    // OGLTextRenderer.c - empty

    // from windows specific code
    // WGLGraphicsConfig.c - empty
    // WGLSurfaceData.c - empty

    // from shared code
    // BufferedMaskBlit.c
    // BufferedRenderPipe.c

    private static void registerBufferedRenderPipeFillSpans(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(method(a, "sun.java2d.pipe.RenderQueue", "flushNow", int.class));
    }

    // Region.c

    private static void registerRegionInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, SunJava2d.REGION, "endIndex", "bands", "lox", "loy", "hix", "hiy"));
    }

    // ShapeSpanIterator.c

    private static void registerShapeSpanIteratorInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, SunJava2d.SHAPE_SPAN_ITERATOR, "pData"));
    }

    // SpanClipRenderer.c

    private static void registerShapeSpanClipRendererInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, SunJava2d.REGION, "bands", "endIndex"));
        JNIRuntimeAccess.register(fields(a, "sun.java2d.pipe.RegionIterator",
                "region", "curIndex", "numXbands"));
    }

    // from windows specific code
    // GDIBlitLoops.cpp - empty
    // GDIRenderer.cpp - empty

    private static void registerGDIWindowSurfaceDataInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, "java.lang.Thread"));
        JNIRuntimeAccess.register(method(a, "java.lang.Thread", "currentThread"));
    }

    // WindowsFlags.cpp

    private static void registerWindowsFlagsInitNativeFlags(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, SunJava2d.WINDOWS_FLAGS,
                "d3dEnabled", "d3dSet", "offscreenSharingEnabled", "setHighDPIAware"));
    }
}