/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.awt.GraphicsEnvironment;
import java.io.FilenameFilter;
import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@Platforms(Platform.LINUX.class)
@SuppressWarnings({"static-method", "unused"})
public final class JavaAWTSubstitutions {
    // Checkstyle: stop
    @TargetClass(className = "sun.awt.DebugSettings")
    static final class Target_sun_awt_DebugSettings {

        @Substitute
        void setCTracingOn(boolean enabled) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        void setCTracingOn(boolean enabled, String file) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        void setCTracingOn(boolean enabled, String file, int line) {
            throw new UnsupportedOperationException();
        }
    }

    @TargetClass(className = "sun.java2d.loops.TransformBlit")
    static final class Target_sun_java2d_loops_TransformBlit {

        // Could not find JNI method Java_sun_java2d_loops_TransformBlit_Transform
        @Substitute
        void Transform(sun.java2d.SurfaceData src, sun.java2d.SurfaceData dst,
                        java.awt.Composite comp, sun.java2d.pipe.Region clip,
                        java.awt.geom.AffineTransform at, int hint,
                        int srcx, int srcy, int dstx, int dsty,
                        int width, int height) {
            throw new UnsupportedOperationException();
        }
    }

    @TargetClass(className = "sun.font.FileFontStrike")
    static final class Target_sun_font_FileFontStrike {

        // Java_sun_font_FileFontStrike_initNative belongs to Windows static lib
        @Substitute
        static boolean initNative() {
            throw new UnsupportedOperationException();
        }

        // Java_sun_font_FileFontStrike_initNative belongs to Windows static lib
        @Substitute
        long _getGlyphImageFromWindows(String family,
                        int style,
                        int size,
                        int glyphCode,
                        boolean fracMetrics,
                        int fontDataSize) {
            throw new UnsupportedOperationException();
        }
    }

    @TargetClass(className = "sun.awt.FontConfiguration")
    static final class Target_sun_awt_FontConfiguration {

        // To prevent an attempt to load fonts from java.home
        @Substitute
        public boolean foundOsSpecificFile() {
            return false;
        }

        // Original method throws an exception if java.home is null
        @Substitute
        private void findFontConfigFile() {
        }

        // Called from Target_sun_font_FcFontConfiguration#init() - original method is protected
        @Alias
        protected native void setFontConfiguration();
    }

    // Used in Target_sun_font_FcFontConfiguration#init()
    @TargetClass(className = "sun.font.FontConfigManager", innerClass = "FcCompFont")
    static final class Target_sun_font_FontConfigManager_FcCompFont {
    }

    private static final class SunFontManagerEnabled implements BooleanSupplier {

        @Override
        public boolean getAsBoolean() {
            return !SubstrateOptions.StaticExecutable.getValue();
        }

    }

    // Used in Target_sun_font_FcFontConfiguration#init()
    @TargetClass(className = "sun.font.SunFontManager", onlyWith = SunFontManagerEnabled.class)
    static final class Target_sun_font_SunFontManager {
    }

    // Used in Target_sun_font_FcFontConfiguration#init()
    @TargetClass(className = "sun.awt.FcFontManager")
    static final class Target_sun_awt_FcFontManager {
        // Called from Target_sun_font_FcFontConfiguration#init()
        @Alias
        public synchronized native Target_sun_font_FontConfigManager getFontConfigManager();
    }

    // Used in Target_sun_font_FcFontConfiguration#init()
    @TargetClass(className = "sun.font.FontConfigManager")
    static final class Target_sun_font_FontConfigManager {
        // Called from Target_sun_font_FcFontConfiguration#init() - original method not visible
        @Alias
        native Target_sun_font_FontConfigManager_FcCompFont[] loadFontConfig();

        // Called from Target_sun_font_FcFontConfiguration#init() - original method not visible
        @Alias
        native void populateFontConfig(Target_sun_font_FontConfigManager_FcCompFont[] fcInfo);
    }

    // Used in Target_sun_font_FcFontConfiguration#init()
    @TargetClass(className = "sun.font.FontUtilities")
    static final class Target_sun_font_FontUtilities {
        // Called from Target_sun_font_FcFontConfiguration#init()
        @Alias
        public static native boolean debugFonts();
    }

    @TargetClass(className = "sun.font.FcFontConfiguration")
    static final class Target_sun_font_FcFontConfiguration {
        // Accessed from #init() - original field is private
        @Alias//
        private Target_sun_font_FontConfigManager_FcCompFont[] fcCompFonts;

        // Accessed from #init() - original field is protected
        @Alias//
        protected Target_sun_font_SunFontManager fontManager;

