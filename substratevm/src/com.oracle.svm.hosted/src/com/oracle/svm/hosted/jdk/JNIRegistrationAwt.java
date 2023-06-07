/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, BELLSOFT. All rights reserved.
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
import java.util.stream.Stream;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.RuntimeJNIAccess;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.nativeimage.impl.RuntimeResourceSupport;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.JNIRegistrationUtil;

@Platforms({InternalPlatform.PLATFORM_JNI.class})
@AutomaticallyRegisteredFeature
@SuppressWarnings({"unused"})
public class JNIRegistrationAwt extends JNIRegistrationUtil implements InternalFeature {

    private static boolean isSupported() {
        return Platform.includedIn(Platform.LINUX.class) || Platform.includedIn(Platform.WINDOWS.class);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {

        if (!isSupported()) {
            return;
        }

        access.registerReachabilityHandler(JNIRegistrationAwt::registerToolkit,
                        clazz(access, "java.awt.Toolkit"));

        access.registerReachabilityHandler(JNIRegistrationAwt::registerGraphicsEnvironment,
                        clazz(access, "java.awt.GraphicsEnvironment"));

        access.registerReachabilityHandler(JNIRegistrationAwt::registerJPEG,
                        clazz(access, "sun.awt.image.JPEGImageDecoder"),
                        clazz(access, "com.sun.imageio.plugins.jpeg.JPEGImageReader"),
                        clazz(access, "com.sun.imageio.plugins.jpeg.JPEGImageWriter"));

        access.registerReachabilityHandler(JNIRegistrationAwt::registerFontManager,
                        clazz(access, "java.awt.Font"),
                        clazz(access, "sun.font.SunFontManager"),
                        clazz(access, "sun.font.FontManagerFactory"),
                        clazz(access, "sun.font.FontManagerNativeLibrary"));

        access.registerReachabilityHandler(JNIRegistrationAwt::registerColorProfiles,
                        clazz(access, "java.awt.color.ICC_Profile"));

        access.registerReachabilityHandler(JNIRegistrationAwt::registerFlavorMapProps,
                        clazz(access, "java.awt.datatransfer.SystemFlavorMap"));

        access.registerReachabilityHandler(JNIRegistrationAwt::registerRTFReaderCharsets,
                        clazz(access, "javax.swing.text.rtf.RTFReader"));

        access.registerReachabilityHandler(JNIRegistrationAwt::registerHtml32bdtd,
                        clazz(access, "javax.swing.text.html.HTMLEditorKit"));

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

            access.registerReachabilityHandler(JNIRegistrationAwt::registerShellFolderManager,
                            clazz(access, "sun.swing.FilePane"));

            access.registerReachabilityHandler(JNIRegistrationAwt::registerMetalLookAndFeel,
                            clazz(access, "javax.swing.plaf.metal.MetalLookAndFeel"));

            if (isLinux()) {
                access.registerReachabilityHandler(JNIRegistrationAwt::registerGtkFileDialog,
                                clazz(access, "sun.awt.X11.GtkFileDialogPeer"));
                access.registerReachabilityHandler(JNIRegistrationAwt::registerGTKLookAndFeel,
                                clazz(access, "com.sun.java.swing.plaf.gtk.GTKLookAndFeel"));
            } else if (isWindows()) {
                access.registerReachabilityHandler(JNIRegistrationAwt::registerEvent,
                                clazz(access, "java.awt.Event"));

                access.registerReachabilityHandler(JNIRegistrationAwt::registerAWTEvent,
                                clazz(access, "java.awt.AWTEvent"));

                access.registerReachabilityHandler(JNIRegistrationAwt::registerInputEvent,
                                clazz(access, "java.awt.event.InputEvent"));

                access.registerReachabilityHandler(JNIRegistrationAwt::registerMouseEvents,
                                clazz(access, "java.awt.event.MouseEvent"));

                access.registerReachabilityHandler(JNIRegistrationAwt::registerCursor,
                                clazz(access, "java.awt.Cursor"));

                access.registerReachabilityHandler(JNIRegistrationAwt::registerWindow,
                                clazz(access, "java.awt.Window"));

                access.registerReachabilityHandler(JNIRegistrationAwt::registerFrame,
                                clazz(access, "java.awt.Frame"));

                access.registerReachabilityHandler(JNIRegistrationAwt::registerPanel,
                                clazz(access, "java.awt.Panel"));

                access.registerReachabilityHandler(JNIRegistrationAwt::registerDialog,
                                clazz(access, "java.awt.Dialog"));

                access.registerReachabilityHandler(JNIRegistrationAwt::registerFileDialog,
                                clazz(access, "java.awt.FileDialog"));

                access.registerReachabilityHandler(JNIRegistrationAwt::registerMenu,
                                clazz(access, "java.awt.Menu"),
                                clazz(access, "java.awt.PopupMenu"));

                access.registerReachabilityHandler(JNIRegistrationAwt::registerTrayIcon,
                                clazz(access, "java.awt.TrayIcon"));

                access.registerReachabilityHandler(JNIRegistrationAwt::registerWindowsLookAndFeel,
                                clazz(access, "com.sun.java.swing.plaf.windows.WindowsLookAndFeel"));
            }
        }
    }

