/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package org.graalvm.visualizer.util.swing;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.awt.image.PixelGrabber;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;


/**
 * Various UI utilities used in the JFluid UI
 *
 * @author Ian Formanek
 * @author Jiri Sedlacek
 */
public final class UIUtils {
    //~ Static fields/initializers -----------------------------------------------------------------------------------------------

    // Used to mark explicit expand/collapse on JTree which shouldn't be handled by automatic expander
    public static final String PROP_AUTO_EXPANDING = "auto_expanding"; // NOI18N
    public static final String PROP_EXPANSION_TRANSACTION = "expansion_transaction"; // NOI18N
    public static Dimension DIMENSION_SMALLEST = new Dimension(0, 0);

    private static final Logger LOGGER = Logger.getLogger(UIUtils.class.getName());
    public static final float ALTERNATE_ROW_DARKER_FACTOR = 0.96f;
    private static final int MAX_TREE_AUTOEXPAND_LINES = 50;
    private static boolean toolTipValuesInitialized = false;
    private static Color unfocusedSelBg;
    private static Color unfocusedSelFg;
    private static Color disabledLineColor;

    //~ Methods ------------------------------------------------------------------------------------------------------------------

    public static JPanel createFillerPanel() {
        JPanel fillerPanel = new JPanel(null) {
            public Dimension getPreferredSize() {
                return DIMENSION_SMALLEST;
            }
        };
        fillerPanel.setOpaque(false);
        return fillerPanel;
    }

    public static JSeparator createHorizontalSeparator() {
        JSeparator horizontalSeparator = new JSeparator() {
            public Dimension getMinimumSize() {
                return getPreferredSize();
            }
        };

        return horizontalSeparator;
    }

    public static JSeparator createHorizontalLine(Color background) {
        final boolean customPaint = isNimbus() || isAquaLookAndFeel();
        JSeparator separator = new JSeparator() {
            public Dimension getMaximumSize() {
                return new Dimension(super.getMaximumSize().width, 1);
            }

            public Dimension getPreferredSize() {
                return new Dimension(super.getPreferredSize().width, 1);
            }

            public void paint(Graphics g) {
                if (customPaint) {
                    g.setColor(getDisabledLineColor());
                    g.fillRect(0, 0, getWidth(), getHeight());
                } else {
                    super.paint(g);
                }
            }
        };
        separator.setBackground(background);
        return separator;
    }

    /**
     * Determines if current L&F is AquaLookAndFeel
     */
    public static boolean isAquaLookAndFeel() {
        // is current L&F some kind of AquaLookAndFeel?
        return UIManager.getLookAndFeel().getID().equals("Aqua"); //NOI18N
    }

    private static Map<Integer, Color> DARKER_CACHE;

    public static Color getDarker(Color c) {
        if (DARKER_CACHE == null) DARKER_CACHE = new HashMap();

        int rgb = c.getRGB();
        Color d = DARKER_CACHE.get(rgb);

        if (d == null) {
            if (c.equals(Color.WHITE)) {
                d = new Color(244, 244, 244);
            } else {
                d = getSafeColor((int) (c.getRed() * ALTERNATE_ROW_DARKER_FACTOR),
                        (int) (c.getGreen() * ALTERNATE_ROW_DARKER_FACTOR),
                        (int) (c.getBlue() * ALTERNATE_ROW_DARKER_FACTOR));
            }
            DARKER_CACHE.put(rgb, d);
        }

        return d;
    }

    public static Color getDarkerLine(Color c, float alternateRowDarkerFactor) {
        return getSafeColor((int) (c.getRed() * alternateRowDarkerFactor), (int) (c.getGreen() * alternateRowDarkerFactor),
                (int) (c.getBlue() * alternateRowDarkerFactor));
    }

    public static Color getDisabledForeground(Color c) {
        Color b = c.brighter();
        if (c.getRGB() == b.getRGB()) return b; // Selection foreground
        else if (isNimbusLookAndFeel()) return UIManager.getColor("nimbusDisabledText").darker(); //NOI18N
        else if (isMetalLookAndFeel()) return UIManager.getColor("Label.disabledForeground"); //NOI18N
        else if (Color.BLACK.getRGB() == c.getRGB()) return Color.GRAY;
        else return b;
    }

