package com.oracle.svm.hosted.jdk;

import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import java.util.function.Consumer;

public abstract class JNIRegistrationAwtUtil extends JNIRegistrationUtil {

    private static final String INITIALIZATION_REASON = "for awt native code support via JNI";

    protected void initializeAtRunTime(Feature.FeatureAccess access, String... classNames) {
        RuntimeClassInitializationSupport classInitSupport = ImageSingletons.lookup(RuntimeClassInitializationSupport.class);
        for (String className : classNames) {
            classInitSupport.initializeAtRunTime(clazz(access, className), INITIALIZATION_REASON);
        }
    }

    protected void registerClassHandler(Feature.BeforeAnalysisAccess a, Consumer<Feature.DuringAnalysisAccess> callback, String classname) {
        a.registerReachabilityHandler(callback, clazz(a, classname));
    }

    protected void registerInitIDsHandler(Feature.BeforeAnalysisAccess a,
                                          Consumer<Feature.DuringAnalysisAccess> callback,
                                          String classname,
                                          Class<?>... paramTypes) {
        a.registerReachabilityHandler(callback, method(a, classname, "initIDs", paramTypes));
    }

    /**
     * transform a normal class reference to an array class reference, readable by {@link Class#forName(String)}.
     * For example, transform 'com.package.ClassName' into '[Lcom.package.ClassName;'
     */
    protected static String array(String clazz) {
        return "[L" + clazz + ';';
    }

    protected static final class JavaAwt {
        protected static final String AWT_EVENT = "java.awt.AWTEvent";
        protected static final String BUTTON = "java.awt.Button";
        protected static final String CANVAS = "java.awt.Canvas";
        protected static final String CHECKBOX_MENU_ITEM = "java.awt.CheckboxMenuItem";
        protected static final String CHECKBOX = "java.awt.Checkbox";
        protected static final String CHOICE = "java.awt.Choice";
        protected static final String COLOR = "java.awt.Color";
        protected static final String COMPONENT = "java.awt.Component";
        protected static final String CONTAINER = "java.awt.Container";
        protected static final String CURSOR = "java.awt.Cursor";
        protected static final String DIALOG = "java.awt.Dialog";
        protected static final String DIMENSION = "java.awt.Dimension";
        protected static final String EVENT = "java.awt.Event";
        protected static final String FILE_DIALOG = "java.awt.FileDialog";
        protected static final String FONT_METRICS = "java.awt.FontMetrics";
        protected static final String FONT = "java.awt.Font";
        protected static final String FRAME = "java.awt.Frame";
        protected static final String INSETS = "java.awt.Insets";
        protected static final String KEYBOARD_FOCUS_MANAGER = "java.awt.KeyboardFocusManager";
        protected static final String LABEL = "java.awt.Label";
        protected static final String LIST = "java.awt.List";
        protected static final String MENU_BAR = "java.awt.MenuBar";
        protected static final String MENU_COMPONENT = "java.awt.MenuComponent";
        protected static final String MENU_ITEM = "java.awt.MenuItem";
        protected static final String MENU = "java.awt.Menu";
        protected static final String PANEL = "java.awt.Panel";
        protected static final String POPUP_MENU = "java.awt.PopupMenu";
        protected static final String RECTANGLE = "java.awt.Rectangle";
        protected static final String ROBOT = "java.awt.Robot";
        protected static final String SCROLL_PANE_ADJUSTABLE = "java.awt.ScrollPaneAdjustable";
        protected static final String SCROLL_PANE = "java.awt.ScrollPane";
        protected static final String SCROLLBAR = "java.awt.Scrollbar";
        protected static final String TASKBAR = "java.awt.Taskbar";
        protected static final String TEXT_AREA = "java.awt.TextArea";
        protected static final String TEXT_COMPONENT = "java.awt.TextComponent";
        protected static final String TEXT_FIELD = "java.awt.TextField";
        protected static final String TOOLKIT = "java.awt.Toolkit";
        protected static final String TRAY_ICON = "java.awt.TrayIcon";
        protected static final String WINDOW = "java.awt.Window";

        protected static final String DRAG_SOURCE = "java.awt.dnd.DragSource";
        protected static final String DROP_TARGET = "java.awt.dnd.DropTarget";

        protected static final String INPUT_EVENT = "java.awt.event.InputEvent";
        protected static final String KEY_EVENT = "java.awt.event.KeyEvent";
        protected static final String MOUSE_EVENT = "java.awt.event.MouseEvent";