        // Called from #init() - original method is private
        @Alias
        private native void readFcInfo();

        // Called from #init() - original method is private
        @Alias
        private native void writeFcInfo();

        // Called from #init() - original method is private
        @Alias
        private native static void warning(String msg);

        // Original method throws an exception if java.home is null
        @Substitute
        public synchronized boolean init() {
            if (fcCompFonts != null) {
                return true;
            }

            SubstrateUtil.cast(this, Target_sun_awt_FontConfiguration.class).setFontConfiguration();
            readFcInfo();
            Target_sun_awt_FcFontManager fm = SubstrateUtil.cast(fontManager, Target_sun_awt_FcFontManager.class);
            Target_sun_font_FontConfigManager fcm = fm.getFontConfigManager();
            if (fcCompFonts == null) {
                fcCompFonts = fcm.loadFontConfig();
                if (fcCompFonts != null) {
                    try {
                        writeFcInfo();
                    } catch (Exception e) {
                        if (Target_sun_font_FontUtilities.debugFonts()) {
                            warning("Exception writing fcInfo " + e);
                        }
                    }
                } else if (Target_sun_font_FontUtilities.debugFonts()) {
                    warning("Failed to get info from libfontconfig");
                }
            } else {
                fcm.populateFontConfig(fcCompFonts);
            }

            /*-
            The below code was part of the original method but has been removed in the substitution. In a native-image,
            java.home is set to null, so executing it would result in an exception.
            The #getInstalledFallbackFonts method is in charge of installing fallback fonts shipped with the JDK. If the
            fallback font directory does not exist, it is a no-op. As we do not have a JDK available at native-image
            runtime, we can safely remove the call.
            
            // NB already in a privileged block from SGE
            String javaHome = System.getProperty("java.home");
            if (javaHome == null) {
                throw new Error("java.home property not set");
            }
            String javaLib = javaHome + File.separator + "lib";
            getInstalledFallbackFonts(javaLib);
             */

            return fcCompFonts != null; // couldn't load fontconfig.
        }

    }

    /*
     * To prevent AWT linkage error that happens with 'awt_headless' in headless mode, we substitute
     * native methods that depend on 'awt_xawt' library in the call-tree.
     */
    @TargetClass(className = "sun.awt.X11.XToolkit", onlyWith = IsHeadless.class)
    static final class Target_sun_awt_X11_XToolkit {

        @Substitute
        static String getEnv(String key) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static void wakeup_poll() {
            throw new UnsupportedOperationException();
        }

        @Substitute
        void run() {
            throw new UnsupportedOperationException();
        }

        @Substitute
        int getNumberOfButtons() {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static long getDefaultXColormap() {
            throw new UnsupportedOperationException();
        }
    }

    @TargetClass(className = "java.awt.Window", onlyWith = IsHeadless.class)
    static final class Target_java_awt_Window {

        @Substitute
        void dispose() {
            throw new UnsupportedOperationException();
        }

        @Substitute
        void doDispose() {
            throw new UnsupportedOperationException();
        }

        @Substitute
        void closeSplashScreen() {
            throw new UnsupportedOperationException();
        }
    }

    @TargetClass(className = "sun.awt.X11.XWindow", onlyWith = IsHeadless.class)
    static final class Target_sun_awt_X11_XWindow {

        @Substitute
        private static void initIDs() {
            throw new UnsupportedOperationException();
        }
    }

    @TargetClass(className = "sun.awt.X11.XBaseWindow", onlyWith = IsHeadless.class)
    @Delete
    static final class Target_sun_awt_X11_XBaseWindow {
    }

    @TargetClass(className = "sun.awt.X11.XlibWrapper", onlyWith = IsHeadless.class)
    static final class Target_sun_awt_X11_XlibWrapper {