    public static int getDefaultRowHeight() {
        return new JLabel("X").getPreferredSize().height + 2; //NOI18N
    }

    /**
     * Determines if current L&F is GTKLookAndFeel
     */
    public static boolean isGTKLookAndFeel() {
        // is current L&F some kind of GTKLookAndFeel?
        return UIManager.getLookAndFeel().getID().equals("GTK"); //NOI18N
    }

    /**
     * Determines if current L&F is Nimbus
     */
    public static boolean isNimbusLookAndFeel() {
        // is current L&F Nimbus?
        return UIManager.getLookAndFeel().getID().equals("Nimbus"); //NOI18N
    }

    /**
     * Determines if current L&F is GTK using Nimbus theme
     */
    public static boolean isNimbusGTKTheme() {
        // is current L&F GTK using Nimbus theme?
        return isGTKLookAndFeel() && "nimbus".equals(Toolkit.getDefaultToolkit().getDesktopProperty("gnome.Net/ThemeName")); //NOI18N
    }

    /**
     * Determines if current L&F is Nimbus or GTK with Nimbus theme
     */
    public static boolean isNimbus() {
        // is current L&F Nimbus or GTK with Nimbus theme?
        return isNimbusLookAndFeel() || isNimbusGTKTheme();
    }

    /**
     * Determines if current L&F is MetalLookAndFeel
     */
    public static boolean isMetalLookAndFeel() {
        // is current L&F some kind of MetalLookAndFeel?
        return UIManager.getLookAndFeel().getID().equals("Metal"); //NOI18N
    }

    // Returns next enabled tab of JTabbedPane
    public static int getNextSubTabIndex(JTabbedPane tabs, int tabIndex) {
        int nextTabIndex = tabIndex;

        for (int i = 0; i < tabs.getComponentCount(); i++) {
            nextTabIndex++;

            if (nextTabIndex == tabs.getComponentCount()) {
                nextTabIndex = 0;
            }

            if (tabs.isEnabledAt(nextTabIndex)) {
                break;
            }
        }

        return nextTabIndex;
    }

    public static Window getParentWindow(Component comp) {
        while ((comp != null) && !(comp instanceof Window)) {
            comp = comp.getParent();
        }

        return (Window) comp;
    }

    // Returns previous enabled tab of JTabbedPane
    public static int getPreviousSubTabIndex(JTabbedPane tabs, int tabIndex) {
        int previousTabIndex = tabIndex;

        for (int i = 0; i < tabs.getComponentCount(); i++) {
            previousTabIndex--;

            if (previousTabIndex < 0) {
                previousTabIndex = tabs.getComponentCount() - 1;
            }

            if (tabs.isEnabledAt(previousTabIndex)) {
                break;
            }
        }

        return previousTabIndex;
    }

    public static Color getSafeColor(int red, int green, int blue) {
        red = Math.max(red, 0);
        red = Math.min(red, 255);
        green = Math.max(green, 0);
        green = Math.min(green, 255);
        blue = Math.max(blue, 0);
        blue = Math.min(blue, 255);

        return new Color(red, green, blue);
    }

    // Copied from org.openide.awt.HtmlLabelUI

    /**
     * Get the system-wide unfocused selection background color
     */
    public static Color getUnfocusedSelectionBackground() {
        if (unfocusedSelBg == null) {
            //allow theme/ui custom definition
            unfocusedSelBg = UIManager.getColor("nb.explorer.unfocusedSelBg"); //NOI18N

            if (unfocusedSelBg == null) {
                //try to get standard shadow color
                unfocusedSelBg = UIManager.getColor("controlShadow"); //NOI18N

                if (unfocusedSelBg == null) {
                    //Okay, the look and feel doesn't suport it, punt
                    unfocusedSelBg = Color.lightGray;
                }

                //Lighten it a bit because disabled text will use controlShadow/
                //gray
                if (!Color.WHITE.equals(unfocusedSelBg.brighter())) {
                    unfocusedSelBg = unfocusedSelBg.brighter();
                }
            }
        }

        return unfocusedSelBg;
    }