    private static void registerToolkit(DuringAnalysisAccess access) {
        RuntimeJNIAccess.register(java.awt.Toolkit.class);
        RuntimeJNIAccess.register(method(access, "java.awt.Toolkit", "getDefaultToolkit"));
        RuntimeJNIAccess.register(method(access, "java.awt.Toolkit", "getFontMetrics", java.awt.Font.class));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.SunToolkit"));
        RuntimeJNIAccess.register(method(access, "sun.awt.SunToolkit", "awtLock"));
        RuntimeJNIAccess.register(method(access, "sun.awt.SunToolkit", "awtLockNotify"));
        RuntimeJNIAccess.register(method(access, "sun.awt.SunToolkit", "awtLockNotifyAll"));
        RuntimeJNIAccess.register(method(access, "sun.awt.SunToolkit", "awtLockWait", long.class));
        RuntimeJNIAccess.register(method(access, "sun.awt.SunToolkit", "awtUnlock"));
        RuntimeJNIAccess.register(method(access, "sun.awt.SunToolkit", "isTouchKeyboardAutoShowEnabled"));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.AWTAutoShutdown"));
        RuntimeJNIAccess.register(method(access, "sun.awt.AWTAutoShutdown", "notifyToolkitThreadBusy"));
        RuntimeJNIAccess.register(method(access, "sun.awt.AWTAutoShutdown", "notifyToolkitThreadFree"));

        RuntimeJNIAccess.register(java.awt.Insets.class);
        RuntimeJNIAccess.register(constructor(access, "java.awt.Insets", int.class, int.class, int.class, int.class));
        RuntimeJNIAccess.register(fields(access, "java.awt.Insets", "bottom", "left", "right", "top"));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.image.SunVolatileImage"));
        RuntimeJNIAccess.register(fields(access, "sun.awt.image.SunVolatileImage", "volSurfaceManager"));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.image.VolatileSurfaceManager"));
        RuntimeJNIAccess.register(fields(access, "sun.awt.image.VolatileSurfaceManager", "sdCurrent"));

        RuntimeJNIAccess.register(java.awt.desktop.UserSessionEvent.Reason.class);
        RuntimeJNIAccess.register(fields(access, "java.awt.desktop.UserSessionEvent$Reason", "CONSOLE", "LOCK", "REMOTE", "UNSPECIFIED"));

        if (isLinux()) {
            registerLinuxToolkit(access);
        } else if (isWindows()) {
            registerWindowsToolkit(access);
        }

        RuntimeJNIAccess.register(java.util.HashMap.class);
        RuntimeJNIAccess.register(method(access, "java.util.HashMap", "containsKey",
                        java.lang.Object.class));
        RuntimeJNIAccess.register(method(access, "java.util.HashMap", "put",
                        java.lang.Object.class, java.lang.Object.class));

        RuntimeJNIAccess.register(java.lang.Thread.class);
        RuntimeJNIAccess.register(method(access, "java.lang.Thread", "yield"));
        RuntimeJNIAccess.register(method(access, "java.lang.Thread", "currentThread"));

        registerSystemColor(access);
        registerDragAndDrop(access);
    }

    private static void registerLinuxToolkit(DuringAnalysisAccess access) {
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
        RuntimeJNIAccess.register(method(access, "sun.awt.X11.XErrorHandlerUtil", "init",
                        long.class));
        RuntimeJNIAccess.register(method(access, "sun.awt.X11.XErrorHandlerUtil", "globalErrorHandler",
                        long.class, long.class));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.X11.XToolkit"));
        RuntimeJNIAccess.register(fields(access, "sun.awt.X11.XToolkit",
                        "modLockIsShiftLock", "numLockMask"));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.X11GraphicsConfig"));
        RuntimeJNIAccess.register(fields(access, "sun.awt.X11GraphicsConfig",
                        "aData", "bitsPerPixel"));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.X11GraphicsDevice"));
        RuntimeJNIAccess.register(fields(access, "sun.awt.X11GraphicsDevice", "screen"));
        RuntimeJNIAccess.register(method(access, "sun.awt.X11GraphicsDevice", "addDoubleBufferVisual",
                        int.class));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.X11InputMethodBase"));
        RuntimeJNIAccess.register(fields(access, "sun.awt.X11InputMethodBase", "pData"));

        RuntimeJNIAccess.register(java.awt.Rectangle.class);
        RuntimeJNIAccess.register(constructor(access, "java.awt.Rectangle",
                        int.class, int.class, int.class, int.class));

        RuntimeJNIAccess.register(java.awt.DisplayMode.class);
        RuntimeJNIAccess.register(constructor(access, "java.awt.DisplayMode",
                        int.class, int.class, int.class, int.class));

        RuntimeJNIAccess.register(clazz(access, "java.awt.AWTError"));
        RuntimeJNIAccess.register(constructor(access, "java.awt.AWTError",
                        java.lang.String.class));

        RuntimeJNIAccess.register(method(access, "java.lang.System", "load",
                        java.lang.String.class));
    }