        protected static final String BUFFERED_IMAGE = "java.awt.image.BufferedImage";
        protected static final String COLOR_MODEL = "java.awt.image.ColorModel";
        protected static final String INDEX_COLOR_MODEL = "java.awt.image.IndexColorModel";
        protected static final String KERNEL = "java.awt.image.Kernel";
        protected static final String RASTER = "java.awt.image.Raster";
        protected static final String SAMPLE_MODEL = "java.awt.image.SampleModel";
        protected static final String SINGLE_PIXEL_PACKED_SAMPLE_MODEL = "java.awt.image.SinglePixelPackedSampleModel";

        protected static final String PAGE_FORMAT = "java.awt.print.PageFormat";
        protected static final String PAPER = "java.awt.print.Paper";
        protected static final String PRINTER_JOB = "java.awt.print.PrinterJob";
    }

    protected static final class SunAwt {
        protected static final String DEBUG_SETTINGS = "sun.awt.DebugSettings";
        protected static final String FONT_DESCRIPTOR = "sun.awt.FontDescriptor";
        protected static final String PLATFORM_FONT = "sun.awt.PlatformFont";
        protected static final String SUN_TOOLKIT = "sun.awt.SunToolkit";
        protected static final String WIN32_FONT_MANAGER = "sun.awt.Win32FontManager";
        protected static final String WIN32_GRAPHICS_CONFIG = "sun.awt.Win32GraphicsConfig";
        protected static final String WIN32_GRAPHICS_DEVICE = "sun.awt.Win32GraphicsDevice";
        protected static final String WIN32_GRAPHICS_ENVIRONMENT = "sun.awt.Win32GraphicsEnvironment";

        protected static final String DATA_TRANSFERER = "sun.awt.datatransfer.DataTransferer";

        protected static final String SUN_DRAG_SOURCE_CONTEXT_PEER = "sun.awt.dnd.SunDragSourceContextPeer";
        protected static final String SUN_DROP_TARGET_CONTEXT_PEER = "sun.awt.dnd.SunDropTargetContextPeer";

        protected static final String BUF_IMG_SURFACE_DATA = "sun.awt.image.BufImgSurfaceData";
        protected static final String BYTE_COMPONENT_RASTER = "sun.awt.image.ByteComponentRaster";
        protected static final String BYTE_PACKED_RASTER = "sun.awt.image.BytePackedRaster";
        protected static final String DATA_BUFFER_NATIVE = "sun.awt.image.DataBufferNative";
        protected static final String GIF_IMAGE_DECODER = "sun.awt.image.GifImageDecoder";
        protected static final String IMAGE_REPRESENTATION = "sun.awt.image.ImageRepresentation";
        protected static final String IMAGING_LIB = "sun.awt.image.ImagingLib";
        protected static final String INTEGER_COMPONENT_RASTER = "sun.awt.image.IntegerComponentRaster";
        protected static final String SHORT_COMPONENT_RASTER = "sun.awt.image.ShortComponentRaster";

        protected static final String WIN32_SHELL_FOLDER2 = "sun.awt.shell.Win32ShellFolder2";
        protected static final String WIN32_SHELL_FOLDER_MANAGER2 = "sun.awt.shell.Win32ShellFolderManager2";