    // Copied from org.openide.awt.HtmlLabelUI

    /**
     * Get the system-wide unfocused selection foreground color
     */
    public static Color getUnfocusedSelectionForeground() {
        if (unfocusedSelFg == null) {
            //allow theme/ui custom definition
            unfocusedSelFg = UIManager.getColor("nb.explorer.unfocusedSelFg"); //NOI18N

            if (unfocusedSelFg == null) {
                //try to get standard shadow color
                unfocusedSelFg = UIManager.getColor("textText"); //NOI18N

                if (unfocusedSelFg == null) {
                    //Okay, the look and feel doesn't suport it, punt
                    unfocusedSelFg = Color.BLACK;
                }
            }
        }

        return unfocusedSelFg;
    }


    private static Color profilerResultsBackground;

    private static Color getGTKProfilerResultsBackground() {
        int[] pixels = new int[1];
        pixels[0] = -1;

        // Prepare textarea to grab the color from
        JTextArea textArea = new JTextArea();
        textArea.setSize(new Dimension(10, 10));
        textArea.doLayout();

        // Print the textarea to an image
        Image image = new BufferedImage(textArea.getSize().width, textArea.getSize().height, BufferedImage.TYPE_INT_RGB);
        textArea.printAll(image.getGraphics());

        // Grab appropriate pixels to get the color
        PixelGrabber pixelGrabber = new PixelGrabber(image, 5, 5, 1, 1, pixels, 0, 1);
        try {
            pixelGrabber.grabPixels();
            if (pixels[0] == -1) return Color.WHITE; // System background not customized
        } catch (InterruptedException e) {
            return getNonGTKProfilerResultsBackground();
        }

        return pixels[0] != -1 ? new Color(pixels[0]) : getNonGTKProfilerResultsBackground();
    }

    private static Color getNonGTKProfilerResultsBackground() {
        return UIManager.getColor("Table.background"); // NOI18N
    }

    public static Color getProfilerResultsBackground() {
        if (profilerResultsBackground == null) {
            if (isGTKLookAndFeel() || isNimbusLookAndFeel()) {
                profilerResultsBackground = getGTKProfilerResultsBackground();
            } else {
                profilerResultsBackground = getNonGTKProfilerResultsBackground();
            }
            if (profilerResultsBackground == null) profilerResultsBackground = Color.WHITE;
        }

        return profilerResultsBackground;
    }

    private static Boolean darkResultsBackground;

    public static boolean isDarkResultsBackground() {
        if (darkResultsBackground == null) {
            Color c = getProfilerResultsBackground();
            int b = (int) (0.3 * c.getRed() + 0.59 * c.getGreen() + 0.11 * c.getBlue());
            darkResultsBackground = b < 85;
        }

        return darkResultsBackground;
    }

    /**
     * Determines if current L&F is Windows Classic LookAndFeel
     */
    public static boolean isWindowsClassicLookAndFeel() {
        if ("Windows Classic".equals(UIManager.getLookAndFeel().getName())) return true; //NOI18N

        if (!isWindowsLookAndFeel()) {
            return false;
        }

        return (!isWindowsXPLookAndFeel());
    }

    /**
     * Determines if current L&F is WindowsLookAndFeel
     */
    public static boolean isWindowsLookAndFeel() {
        // is current L&F some kind of WindowsLookAndFeel?
        return UIManager.getLookAndFeel().getID().equals("Windows"); //NOI18N
    }

    /**
     * Determines if current L&F is Windows XP LookAndFeel
     */
    public static boolean isWindowsXPLookAndFeel() {
        if (!isWindowsLookAndFeel()) {
            return false;
        }

        // is XP theme active in the underlying OS?
        boolean xpThemeActiveOS = Boolean.TRUE.equals(Toolkit.getDefaultToolkit().getDesktopProperty("win.xpstyle.themeActive")); //NOI18N
        // is XP theme disabled by the application?

        boolean xpThemeDisabled = (System.getProperty("swing.noxp") != null); // NOI18N

        return ((xpThemeActiveOS) && (!xpThemeDisabled));
    }

