package com.oracle.svm.hosted.jdk;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jni.JNIRuntimeAccess;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import java.util.Map;


/**
 * based on https://github.com/graalvm/labs-openjdk-11/tree/jvmci-20.2-b03
 * shared code: everything in src/java.desktop/share/native/libawt
 * windows code: everything in src/java.desktop/windows/native/libawt
 */
@Platforms(Platform.WINDOWS.class)
@AutomaticFeature
@SuppressWarnings("unused")
public class JNIRegistrationJavaAwtWindows extends JNIRegistrationAwtUtil implements Feature {

    @Override
    public void duringSetup(DuringSetupAccess a) {
        initializeAtRunTime(a,
                // from windows specific code
                // awt_AWTEvent.cpp
                JavaAwt.AWT_EVENT,
                // awt_Button.cpp
                JavaAwt.BUTTON, SunAwt.W_BUTTON_PEER,
                // awt_Checkbox.cpp
                JavaAwt.CHECKBOX, SunAwt.W_CHECKBOX_PEER,
                // awt_Choice.cpp
                JavaAwt.CHOICE, SunAwt.W_CHOICE_PEER,
                // awt_Color.cpp
                JavaAwt.COLOR,
                // awt_Component.cpp
                JavaAwt.COMPONENT, SunAwt.W_COMPONENT_PEER,
                // awt_Container.cpp
                JavaAwt.CONTAINER,
                // awt_Cursor.cpp
                JavaAwt.CURSOR, SunAwt.W_CUSTOM_CURSOR, SunAwt.W_GLOBAL_CURSOR_MANAGER,
                // awt_Dialog.cpp
                JavaAwt.DIALOG, SunAwt.W_DIALOG_PEER,
                // awt_Dimension.cpp
                JavaAwt.DIMENSION,
                // awt_Event.cpp
                JavaAwt.EVENT,
                // awt_FileDialog.cpp
                JavaAwt.FILE_DIALOG, SunAwt.W_FILE_DIALOG_PEER,
                // awt_Font.cpp
                SunAwt.W_FONT_METRICS, JavaAwt.FONT, JavaAwt.FONT_METRICS, SunAwt.FONT_DESCRIPTOR, SunAwt.PLATFORM_FONT,
                SunAwt.W_FONT_PEER, SunAwt.W_DEFAULT_FONT_CHARSET,
                // awt_Frame.cpp
                JavaAwt.FRAME, SunAwt.W_FRAME_PEER, SunAwt.W_EMBEDDED_FRAME, SunAwt.W_EMBEDDED_FRAME_PEER,
                // awt_Insets.cpp
                JavaAwt.INSETS,
                // awt_KeyboardFocusManager.cpp
                JavaAwt.KEYBOARD_FOCUS_MANAGER, SunAwt.W_KEYBOARD_FOCUS_MANAGER_PEER,
                // awt_Label.cpp
                JavaAwt.LABEL, SunAwt.W_LABEL_PEER,
                // awt_MenuBar.cpp
                JavaAwt.MENU_BAR, SunAwt.W_MENU_BAR_PEER,
                // awt_MenuItem.cpp
                JavaAwt.CHECKBOX_MENU_ITEM, JavaAwt.MENU_COMPONENT, JavaAwt.MENU_ITEM,
                SunAwt.W_MENU_ITEM_PEER, SunAwt.W_CHECKBOX_MENU_ITEM_PEER,
                // awt_Menu.cpp
                JavaAwt.MENU, SunAwt.W_MENU_PEER,
                // awt_Rectangle.cpp
                JavaAwt.RECTANGLE,
                // awt_ScrollPane.cpp
                JavaAwt.SCROLL_PANE, JavaAwt.SCROLL_PANE_ADJUSTABLE, SunAwt.W_SCROLL_PANE_PEER,
                // awt_Scrollbar.cpp
                JavaAwt.SCROLLBAR, SunAwt.W_SCROLLBAR_PEER,
                // awt_TextArea.cpp
                JavaAwt.TEXT_AREA, SunAwt.W_TEXT_AREA_PEER,
                // awt_TextField.cpp
                JavaAwt.TEXT_FIELD, SunAwt.W_TEXT_FIELD_PEER,
                // awt_Toolkit.cpp
                JavaAwt.TOOLKIT, SunAwt.W_TOOLKIT, SunAwt.SUN_TOOLKIT,
                // awt_TrayIcon.cpp
                JavaAwt.TRAY_ICON, SunAwt.W_TRAY_ICON_PEER,
                // awt_Window.cpp
                JavaAwt.WINDOW, SunAwt.W_WINDOW_PEER, SunAwt.W_LIGHTWEIGHT_FRAME_PEER,
                // awt_InputEvent.cpp
                JavaAwt.INPUT_EVENT,
                // awt_KeyEvent.cpp
                JavaAwt.KEY_EVENT,
                // awt_MouseEvent.cpp
                JavaAwt.MOUSE_EVENT,
                // awt_Win32GraphicsEnv.cpp
                SunAwt.WIN32_GRAPHICS_ENVIRONMENT, SunAwt.WIN32_FONT_MANAGER,
                // awt_Win32GraphicsConfig.cpp
                SunAwt.WIN32_GRAPHICS_CONFIG,
                // awt_Win32GraphicsDevice.cpp
                SunAwt.WIN32_GRAPHICS_DEVICE,
                // ShellFOlder2.cpp
                SunAwt.WIN32_SHELL_FOLDER2, SunAwt.WIN32_SHELL_FOLDER_MANAGER2,
                // ThemeReader.cpp
                SunAwt.THEME_READER,
                // awt_Canvas.cpp
                JavaAwt.CANVAS, SunAwt.W_CANVAS_PEER,
                // awt_Clipboard.cpp
                SunAwt.W_CLIPBOARD,
                // awt_DataTransferer.cpp
                SunAwt.DATA_TRANSFERER, SunAwt.W_DATA_TRANSFERER, SunAwt.W_TOOLKIT_THREAD_BLOCKED_HANDLER,
                // awt_Desktop.cpp
                SunAwt.W_DESKTOP_PEER,
                // awt_DesktopProperties.cpp
                SunAwt.W_DESKTOP_PROPERTIES,
                // awt_DnDDS.cpp
                JavaAwt.DRAG_SOURCE, SunAwt.W_DRAG_SOURCE_CONTEXT_PEER,
                // awt_DnDDT.cpp
                JavaAwt.DROP_TARGET, SunAwt.W_DROP_TARGET_CONTEXT_PEER,
                SunAwt.W_DROP_TARGET_CONTEXT_PEER_FILE_STREAM, SunAwt.W_DROP_TARGET_CONTEXT_PEER_I_STREAM,
                // awt_InputMethod.cpp
                SunAwt.W_INPUT_METHOD, SunAwt.W_INPUT_METHOD_DESCRIPTOR,
                // awt_List.cpp
                JavaAwt.LIST, SunAwt.W_LIST_PEER,
                // MouseInfo.cpp
                SunAwt.W_MOUSE_INFO_PEER,
                // awt_Object.cpp
                SunAwt.W_OBJECT_PEER,
                // awt_Panel.cpp
                JavaAwt.PANEL, SunAwt.W_PANEL_PEER,
                // awt_PopupMenu.cpp
                JavaAwt.POPUP_MENU, SunAwt.W_POPUP_MENU_PEER,
                // awt_PrintDialog.cpp
                SunAwt.W_PPRINT_DIALOG, SunAwt.W_PPRINT_DIALOG_PEER,
                // awt_PrintJob.cpp
                SunAwt.W_PAGE_DIALOG, SunAwt.W_PAGE_DIALOG_PEER, SunAwt.W_PRINTER_JOB, SunPrint.RASTER_PRINTER_JOB,
                JavaAwt.PRINTER_JOB, JavaAwt.PAGE_FORMAT, JavaAwt.PAPER,
                // awt_PrintControl.cpp - empty
                // awt_Taskbar.cpp
                /* JavaAwt.TASKBAR, */ // no initialization rerun required
                SunAwt.W_TASKBAR_PEER,
                // awt_Robot.cpp
                JavaAwt.ROBOT, SunAwt.W_ROBOT_PEER,
                // awt_TextComponent.cpp
                SunAwt.W_TEXT_COMPONENT_PEER,
                // awt_DrawingSurface.cpp - empty

                // from shared code
                // imageInitIDs.c
                JavaAwt.BUFFERED_IMAGE, JavaAwt.RASTER, SunAwt.BYTE_COMPONENT_RASTER, SunAwt.BYTE_PACKED_RASTER,
                SunAwt.SHORT_COMPONENT_RASTER, SunAwt.INTEGER_COMPONENT_RASTER, JavaAwt.SINGLE_PIXEL_PACKED_SAMPLE_MODEL,
                JavaAwt.COLOR_MODEL, JavaAwt.INDEX_COLOR_MODEL, JavaAwt.SAMPLE_MODEL, JavaAwt.KERNEL,
                // debug_trace.c
                SunAwt.DEBUG_SETTINGS,
                // BufImgSurfaceData.c
                SunAwt.BUF_IMG_SURFACE_DATA,
                // DataBufferNative.c
                SunAwt.DATA_BUFFER_NATIVE,
                // gifdecoder.c
                SunAwt.GIF_IMAGE_DECODER,
                // awt_ImageRep.c
                SunAwt.IMAGE_REPRESENTATION,
                // awt_ImagingLib.c
                SunAwt.IMAGING_LIB
        );
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {

        a.registerReachabilityHandler(JNIRegistrationJavaAwtWindows::linkAwt,
                clazz(a, "java.awt.Toolkit"),
                clazz(a, "sun.java2d.cmm.lcms.LCMS"),
                clazz(a, "java.awt.event.NativeLibLoader"),
                clazz(a, "sun.awt.NativeLibLoader"),
                clazz(a, "sun.awt.image.NativeLibLoader"),
                clazz(a, "java.awt.image.ColorModel"),
                clazz(a, "sun.font.FontManagerNativeLibrary"),
                clazz(a, "sun.awt.windows.WToolkit"),
                clazz(a, "sun.print.PrintServiceLookupProvider"),
                clazz(a, "sun.java2d.Disposer"));

        registerForThrowNew(a,
                "java.awt.AWTError",
                "java.awt.IllegalComponentStateException",
                "java.awt.dnd.InvalidDnDOperationException",
                "java.awt.print.PrinterException"
        );
        // from windows specific code
        // awt_AWTEvent.cpp
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerAWTEventInitIDs, JavaAwt.AWT_EVENT);
        // awt_Button.cpp
        registerClassHandler(a, JNIRegistrationJavaAwtWindows::registerButtonClass, JavaAwt.BUTTON);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerWButtonPeerInitIDs, SunAwt.W_BUTTON_PEER);
        // awt_Checkbox.cpp
        registerClassHandler(a, JNIRegistrationJavaAwtWindows::registerCheckboxClass, JavaAwt.CHECKBOX);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerCheckboxInitIDs, JavaAwt.CHECKBOX);
        // awt_Choice.cpp
        registerClassHandler(a, JNIRegistrationJavaAwtWindows::registerChoiceClass, JavaAwt.CHOICE);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerChoiceInitIDs, JavaAwt.CHOICE);
        // awt_Color.cpp
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerColorInitIDs, JavaAwt.COLOR);
        // awt_Component.cpp
        registerClassHandler(a, JNIRegistrationJavaAwtWindows::registerComponentClass, JavaAwt.COMPONENT);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerComponentInitIDs, JavaAwt.COMPONENT);
        // awt_Container.cpp
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerContainerInitIDs, JavaAwt.CONTAINER);
        // awt_Cursor.cpp
        registerClassHandler(a, JNIRegistrationJavaAwtWindows::registerCursorClass, JavaAwt.CURSOR);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerCursorInitIDs, JavaAwt.CURSOR);
        // awt_Dialog.cpp
        registerClassHandler(a, JNIRegistrationJavaAwtWindows::registerDialogClass, JavaAwt.DIALOG);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerDialogInitIDs, JavaAwt.DIALOG);
        // awt_Dimension.cpp
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerDimensionInitIDs, JavaAwt.DIMENSION);
        // awt_Event.cpp
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerEventInitIDs, JavaAwt.EVENT);
        // awt_FileDialog.cpp
        registerClassHandler(a, JNIRegistrationJavaAwtWindows::registerWFileDialogClass, JavaAwt.FILE_DIALOG);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerWFileDialogPeerInitIDs, SunAwt.W_FILE_DIALOG_PEER);
        // awt_Font.cpp
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerWFontMetricsInitIDs, SunAwt.W_FONT_METRICS);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerFontInitIDs, JavaAwt.FONT);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerFontMetricsInitIDs, JavaAwt.FONT_METRICS);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerFontDescriptorInitIDs, SunAwt.FONT_DESCRIPTOR);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerPlatformFontInitIDs, SunAwt.PLATFORM_FONT);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerWFontPeerInitIDs, SunAwt.W_FONT_PEER);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerWDefaultFontCharsetInitIDs, SunAwt.W_DEFAULT_FONT_CHARSET);
        // awt_Frame.cpp
        registerClassHandler(a, JNIRegistrationJavaAwtWindows::registerFrameClass, JavaAwt.FRAME);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerFrameInitIDs, JavaAwt.FRAME);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerWFramePeerInitIDs, SunAwt.W_FRAME_PEER);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerWEmbeddedFrameInitIDs, SunAwt.W_EMBEDDED_FRAME);
        // awt_Insets.cpp
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerInsetsInitIDs, JavaAwt.INSETS);
        // awt_KeyboardFocusManager.cpp - empty
        // awt_Label.cpp
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerLabelInitIDs, JavaAwt.LABEL);
        // awt_MenuBar.cpp
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerMenuBarInitIDs, JavaAwt.MENU_BAR);
        // awt_MenuItem.cpp
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerCheckboxMenuItemInitIDs, JavaAwt.CHECKBOX_MENU_ITEM);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerMenuComponentInitIDs, JavaAwt.MENU_COMPONENT);
        registerClassHandler(a, JNIRegistrationJavaAwtWindows::registerMenuItemClass, JavaAwt.MENU_ITEM);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerMenuItemInitIDs, JavaAwt.MENU_ITEM);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerWMenuItemInitIDs, SunAwt.W_MENU_ITEM_PEER);
        // awt_Menu.cpp
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerMenuInitIDs, JavaAwt.MENU);
        // awt_Rectangle.cpp
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerRectangleInitIDs, JavaAwt.RECTANGLE);
        // awt_ScrollPane.cpp
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerScrollPaneInitIDs, JavaAwt.SCROLL_PANE);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerScrollPaneAdjustableInitIDs, JavaAwt.SCROLL_PANE_ADJUSTABLE);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerWScrollPanePeerInitIDs, SunAwt.W_SCROLL_PANE_PEER);
        // awt_Scrollbar.cpp
        registerClassHandler(a, JNIRegistrationJavaAwtWindows::registerScrollbarClass, JavaAwt.SCROLLBAR);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerScrollbarInitIDs, JavaAwt.SCROLLBAR);
        // awt_TextArea.cpp
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerTextAreaInitIDs, JavaAwt.TEXT_AREA);
        // awt_TextField.cpp - empty
        // awt_Toolkit.cpp
        registerClassHandler(a, JNIRegistrationJavaAwtWindows::registerToolkitClass, JavaAwt.TOOLKIT);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerToolkitInitIDs, JavaAwt.TOOLKIT);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerWToolkitInitIDs, SunAwt.W_TOOLKIT);
        a.registerReachabilityHandler(JNIRegistrationJavaAwtWindows::registerWToolkitGetScreenInsets,
                method(a, SunAwt.W_TOOLKIT, "getScreenInsets", int.class));
        // awt_TrayIcon.cpp
        registerClassHandler(a, JNIRegistrationJavaAwtWindows::registerTrayIconClass, JavaAwt.TRAY_ICON);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerTrayIconInitIDs, JavaAwt.TRAY_ICON);
        // awt_Window.cpp
        registerClassHandler(a, JNIRegistrationJavaAwtWindows::registerWindowClass, JavaAwt.WINDOW);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerWindowInitIDs, JavaAwt.WINDOW);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerWWindowPeerInitIDs, SunAwt.W_WINDOW_PEER);
        a.registerReachabilityHandler(JNIRegistrationJavaAwtWindows::registerWWindowPeerGetNativeWindowSize,
                method(a, SunAwt.W_WINDOW_PEER, "getNativeWindowSize"));
        // awt_InputEvent.cpp
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerInputEventInitIDs, JavaAwt.INPUT_EVENT);
        // awt_KeyEvent.cpp
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerKeyEventInitIDs, JavaAwt.KEY_EVENT);
        // awt_MouseEvent.cpp
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerMouseEventInitIDs, JavaAwt.MOUSE_EVENT);
        // awt_Win32GraphicsEnv.cpp
        registerClassHandler(a, JNIRegistrationJavaAwtWindows::registerWin32GraphicsEnvironmentClass, SunAwt.WIN32_GRAPHICS_ENVIRONMENT);
        // awt_Win32GraphicsConfig.cpp
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerWin32GraphicsConfigInitIDs, SunAwt.WIN32_GRAPHICS_CONFIG);
        a.registerReachabilityHandler(JNIRegistrationJavaAwtWindows::registerWin32GraphicsConfigGetBounds,
                method(a, SunAwt.WIN32_GRAPHICS_CONFIG, "getBounds", int.class));
        // awt_Win32GraphicsDevice.cpp
        registerClassHandler(a, JNIRegistrationJavaAwtWindows::registerWin32GraphicsDeviceClass, SunAwt.WIN32_GRAPHICS_DEVICE);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerWin32GraphicsDeviceInitIDs, SunAwt.WIN32_GRAPHICS_DEVICE);
        a.registerReachabilityHandler(JNIRegistrationJavaAwtWindows::registerWin32GraphicsDeviceExitFullScreenExclusive,
                method(a, SunAwt.WIN32_GRAPHICS_DEVICE, "exitFullScreenExclusive", int.class, clazz(a, "java.awt.peer.WindowPeer")));
        // ShellFolder2.cpp
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerWin32ShellFolder2InitIDs, SunAwt.WIN32_SHELL_FOLDER2);
        a.registerReachabilityHandler(JNIRegistrationJavaAwtWindows::registerWin32ShellFolder2DoGetColumnInfo,
                method(a, SunAwt.WIN32_SHELL_FOLDER2, "doGetColumnInfo", long.class));
        a.registerReachabilityHandler(JNIRegistrationJavaAwtWindows::registerWin32ShellFolder2LoadKnownFolders,
                method(a, SunAwt.WIN32_SHELL_FOLDER2, "loadKnownFolders"));
        // ThemeReader.cpp
        a.registerReachabilityHandler(JNIRegistrationJavaAwtWindows::registerThemeReaderGetThemeMargins,
                method(a, SunAwt.THEME_READER, "getThemeMargins", long.class, int.class, int.class, int.class));
        a.registerReachabilityHandler(JNIRegistrationJavaAwtWindows::registerThemeReaderGetColor,
                method(a, SunAwt.THEME_READER, "getColor", long.class, int.class, int.class, int.class));
        a.registerReachabilityHandler(JNIRegistrationJavaAwtWindows::registerThemeReaderGetPoint,
                method(a, SunAwt.THEME_READER, "getPoint", long.class, int.class, int.class, int.class));
        a.registerReachabilityHandler(JNIRegistrationJavaAwtWindows::registerThemeReaderGetPosition,
                method(a, SunAwt.THEME_READER, "getPosition", long.class, int.class, int.class, int.class));
        a.registerReachabilityHandler(JNIRegistrationJavaAwtWindows::registerThemeReaderGetPartSize,
                method(a, SunAwt.THEME_READER, "getPartSize", long.class, int.class, int.class));
        a.registerReachabilityHandler(JNIRegistrationJavaAwtWindows::registerThemeReaderGetThemeBackgroundContentMargins,
                method(a, SunAwt.THEME_READER, "getThemeBackgroundContentMargins", long.class, int.class, int.class, int.class, int.class));
        // awt_Canvas.cpp
        registerClassHandler(a, JNIRegistrationJavaAwtWindows::registerCanvasClass, JavaAwt.CANVAS);
        // awt_Clipboard.cpp
        registerClassHandler(a, JNIRegistrationJavaAwtWindows::registerWClipboardClass, SunAwt.W_CLIPBOARD);
        a.registerReachabilityHandler(JNIRegistrationJavaAwtWindows::registerWClipboardInit,
                method(a, SunAwt.W_CLIPBOARD, "init"));
        // awt_DataTransferer.cpp
        registerClassHandler(a, JNIRegistrationJavaAwtWindows::registerDataTransfererClass, SunAwt.DATA_TRANSFERER);
        a.registerReachabilityHandler(JNIRegistrationJavaAwtWindows::registerWDataTransfererDragQueryFile,
                method(a, SunAwt.W_DATA_TRANSFERER, "dragQueryFile", byte[].class));
        // awt_Desktop.cpp - empty
        // awt_DesktopProperties.cpp
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerWDesktopPropertiesInitIDs, SunAwt.W_DESKTOP_PROPERTIES);
        // awt_DnDDS.cpp
        registerClassHandler(a, JNIRegistrationJavaAwtWindows::registerDragSourceClass, JavaAwt.DRAG_SOURCE);
        // awt_DnDDT.cpp
        registerClassHandler(a, JNIRegistrationJavaAwtWindows::registerDropTargetClass, JavaAwt.DROP_TARGET);
        // awt_InputMethod.cpp
        registerClassHandler(a, JNIRegistrationJavaAwtWindows::registerWInputMethodClass, SunAwt.W_INPUT_METHOD);
        a.registerReachabilityHandler(JNIRegistrationJavaAwtWindows::registerWInputMethodDescriptorGetNativeAvailableLocales,
                method(a, SunAwt.W_INPUT_METHOD_DESCRIPTOR, "getNativeAvailableLocales"));
        // awt_List.cpp
        registerClassHandler(a, JNIRegistrationJavaAwtWindows::registerListClass, JavaAwt.LIST);
        // MouseInfo.cpp
        a.registerReachabilityHandler(JNIRegistrationJavaAwtWindows::registerWMouseInfoPeerFillPointWithCoords,
                method(a, SunAwt.W_MOUSE_INFO_PEER, "fillPointWithCoords", clazz(a, "java.awt.Point")));
        // awt_Object.cpp
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerWObjectPeerInitIDs, SunAwt.W_OBJECT_PEER);
        // awt_Panel.cpp
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerWPanelPeerInitIDs, SunAwt.W_PANEL_PEER);
        // awt_Panel.cpp
        registerClassHandler(a, JNIRegistrationJavaAwtWindows::registerWPopupMenuClass, JavaAwt.POPUP_MENU);
        // awt_PrintDialog.cpp
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerWPrintDialogInitIDs, SunAwt.W_PPRINT_DIALOG);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerWPrintDialogPeerInitIDs, SunAwt.W_PPRINT_DIALOG_PEER);
        // awt_PrintJob.cpp
        registerClassHandler(a, JNIRegistrationJavaAwtWindows::registerWPrinterJobClass, SunAwt.W_PRINTER_JOB);
        registerClassHandler(a, JNIRegistrationJavaAwtWindows::registerPageFormatClass, JavaAwt.PAGE_FORMAT);
        registerClassHandler(a, JNIRegistrationJavaAwtWindows::registerPaperClass, JavaAwt.PAPER);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerWPageDialogInitIDs, SunAwt.W_PAGE_DIALOG);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerWPrinterJobInitIDs, SunAwt.W_PRINTER_JOB);
        // awt_PrintControl.cpp - empty, see PrintDialog and PrintJob
        // awt_Taskbar.cpp
        a.registerReachabilityHandler(JNIRegistrationJavaAwtWindows::registerWTaskbarPeerSetProgressState,
                method(a, SunAwt.W_TASKBAR_PEER, "setProgressState", long.class, clazz(a, JavaAwt.TASKBAR + "$State")));
        // awt_Robot.cpp - empty
        // awt_TextComponent.cpp
        registerClassHandler(a, JNIRegistrationJavaAwtWindows::registerTextComponentClass, JavaAwt.TEXT_COMPONENT);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerWTextComponentPeerInitIDs, SunAwt.W_TEXT_COMPONENT_PEER);
        // awt_DrawingSurface.cpp
        registerClassHandler(a, JNIRegistrationJavaAwtWindows::registerWEmbeddedFrameClass, SunAwt.W_EMBEDDED_FRAME);


        // from shared code
        // imageInitIDs.c
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerBufferedImageInitIDs, JavaAwt.BUFFERED_IMAGE);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerRasterInitIDs, JavaAwt.RASTER);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerByteComponentRasterInitIDs, SunAwt.BYTE_COMPONENT_RASTER);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerBytePackedRasterInitIDs, SunAwt.BYTE_PACKED_RASTER);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerShortComponentRasterInitIDs, SunAwt.SHORT_COMPONENT_RASTER);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerIntegerComponentRasterInitIDs, SunAwt.INTEGER_COMPONENT_RASTER);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerSinglePixelPackedSampleModelInitIDs, JavaAwt.SINGLE_PIXEL_PACKED_SAMPLE_MODEL);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerColorModelInitIDs, JavaAwt.COLOR_MODEL);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerIndexColorModelInitIDs, JavaAwt.INDEX_COLOR_MODEL);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerSampleModelInitIDs, JavaAwt.SAMPLE_MODEL);
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerKernelInitIDs, JavaAwt.KERNEL);
        // debug_trace.c - empty
        // BufImgSurfaceData.c
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerBufImgSurfaceDataInitIDs,
                SunAwt.BUF_IMG_SURFACE_DATA, Class.class, Class.class);
        // DataBufferNative.c - empty
        // gifdecoder.c
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerGifImageDecoderInitIDs, SunAwt.GIF_IMAGE_DECODER);
        // awt_ImageRep.c
        registerInitIDsHandler(a, JNIRegistrationJavaAwtWindows::registerImageRepresentationInitIDs, SunAwt.IMAGE_REPRESENTATION);
        // awt_ImagingLib.c - empty
    }

    private static void linkAwt(DuringAnalysisAccess a) {
//        TODO: awt needs to be statically build so it can be added as built-in library
//        NativeLibraries nativeLibraries = ((FeatureImpl.DuringAnalysisAccessImpl) a).getNativeLibraries();
//        NativeLibrarySupport.singleton().preregisterUninitializedBuiltinLibrary("awt");
//        PlatformNativeLibrarySupport.singleton().addBuiltinPkgNativePrefix("java_awt");
//        PlatformNativeLibrarySupport.singleton().addBuiltinPkgNativePrefix("sun_awt");
//        PlatformNativeLibrarySupport.singleton().addBuiltinPkgNativePrefix("sun_java2d");
//        PlatformNativeLibrarySupport.singleton().addBuiltinPkgNativePrefix("sun_print");
//        nativeLibraries.addStaticJniLibrary("awt");
    }

    // from windows specific code

    // awt_AWTEvent.cpp

    private static void registerAWTEventInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.AWT_EVENT, "bdata", "id", "consumed"));
    }

    // awt_Button.cpp

    private static void registerButtonClass(DuringAnalysisAccess a) {
        // WComponentPeer#handlePaint?
        JNIRuntimeAccess.register(method(a, SunAwt.W_BUTTON_PEER, "handleAction", long.class, int.class));
    }

    private static void registerWButtonPeerInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, JavaAwt.BUTTON));
        JNIRuntimeAccess.register(fields(a, JavaAwt.BUTTON, "label"));
    }

    // awt_Checkbox.cpp

    private static void registerCheckboxClass(DuringAnalysisAccess a) {
        // WComponentPeer#handlePaint?
        JNIRuntimeAccess.register(method(a, SunAwt.W_CHECKBOX_PEER, "handleAction", boolean.class));
    }

    private static void registerCheckboxInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.CHECKBOX, "label", "group", "state"));
    }

    // awt_Choice.cpp

    private static void registerChoiceClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(method(a, SunAwt.W_COMPONENT_PEER, "getPreferredSize"));
        JNIRuntimeAccess.register(method(a, SunAwt.W_CHOICE_PEER, "handleAction", int.class));
        // from awt_Component.cpp
        JNIRuntimeAccess.register(method(a, JavaAwt.CHOICE, "getItemImpl", int.class));
    }

    private static void registerChoiceInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.CHOICE, "selectedIndex"));
    }

    // awt_Color.cpp

    private static void registerColorInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(method(a, JavaAwt.COLOR, "getRGB"));
    }

    // awt_Component.cpp

    private static void registerComponentClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, SunAwt.WIN32_GRAPHICS_CONFIG));

        JNIRuntimeAccess.register(clazz(a, JavaAwt.COLOR));
        JNIRuntimeAccess.register(constructor(a, JavaAwt.COLOR, int.class, int.class, int.class));

        JNIRuntimeAccess.register(method(a, SunAwt.W_COMPONENT_PEER, "setBackground",
                clazz(a, JavaAwt.COLOR)));
        JNIRuntimeAccess.register(method(a, SunAwt.W_COMPONENT_PEER, "handleExpose",
                int.class, int.class, int.class, int.class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_COMPONENT_PEER, "handlePaint",
                int.class, int.class, int.class, int.class));

        JNIRuntimeAccess.register(clazz(a, "sun.awt.ExtendedKeyCodes"));
        JNIRuntimeAccess.register(method(a, "sun.awt.ExtendedKeyCodes", "getExtendedKeyCodeForChar", int.class));

        JNIRuntimeAccess.register(clazz(a, SunAwt.W_INPUT_METHOD));
        JNIRuntimeAccess.register(method(a, SunAwt.W_INPUT_METHOD, "sendInputMethodEvent",
                /* id */ int.class, /* when */ long.class, /* text */  String.class,
                /* clauseBoundary */ int[].class, /* clauseReading */ String[].class,
                /* attributeBoundary */ int[].class, /* attributeValue */ byte[].class,
                /* commitedTextLength */ int.class, /* caretPos */ int.class, /* visiblePos */ int.class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_INPUT_METHOD, "inquireCandidatePosition"));

        JNIRuntimeAccess.register(clazz(a, JavaAwt.KEY_EVENT));
        JNIRuntimeAccess.register(constructor(a, JavaAwt.KEY_EVENT,
                /* source */ clazz(a, JavaAwt.COMPONENT),
                /* id */ int.class, /* when */ long.class, /* modifiers */ int.class,
                /* keyCode */ int.class, /* keyChar */ char.class, /* keyLocation */ int.class));

        JNIRuntimeAccess.register(clazz(a, JavaAwt.MOUSE_EVENT));
        JNIRuntimeAccess.register(constructor(a, JavaAwt.MOUSE_EVENT,
                /* source */ clazz(a, JavaAwt.COMPONENT),
                /* id */ int.class, /* when */ long.class, /* modifiers */ int.class,
                /* x */ int.class, /* y */ int.class, /* xAbs */ int.class, /* yAbs */ int.class,
                /* clickCount */ int.class, /* popupTrigger */ boolean.class, /* button */ int.class));

        JNIRuntimeAccess.register(clazz(a, "java.awt.event.MouseWheelEvent"));
        JNIRuntimeAccess.register(constructor(a, "java.awt.event.MouseWheelEvent",
                /* source */ clazz(a, JavaAwt.COMPONENT),
                /* id */ int.class, /* when */ long.class, /* modifiers */ int.class,
                /* x */ int.class, /* y */ int.class, /* xAbs */ int.class, /* yAbs */ int.class,
                /* clickCount */ int.class, /* popupTrigger */ boolean.class,
                /* scrollType */ int.class, /* scrollAmount */ int.class, /* wheelRotation */ int.class,
                /* preciseWheelRotation */ double.class));
        JNIRuntimeAccess.register(method(a, "java.awt.event.MouseWheelEvent", "getWheelRotation"));

        JNIRuntimeAccess.register(clazz(a, "java.awt.event.FocusEvent"));
        JNIRuntimeAccess.register(constructor(a, "java.awt.event.FocusEvent",
                /* source */ clazz(a, JavaAwt.COMPONENT), /* id */ int.class, /* temporary */ boolean.class,
                /* opposite */ clazz(a, JavaAwt.COMPONENT)));

        JNIRuntimeAccess.register(clazz(a, "java.awt.SequencedEvent"));
        JNIRuntimeAccess.register(constructor(a, "java.awt.SequencedEvent", clazz(a, JavaAwt.AWT_EVENT)));

        JNIRuntimeAccess.register(clazz(a, "java.awt.Point"));
        JNIRuntimeAccess.register(constructor(a, "java.awt.Point", int.class, int.class));

        JNIRuntimeAccess.register(clazz(a, "sun.awt.UngrabEvent"));
        JNIRuntimeAccess.register(constructor(a, "sun.awt.UngrabEvent", clazz(a, JavaAwt.COMPONENT)));

        // for throw new: java.lang.OutOfMemoryError
        // for throw new: java.lang.InternalError

        // see awt_InputTextInfor.cpp
        JNIRuntimeAccess.register(method(a, "java.lang.String", "concat", String.class));
    }

    private static void registerComponentInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, JavaAwt.INPUT_EVENT));
        JNIRuntimeAccess.register(method(a, JavaAwt.INPUT_EVENT, "getButtonDownMasks"));

        JNIRuntimeAccess.register(clazz(a, SunAwt.W_COMPONENT_PEER));
        JNIRuntimeAccess.register(fields(a, SunAwt.W_COMPONENT_PEER, "winGraphicsConfig", "hwnd"));
        JNIRuntimeAccess.register(method(a, SunAwt.W_COMPONENT_PEER, "replaceSurfaceData"));
        JNIRuntimeAccess.register(method(a, SunAwt.W_COMPONENT_PEER, "replaceSurfaceDataLater"));
        JNIRuntimeAccess.register(method(a, SunAwt.W_COMPONENT_PEER, "disposeLater"));

        JNIRuntimeAccess.register(fields(a, JavaAwt.COMPONENT,
                "peer", "x", "y", "height", "width", "visible", "background", "foreground",
                "enabled", "parent", "graphicsConfig", "focusable", "appContext", "cursor"));
        JNIRuntimeAccess.register(method(a, JavaAwt.COMPONENT, "getFont_NoClientCode"));
        JNIRuntimeAccess.register(method(a, JavaAwt.COMPONENT, "getToolkitImpl"));
        JNIRuntimeAccess.register(method(a, JavaAwt.COMPONENT, "isEnabledImpl"));
        JNIRuntimeAccess.register(method(a, JavaAwt.COMPONENT, "getLocationOnScreen_NoTreeLock"));
    }

    // awt_Container.cpp

    private static void registerContainerInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.CONTAINER, "layoutMgr"));
    }

    // awt_Cursor.cpp

    private static void registerCursorClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, SunAwt.W_GLOBAL_CURSOR_MANAGER));
        JNIRuntimeAccess.register(method(a, SunAwt.W_GLOBAL_CURSOR_MANAGER, "nativeUpdateCursor",
                clazz(a, JavaAwt.COMPONENT)));
    }

    private static void registerCursorInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.CURSOR, "pData", "type"));
        JNIRuntimeAccess.register(method(a, JavaAwt.CURSOR, "setPData", long.class));

        JNIRuntimeAccess.register(clazz(a, "java.awt.Point"));
        JNIRuntimeAccess.register(fields(a, "java.awt.Point", "x", "y"));
    }

    // awt_Dialog.cpp

    private static void registerDialogClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, SunAwt.W_WINDOW_PEER));
        JNIRuntimeAccess.register(method(a, SunAwt.W_WINDOW_PEER, "getActiveWindowHandles",
                clazz(a, JavaAwt.COMPONENT)));
    }

    private static void registerDialogInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.DIALOG, "title", "undecorated"));
    }

    // awt_Dimension.cpp

    private static void registerDimensionInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.DIMENSION, "width", "height"));
    }

    // awt_Event.cpp

    private static void registerEventInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.EVENT, "target", "x", "y"));
    }

    // awt_FileDialog.cpp

    private static void registerWFileDialogClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, "java.awt.Point"));
        JNIRuntimeAccess.register(constructor(a, "java.awt.Point", int.class, int.class));
    }

    private static void registerWFileDialogPeerInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, SunAwt.W_FILE_DIALOG_PEER, "parent", "fileFilter"));
        JNIRuntimeAccess.register(method(a, SunAwt.W_FILE_DIALOG_PEER, "setHWnd", long.class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_FILE_DIALOG_PEER, "handleSelected", char[].class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_FILE_DIALOG_PEER, "handleCancel"));
        JNIRuntimeAccess.register(method(a, SunAwt.W_FILE_DIALOG_PEER, "checkFilenameFilter", String.class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_FILE_DIALOG_PEER, "isMultipleMode"));

        JNIRuntimeAccess.register(clazz(a, JavaAwt.FILE_DIALOG));
        JNIRuntimeAccess.register(fields(a, JavaAwt.FILE_DIALOG, "mode", "dir", "file", "filter"));
    }

    // awt_Font.cpp

    private static void registerWFontMetricsInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, SunAwt.W_FONT_METRICS,
                "widths", "ascent", "descent", "leading", "height",
                "maxAscent", "maxDescent", "maxHeight", "maxAdvance"));
    }

    private static void registerFontInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.FONT, "pData", "name", "size", "style"));
        JNIRuntimeAccess.register(method(a, JavaAwt.FONT, "getFontPeer"));
        JNIRuntimeAccess.register(method(a, JavaAwt.FONT, "getFont", String.class));
    }

    private static void registerFontMetricsInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.FONT_METRICS, "font"));
        JNIRuntimeAccess.register(method(a, JavaAwt.FONT_METRICS, "getHeight"));
    }

    private static void registerFontDescriptorInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, SunAwt.FONT_DESCRIPTOR, "nativeName", "useUnicode"));
    }

    private static void registerPlatformFontInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, SunAwt.PLATFORM_FONT, "fontConfig", "componentFonts"));
        JNIRuntimeAccess.register(method(a, SunAwt.PLATFORM_FONT, "makeConvertedMultiFontString", String.class));
    }

    private static void registerWFontPeerInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, SunAwt.W_FONT_PEER, "textComponentFontName"));
    }

    private static void registerWDefaultFontCharsetInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, SunAwt.W_DEFAULT_FONT_CHARSET, "fontName"));
    }

    // awt_Frame.cpp

    private static void registerFrameClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, "sun.awt.EmbeddedFrame"));
        JNIRuntimeAccess.register(clazz(a, "sun.awt.LightweightFrame"));
        JNIRuntimeAccess.register(clazz(a, "sun.awt.im.InputMethodWindow"));

        JNIRuntimeAccess.register(clazz(a, SunAwt.W_FRAME_PEER));
        JNIRuntimeAccess.register(fields(a, SunAwt.W_FRAME_PEER, "keepOnMinimize"));
        JNIRuntimeAccess.register(method(a, SunAwt.W_FRAME_PEER, "notifyIMMOptionChange"));

        JNIRuntimeAccess.register(clazz(a, JavaAwt.RECTANGLE));
        JNIRuntimeAccess.register(constructor(a, JavaAwt.RECTANGLE, int.class, int.class, int.class, int.class));
    }

    private static void registerFrameInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.FRAME, "undecorated"));
    }

    private static void registerWFramePeerInitIDs(DuringAnalysisAccess a) {
        // TODO removed since labsjdk jvmci-21.0-b04?
//        JNIRuntimeAccess.register(method(a, SunAwt.W_FRAME_PEER, "setExtendedState", int.class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_FRAME_PEER, "getExtendedState"));
    }

    private static void registerWEmbeddedFrameInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, SunAwt.W_EMBEDDED_FRAME, "handle", "isEmbeddedInIE"));
        JNIRuntimeAccess.register(method(a, SunAwt.W_EMBEDDED_FRAME, "activateEmbeddingTopLevel"));
    }

    // awt_Insets.cpp

    private static void registerInsetsInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.INSETS, "left", "right", "top", "bottom"));
    }

    // awt_KeyboardFocusManager.cpp - empty

    // awt_Label.cpp

    private static void registerLabelInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.LABEL, "text", "alignment"));
        // WComponentPeer#handlePaint?
    }

    // awt_MenuBar.cpp

    private static void registerMenuBarInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(method(a, JavaAwt.MENU_BAR, "getMenuCountImpl"));
        JNIRuntimeAccess.register(method(a, JavaAwt.MENU_BAR, "getMenuImpl", int.class));
    }

    // awt_MenuItem.cpp

    private static void registerCheckboxMenuItemInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.CHECKBOX_MENU_ITEM, "state"));
    }

    private static void registerMenuComponentInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.MENU_COMPONENT, "font", "appContext"));
    }

    private static void registerMenuItemClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(method(a, JavaAwt.MENU_COMPONENT, "getFont_NoClientCode"));
        JNIRuntimeAccess.register(clazz(a, SunAwt.W_MENU_ITEM_PEER));
        JNIRuntimeAccess.register(method(a, JavaAwt.FONT, "getName"));
        JNIRuntimeAccess.register(method(a, SunAwt.W_FONT_METRICS, "getHeight"));
        JNIRuntimeAccess.register(clazz(a, JavaAwt.TOOLKIT));
        JNIRuntimeAccess.register(method(a, SunAwt.W_MENU_ITEM_PEER, "handleAction", long.class, int.class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_CHECKBOX_MENU_ITEM_PEER, "handleAction", boolean.class));
    }

    private static void registerMenuItemInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.MENU_ITEM, "label", "enabled"));
    }

    private static void registerWMenuItemInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, SunAwt.W_MENU_ITEM_PEER, "isCheckbox", "shortcutLabel"));
        JNIRuntimeAccess.register(method(a, SunAwt.W_MENU_ITEM_PEER, "getDefaultFont"));
    }

    // awt_Menu.cpp

    private static void registerMenuInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(method(a, JavaAwt.MENU, "countItemsImpl"));
        JNIRuntimeAccess.register(method(a, JavaAwt.MENU, "getItemImpl", int.class));
    }

    // awt_Rectangle.cpp

    private static void registerRectangleInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.RECTANGLE, "x", "y", "width", "height"));
    }

    // awt_ScrollPane.cpp

    private static void registerScrollPaneInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.SCROLL_PANE, "scrollbarDisplayPolicy", "hAdjustable", "vAdjustable"));
    }

    private static void registerScrollPaneAdjustableInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.SCROLL_PANE_ADJUSTABLE, "unitIncrement", "blockIncrement"));
    }

    private static void registerWScrollPanePeerInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(method(a, SunAwt.W_SCROLL_PANE_PEER, "postScrollEvent",
                int.class, int.class, int.class, boolean.class));
    }

    // awt_Scrollbar.cpp

    private static void registerScrollbarClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(method(a, SunAwt.W_SCROLLBAR_PEER, "lineDown", int.class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_SCROLLBAR_PEER, "lineUp", int.class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_SCROLLBAR_PEER, "pageDown", int.class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_SCROLLBAR_PEER, "pageUp", int.class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_SCROLLBAR_PEER, "drag", int.class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_SCROLLBAR_PEER, "dragEnd", int.class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_SCROLLBAR_PEER, "warp", int.class));
    }

    private static void registerScrollbarInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.SCROLLBAR, "lineIncrement", "pageIncrement", "orientation"));
    }

    // awt_TextArea.cpp

    private static void registerTextAreaInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.TEXT_AREA, "scrollbarVisibility"));
    }

    // awt_TextField.cpp - empty

    // awt_Toolkit.cpp

    private static void registerToolkitClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, "java.awt.GraphicsEnvironment"));
        JNIRuntimeAccess.register(method(a, "java.awt.GraphicsEnvironment", "isHeadless"));

        JNIRuntimeAccess.register(clazz(a, "sun.awt.AWTAutoShutdown"));
        JNIRuntimeAccess.register(method(a, "sun.awt.AWTAutoShutdown", "notifyToolkitThreadBusy"));
        JNIRuntimeAccess.register(method(a, "sun.awt.AWTAutoShutdown", "notifyToolkitThreadFree"));

        JNIRuntimeAccess.register(method(a, "java.lang.Runnable", "run"));

        JNIRuntimeAccess.register(clazz(a, SunAwt.SUN_TOOLKIT));
        JNIRuntimeAccess.register(method(a, SunAwt.SUN_TOOLKIT, "isTouchKeyboardAutoShowEnabled"));

        JNIRuntimeAccess.register(clazz(a, "java.awt.SystemColor"));
        JNIRuntimeAccess.register(method(a, "java.awt.SystemColor", "updateSystemColors"));

        JNIRuntimeAccess.register(clazz(a, SunAwt.W_TOOLKIT));
        JNIRuntimeAccess.register(clazz(a, SunAwt.W_DESKTOP_PEER));
    }

    private static void registerToolkitInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(method(a, JavaAwt.TOOLKIT, "getDefaultToolkit"));
        JNIRuntimeAccess.register(method(a, JavaAwt.TOOLKIT, "getFontMetrics", clazz(a, JavaAwt.FONT)));

        JNIRuntimeAccess.register(clazz(a, JavaAwt.INSETS));
        JNIRuntimeAccess.register(constructor(a, JavaAwt.INSETS, int.class, int.class, int.class, int.class));
    }

    private static void registerWToolkitInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(method(a, SunAwt.W_TOOLKIT, "windowsSettingChange"));
        JNIRuntimeAccess.register(method(a, SunAwt.W_TOOLKIT, "displayChanged"));

        JNIRuntimeAccess.register(clazz(a, SunJava2d.SURFACE_DATA));
        JNIRuntimeAccess.register(fields(a, SunJava2d.SURFACE_DATA, "pData"));

        JNIRuntimeAccess.register(clazz(a, "sun.awt.image.SunVolatileImage"));
        JNIRuntimeAccess.register(fields(a, "sun.awt.image.SunVolatileImage", "volSurfaceManager"));

        JNIRuntimeAccess.register(clazz(a, "sun.awt.image.VolatileSurfaceManager"));
        JNIRuntimeAccess.register(fields(a, "sun.awt.image.VolatileSurfaceManager", "sdCurrent"));

        JNIRuntimeAccess.register(clazz(a, JavaAwt.COMPONENT));

        JNIRuntimeAccess.register(clazz(a, SunAwt.W_DESKTOP_PEER));
        JNIRuntimeAccess.register(method(a, SunAwt.W_DESKTOP_PEER, "userSessionCallback",
                boolean.class, clazz(a, "java.awt.desktop.UserSessionEvent$Reason")));
        JNIRuntimeAccess.register(method(a, SunAwt.W_DESKTOP_PEER, "systemSleepCallback", boolean.class));

        JNIRuntimeAccess.register(fields(a, "java.awt.desktop.UserSessionEvent$Reason",
                "UNSPECIFIED", "CONSOLE", "REMOTE", "LOCK"));
    }

    private static void registerWToolkitGetScreenInsets(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, JavaAwt.INSETS));
    }

    // awt_TrayIcon.cpp

    private static void registerTrayIconClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(method(a, SunAwt.W_TRAY_ICON_PEER, "showPopupMenu", int.class, int.class));

        JNIRuntimeAccess.register(clazz(a, JavaAwt.MOUSE_EVENT));
        JNIRuntimeAccess.register(constructor(a, JavaAwt.MOUSE_EVENT,
                /* source */ clazz(a, JavaAwt.COMPONENT),
                /* id */ int.class, /* when */ long.class, /* modifiers */ int.class,
                /* x */ int.class, /* y */ int.class, /* xAbs */ int.class, /* yAbs */ int.class,
                /* clickCount */ int.class, /* popupTrigger */ boolean.class, /* button */ int.class));

        JNIRuntimeAccess.register(clazz(a, "java.awt.event.ActionEvent"));
        JNIRuntimeAccess.register(constructor(a, "java.awt.event.ActionEvent",
                /* source */ Object.class, /* id */ int.class, /* command */ String.class,
                /* when */ long.class, /* modifiers */ int.class));
    }

    private static void registerTrayIconInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.TRAY_ICON, "id", "actionCommand"));
    }

    // awt_Window.cpp

    private static void registerWindowClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, "java.awt.geom.Point2D"));
        JNIRuntimeAccess.register(method(a, "java.awt.geom.Point2D", "getX"));
        JNIRuntimeAccess.register(method(a, "java.awt.geom.Point2D", "getY"));

        JNIRuntimeAccess.register(clazz(a, "javax.swing.Popup$HeavyWeightWindow"));

        JNIRuntimeAccess.register(clazz(a, "com.sun.java.swing.plaf.windows.WindowsPopupWindow"));
        JNIRuntimeAccess.register(fields(a, "com.sun.java.swing.plaf.windows.WindowsPopupWindow",
                "windowType", "UNDEFINED_WINDOW_TYPE", "TOOLTIP_WINDOW_TYPE", "MENU_WINDOW_TYPE",
                "SUBMENU_WINDOW_TYPE", "POPUPMENU_WINDOW_TYPE", "COMBOBOX_POPUP_WINDOW_TYPE"));

        JNIRuntimeAccess.register(clazz(a, JavaAwt.FRAME));

        JNIRuntimeAccess.register(clazz(a, "java.awt.event.ComponentEvent"));
        JNIRuntimeAccess.register(constructor(a, "java.awt.event.ComponentEvent",
                clazz(a, JavaAwt.COMPONENT), int.class));

        JNIRuntimeAccess.register(clazz(a, "sun.awt.TimedWindowEvent"));
        JNIRuntimeAccess.register(constructor(a, "sun.awt.TimedWindowEvent",
                /* source */ clazz(a, JavaAwt.WINDOW), /* id */ int.class, /* opposite */ clazz(a, JavaAwt.WINDOW),
                /* oldState */ int.class, /* newState */ int.class, /* time */ long.class));

        JNIRuntimeAccess.register(clazz(a, "java.awt.SequencedEvent"));
        JNIRuntimeAccess.register(constructor(a, "java.awt.SequencedEvent", clazz(a, JavaAwt.AWT_EVENT)));

        // TODO since labsjdk jvmci-21.0-b04?
        JNIRuntimeAccess.register(clazz(a, "java.awt.Window"));

        JNIRuntimeAccess.register(method(a, SunAwt.W_COMPONENT_PEER, "dynamicallyLayoutContainer"));
        JNIRuntimeAccess.register(method(a, SunAwt.W_WINDOW_PEER, "draggedToNewScreen"));
    }

    private static void registerWindowInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.WINDOW,
                "warningString", "locationByPlatform", "securityWarningWidth",
                "securityWarningHeight", "autoRequestFocus"));
        JNIRuntimeAccess.register(method(a, JavaAwt.WINDOW, "getWarningString"));
        JNIRuntimeAccess.register(method(a, JavaAwt.WINDOW, "calculateSecurityWarningPosition",
                double.class, double.class, double.class, double.class));

        JNIRuntimeAccess.register(clazz(a, JavaAwt.WINDOW + "$Type"));
        // for java.awt.Window$Type
        JNIRuntimeAccess.register(method(a, "java.lang.Enum", "name"));
    }

    private static void registerWWindowPeerInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, SunAwt.W_WINDOW_PEER,
                "sysX", "sysY", "sysW", "sysH", "windowType"));
        // TODO since labsjdk jvmci-21.0-b04?
        JNIRuntimeAccess.register(method(a, SunAwt.W_WINDOW_PEER, "notifyWindowStateChanged",
                int.class, int.class));
    }

    private static void registerWWindowPeerGetNativeWindowSize(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, JavaAwt.DIMENSION));
        JNIRuntimeAccess.register(constructor(a, JavaAwt.DIMENSION, int.class, int.class));
    }

    // awt_InputEvent.cpp

    private static void registerInputEventInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.INPUT_EVENT, "modifiers"));
    }

    // awt_KeyEvent.cpp

    private static void registerKeyEventInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.KEY_EVENT,
                "keyCode", "keyChar", "rawCode", "primaryLevelUnicode", "scancode", "extendedKeyCode"));
    }

    // awt_MouseEvent.cpp

    private static void registerMouseEventInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.MOUSE_EVENT,
                "x", "y", "causedByTouchEvent", "button"));
    }

    // awt_Win32GraphicsEnv.cpp

    private static void registerWin32GraphicsEnvironmentClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(method(a, SunAwt.WIN32_GRAPHICS_ENVIRONMENT, "dwmCompositionChanged", boolean.class));
    }

    // awt_Win32GraphicsConfig.cpp

    private static void registerWin32GraphicsConfigInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, SunAwt.WIN32_GRAPHICS_CONFIG, "visual"));
    }

    private static void registerWin32GraphicsConfigGetBounds(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, JavaAwt.RECTANGLE));
        JNIRuntimeAccess.register(constructor(a, JavaAwt.RECTANGLE,
                int.class, int.class, int.class, int.class));
    }

    // awt_Win32GraphicsDevice.cpp

    private static void registerWin32GraphicsDeviceClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(method(a, SunAwt.WIN32_GRAPHICS_DEVICE, "invalidate", int.class));

        JNIRuntimeAccess.register(clazz(a, "sun.awt.Win32ColorModel24"));
        JNIRuntimeAccess.register(constructor(a, "sun.awt.Win32ColorModel24"));

        JNIRuntimeAccess.register(clazz(a, "java.awt.image.DirectColorModel"));
        JNIRuntimeAccess.register(constructor(a, "java.awt.image.DirectColorModel",
                int.class, int.class, int.class, int.class));

        JNIRuntimeAccess.register(clazz(a, "java.awt.color.ColorSpace"));
        JNIRuntimeAccess.register(method(a, "java.awt.color.ColorSpace", "getInstance", int.class));

        JNIRuntimeAccess.register(clazz(a, "java.awt.image.ComponentColorModel"));
        JNIRuntimeAccess.register(constructor(a, "java.awt.image.ComponentColorModel",
                /* colorSpace */ clazz(a, "java.awt.color.ColorSpace"),
                /* bits */ int[].class, /* hasAlpha */ boolean.class, /* isAlphaPremultiplied */ boolean.class,
                /* transparency */ int.class, /* transferType */ int.class));

        JNIRuntimeAccess.register(clazz(a, "java.math.BigInteger"));
        JNIRuntimeAccess.register(constructor(a, "java.math.BigInteger", byte[].class));

        JNIRuntimeAccess.register(clazz(a, JavaAwt.INDEX_COLOR_MODEL));
        JNIRuntimeAccess.register(constructor(a, JavaAwt.INDEX_COLOR_MODEL,
                /* bits */ int.class, /* size */ int.class, /* cmap */ int[].class, /* start */ int.class,
                /* transferType */ int.class, /* validBits */ clazz(a, "java.math.BigInteger")));

        JNIRuntimeAccess.register(clazz(a, "java.awt.DisplayMode"));
        JNIRuntimeAccess.register(constructor(a, "java.awt.DisplayMode",
                int.class, int.class, int.class, int.class));

        JNIRuntimeAccess.register(clazz(a, "java.util.ArrayList"));
        JNIRuntimeAccess.register(method(a, "java.util.ArrayList", "add", Object.class));
    }

    private static void registerWin32GraphicsDeviceInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, JavaAwt.INDEX_COLOR_MODEL));
        JNIRuntimeAccess.register(fields(a, JavaAwt.INDEX_COLOR_MODEL, "rgb", "lookupcache"));

        JNIRuntimeAccess.register(clazz(a, SunAwt.W_TOOLKIT));
        JNIRuntimeAccess.register(method(a, SunAwt.W_TOOLKIT, "paletteChanged"));

        JNIRuntimeAccess.register(fields(a, SunAwt.WIN32_GRAPHICS_DEVICE, "dynamicColorModel"));
    }

    private static void registerWin32GraphicsDeviceExitFullScreenExclusive(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.WINDOW, "alwaysOnTop"));
    }

    // ShellFolder2.cpp

    private static void registerWin32ShellFolder2InitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, SunAwt.WIN32_SHELL_FOLDER2,
                "pIShellIcon", "displayName", "folderType", "FNAME", "FSIZE", "FTYPE", "FDATE"));
        JNIRuntimeAccess.register(method(a, SunAwt.WIN32_SHELL_FOLDER2, "setIShellFolder", long.class));
        JNIRuntimeAccess.register(method(a, SunAwt.WIN32_SHELL_FOLDER2, "setRelativePIDL", long.class));
    }

    private static void registerWin32ShellFolder2DoGetColumnInfo(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, "sun.awt.shell.ShellFolderColumnInfo"));
        JNIRuntimeAccess.register(constructor(a, "sun.awt.shell.ShellFolderColumnInfo",
                String.class, int.class, int.class, boolean.class));
    }

    private static void registerWin32ShellFolder2LoadKnownFolders(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, SunAwt.WIN32_SHELL_FOLDER2 + "$KnownFolderDefinition"));
        JNIRuntimeAccess.register(constructor(a, SunAwt.WIN32_SHELL_FOLDER2 + "$KnownFolderDefinition"));
        JNIRuntimeAccess.register(fields(a, SunAwt.WIN32_SHELL_FOLDER2 + "$KnownFolderDefinition",
                "guid", "name", "description", "parent", "relativePath", "parsingName", "tooltip",
                "localizedName", "icon", "security", "path", "saveLocation", "category", "attributes",
                "defenitionFlags", "ftidType"));
    }

    // ThemeReader.cpp

    private static void registerThemeReaderGetThemeMargins(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, JavaAwt.INSETS));
    }

    private static void registerThemeReaderGetColor(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, JavaAwt.COLOR));
        JNIRuntimeAccess.register(constructor(a, JavaAwt.COLOR, int.class, int.class, int.class));
    }

    private static void registerThemeReaderGetPoint(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, "java.awt.Point"));
        JNIRuntimeAccess.register(constructor(a, "java.awt.Point", int.class, int.class));
    }

    private static void registerThemeReaderGetPosition(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, JavaAwt.DIMENSION));
        JNIRuntimeAccess.register(constructor(a, JavaAwt.DIMENSION, int.class, int.class));
    }

    private static void registerThemeReaderGetPartSize(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, JavaAwt.DIMENSION));
        JNIRuntimeAccess.register(constructor(a, JavaAwt.DIMENSION, int.class, int.class));
    }

    private static void registerThemeReaderGetThemeBackgroundContentMargins(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, JavaAwt.INSETS));
    }

    // awt_Canvas.cpp

    private static void registerCanvasClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, JavaAwt.CANVAS));
        JNIRuntimeAccess.register(clazz(a, SunAwt.WIN32_GRAPHICS_CONFIG));
    }

    // awt_Clipboard.cpp

    private static void registerWClipboardClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(method(a, SunAwt.W_CLIPBOARD, "handleContentsChanged"));
    }

    private static void registerWClipboardInit(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(method(a, SunAwt.W_CLIPBOARD, "lostSelectionOwnershipImpl"));
    }

    // awt_DataTransferer.cpp

    private static void registerDataTransfererClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, SunAwt.DATA_TRANSFERER));
        JNIRuntimeAccess.register(method(a, SunAwt.DATA_TRANSFERER, "getInstance"));
        JNIRuntimeAccess.register(method(a, SunAwt.DATA_TRANSFERER, "convertData",
                Object.class, clazz(a, "java.awt.datatransfer.Transferable"), long.class, Map.class, boolean.class));
        JNIRuntimeAccess.register(method(a, SunAwt.DATA_TRANSFERER, "concatData", Object.class, Object.class));
    }

    private static void registerWDataTransfererDragQueryFile(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(String.class);
    }

    // awt_Desktop.cpp - empty

    // awt_DesktopProperties.cpp

    private static void registerWDesktopPropertiesInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, SunAwt.W_DESKTOP_PROPERTIES, "pData"));
        JNIRuntimeAccess.register(method(a, SunAwt.W_DESKTOP_PROPERTIES, "setBooleanProperty", String.class, boolean.class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_DESKTOP_PROPERTIES, "setIntegerProperty", String.class, int.class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_DESKTOP_PROPERTIES, "setStringProperty", String.class, String.class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_DESKTOP_PROPERTIES, "setColorProperty", String.class, int.class, int.class, int.class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_DESKTOP_PROPERTIES, "setFontProperty", String.class, String.class, int.class, int.class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_DESKTOP_PROPERTIES, "setSoundProperty", String.class, String.class));
    }

    // awt_DnDDS.cpp

    private static void registerDragSourceClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, JavaAwt.INPUT_EVENT));
        JNIRuntimeAccess.register(clazz(a, SunAwt.W_DRAG_SOURCE_CONTEXT_PEER));
        JNIRuntimeAccess.register(method(a, SunAwt.SUN_DRAG_SOURCE_CONTEXT_PEER, "dragEnter",
                int.class, int.class, int.class, int.class));
        JNIRuntimeAccess.register(method(a, SunAwt.SUN_DRAG_SOURCE_CONTEXT_PEER, "dragMotion",
                int.class, int.class, int.class, int.class));
        JNIRuntimeAccess.register(method(a, SunAwt.SUN_DRAG_SOURCE_CONTEXT_PEER, "operationChanged",
                int.class, int.class, int.class, int.class));
        JNIRuntimeAccess.register(method(a, SunAwt.SUN_DRAG_SOURCE_CONTEXT_PEER, "dragExit",
                int.class, int.class));
        JNIRuntimeAccess.register(method(a, SunAwt.SUN_DRAG_SOURCE_CONTEXT_PEER, "dragDropFinished",
                boolean.class, int.class, int.class, int.class));
        JNIRuntimeAccess.register(method(a, SunAwt.SUN_DRAG_SOURCE_CONTEXT_PEER, "dragMouseMoved",
                int.class, int.class, int.class, int.class));
    }

    // awt_DnDDT.cpp

    private static void registerDropTargetClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, "java.lang.System"));
        JNIRuntimeAccess.register(method(a, "java.lang.System", "getProperty", String.class));

        JNIRuntimeAccess.register(clazz(a, SunAwt.W_DROP_TARGET_CONTEXT_PEER));
        JNIRuntimeAccess.register(method(a, SunAwt.W_DROP_TARGET_CONTEXT_PEER, "getWDropTargetContextPeer"));
        JNIRuntimeAccess.register(method(a, SunAwt.SUN_DROP_TARGET_CONTEXT_PEER, "handleEnterMessage",
                clazz(a, JavaAwt.COMPONENT), int.class, int.class, int.class, int.class, long[].class, long.class));
        JNIRuntimeAccess.register(method(a, SunAwt.SUN_DROP_TARGET_CONTEXT_PEER, "handleExitMessage",
                clazz(a, JavaAwt.COMPONENT), long.class));
        JNIRuntimeAccess.register(method(a, SunAwt.SUN_DROP_TARGET_CONTEXT_PEER, "handleMotionMessage",
                clazz(a, JavaAwt.COMPONENT), int.class, int.class, int.class, int.class, long[].class, long.class));
        JNIRuntimeAccess.register(method(a, SunAwt.SUN_DROP_TARGET_CONTEXT_PEER, "handleDropMessage",
                clazz(a, JavaAwt.COMPONENT), int.class, int.class, int.class, int.class, long[].class, long.class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_DROP_TARGET_CONTEXT_PEER, "getFileStream",
                String.class, long.class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_DROP_TARGET_CONTEXT_PEER, "getIStream", long.class));
    }

    // awt_InputMethod.cpp

    private static void registerWInputMethodClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, "java.util.Locale"));
        JNIRuntimeAccess.register(method(a, "java.util.Locale", "forLanguageTag", String.class));
    }

    private static void registerWInputMethodDescriptorGetNativeAvailableLocales(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, "java.util.Locale"));
    }

    // awt_List.cpp
    private static void registerListClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(method(a, SunAwt.W_LIST_PEER, "getPreferredSize", int.class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_LIST_PEER, "handleListChanged", int.class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_LIST_PEER, "handleAction",
                int.class, long.class, int.class));
        // from awt_Component.cpp
        JNIRuntimeAccess.register(method(a, "java.awt.List", "getItemImpl", int.class));
    }

    // MouseInfo.cpp

    private static void registerWMouseInfoPeerFillPointWithCoords(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, "java.awt.Point"));
        JNIRuntimeAccess.register(fields(a, "java.awt.Point", "x", "y"));
    }

    // awt_Object.cpp

    private static void registerWObjectPeerClass(DuringAnalysisAccess a) {
        // TODO:
        //     JNU_CallMethodByName(env, NULL, GetPeer(env), "postEvent",
        //                         "(Ljava/awt/AWTEvent;)V", event);
    }

    private static void registerWObjectPeerInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, SunAwt.W_OBJECT_PEER, "pData", "destroyed", "target", "createError"));
        JNIRuntimeAccess.register(method(a, SunAwt.W_OBJECT_PEER, "getPeerForTarget", Object.class));
    }

    // awt_Panel.cpp

    private static void registerWPanelPeerInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, SunAwt.W_PANEL_PEER, "insets_"));
    }

    // awt_PopupMenu.cpp

    private static void registerWPopupMenuClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, JavaAwt.POPUP_MENU));
        JNIRuntimeAccess.register(fields(a, JavaAwt.POPUP_MENU, "isTrayIconPopup"));
    }

    // awt_PrintDialog.cpp

    private static void registerWPrintDialogInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, SunAwt.W_PPRINT_DIALOG, "pjob"));
        // see awt_PrintControl.cpp
        registerAwtPrintControlInitIDs(a);
    }

    private static void registerWPrintDialogPeerInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, SunAwt.W_PPRINT_DIALOG_PEER, "parent"));
        JNIRuntimeAccess.register(method(a, SunAwt.W_PPRINT_DIALOG_PEER, "setHWnd", long.class));
    }

    // awt_PrintJob.cpp

    private static void registerWPrinterJobClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, SunAwt.W_PRINTER_JOB,
                "mPrintPaperSize", "mPrintXRes", "mPrintYRes", "mPrintPhysX", "mPrintPhysY",
                "mPrintWidth", "mPrintHeight", "mPageWidth", "mPageHeight",
                "driverDoesMultipleCopies", "driverDoesCollation", "userRequestedCollation", "noDefaultPrinter",
                "mAttSides", "mAttChromaticity", "mAttXRes", "mAttYRes", "mAttQuality", "mAttCollate", "mAttCopies",
                "mAttMediaSizeName", "mAttMediaTray"));

        JNIRuntimeAccess.register(fields(a, SunPrint.RASTER_PRINTER_JOB, "landscapeRotates270"));
        JNIRuntimeAccess.register(method(a, JavaAwt.PRINTER_JOB, "getCopies"));
    }

    private static void registerPageFormatClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(method(a, JavaAwt.PAGE_FORMAT, "getPaper"));
        JNIRuntimeAccess.register(method(a, JavaAwt.PAGE_FORMAT, "setPaper", clazz(a, JavaAwt.PAPER)));
        JNIRuntimeAccess.register(method(a, JavaAwt.PAGE_FORMAT, "getOrientation"));
        JNIRuntimeAccess.register(method(a, JavaAwt.PAGE_FORMAT, "setOrientation", int.class));
    }

    private static void registerPaperClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(method(a, JavaAwt.PAPER, "setSize", double.class, double.class));
        JNIRuntimeAccess.register(method(a, JavaAwt.PAPER, "setImageableArea",
                double.class, double.class, double.class, double.class));
        JNIRuntimeAccess.register(method(a, JavaAwt.PAPER, "getWidth"));
        JNIRuntimeAccess.register(method(a, JavaAwt.PAPER, "getHeight"));
        JNIRuntimeAccess.register(method(a, JavaAwt.PAPER, "getImageableX"));
        JNIRuntimeAccess.register(method(a, JavaAwt.PAPER, "getImageableY"));
        JNIRuntimeAccess.register(method(a, JavaAwt.PAPER, "getImageableWidth"));
        JNIRuntimeAccess.register(method(a, JavaAwt.PAPER, "getImageableHeight"));
    }

    private static void registerWPageDialogInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, SunAwt.W_PAGE_DIALOG, "page"));
    }

    private static void registerWPrinterJobInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, SunAwt.W_PRINTER_JOB, "pjob"));
        JNIRuntimeAccess.register(clazz(a, SunAwt.W_PPRINT_DIALOG_PEER));
        JNIRuntimeAccess.register(method(a, SunAwt.W_PPRINT_DIALOG_PEER, "setHWnd", long.class));
        // see awt_PrintControl.cpp
        registerAwtPrintControlInitIDs(a);
    }

    // awt_PrintControl.cpp

    /**
     * function called by {@link #registerWPrintDialogInitIDs} and {@link #registerWPrinterJobInitIDs}.
     */
    private static void registerAwtPrintControlInitIDs(DuringAnalysisAccess a) {
        if (isRunOnce(JNIRegistrationJavaAwtWindows::registerAwtPrintControlInitIDs)) {
            return; // already registered
        }
        JNIRuntimeAccess.register(clazz(a, SunAwt.W_PRINTER_JOB));
        JNIRuntimeAccess.register(fields(a, SunAwt.W_PRINTER_JOB, "dialogOwnerPeer",
                "driverDoesMultipleCopies", "driverDoesCollation"));
        JNIRuntimeAccess.register(method(a, SunAwt.W_PRINTER_JOB, "getPrintDC"));
        JNIRuntimeAccess.register(method(a, SunAwt.W_PRINTER_JOB, "setPrintDC", long.class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_PRINTER_JOB, "getDevMode"));
        JNIRuntimeAccess.register(method(a, SunAwt.W_PRINTER_JOB, "setDevMode", long.class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_PRINTER_JOB, "getDevNames"));
        JNIRuntimeAccess.register(method(a, SunAwt.W_PRINTER_JOB, "setDevNames", long.class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_PRINTER_JOB, "getCopiesAttrib"));
        JNIRuntimeAccess.register(method(a, SunAwt.W_PRINTER_JOB, "getCollateAttrib"));
        JNIRuntimeAccess.register(method(a, SunAwt.W_PRINTER_JOB, "getOrientAttrib"));
        JNIRuntimeAccess.register(method(a, SunAwt.W_PRINTER_JOB, "getDestAttrib"));
        JNIRuntimeAccess.register(method(a, SunAwt.W_PRINTER_JOB, "getQualityAttrib"));
        JNIRuntimeAccess.register(method(a, SunAwt.W_PRINTER_JOB, "getColorAttrib"));
        JNIRuntimeAccess.register(method(a, SunAwt.W_PRINTER_JOB, "getSidesAttrib"));
        JNIRuntimeAccess.register(method(a, SunAwt.W_PRINTER_JOB, "getPrinterAttrib"));
        JNIRuntimeAccess.register(method(a, SunAwt.W_PRINTER_JOB, "getWin32MediaAttrib"));
        JNIRuntimeAccess.register(method(a, SunAwt.W_PRINTER_JOB, "setWin32MediaAttrib", int.class, int.class, int.class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_PRINTER_JOB, "getMediaTrayAttrib"));
        JNIRuntimeAccess.register(method(a, SunAwt.W_PRINTER_JOB, "setMediaTrayAttrib", int.class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_PRINTER_JOB, "getPrintToFileEnabled"));
        JNIRuntimeAccess.register(method(a, SunAwt.W_PRINTER_JOB, "setNativeAttributes", int.class, int.class, int.class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_PRINTER_JOB, "setRangeCopiesAttribute", int.class, int.class, boolean.class, int.class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_PRINTER_JOB, "setResolutionDPI", int.class, int.class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_PRINTER_JOB, "setPrinterNameAttrib", String.class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_PRINTER_JOB, "setJobAttributes",
                /* attributes */ clazz(a, "javax.print.attribute.PrintRequestAttributeSet"),
                /* fields */ int.class, /* values */ int.class, /* copies */ short.class,
                /* dmPaperSize */ short.class, /* dmPaperWidth */ short.class, /* dmPaperLength */ short.class,
                /* dmDefaultSource */ short.class, /* xRes */ short.class, /* yRes */ short.class));

        JNIRuntimeAccess.register(method(a, SunPrint.RASTER_PRINTER_JOB, "getParentWindowID"));
        JNIRuntimeAccess.register(method(a, SunPrint.RASTER_PRINTER_JOB, "getFromPageAttrib"));
        JNIRuntimeAccess.register(method(a, SunPrint.RASTER_PRINTER_JOB, "getToPageAttrib"));
        JNIRuntimeAccess.register(method(a, SunPrint.RASTER_PRINTER_JOB, "getMinPageAttrib"));
        JNIRuntimeAccess.register(method(a, SunPrint.RASTER_PRINTER_JOB, "getMaxPageAttrib"));
        JNIRuntimeAccess.register(method(a, SunPrint.RASTER_PRINTER_JOB, "getSelectAttrib"));
    }

    // awt_Taskbar.cpp

    private static void registerWTaskbarPeerSetProgressState(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, JavaAwt.TASKBAR + "$State"));
        JNIRuntimeAccess.register(method(a, JavaAwt.TASKBAR + "$State", "name"));
    }

    // awt_Robot.cpp - empty

    // awt_TextComponent.cpp

    private static void registerTextComponentClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(method(a, SunAwt.W_TEXT_COMPONENT_PEER, "valueChanged"));
    }

    private static void registerWTextComponentPeerInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, JavaAwt.TEXT_COMPONENT));
        JNIRuntimeAccess.register(method(a, JavaAwt.TEXT_COMPONENT, "canAccessClipboard"));
    }

    // awt_DrawingSurface.cpp

    /**
     * It is not entirely clear how this is called, but we're assuming it's fine to go with class reachability,
     * since WEmbeddedFrame has a native static initializer anyway.
     */
    private static void registerWEmbeddedFrameClass(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, SunAwt.W_EMBEDDED_FRAME));
        JNIRuntimeAccess.register(constructor(a, SunAwt.W_EMBEDDED_FRAME, long.class));
        JNIRuntimeAccess.register(method(a, "sun.awt.EmbeddedFrame", "setBoundsPrivate",
                int.class, int.class, int.class, int.class));
        JNIRuntimeAccess.register(method(a, SunAwt.W_EMBEDDED_FRAME, "synthesizeWindowActivation", boolean.class));
    }


    // from shared code

    // imageInitIDs.c

    private static void registerBufferedImageInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.BUFFERED_IMAGE, "raster", "imageType", "colorModel"));
        JNIRuntimeAccess.register(method(a, JavaAwt.BUFFERED_IMAGE, "getRGB",
                int.class, int.class, int.class, int.class, int[].class, int.class, int.class));
        JNIRuntimeAccess.register(method(a, JavaAwt.BUFFERED_IMAGE, "setRGB",
                int.class, int.class, int.class, int.class, int[].class, int.class, int.class));
    }

    private static void registerRasterInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.RASTER,
                "width", "height", "numBands", "minX", "minY", "sampleModelTranslateX", "sampleModelTranslateY",
                "sampleModel", "numDataElements", "numBands", "dataBuffer"));
    }

    private static void registerByteComponentRasterInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, SunAwt.BYTE_COMPONENT_RASTER,
                "data", "scanlineStride", "pixelStride", "dataOffsets", "type"));
    }

    private static void registerBytePackedRasterInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, SunAwt.BYTE_PACKED_RASTER,
                "data", "scanlineStride", "pixelBitStride", "type", "dataBitOffset"));
    }

    private static void registerShortComponentRasterInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, SunAwt.SHORT_COMPONENT_RASTER,
                "data", "scanlineStride", "pixelStride", "dataOffsets", "type"));
    }

    private static void registerIntegerComponentRasterInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, SunAwt.INTEGER_COMPONENT_RASTER,
                "data", "scanlineStride", "pixelStride", "dataOffsets", "type"));
    }

    private static void registerSinglePixelPackedSampleModelInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.SINGLE_PIXEL_PACKED_SAMPLE_MODEL,
                "bitMasks", "bitOffsets", "bitSizes", "maxBitSize"));
    }

    private static void registerColorModelInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.COLOR_MODEL,
                "pData", "nBits", "colorSpace", "numComponents", "supportsAlpha",
                "isAlphaPremultiplied", "transparency", "colorSpaceType", "is_sRGB"));
        JNIRuntimeAccess.register(method(a, JavaAwt.COLOR_MODEL, "getRGBdefault"));
    }

    private static void registerIndexColorModelInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.INDEX_COLOR_MODEL, "transparent_index", "map_size", "rgb"));
    }

    private static void registerSampleModelInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.SAMPLE_MODEL, "width", "height"));
        JNIRuntimeAccess.register(method(a, JavaAwt.SAMPLE_MODEL, "getPixels",
                int.class, int.class, int.class, int.class, int[].class, clazz(a, "java.awt.image.DataBuffer")));
        JNIRuntimeAccess.register(method(a, JavaAwt.SAMPLE_MODEL, "setPixels",
                int.class, int.class, int.class, int.class, int[].class, clazz(a, "java.awt.image.DataBuffer")));
    }

    private static void registerKernelInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.KERNEL, "width", "height", "data"));
    }

    // debug_trace.c - empty

    // BufImgSurfaceData.c

    private static void registerBufImgSurfaceDataInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, JavaAwt.INDEX_COLOR_MODEL,
                "rgb", "allgrayopaque", "map_size", "colorData"));
        JNIRuntimeAccess.register(fields(a, SunAwt.BUF_IMG_SURFACE_DATA + "$ICMColorData", "pData"));
        JNIRuntimeAccess.register(constructor(a, SunAwt.BUF_IMG_SURFACE_DATA + "$ICMColorData", long.class));
    }

    // DataBufferNative.c - empty

    // gifdecoder.c

    private static void registerGifImageDecoderInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, SunAwt.GIF_IMAGE_DECODER, "prefix", "suffix", "outCode"));
        JNIRuntimeAccess.register(method(a, SunAwt.GIF_IMAGE_DECODER, "readBytes",
                byte[].class, int.class, int.class));
        JNIRuntimeAccess.register(method(a, SunAwt.GIF_IMAGE_DECODER, "sendPixels",
                int.class, int.class, int.class, int.class, byte[].class, clazz(a, JavaAwt.COLOR_MODEL)));
    }

    // awt_ImageRep.c

    private static void registerImageRepresentationInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, SunAwt.IMAGE_REPRESENTATION, "numSrcLUT", "srcLUTtransIndex"));
    }

    // awt_ImagingLib.c - empty
}