        @Substitute
        static long DisplayWidth(long display, long screen) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static long DefaultScreen(long display) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static long DisplayWidthMM(long display, long screen) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static long InternAtom(long display, String string, int only_if_exists) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static void XSync(long display, int discard) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static long XAllocSizeHints() {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static String[] XTextPropertyToStringList(byte[] bytes, long encoding_atom) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static String XGetAtomName(long display, long atom) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static byte[] getStringBytes(long str_ptr) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static long XAllocWMHints() {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static void XFree(long ptr) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static void memcpy(long dest_ptr, long src_ptr, long length) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static long SetToolkitErrorHandler() {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static long XMaxRequestSize(long display) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static void XChangePropertyS(long display, long window, long atom,
                        long type, int format, int mode, String value) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static int XGetWindowProperty(long display, long window, long atom,
                        long long_offset, long long_length,
                        long delete, long req_type, long actualy_type,
                        long actualy_format, long nitems_ptr,
                        long bytes_after, long data_ptr) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static boolean XAllocColor(long display, long colormap, long screen_in_out) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static long XGetSelectionOwner(long display, long selection) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static long XCreateBitmapFromData(long display, long drawable, long data, int width, int height) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static void XFreePixmap(long display, long pixmap) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static void XChangeWindowAttributes(long display, long window, long valuemask, long attributes) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static long RootWindow(long display, long screen_number) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static long XCreatePixmapCursor(long display, long source, long mask, long fore, long back, int x, int y) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static String ServerVendor(long display) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static boolean XQueryBestCursor(long display, long drawable, int width, int height, long width_return, long height_return) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static void XResizeWindow(long display, long window, int width, int height) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static void XReparentWindow(long display, long window, long parent, int x, int y) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static void XDeleteProperty(long display, long window, long atom) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static int XGetWMNormalHints(long display, long window, long hints, long supplied_return) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static void XBell(long display, int percent) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static boolean XShapeQueryExtension(long display, long event_base_return, long error_base_return) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static void XMoveResizeWindow(long display, long window, int x, int y, int width, int height) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static void XMoveWindow(long display, long window, int x, int y) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static void SetZOrder(long display, long window, long above) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static int XQueryTree(long display, long window, long root_return, long parent_return, long children_return, long nchildren_return) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static void SetRectangularShape(long display, long window,
                        int lox, int loy, int hix, int hiy,
                        sun.java2d.pipe.Region region) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static void XFlush(long display) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static void XMapRaised(long display, long window) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static int XSendEvent(long display, long window, boolean propagate, long event_mask, long event) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static void XChangePropertyImpl(long display, long window, long atom,
                        long type, int format, int mode, long data,
                        int nelements) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static void XUnmapWindow(long display, long window) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static boolean XQueryPointer(long display, long window, long root_return, long child_return, long root_x_return, long root_y_return, long win_x_return, long win_y_return, long mask_return) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static int XGetWindowAttributes(long display, long window, long attr_ptr) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static void XUngrabServer(long display) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static int XKeysymToKeycode(long display, long keysym) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static long XGetModifierMapping(long display) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static int ScreenCount(long display) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static int XCreateFontCursor(long display, int shape) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static void XFreeModifiermap(long keymap) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static void XSelectInput(long display, long window, long event_mask) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        static void XGrabServer(long display) {
            throw new UnsupportedOperationException();
        }
    }

    @TargetClass(className = "sun.java2d.xr.XRBackendNative", onlyWith = IsHeadless.class)
    static final class Target_sun_java2d_xr_XRBackendNative {

        @Substitute
        static void initIDs() {
            throw new UnsupportedOperationException();
        }

        @Substitute
        private static void XRAddGlyphsNative(int glyphSet,
                        long[] glyphInfoPtrs,
                        int glyphCnt,
                        byte[] pixelData,
                        int pixelDataLength) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        private static int XRCreateLinearGradientPaintNative(float[] fractionsArray,
                        short[] pixelsArray,
                        int x1, int y1, int x2, int y2,
                        int numStops, int repeat) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        private static void XRSetClipNative(long dst,
                        int x1, int y1, int x2, int y2,
                        sun.java2d.pipe.Region clip, boolean isGC) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        private static void XRenderCompositeTextNative(int op, int src, int dst,
                        int srcX, int srcY, long maskFormat,
                        int[] eltArray, int[] glyphIDs, int eltCnt,
                        int glyphCnt) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        private static void GCRectanglesNative(int drawable, long gc,
                        int[] rectArray, int rectCnt) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        private static void XRFreeGlyphsNative(int glyphSet,
                        int[] gids, int idCnt) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        private static void XRenderRectanglesNative(int dst, byte op,
                        short red, short green,
                        short blue, short alpha,
                        int[] rects, int rectCnt) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        private static int XRCreateRadialGradientPaintNative(float[] fractionsArray,
                        short[] pixelsArray, int numStops,
                        int centerX, int centerY,
                        int innerRadius, int outerRadius,
                        int repeat) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        public void renderComposite(byte op, int src, int mask,
                        int dst, int srcX, int srcY,
                        int maskX, int maskY, int dstX, int dstY,
                        int width, int height) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        public void setGCMode(long gc, boolean copy) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        public void freePicture(int picture) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        public void setPictureRepeat(int picture, int repeat) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        public void setGCForeground(long gc, int pixel) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        public void setFilter(int picture, int filter) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        private void renderRectangle(int dst, byte op,
                        short red, short green,
                        short blue, short alpha,
                        int x, int y, int width, int height) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        private void XRSetTransformNative(int pic,
                        int m00, int m01, int m02,
                        int m10, int m11, int m12) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        public void setGCExposures(long gc, boolean exposure) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        public void copyArea(int src, int dst, long gc,
                        int srcx, int srcy, int width, int height,
                        int dstx, int dsty) {
            throw new UnsupportedOperationException();
        }
    }

    @TargetClass(className = "sun.awt.X11InputMethodBase", onlyWith = IsHeadless.class)
    static final class Target_sun_awt_X11InputMethodBase {

        @Substitute
        static void initIDs() {
            throw new UnsupportedOperationException();
        }

        @Substitute
        void turnoffStatusWindow() {
            throw new UnsupportedOperationException();
        }

        @Substitute
        boolean setCompositionEnabledNative(boolean enable) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        boolean isCompositionEnabledNative() {
            throw new UnsupportedOperationException();
        }

        @Substitute
        String resetXIC() {
            throw new UnsupportedOperationException();
        }

        @Substitute
        void disposeXIC() {
            throw new UnsupportedOperationException();
        }
    }

    @TargetClass(className = "sun.awt.UNIXToolkit", onlyWith = IsHeadless.class)
    static final class Target_sun_awt_UNIXToolkit {

        @Substitute
        private static boolean load_gtk(int version, boolean verbose) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        public void sync() {
            throw new UnsupportedOperationException();
        }

        @Substitute
        public java.awt.image.BufferedImage getStockIcon(final int widgetType, final String stockId,
                        final int iconSize, final int direction,
                        final String detail) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        public boolean isNativeGTKAvailable() {
            throw new UnsupportedOperationException();
        }

        @Substitute
        private boolean gtkCheckVersionImpl(int major, int minor, int micro) {
            throw new UnsupportedOperationException();
        }
    }

    @TargetClass(className = "sun.awt.X11GraphicsConfig", onlyWith = IsHeadless.class)
    static final class Target_sun_awt_X11GraphicsConfig {

        @Substitute
        static void initIDs() {
            throw new UnsupportedOperationException();
        }

    }

    /**
     * This is necessary to workaround `javac` warnings that cannot be suppressed. For example:
     *
     * <pre>
     * JavaAWTSubstitutions.java:574: warning: ComponentPeer is internal proprietary API and may be removed in a future release
     *       private void nativeSetSource(java.awt.peer.ComponentPeer peer) {
     *                                                 ^
     * </pre>
     */
    @TargetClass(className = "java.awt.peer.ComponentPeer", onlyWith = IsHeadless.class)
    static final class Target_java_awt_peer_ComponentPeer {
    }

    @TargetClass(className = "java.awt.AWTEvent", onlyWith = IsHeadless.class)
    static final class Target_java_awt_AWTEvent {

        @Substitute
        private void nativeSetSource(Target_java_awt_peer_ComponentPeer peer) {
            throw new UnsupportedOperationException();
        }
    }

    @TargetClass(className = "sun.java2d.opengl.OGLSurfaceData", onlyWith = IsHeadless.class)
    static final class Target_sun_java2d_opengl_OGLSurfaceData {

        @Substitute
        protected boolean initFlipBackbuffer(long pData) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        protected boolean initTexture(long pData,
                        boolean isOpaque, boolean texNonPow2,
                        boolean texRect,
                        int width, int height) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        protected boolean initFBObject(long pData,
                        boolean isOpaque, boolean texNonPow2,
                        boolean texRect,
                        int width, int height) {
            throw new UnsupportedOperationException();
        }
    }

    @TargetClass(className = "sun.java2d.opengl.OGLRenderQueue", onlyWith = IsHeadless.class)
    static final class Target_sun_java2d_opengl_OGLRenderQueue {

        @Substitute
        private void flushBuffer(long buf, int limit) {
            throw new UnsupportedOperationException();
        }
    }

    @TargetClass(className = "sun.awt.X11.XTaskbarPeer", onlyWith = IsHeadless.class)
    static final class Target_sun_awt_X11_XTaskbarPeer {

        @Substitute
        private static void initWithLock() {
            throw new UnsupportedOperationException();
        }
    }

    @TargetClass(className = "sun.awt.X11.XDesktopPeer", onlyWith = IsHeadless.class)
    static final class Target_sun_awt_X11_XDesktopPeer {

        @Substitute
        private static void initWithLock() {
            throw new UnsupportedOperationException();
        }
    }

    @TargetClass(className = "sun.awt.X11.GtkFileDialogPeer", onlyWith = IsHeadless.class)
    static final class Target_sun_awt_X11_GtkFileDialogPeer {

        @Substitute
        private static void initIDs() {
            throw new UnsupportedOperationException();
        }

        @Substitute
        private void quit() {
            throw new UnsupportedOperationException();
        }

        @Substitute
        private void run(String title, int mode, String dir, String file,
                        FilenameFilter filter, boolean isMultipleMode, int x, int y) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        public void setBounds(int x, int y, int width, int height, int op) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        public void toFront() {
            throw new UnsupportedOperationException();
        }
    }

    @TargetClass(className = "sun.awt.X11.XRobotPeer", onlyWith = IsHeadless.class)
    static final class Target_sun_awt_X11_XRobotPeer {

        @Substitute
        private static void loadNativeLibraries() {
            throw new UnsupportedOperationException();
        }
    }

    @TargetClass(className = "sun.awt.X11.XInputMethod", onlyWith = IsHeadless.class)
    static final class Target_sun_awt_X11_XInputMethod {

        @Substitute
        private boolean openXIMNative(long display) {
            throw new UnsupportedOperationException();
        }
    }

    /*
     * These classes transitively use libfontconfig. All libfontconfig uses are done through dlsym,
     * which doesn't work in a static executable. Simply not linking against fontconfig is not
     * enough - we must @Delete these classes to prevent linker errors.
     */

    @TargetClass(className = "sun.java2d.loops.DrawGlyphListAA", onlyWith = FontsDisabled.class)
    @Delete(value = FontsDisabled.FONTS_DISABLED_REASON)
    static final class Target_sun_java2d_loops_DrawGlyphListAA {
    }

    @TargetClass(className = "sun.java2d.loops.DrawGlyphList", onlyWith = FontsDisabled.class)
    @Delete(value = FontsDisabled.FONTS_DISABLED_REASON)
    static final class Target_sun_java2d_loops_DrawGlyphList {
    }

    @TargetClass(className = "sun.java2d.loops.DrawGlyphListLCD", onlyWith = FontsDisabled.class)
    @Delete(value = FontsDisabled.FONTS_DISABLED_REASON)
    static final class Target_sun_java2d_loops_DrawGlyphListLCD {
    }

    @TargetClass(className = "sun.font.SunLayoutEngine", onlyWith = FontsDisabled.class)
    @Delete(value = FontsDisabled.FONTS_DISABLED_REASON)
    static final class Target_sun_font_SunLayoutEngine {
    }

    @TargetClass(className = "sun.font.FreetypeFontScaler", onlyWith = FontsDisabled.class)
    @Delete(value = FontsDisabled.FONTS_DISABLED_REASON)
    static final class Target_sun_font_FreetypeFontScaler {
    }

    @TargetClass(className = "sun.font.NativeFont", onlyWith = FontsDisabled.class)
    @Delete(value = FontsDisabled.FONTS_DISABLED_REASON)
    static final class Target_sun_font_NativeFont {
    }

    @TargetClass(className = "sun.font.StrikeCache", onlyWith = FontsDisabled.class)
    @Delete(value = FontsDisabled.FONTS_DISABLED_REASON)
    static final class Target_sun_font_StrikeCache {
    }

    @TargetClass(className = "sun.font.NativeStrikeDisposer", onlyWith = FontsDisabled.class)
    @Delete(value = FontsDisabled.FONTS_DISABLED_REASON)
    static final class Target_sun_font_NativeStrikeDisposer {
    }

    @TargetClass(className = "sun.font.NativeStrike", onlyWith = FontsDisabled.class)
    @Delete(value = FontsDisabled.FONTS_DISABLED_REASON)
    static final class Target_sun_font_NativeStrike {
    }

    @TargetClass(className = "sun.font.SunFontManager", onlyWith = FontsDisabled.class)
    @Delete(value = FontsDisabled.FONTS_DISABLED_REASON)
    static final class Target_sun_font_SunFontManagerDeleted {
    }

    @TargetClass(className = "sun.font.NullFontScaler", onlyWith = FontsDisabled.class)
    @Delete(value = FontsDisabled.FONTS_DISABLED_REASON)
    static final class Target_sun_font_NullFontScaler {
    }

    // To support headless mode
    static class IsHeadless implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return GraphicsEnvironment.isHeadless();
        }
    }
    // Checkstyle: resume

    static class FontsDisabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return SubstrateOptions.StaticExecutable.getValue();
        }

        public static final String FONTS_DISABLED_REASON = "AWT uses fontconfig to implement font related functionality on Linux. All fontconfig uses happen through dlsym which doesn't work in a static executable. " +
                        "Try avoiding the use of this class in the project or turn off static executable generation.";
    }
}