    public static boolean isWindowsModernLookAndFeel() {
        if (!isWindowsXPLookAndFeel()) return false;
        String osName = System.getProperty("os.name"); // NOI18N
        return osName != null && (osName.contains("Windows 8") || osName.contains("Windows 10")); // NOI18N
    }

    public static boolean isOracleLookAndFeel() {
        // is current L&F some kind of WindowsLookAndFeel?
        return UIManager.getLookAndFeel().getID().contains("Oracle"); //NOI18N
    }

    /**
     * Checks give TreePath for the last node, and if it ends with a node with just one child,
     * it keeps expanding further.
     * Current implementation expands through the first child that is not leaf. To more correctly
     * fulfil expected semantics in case maxChildToExpand is > 1, it should expand all paths through
     * all children.
     *
     * @param tree
     * @param path
     * @param maxChildToExpand
     */
    public static void autoExpand(JTree tree, TreePath path, int maxLines, int maxChildToExpand, boolean dontExpandToLeafs) {
        TreeModel model = tree.getModel();
        Object node = path.getLastPathComponent();
        TreePath newPath = path;

        int currentLines = 0;

        while (currentLines++ < maxLines &&
                !model.isLeaf(node) &&
                (model.getChildCount(node) > 0) &&
                (model.getChildCount(node) <= maxChildToExpand)) {
            for (int i = 0; i < model.getChildCount(node); i++) {
                node = tree.getModel().getChild(node, i);

                if (!model.isLeaf(node)) {
                    if (dontExpandToLeafs && hasOnlyLeafs(tree, node)) {
                        break;
                    }

                    newPath = newPath.pathByAddingChild(node); // if the leaf is added the path will not expand

                    break; // from for
                }
            }
        }

        tree.expandPath(newPath);
    }

    /**
     * Checks if the root of the provided tree has only one child, and if so,
     * it autoexpands it.
     *
     * @param tree The tree whose root should be autoexpanded
     */
    public static void autoExpandRoot(JTree tree) {
        autoExpandRoot(tree, 1);
    }

    /**
     * Checks if the root of the provided tree has only one child, and if so,
     * it autoexpands it.
     *
     * @param tree The tree whose root should be autoexpanded
     */
    public static void autoExpandRoot(JTree tree, int maxChildToExpand) {
        Object root = tree.getModel().getRoot();

        if (root == null) {
            return;
        }

        TreePath rootPath = new TreePath(root);
        autoExpand(tree, rootPath, MAX_TREE_AUTOEXPAND_LINES, maxChildToExpand, false);
    }

    public static long[] copyArray(long[] array) {
        if (array == null) {
            return new long[0];
        }

        if (array.length == 0) {
            return new long[0];
        } else {
            long[] ret = new long[array.length];
            System.arraycopy(array, 0, ret, 0, array.length);

            return ret;
        }
    }

    public static int[] copyArray(int[] array) {
        if (array == null) {
            return new int[0];
        }

        if (array.length == 0) {
            return new int[0];
        } else {
            int[] ret = new int[array.length];
            System.arraycopy(array, 0, ret, 0, array.length);

            return ret;
        }
    }

    public static float[] copyArray(float[] array) {
        if (array == null) {
            return new float[0];
        }

        if (array.length == 0) {
            return new float[0];
        } else {
            float[] ret = new float[array.length];
            System.arraycopy(array, 0, ret, 0, array.length);

            return ret;
        }
    }

    public static void ensureMinimumSize(Component comp) {
        comp = getParentWindow(comp);

        if (comp != null) {
            final Component top = comp;
            top.addComponentListener(new ComponentAdapter() {
                public void componentResized(ComponentEvent e) {
                    Dimension d = top.getSize();
                    Dimension min = top.getMinimumSize();

                    if ((d.width < min.width) || (d.height < min.height)) {
                        top.setSize(Math.max(d.width, min.width), Math.max(d.height, min.height));
                    }
                }
            });
        }
    }

    // Classic Windows LaF doesn't draw dotted focus rectangle inside JButton if parent is JToolBar,
    // XP Windows LaF doesn't draw dotted focus rectangle inside JButton at all
    // This method installs customized Windows LaF that draws dotted focus rectangle inside JButton always