        protected static final String THEME_READER = "sun.awt.windows.ThemeReader";
        protected static final String W_BUTTON_PEER = "sun.awt.windows.WButtonPeer";
        protected static final String W_CANVAS_PEER = "sun.awt.windows.WCanvasPeer";
        protected static final String W_CHECKBOX_MENU_ITEM_PEER = "sun.awt.windows.WCheckboxMenuItemPeer";
        protected static final String W_CHECKBOX_PEER = "sun.awt.windows.WCheckboxPeer"; // no rerun init?
        protected static final String W_CHOICE_PEER = "sun.awt.windows.WChoicePeer";
        protected static final String W_CLIPBOARD = "sun.awt.windows.WClipboard";
        protected static final String W_COMPONENT_PEER = "sun.awt.windows.WComponentPeer";
        protected static final String W_CUSTOM_CURSOR = "sun.awt.windows.WCustomCursor";
        protected static final String W_DATA_TRANSFERER = "sun.awt.windows.WDataTransferer";
        protected static final String W_DEFAULT_FONT_CHARSET = "sun.awt.windows.WDefaultFontCharset";
        protected static final String W_DESKTOP_PEER = "sun.awt.windows.WDesktopPeer";
        protected static final String W_DESKTOP_PROPERTIES = "sun.awt.windows.WDesktopProperties";
        protected static final String W_DIALOG_PEER = "sun.awt.windows.WDialogPeer";
        protected static final String W_DRAG_SOURCE_CONTEXT_PEER = "sun.awt.windows.WDragSourceContextPeer";
        protected static final String W_DROP_TARGET_CONTEXT_PEER = "sun.awt.windows.WDropTargetContextPeer";
        protected static final String W_DROP_TARGET_CONTEXT_PEER_FILE_STREAM = "sun.awt.windows.WDropTargetContextPeerFileStream";
        protected static final String W_DROP_TARGET_CONTEXT_PEER_I_STREAM = "sun.awt.windows.WDropTargetContextPeerIStream";
        protected static final String W_EMBEDDED_FRAME = "sun.awt.windows.WEmbeddedFrame";
        protected static final String W_EMBEDDED_FRAME_PEER = "sun.awt.windows.WEmbeddedFramePeer";
        protected static final String W_FILE_DIALOG_PEER = "sun.awt.windows.WFileDialogPeer";
        protected static final String W_FONT_METRICS = "sun.awt.windows.WFontMetrics";
        protected static final String W_FONT_PEER = "sun.awt.windows.WFontPeer";
        protected static final String W_FRAME_PEER = "sun.awt.windows.WFramePeer";
        protected static final String W_GLOBAL_CURSOR_MANAGER = "sun.awt.windows.WGlobalCursorManager";
        protected static final String W_INPUT_METHOD = "sun.awt.windows.WInputMethod";
        protected static final String W_INPUT_METHOD_DESCRIPTOR = "sun.awt.windows.WInputMethodDescriptor";
        protected static final String W_KEYBOARD_FOCUS_MANAGER_PEER = "sun.awt.windows.WKeyboardFocusManagerPeer";
        protected static final String W_LABEL_PEER = "sun.awt.windows.WLabelPeer";
        protected static final String W_LIGHTWEIGHT_FRAME_PEER = "sun.awt.windows.WLightweightFramePeer";
        protected static final String W_LIST_PEER = "sun.awt.windows.WListPeer";
        protected static final String W_MENU_BAR_PEER = "sun.awt.windows.WMenuBarPeer";
        protected static final String W_MENU_ITEM_PEER = "sun.awt.windows.WMenuItemPeer";
        protected static final String W_MENU_PEER = "sun.awt.windows.WMenuPeer";
        protected static final String W_MOUSE_INFO_PEER = "sun.awt.windows.WMouseInfoPeer";
        protected static final String W_OBJECT_PEER = "sun.awt.windows.WObjectPeer";
        protected static final String W_PAGE_DIALOG = "sun.awt.windows.WPageDialog";
        protected static final String W_PAGE_DIALOG_PEER = "sun.awt.windows.WPageDialogPeer";
        protected static final String W_PANEL_PEER = "sun.awt.windows.WPanelPeer";
        protected static final String W_POPUP_MENU_PEER = "sun.awt.windows.WPopupMenuPeer";
        protected static final String W_PPRINT_DIALOG = "sun.awt.windows.WPrintDialog";
        protected static final String W_PPRINT_DIALOG_PEER = "sun.awt.windows.WPrintDialogPeer";
        protected static final String W_PRINTER_JOB = "sun.awt.windows.WPrinterJob";
        protected static final String W_ROBOT_PEER = "sun.awt.windows.WRobotPeer";
        protected static final String W_SCROLL_PANE_PEER = "sun.awt.windows.WScrollPanePeer";
        protected static final String W_SCROLLBAR_PEER = "sun.awt.windows.WScrollbarPeer";
        protected static final String W_TASKBAR_PEER = "sun.awt.windows.WTaskbarPeer";
        protected static final String W_TEXT_AREA_PEER = "sun.awt.windows.WTextAreaPeer";
        protected static final String W_TEXT_COMPONENT_PEER = "sun.awt.windows.WTextComponentPeer";
        protected static final String W_TEXT_FIELD_PEER = "sun.awt.windows.WTextFieldPeer";
        protected static final String W_TOOLKIT_THREAD_BLOCKED_HANDLER = "sun.awt.windows.WToolkitThreadBlockedHandler";
        protected static final String W_TOOLKIT = "sun.awt.windows.WToolkit";
        protected static final String W_TRAY_ICON_PEER = "sun.awt.windows.WTrayIconPeer";
        protected static final String W_WINDOW_PEER = "sun.awt.windows.WWindowPeer";
    }