    private static void registerWindowsToolkit(DuringAnalysisAccess access) {
        RuntimeJNIAccess.register(clazz(access, "sun.awt.windows.WToolkit"));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WToolkit", "windowsSettingChange"));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WToolkit", "displayChanged"));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WToolkit", "paletteChanged"));

        RuntimeJNIAccess.register(clazz(access, "sun.java2d.windows.WindowsFlags"));
        RuntimeJNIAccess.register(fields(access, "sun.java2d.windows.WindowsFlags",
                        "d3dEnabled", "d3dSet", "offscreenSharingEnabled", "setHighDPIAware"));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.windows.WDesktopPeer"));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WDesktopPeer", "systemSleepCallback",
                        boolean.class));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WDesktopPeer", "userSessionCallback",
                        boolean.class, java.awt.desktop.UserSessionEvent.Reason.class));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.windows.WDesktopProperties"));
        RuntimeJNIAccess.register(fields(access, "sun.awt.windows.WDesktopProperties", "pData"));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WDesktopProperties", "setStringProperty",
                        java.lang.String.class, java.lang.String.class));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WDesktopProperties", "setBooleanProperty",
                        java.lang.String.class, boolean.class));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WDesktopProperties", "setSoundProperty",
                        java.lang.String.class, java.lang.String.class));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WDesktopProperties", "setFontProperty",
                        java.lang.String.class, java.lang.String.class, int.class, int.class));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WDesktopProperties", "setIntegerProperty",
                        java.lang.String.class, int.class));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WDesktopProperties", "setColorProperty",
                        java.lang.String.class, int.class, int.class, int.class));

        RuntimeJNIAccess.register(clazz(access, "com.sun.java.swing.plaf.windows.WindowsPopupWindow"));
        RuntimeJNIAccess.register(clazz(access, "javax.swing.Popup$HeavyWeightWindow"));

        RuntimeJNIAccess.register(method(access, "java.lang.System", "getProperty",
                        java.lang.String.class));
    }

    private static void registerSystemColor(DuringAnalysisAccess access) {

        RuntimeReflection.register(java.awt.SystemColor.class);

        RuntimeReflection.register(fields(access, "java.awt.SystemColor",
                        "activeCaption",
                        "activeCaptionBorder",
                        "activeCaptionText",
                        "control",
                        "controlDkShadow",
                        "controlHighlight",
                        "controlLtHighlight",
                        "controlShadow",
                        "controlText",
                        "desktop",
                        "inactiveCaption",
                        "inactiveCaptionBorder",
                        "inactiveCaptionText",
                        "info",
                        "infoText",
                        "menu",
                        "menuText",
                        "scrollbar",
                        "text",
                        "textHighlight",
                        "textHighlightText",
                        "textInactiveText",
                        "textText",
                        "window",
                        "windowBorder",
                        "windowText"));
    }

    private static void registerDragAndDrop(DuringAnalysisAccess access) {
        RuntimeJNIAccess.register(clazz(access, "sun.awt.dnd.SunDragSourceContextPeer"));
        RuntimeJNIAccess.register(method(access, "sun.awt.dnd.SunDragSourceContextPeer", "dragEnter",
                        int.class, int.class, int.class, int.class));
        RuntimeJNIAccess.register(method(access, "sun.awt.dnd.SunDragSourceContextPeer", "dragExit",
                        int.class, int.class));
        RuntimeJNIAccess.register(method(access, "sun.awt.dnd.SunDragSourceContextPeer", "operationChanged",
                        int.class, int.class, int.class, int.class));
        RuntimeJNIAccess.register(method(access, "sun.awt.dnd.SunDragSourceContextPeer", "dragDropFinished",
                        boolean.class, int.class, int.class, int.class));
        RuntimeJNIAccess.register(method(access, "sun.awt.dnd.SunDragSourceContextPeer", "dragMotion",
                        int.class, int.class, int.class, int.class));
        RuntimeJNIAccess.register(method(access, "sun.awt.dnd.SunDragSourceContextPeer", "dragMouseMoved",
                        int.class, int.class, int.class, int.class));
        RuntimeJNIAccess.register(method(access, "sun.awt.dnd.SunDropTargetContextPeer", "handleExitMessage",
                        java.awt.Component.class, long.class));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.dnd.SunDropTargetContextPeer"));
        RuntimeJNIAccess.register(method(access, "sun.awt.dnd.SunDropTargetContextPeer", "handleMotionMessage",
                        java.awt.Component.class, int.class, int.class, int.class, int.class, long[].class, long.class));
        RuntimeJNIAccess.register(method(access, "sun.awt.dnd.SunDropTargetContextPeer", "handleDropMessage",
                        java.awt.Component.class, int.class, int.class, int.class, int.class, long[].class, long.class));
        RuntimeJNIAccess.register(method(access, "sun.awt.dnd.SunDropTargetContextPeer", "handleEnterMessage",
                        java.awt.Component.class, int.class, int.class, int.class, int.class, long[].class, long.class));

        if (isWindows()) {
            RuntimeJNIAccess.register(clazz(access, "sun.awt.windows.WDragSourceContextPeer"));

            RuntimeJNIAccess.register(clazz(access, "sun.awt.windows.WDropTargetContextPeer"));
            RuntimeJNIAccess.register(method(access, "sun.awt.windows.WDropTargetContextPeer", "getWDropTargetContextPeer"));
            RuntimeJNIAccess.register(method(access, "sun.awt.windows.WDropTargetContextPeer", "getFileStream",
                            java.lang.String.class, long.class));
            RuntimeJNIAccess.register(method(access, "sun.awt.windows.WDropTargetContextPeer", "getIStream",
                            long.class));
        }
    }

    private static void registerGraphicsEnvironment(DuringAnalysisAccess access) {
        RuntimeJNIAccess.register(method(access, "java.awt.GraphicsEnvironment", "isHeadless"));
        RuntimeJNIAccess.register(method(access, "java.awt.GraphicsEnvironment", "getLocalGraphicsEnvironment"));

        RuntimeJNIAccess.register(clazz(access, "sun.java2d.SunGraphicsEnvironment"));
        RuntimeJNIAccess.register(method(access, "sun.java2d.SunGraphicsEnvironment", "isDisplayLocal"));

        RuntimeJNIAccess.register(clazz(access, "java.lang.Exception"));
        RuntimeJNIAccess.register(constructor(access, "java.lang.Exception",
                        java.lang.String.class));

        if (isWindows()) {
            RuntimeJNIAccess.register(clazz(access, "sun.awt.Win32GraphicsEnvironment"));
            RuntimeJNIAccess.register(method(access, "sun.awt.Win32GraphicsEnvironment", "dwmCompositionChanged",
                            boolean.class));

            RuntimeJNIAccess.register(clazz(access, "sun.awt.Win32GraphicsConfig"));
            RuntimeJNIAccess.register(fields(access, "sun.awt.Win32GraphicsConfig", "visual"));

            RuntimeJNIAccess.register(fields(access, "sun.awt.Win32GraphicsDevice", "dynamicColorModel"));
            RuntimeJNIAccess.register(clazz(access, "sun.awt.Win32GraphicsDevice"));

            RuntimeJNIAccess.register(clazz(access, "java.awt.AWTError"));
            RuntimeJNIAccess.register(constructor(access, "java.awt.AWTError", java.lang.String.class));
        }
    }

    private static void registerJPEG(DuringAnalysisAccess access) {

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

    private static void registerFontManager(DuringAnalysisAccess access) {

        RuntimeJNIAccess.register(clazz(access, "sun.font.GlyphLayout$GVData"));
        RuntimeJNIAccess.register(fields(access, "sun.font.GlyphLayout$GVData",
                        "_count", "_flags", "_glyphs", "_indices", "_positions"));
        RuntimeJNIAccess.register(method(access, "sun.font.GlyphLayout$GVData",
                        "grow"));

        RuntimeJNIAccess.register(clazz(access, "sun.font.CharToGlyphMapper"));
        RuntimeJNIAccess.register(method(access, "sun.font.CharToGlyphMapper", "charToGlyph", int.class));

        RuntimeJNIAccess.register(clazz(access, "sun.font.Font2D"));
        RuntimeJNIAccess.register(method(access, "sun.font.Font2D", "canDisplay",
                        char.class));
        RuntimeJNIAccess.register(method(access, "sun.font.Font2D", "charToGlyph",
                        int.class));
        RuntimeJNIAccess.register(method(access, "sun.font.Font2D", "charToVariationGlyph",
                        int.class, int.class));
        RuntimeJNIAccess.register(method(access, "sun.font.Font2D", "getMapper"));
        RuntimeJNIAccess.register(method(access, "sun.font.Font2D", "getTableBytes",
                        int.class));

        RuntimeJNIAccess.register(clazz(access, "sun.font.FontStrike"));
        RuntimeJNIAccess.register(method(access, "sun.font.FontStrike", "getGlyphMetrics",
                        int.class));

        RuntimeJNIAccess.register(clazz(access, "sun.font.FreetypeFontScaler"));
        RuntimeJNIAccess.register(method(access, "sun.font.FreetypeFontScaler", "invalidateScaler"));

        RuntimeJNIAccess.register(clazz(access, "sun.font.GlyphList"));
        RuntimeJNIAccess.register(fields(access, "sun.font.GlyphList",
                        "images", "lcdRGBOrder", "lcdSubPixPos", "len", "positions",
                        "usePositions", "x", "y", "gposx", "gposy"));

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
        RuntimeJNIAccess.register(method(access, "sun.font.TrueTypeFont", "readBytes",
                        int.class, int.class));

        RuntimeJNIAccess.register(clazz(access, "sun.font.Type1Font"));
        RuntimeJNIAccess.register(method(access, "sun.font.Type1Font", "readFile",
                        java.nio.ByteBuffer.class));

        RuntimeJNIAccess.register(java.lang.String.class);
        RuntimeJNIAccess.register(method(access, "java.lang.String", "toLowerCase",
                        java.util.Locale.class));

        RuntimeJNIAccess.register(java.util.Locale.class);

        RuntimeJNIAccess.register(java.util.ArrayList.class);
        RuntimeJNIAccess.register(constructor(access, "java.util.ArrayList"));
        RuntimeJNIAccess.register(constructor(access, "java.util.ArrayList",
                        int.class));
        RuntimeJNIAccess.register(method(access, "java.util.ArrayList", "add",
                        java.lang.Object.class));
        RuntimeJNIAccess.register(method(access, "java.util.ArrayList", "contains",
                        java.lang.Object.class));

        if (JavaVersionUtil.JAVA_SPEC >= 19) {
            // JDK-8269223 -Xcheck:jni WARNINGs working with fonts on Linux
            RuntimeJNIAccess.register(clazz(access, "sun.font.FontUtilities"));
            RuntimeJNIAccess.register(method(access, "sun.font.FontUtilities", "debugFonts"));
        }

        if (JavaVersionUtil.JAVA_SPEC <= 17) {
            // JDK-8273581 Change the mechanism by which JDK loads the platform-specific FontManager
            // class
            if (isLinux()) {
                RuntimeReflection.register(clazz(access, "sun.awt.X11FontManager"));
                RuntimeReflection.register(constructor(access, "sun.awt.X11FontManager"));
            } else if (isWindows()) {
                RuntimeReflection.register(clazz(access, "sun.awt.Win32FontManager"));
                RuntimeReflection.register(constructor(access, "sun.awt.Win32FontManager"));
            }
        }

        if (isLinux()) {
            RuntimeJNIAccess.register(clazz(access, "sun.font.FontConfigManager$FontConfigInfo"));
            RuntimeJNIAccess.register(fields(access, "sun.font.FontConfigManager$FontConfigInfo",
                            "fcVersion", "cacheDirs"));
            RuntimeJNIAccess.register(clazz(access, "sun.font.FontConfigManager$FcCompFont"));
            RuntimeJNIAccess.register(fields(access, "sun.font.FontConfigManager$FcCompFont",
                            "fcName", "firstFont", "allFonts"));
            RuntimeJNIAccess.register(clazz(access, "sun.font.FontConfigManager$FontConfigFont"));
            RuntimeJNIAccess.register(constructor(access, "sun.font.FontConfigManager$FontConfigFont"));
            RuntimeJNIAccess.register(fields(access, "sun.font.FontConfigManager$FontConfigFont",
                            "familyName", "styleStr", "fullName", "fontFile"));
        } else if (isWindows()) {
            RuntimeJNIAccess.register(java.awt.Font.class);
            RuntimeJNIAccess.register(method(access, "java.awt.Font", "getFontPeer"));
            RuntimeJNIAccess.register(method(access, "java.awt.Font", "getFont", java.lang.String.class));
            RuntimeJNIAccess.register(fields(access, "java.awt.Font", "name", "pData", "size", "style"));
            RuntimeJNIAccess.register(method(access, "java.awt.Font", "getFamily_NoClientCode"));
            RuntimeJNIAccess.register(method(access, "java.awt.Font", "getName"));

            RuntimeJNIAccess.register(clazz(access, "sun.awt.PlatformFont"));
            RuntimeJNIAccess.register(fields(access, "sun.awt.PlatformFont", "componentFonts", "fontConfig"));
            RuntimeJNIAccess.register(method(access, "sun.awt.PlatformFont", "makeConvertedMultiFontString",
                            java.lang.String.class));

            RuntimeJNIAccess.register(fields(access, "java.awt.FontMetrics", "font"));
            RuntimeJNIAccess.register(method(access, "java.awt.FontMetrics", "getHeight"));

            RuntimeJNIAccess.register(fields(access, "sun.awt.FontDescriptor", "nativeName", "useUnicode"));

            RuntimeJNIAccess.register(clazz(access, "sun.awt.windows.WFontPeer"));
            RuntimeJNIAccess.register(fields(access, "sun.awt.windows.WFontPeer", "textComponentFontName"));

            RuntimeJNIAccess.register(clazz(access, "sun.font.FontDesignMetrics"));
            RuntimeJNIAccess.register(method(access, "sun.font.FontDesignMetrics", "getHeight"));
        }
    }

    private static void registerColorProfiles(DuringAnalysisAccess duringAnalysisAccess) {
        ImageSingletons.lookup(RuntimeResourceSupport.class)
                        .addResources(ConfigurationCondition.alwaysTrue(), "sun.java2d.cmm.profiles.*");
    }

    private static void registerFlavorMapProps(DuringAnalysisAccess duringAnalysisAccess) {
        ImageSingletons.lookup(RuntimeResourceSupport.class)
                        .addResources(ConfigurationCondition.alwaysTrue(), "sun.datatransfer.resources.flavormap.properties");
    }

    private static void registerRTFReaderCharsets(DuringAnalysisAccess duringAnalysisAccess) {
        ImageSingletons.lookup(RuntimeResourceSupport.class)
                        .addResources(ConfigurationCondition.alwaysTrue(), "javax.swing.text.rtf.charsets.*");
    }

    private static void registerHtml32bdtd(DuringAnalysisAccess duringAnalysisAccess) {

        RuntimeReflection.register(clazz(duringAnalysisAccess, "javax.swing.text.html.HTMLEditorKit"));
        RuntimeReflection.register(constructor(duringAnalysisAccess, "javax.swing.text.html.HTMLEditorKit"));

        RuntimeReflection.register(clazz(duringAnalysisAccess, "javax.swing.text.rtf.RTFEditorKit"));
        RuntimeReflection.register(constructor(duringAnalysisAccess, "javax.swing.text.rtf.RTFEditorKit"));

        RuntimeReflection.register(clazz(duringAnalysisAccess, "javax.swing.JEditorPane$PlainEditorKit"));
        RuntimeReflection.register(constructor(duringAnalysisAccess, "javax.swing.JEditorPane$PlainEditorKit"));

        ImageSingletons.lookup(RuntimeResourceSupport.class)
                        .addResources(ConfigurationCondition.alwaysTrue(), "javax.swing.text.html.default.css");
        ImageSingletons.lookup(RuntimeResourceSupport.class)
                        .addResources(ConfigurationCondition.alwaysTrue(), "javax.swing.text.html.parser.html32.bdtd");
    }

    private static void registerDndIcons(DuringAnalysisAccess duringAnalysisAccess) {
        ImageSingletons.lookup(RuntimeResourceSupport.class)
                        .addResources(ConfigurationCondition.alwaysTrue(), "sun.awt.*");
    }

    private static void registerDisposer(DuringAnalysisAccess access) {
        RuntimeJNIAccess.register(clazz(access, "sun.java2d.Disposer"));
        RuntimeJNIAccess.register(method(access, "sun.java2d.Disposer", "addRecord",
                        java.lang.Object.class, long.class, long.class));
    }

    private static void registerKeyCodes(DuringAnalysisAccess access) {

        RuntimeReflection.register(java.awt.event.KeyEvent.class);
        RuntimeJNIAccess.register(fields(access, "java.awt.event.KeyEvent",
                        "extendedKeyCode", "keyChar", "keyCode", "primaryLevelUnicode",
                        "rawCode", "scancode", "isProxyActive"));

        RuntimeJNIAccess.register(constructor(access, "java.awt.event.KeyEvent",
                        java.awt.Component.class, int.class, long.class, int.class, int.class, char.class, int.class));

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

        RuntimeJNIAccess.register(fields(access, "javax.imageio.plugins.jpeg.JPEGQTable",
                        "qTable"));
        RuntimeJNIAccess.register(fields(access, "javax.imageio.plugins.jpeg.JPEGHuffmanTable",
                        "lengths", "values"));

        if (isWindows()) {
            RuntimeJNIAccess.register(clazz(access, "sun.awt.windows.WClipboard"));
            RuntimeJNIAccess.register(method(access, "sun.awt.windows.WClipboard", "lostSelectionOwnershipImpl"));
        }
    }

    private static void registerShellFolderManager(DuringAnalysisAccess access) {

        RuntimeReflection.register(clazz(access, "sun.awt.shell.ShellFolderManager"));
        RuntimeReflection.register(constructor(access, "sun.awt.shell.ShellFolderManager"));

        if (isWindows()) {
            RuntimeReflection.register(clazz(access, "sun.awt.shell.Win32ShellFolderManager2"));
            RuntimeReflection.register(constructor(access, "sun.awt.shell.Win32ShellFolderManager2"));

            RuntimeJNIAccess.register(clazz(access, "sun.awt.shell.Win32ShellFolder2"));
            RuntimeJNIAccess.register(fields(access, "sun.awt.shell.Win32ShellFolder2",
                            "FDATE", "FNAME", "FSIZE", "FTYPE", "displayName", "folderType", "pIShellIcon"));
            RuntimeJNIAccess.register(method(access, "sun.awt.shell.Win32ShellFolder2", "setRelativePIDL",
                            long.class));
            RuntimeJNIAccess.register(method(access, "sun.awt.shell.Win32ShellFolder2", "setIShellFolder",
                            long.class));

            RuntimeJNIAccess.register(clazz(access, "sun.awt.shell.Win32ShellFolder2$KnownFolderDefinition"));
            RuntimeJNIAccess.register(constructor(access, "sun.awt.shell.Win32ShellFolder2$KnownFolderDefinition"));
            RuntimeJNIAccess.register(fields(access, "sun.awt.shell.Win32ShellFolder2$KnownFolderDefinition",
                            "attributes", "category", "defenitionFlags", "description", "ftidType", "guid", "icon", "localizedName", "name", "parent", "parsingName", "path", "relativePath",
                            "saveLocation", "security", "tooltip"));

            RuntimeJNIAccess.register(clazz(access, "sun.awt.shell.ShellFolderColumnInfo"));
            RuntimeJNIAccess.register(constructor(access, "sun.awt.shell.ShellFolderColumnInfo",
                            java.lang.String.class, int.class, int.class, boolean.class));
        }
    }

    private static void registerSurfaceData(DuringAnalysisAccess access) {
        RuntimeJNIAccess.register(clazz(access, "sun.java2d.InvalidPipeException"));
        RuntimeJNIAccess.register(clazz(access, "sun.java2d.NullSurfaceData"));

        RuntimeJNIAccess.register(clazz(access, "sun.java2d.SurfaceData"));
        RuntimeJNIAccess.register(fields(access, "sun.java2d.SurfaceData",
                        "pData", "valid"));

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
        RuntimeJNIAccess.register(fields(access, "java.awt.image.SampleModel",
                        "height", "width"));
        RuntimeJNIAccess.register(method(access, "java.awt.image.SampleModel", "getPixels",
                        int.class, int.class, int.class, int.class, int[].class,
                        java.awt.image.DataBuffer.class));
        RuntimeJNIAccess.register(method(access, "java.awt.image.SampleModel", "setPixels",
                        int.class, int.class, int.class, int.class, int[].class,
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

        if (isLinux()) {
            RuntimeJNIAccess.register(clazz(access, "sun.java2d.xr.XRSurfaceData"));
            RuntimeJNIAccess.register(fields(access, "sun.java2d.xr.XRSurfaceData", "picture", "xid"));

            RuntimeJNIAccess.register(clazz(access, "sun.java2d.xr.XRBackendNative"));
            RuntimeJNIAccess.register(fields(access, "sun.java2d.xr.XRBackendNative",
                            "FMTPTR_A8", "FMTPTR_ARGB32", "MASK_XIMG"));
        }
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

    private static void registerColorModel(DuringAnalysisAccess access) {
        RuntimeJNIAccess.register(java.awt.image.ColorModel.class);
        RuntimeJNIAccess.register(fields(access, "java.awt.image.ColorModel",
                        "colorSpace", "colorSpaceType", "isAlphaPremultiplied", "is_sRGB", "nBits",
                        "numComponents", "pData", "supportsAlpha", "transparency"));
        RuntimeJNIAccess.register(method(access, "java.awt.image.ColorModel", "getRGBdefault"));

        RuntimeJNIAccess.register(java.awt.image.IndexColorModel.class);
        RuntimeJNIAccess.register(fields(access, "java.awt.image.IndexColorModel",
                        "allgrayopaque", "colorData", "lookupcache", "map_size", "rgb", "transparent_index"));

        RuntimeJNIAccess.register(java.awt.image.DirectColorModel.class);
        RuntimeJNIAccess.register(constructor(access, "java.awt.image.DirectColorModel",
                        int.class, int.class, int.class, int.class));
        RuntimeJNIAccess.register(constructor(access, "java.awt.image.DirectColorModel",
                        int.class, int.class, int.class, int.class, int.class));
    }

    private static void registerComponent(DuringAnalysisAccess access) {
        RuntimeJNIAccess.register(java.awt.Component.class);

        RuntimeJNIAccess.register(fields(access, "java.awt.Component",
                        "appContext", "background", "cursor", "enabled", "focusable",
                        "foreground", "graphicsConfig", "height", "parent",
                        "isPacked", "name", "peer", "visible", "width", "x", "y"));

        RuntimeJNIAccess.register(method(access, "java.awt.Component", "getLocationOnScreen_NoTreeLock"));
        RuntimeJNIAccess.register(method(access, "java.awt.Component", "getParent_NoClientCode"));

        if (isWindows()) {
            registerComponentWindows(access);
        }
    }

    private static void registerComponentWindows(DuringAnalysisAccess access) {

        RuntimeJNIAccess.register(method(access, "java.awt.Component", "getFont_NoClientCode"));
        RuntimeJNIAccess.register(method(access, "java.awt.Component", "getToolkitImpl"));
        RuntimeJNIAccess.register(method(access, "java.awt.Component", "isEnabledImpl"));

        RuntimeJNIAccess.register(fields(access, "java.awt.Container", "layoutMgr"));
        RuntimeJNIAccess.register(fields(access, "java.awt.Cursor", "pData", "type"));

        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WComponentPeer", "postEvent",
                        java.awt.AWTEvent.class));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WComponentPeer", "replaceSurfaceData"));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WComponentPeer", "replaceSurfaceDataLater"));

        RuntimeJNIAccess.register(fields(access, "sun.awt.windows.WComponentPeer", "hwnd", "winGraphicsConfig"));

        RuntimeJNIAccess.register(clazz(access, "java.awt.event.FocusEvent"));
        RuntimeJNIAccess.register(constructor(access, "java.awt.event.FocusEvent",
                        java.awt.Component.class, int.class, boolean.class, java.awt.Component.class));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.UngrabEvent"));
        RuntimeJNIAccess.register(constructor(access, "sun.awt.UngrabEvent",
                        java.awt.Component.class));

        RuntimeJNIAccess.register(java.awt.Point.class);
        RuntimeJNIAccess.register(fields(access, "java.awt.Point", "x", "y"));
        RuntimeJNIAccess.register(constructor(access, "java.awt.Point", int.class, int.class));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.windows.WInputMethod"));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WInputMethod", "inquireCandidatePosition"));

        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WComponentPeer", "disposeLater"));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WComponentPeer", "handleExpose",
                        int.class, int.class, int.class, int.class));
        RuntimeJNIAccess.register(clazz(access, "sun.awt.windows.WComponentPeer"));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.ExtendedKeyCodes"));
        RuntimeJNIAccess.register(method(access, "sun.awt.ExtendedKeyCodes", "getExtendedKeyCodeForChar",
                        int.class));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.windows.WObjectPeer"));
        RuntimeJNIAccess.register(fields(access, "sun.awt.windows.WObjectPeer", "createError",
                        "destroyed", "pData", "target"));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WObjectPeer", "getPeerForTarget",
                        java.lang.Object.class));

        RuntimeJNIAccess.register(method(access, "java.util.Locale", "forLanguageTag",
                        java.lang.String.class));
    }

    private static void registerEvent(DuringAnalysisAccess access) {
        RuntimeJNIAccess.register(fields(access, "java.awt.Event", "target", "x", "y"));
    }

    private static void registerAWTEvent(DuringAnalysisAccess access) {
        RuntimeJNIAccess.register(fields(access, "java.awt.AWTEvent", "bdata", "consumed", "id"));
    }

    private static void registerInputEvent(DuringAnalysisAccess access) {
        RuntimeJNIAccess.register(fields(access, "java.awt.event.InputEvent", "modifiers"));
        RuntimeJNIAccess.register(method(access, "java.awt.event.InputEvent", "getButtonDownMasks"));
    }

    private static void registerMouseEvents(DuringAnalysisAccess access) {
        RuntimeJNIAccess.register(java.awt.event.MouseEvent.class);
        RuntimeJNIAccess.register(fields(access, "java.awt.event.MouseEvent",
                        "x", "y", "button", "causedByTouchEvent"));

        RuntimeJNIAccess.register(constructor(access, "java.awt.event.MouseEvent",
                        clazz(access, "java.awt.Component"),
                        int.class, long.class, int.class, int.class,
                        int.class, int.class, int.class, int.class, boolean.class, int.class));

        RuntimeJNIAccess.register(java.awt.event.MouseWheelEvent.class);
        RuntimeJNIAccess.register(constructor(access, "java.awt.event.MouseWheelEvent",
                        java.awt.Component.class,
                        int.class, long.class, int.class, int.class,
                        int.class, int.class, int.class, int.class, boolean.class, int.class,
                        int.class, int.class, double.class));

        RuntimeJNIAccess.register(fields(access, "java.awt.event.MouseEvent",
                        "button", "causedByTouchEvent", "x", "y"));

        RuntimeReflection.register(java.awt.event.MouseMotionListener.class);
    }

    private static void registerCursor(DuringAnalysisAccess access) {
        RuntimeJNIAccess.register(method(access, "java.awt.Cursor", "setPData", long.class));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WGlobalCursorManager", "nativeUpdateCursor",
                        java.awt.Component.class));
    }

    private static void registerWindow(DuringAnalysisAccess access) {
        RuntimeJNIAccess.register(java.awt.Window.class);
        RuntimeJNIAccess.register(java.awt.Window.Type.class);
        RuntimeJNIAccess.register(method(access, "java.lang.Enum", "name"));

        RuntimeJNIAccess.register(fields(access, "java.awt.Window",
                        "autoRequestFocus", "locationByPlatform", "securityWarningHeight", "securityWarningWidth", "warningString"));
        RuntimeJNIAccess.register(method(access, "java.awt.Window", "getWarningString"));
        RuntimeJNIAccess.register(method(access, "java.awt.Window", "calculateSecurityWarningPosition",
                        double.class, double.class, double.class, double.class));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.windows.WGlobalCursorManager"));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.windows.WWindowPeer"));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WWindowPeer", "notifyWindowStateChanged",
                        int.class, int.class));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WWindowPeer", "setBackground",
                        java.awt.Color.class));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WWindowPeer", "draggedToNewScreen"));
        RuntimeJNIAccess.register(fields(access, "sun.awt.windows.WWindowPeer", "windowType"));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WWindowPeer", "getActiveWindowHandles",
                        java.awt.Component.class));

        RuntimeReflection.register(clazz(access, "java.awt.SequencedEvent"));
        RuntimeJNIAccess.register(clazz(access, "java.awt.SequencedEvent"));
        RuntimeJNIAccess.register(constructor(access, "java.awt.SequencedEvent", java.awt.AWTEvent.class));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.TimedWindowEvent"));
        RuntimeJNIAccess.register(constructor(access, "sun.awt.TimedWindowEvent",
                        java.awt.Window.class, int.class, java.awt.Window.class, int.class, int.class, long.class));

        RuntimeJNIAccess.register(java.awt.event.ComponentEvent.class);
        RuntimeJNIAccess.register(constructor(access, "java.awt.event.ComponentEvent",
                        java.awt.Component.class, int.class));

        RuntimeJNIAccess.register(java.awt.Dimension.class);
        RuntimeJNIAccess.register(fields(access, "java.awt.Dimension", "height", "width"));
        RuntimeJNIAccess.register(constructor(access, "java.awt.Dimension", int.class, int.class));
    }

    private static void registerFrame(DuringAnalysisAccess access) {
        RuntimeJNIAccess.register(fields(access, "java.awt.Frame", "undecorated"));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.LightweightFrame"));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.windows.WFramePeer"));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WFramePeer", "getExtendedState"));
        RuntimeJNIAccess.register(fields(access, "sun.awt.windows.WFramePeer", "keepOnMinimize"));

        RuntimeJNIAccess.register(java.awt.Rectangle.class);
        RuntimeJNIAccess.register(fields(access, "java.awt.Rectangle", "height", "width", "x", "y"));
        RuntimeJNIAccess.register(constructor(access, "java.awt.Rectangle",
                        int.class, int.class, int.class, int.class));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.EmbeddedFrame"));
        RuntimeJNIAccess.register(clazz(access, "sun.awt.im.InputMethodWindow"));
    }

    private static void registerPanel(DuringAnalysisAccess access) {
        RuntimeJNIAccess.register(clazz(access, "sun.awt.windows.WPanelPeer"));
        RuntimeJNIAccess.register(fields(access, "sun.awt.windows.WPanelPeer", "insets_"));
    }

    private static void registerDialog(DuringAnalysisAccess access) {
        RuntimeJNIAccess.register(fields(access, "java.awt.Dialog", "title", "undecorated"));
    }

    private static void registerFileDialog(DuringAnalysisAccess access) {

        RuntimeJNIAccess.register(java.awt.FileDialog.class);
        RuntimeJNIAccess.register(fields(access, "java.awt.FileDialog", "dir", "file", "filter", "mode"));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.windows.WDialogPeer"));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.windows.WFileDialogPeer"));
        RuntimeJNIAccess.register(fields(access, "sun.awt.windows.WFileDialogPeer", "fileFilter", "parent"));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WFileDialogPeer", "checkFilenameFilter",
                        java.lang.String.class));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WFileDialogPeer", "handleSelected",
                        char[].class));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WFileDialogPeer", "isMultipleMode"));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WFileDialogPeer", "setHWnd",
                        long.class));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WFileDialogPeer", "handleCancel"));
    }

    private static void registerMenu(DuringAnalysisAccess access) {

        RuntimeJNIAccess.register(java.awt.Menu.class);
        RuntimeJNIAccess.register(method(access, "java.awt.Menu", "countItemsImpl"));
        RuntimeJNIAccess.register(method(access, "java.awt.Menu", "getItemImpl",
                        int.class));

        RuntimeJNIAccess.register(java.awt.MenuComponent.class);
        RuntimeJNIAccess.register(method(access, "java.awt.MenuComponent", "getFont_NoClientCode"));

        RuntimeJNIAccess.register(java.awt.MenuItem.class);
        RuntimeJNIAccess.register(fields(access, "java.awt.MenuItem", "enabled", "label"));

        RuntimeJNIAccess.register(java.awt.CheckboxMenuItem.class);
        RuntimeJNIAccess.register(fields(access, "java.awt.CheckboxMenuItem", "state"));

        RuntimeJNIAccess.register(java.awt.PopupMenu.class);
        RuntimeJNIAccess.register(fields(access, "java.awt.PopupMenu", "isTrayIconPopup"));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.windows.WMenuItemPeer"));
        RuntimeJNIAccess.register(fields(access, "sun.awt.windows.WMenuItemPeer", "isCheckbox", "shortcutLabel"));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WMenuItemPeer", "getDefaultFont"));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WMenuItemPeer", "handleAction",
                        long.class, int.class));

        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WCheckboxMenuItemPeer", "handleAction",
                        boolean.class));
    }

    private static void registerTrayIcon(DuringAnalysisAccess access) {
        RuntimeJNIAccess.register(java.awt.TrayIcon.class);
        RuntimeJNIAccess.register(fields(access, "java.awt.TrayIcon", "actionCommand", "id"));

        RuntimeJNIAccess.register(clazz(access, "sun.awt.windows.WTrayIconPeer"));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WTrayIconPeer", "postEvent",
                        java.awt.AWTEvent.class));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WTrayIconPeer", "showPopupMenu",
                        int.class, int.class));
        RuntimeJNIAccess.register(method(access, "sun.awt.windows.WTrayIconPeer", "updateImage"));

        RuntimeJNIAccess.register(clazz(access, "java.awt.event.ActionEvent"));
        RuntimeJNIAccess.register(constructor(access, "java.awt.event.ActionEvent",
                        Object.class, int.class, String.class, long.class, int.class));
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

    private static void registerGTKLookAndFeel(DuringAnalysisAccess access) {
        RuntimeReflection.register(clazz(access, "com.sun.java.swing.plaf.gtk.GTKLookAndFeel"));
        RuntimeReflection.register(constructor(access, "com.sun.java.swing.plaf.gtk.GTKLookAndFeel"));
        RuntimeReflection.register(method(access, "com.sun.java.swing.plaf.gtk.GTKLookAndFeel", "createUI",
                        javax.swing.JComponent.class));

        RuntimeReflection.register(clazz(access, "com.sun.java.swing.plaf.gtk.GTKIconFactory"));
        RuntimeReflection.register(method(access, "com.sun.java.swing.plaf.gtk.GTKIconFactory", "getCheckBoxMenuItemCheckIcon"));

        ImageSingletons.lookup(RuntimeResourceSupport.class)
                        .addResources(ConfigurationCondition.alwaysTrue(), "com.sun.java.swing.plaf.gtk.resources.metacity.*");

        Stream.of("paintTreeExpandedIcon",
                        "paintTreeCollapsedIcon",
                        "paintCheckBoxIcon",
                        "paintRadioButtonIcon",
                        "paintCheckBoxMenuItemCheckIcon",
                        "paintRadioButtonMenuItemCheckIcon",
                        "paintAscendingSortIcon",
                        "paintDescendingSortIcon")
                        .forEach(paintIconMethod -> {
                            RuntimeReflection.register(method(access, "com.sun.java.swing.plaf.gtk.GTKPainter", paintIconMethod,
                                            javax.swing.plaf.synth.SynthContext.class, java.awt.Graphics.class,
                                            int.class, int.class, int.class, int.class, int.class));
                        });

        RuntimeReflection.register(method(access, "com.sun.java.swing.plaf.gtk.GTKPainter", "paintMenuArrowIcon",
                        javax.swing.plaf.synth.SynthContext.class, java.awt.Graphics.class,
                        int.class, int.class, int.class, int.class, int.class,
                        clazz(access, "com.sun.java.swing.plaf.gtk.GTKConstants$ArrowType")));

        RuntimeReflection.register(method(access, "com.sun.java.swing.plaf.gtk.GTKPainter", "paintToolBarHandleIcon",
                        javax.swing.plaf.synth.SynthContext.class, java.awt.Graphics.class,
                        int.class, int.class, int.class, int.class, int.class,
                        clazz(access, "com.sun.java.swing.plaf.gtk.GTKConstants$Orientation")));

        Stream.of("getAscendingSortIcon",
                        "getDescendingSortIcon",
                        "getTreeExpandedIcon",
                        "getTreeCollapsedIcon",
                        "getRadioButtonIcon",
                        "getCheckBoxIcon",
                        "getMenuArrowIcon",
                        "getCheckBoxMenuItemCheckIcon",
                        "getRadioButtonMenuItemCheckIcon",
                        "getToolBarHandleIcon")
                        .forEach(getIconMethod -> {
                            RuntimeReflection.register(method(access, "com.sun.java.swing.plaf.gtk.GTKIconFactory", getIconMethod));
                        });

        RuntimeReflection.register(method(access, "com.sun.java.swing.plaf.gtk.GTKPainter$ListTableFocusBorder",
                        "getSelectedCellBorder"));
    }

    private static void registerWindowsLookAndFeel(DuringAnalysisAccess access) {
        RuntimeReflection.register(clazz(access, "com.sun.java.swing.plaf.windows.WindowsLookAndFeel"));
        RuntimeReflection.register(constructor(access, "com.sun.java.swing.plaf.windows.WindowsLookAndFeel"));

        RuntimeReflection.register(clazz(access, "com.sun.java.swing.plaf.windows.WindowsClassicLookAndFeel"));
        RuntimeReflection.register(constructor(access, "com.sun.java.swing.plaf.windows.WindowsClassicLookAndFeel"));

        // exception from componentUI class
        // WindowsSeparatorUI does not contain static createUI(JComponent) method
        RuntimeReflection.register(clazz(access, "com.sun.java.swing.plaf.windows.WindowsSeparatorUI"));

        registerComponentUIs(access, "com.sun.java.swing.plaf.windows",
                        "WindowsButtonUI",
                        "WindowsCheckBoxUI",
                        "WindowsCheckBoxMenuItemUI",
                        "WindowsLabelUI",
                        "WindowsRadioButtonUI",
                        "WindowsRadioButtonMenuItemUI",
                        "WindowsToggleButtonUI",
                        "WindowsProgressBarUI",
                        "WindowsSliderUI",
                        // "WindowsSeparatorUI", // does not contain static createUI(JComponent)
                        // method
                        "WindowsSplitPaneUI",
                        "WindowsSpinnerUI",
                        "WindowsTabbedPaneUI",
                        "WindowsTextAreaUI",
                        "WindowsTextFieldUI",
                        "WindowsPasswordFieldUI",
                        "WindowsTextPaneUI",
                        "WindowsEditorPaneUI",
                        "WindowsTreeUI",
                        "WindowsToolBarUI",
                        "WindowsToolBarSeparatorUI",
                        "WindowsComboBoxUI",
                        "WindowsTableHeaderUI",
                        "WindowsInternalFrameUI",
                        "WindowsDesktopPaneUI",
                        "WindowsDesktopIconUI",
                        "WindowsFileChooserUI",
                        "WindowsMenuUI",
                        "WindowsMenuItemUI",
                        "WindowsMenuBarUI",
                        "WindowsPopupMenuUI",
                        "WindowsPopupMenuSeparatorUI",
                        "WindowsScrollBarUI",
                        "WindowsRootPaneUI");

        ImageSingletons.lookup(RuntimeResourceSupport.class)
                        .addResources(ConfigurationCondition.alwaysTrue(), "com.sun.java.swing.plaf.windows.icons.*");
        ImageSingletons.lookup(RuntimeResourceSupport.class)
                        .addResourceBundles(ConfigurationCondition.alwaysTrue(), "com.sun.java.swing.plaf.windows.resources.windows");
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

        ImageSingletons.lookup(RuntimeResourceSupport.class)
                        .addResources(ConfigurationCondition.alwaysTrue(), "javax.swing.plaf.basic.icons.*");
        ImageSingletons.lookup(RuntimeResourceSupport.class)
                        .addResourceBundles(ConfigurationCondition.alwaysTrue(), "com.sun.swing.internal.plaf.basic.resources.basic");

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

        ImageSingletons.lookup(RuntimeResourceSupport.class)
                        .addResources(ConfigurationCondition.alwaysTrue(), "javax.swing.plaf.metal.icons.*");
        ImageSingletons.lookup(RuntimeResourceSupport.class)
                        .addResourceBundles(ConfigurationCondition.alwaysTrue(), "com.sun.swing.internal.plaf.metal.resources.metal");
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

    private static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }
}