    // On JDK 1.5 the XP Windows LaF enforces special border to all buttons, overriding any custom border
    // set by setBorder(). Class responsible for this is WindowsButtonListener. See Issue 71546.
    // Also fixes buttons size in JToolbar.

    /**
     * Ensures that focus will be really painted if button is focused
     * and fixes using custom border for JDK 1.5 & XP LaF
     */
    public static void fixButtonUI(AbstractButton button) {
        // Doesn't seem to be necessary any more, conflicts with Jigsaw
    }

    public static boolean hasOnlyLeafs(JTree tree, Object node) {
        TreeModel model = tree.getModel();

        for (int i = 0; i < model.getChildCount(node); i++) {
            if (!model.isLeaf(model.getChild(node, i))) {
                return false;
            }
        }

        return true;
    }

    /**
     * By calling this method, the provided tree will become auto-expandable, i.e.
     * When a node is expanded, if it has only one child, that child gets expanded, and so on.
     * This is very useful for trees that have a deep node hierarchy with typical paths from
     * root to leaves containing only one node along the whole path.
     *
     * @param tree The tree to make auto-expandable
     */
    public static void makeTreeAutoExpandable(JTree tree) {
        makeTreeAutoExpandable(tree, 1, false);
    }

    public static void makeTreeAutoExpandable(JTree tree, final boolean dontExpandToLeafs) {
        makeTreeAutoExpandable(tree, 1, dontExpandToLeafs);
    }

    /**
     * By calling this method, the provided tree will become auto-expandable, i.e.
     * When a node is expanded, if it has only one child, that child gets expanded, and so on.
     * This is very useful for trees that have a deep node hierarchy with typical paths from
     * root to leaves containing only one node along the whole path.
     *
     * @param tree The tree to make auto-expandable
     */
    public static void makeTreeAutoExpandable(JTree tree, final int maxChildToExpand) {
        makeTreeAutoExpandable(tree, maxChildToExpand, false);
    }

    public static void makeTreeAutoExpandable(final JTree tree, final int maxChildToExpand, final boolean dontExpandToLeafs) {
        tree.addTreeExpansionListener(new TreeExpansionListener() {
            boolean internalChange = false;

            public void treeCollapsed(TreeExpansionEvent event) {
            }

            public void treeExpanded(TreeExpansionEvent event) {
                if (internalChange || Boolean.TRUE.equals(tree.getClientProperty(PROP_EXPANSION_TRANSACTION))) { // NOI18N
                    return;
                }

                // Auto expand more if the just expanded child has only one child
                TreePath path = event.getPath();
                JTree tree = (JTree) event.getSource();
                internalChange = true;
                tree.putClientProperty(PROP_AUTO_EXPANDING, Boolean.TRUE);
                try {
                    autoExpand(tree, path, MAX_TREE_AUTOEXPAND_LINES, maxChildToExpand, dontExpandToLeafs);
                } finally {
                    tree.putClientProperty(PROP_AUTO_EXPANDING, null);
                    internalChange = false;
                }
            }
        });
    }

    public static void runInEventDispatchThread(final Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    public static void runInEventDispatchThreadAndWait(final Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(r);
            } catch (InvocationTargetException e) {
                LOGGER.severe(e.getMessage());
            } catch (InterruptedException e) {
                LOGGER.severe(e.getMessage());
            }
        }
    }

    public static void addBorder(JComponent c, Border b) {
        Border cb = c.getBorder();
        Border nb = cb == null ? b : new CompoundBorder(cb, b);
        c.setBorder(nb);
    }

    public static Color getDisabledLineColor() {
        if (disabledLineColor == null) {
            disabledLineColor = UIManager.getColor(isAquaLookAndFeel() ? "controlShadow" : // NOI18N
                    "Label.disabledForeground"); // NOI18N
            if (disabledLineColor == null)
                disabledLineColor = UIManager.getColor("Label.disabledText"); // NOI18N
            if (disabledLineColor == null || disabledLineColor.equals(getProfilerResultsBackground()))
                disabledLineColor = Color.GRAY;
        }
        return disabledLineColor;
    }
}