    protected static final class SunJava2d {
        protected static final String DISPOSER = "sun.java2d.Disposer";
        protected static final String SURFACE_DATA = "sun.java2d.SurfaceData";
        protected static final String NULL_SURFACE_DATA = "sun.java2d.NullSurfaceData";

        protected static final String D3D_GRAPHICS_DEVICE = "sun.java2d.d3d.D3DGraphicsDevice";
        protected static final String D3D_RENDER_QUEUE = "sun.java2d.d3d.D3DRenderQueue";
        protected static final String D3D_RENDERER = "sun.java2d.d3d.D3DRenderer";
        protected static final String D3D_SURFACE_DATA = "sun.java2d.d3d.D3DSurfaceData";

        protected static final String BLIT = "sun.java2d.loops.Blit";
        protected static final String BLIT_BG = "sun.java2d.loops.BlitBg";
        protected static final String DRAW_LINE = "sun.java2d.loops.DrawLine";
        protected static final String DRAW_PARALLELOGRAM = "sun.java2d.loops.DrawParallelogram";
        protected static final String DRAW_PATH = "sun.java2d.loops.DrawPath";
        protected static final String DRAW_POLYGONS = "sun.java2d.loops.DrawPolygons";
        protected static final String DRAW_RECT = "sun.java2d.loops.DrawRect";
        protected static final String FILL_PARALLELOGRAM = "sun.java2d.loops.FillParallelogram";
        protected static final String FILL_PATH = "sun.java2d.loops.FillPath";
        protected static final String FILL_RECT = "sun.java2d.loops.FillRect";
        protected static final String FILL_SPANS = "sun.java2d.loops.FillSpans";
        protected static final String GRAPHICS_PRIMITIVE = "sun.java2d.loops.GraphicsPrimitive";
        protected static final String GRAPHICS_PRIMITIVE_MGR = "sun.java2d.loops.GraphicsPrimitiveMgr";
        protected static final String MASK_BLIT = "sun.java2d.loops.MaskBlit";
        protected static final String MASK_FILL = "sun.java2d.loops.MaskFill";
        protected static final String SCALED_BLIT = "sun.java2d.loops.ScaledBlit";
        protected static final String TRANSFORM_HELPER = "sun.java2d.loops.TransformHelper";
        protected static final String DRAW_GLYPH_LIST = "sun.java2d.loops.DrawGlyphList";
        protected static final String DRAW_GLYPH_LIST_AA = "sun.java2d.loops.DrawGlyphListAA";
        protected static final String DRAW_GLYPH_LIST_LCD = "sun.java2d.loops.DrawGlyphListLCD";

        protected static final String OGL_CONTEXT = "sun.java2d.opengl.OGLContext";
        protected static final String OGL_MASK_FILL = "sun.java2d.opengl.OGLMaskFill";
        protected static final String OGL_RENDER_QUEUE = "sun.java2d.opengl.OGLRenderQueue";
        protected static final String OGL_RENDERER = "sun.java2d.opengl.OGLRenderer";
        protected static final String OGL_SURFACE_DATA = "sun.java2d.opengl.OGLSurfaceData";
        protected static final String WGL_GRAPHICS_CONFIG = "sun.java2d.opengl.WGLGraphicsConfig";
        protected static final String WGL_SURFACE_DATA = "sun.java2d.opengl.WGLSurfaceData";

        protected static final String BUFFERED_RENDER_PIPE = "sun.java2d.pipe.BufferedRenderPipe";
        protected static final String REGION = "sun.java2d.pipe.Region";
        protected static final String SHAPE_SPAN_ITERATOR = "sun.java2d.pipe.ShapeSpanIterator";
        protected static final String SPAN_CLIP_RENDERER = "sun.java2d.pipe.SpanClipRenderer";

        protected static final String GDI_WINDOW_SURFACE_DATA = "sun.java2d.windows.GDIWindowSurfaceData";
        protected static final String WINDOWS_FLAGS = "sun.java2d.windows.WindowsFlags";
    }

    protected static final class SunPrint {
        protected static final String PRINT_SERVICE_LOOKUP_PROVIDER = "sun.print.PrintServiceLookupProvider";
        protected static final String RASTER_PRINTER_JOB = "sun.print.RasterPrinterJob";
        protected static final String WIN32_PRINT_JOB = "sun.print.Win32PrintJob";
        protected static final String WIN32_PRINT_SERVICE = "sun.print.Win32PrintService";
    }
}
