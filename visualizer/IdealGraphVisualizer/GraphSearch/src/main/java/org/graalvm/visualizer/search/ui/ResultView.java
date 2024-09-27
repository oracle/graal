/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.search.ui;

import org.graalvm.visualizer.util.StringUtils;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.MouseUtils;
import org.openide.awt.TabbedPaneFactory;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

import javax.swing.AbstractAction;
import javax.swing.BoxLayout;
import javax.swing.FocusManager;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;


/**
 * Panel which displays search results in explorer like manner.
 * This panel is a singleton.
 *
 * @author Petr Kuzel, Jiri Mzourek, Peter Zavadsky
 * @author Marian Petras
 * @author kaktus
 * @see <a href="doc-files/results-class-diagram.png">Class diagram</a>
 */

@NbBundle.Messages({
        "ACTION_NodeSearchResults=Node Search",
})
@TopComponent.Description(preferredID = ResultView.ID, persistenceType = TopComponent.PERSISTENCE_ALWAYS, iconBase = "org/netbeans/modules/search/res/find.gif")
@TopComponent.Registration(mode = "output", position = 1900, openAtStartup = false)
@ActionID(id = "org.graalvm.visualizer.search.ResultsOpenAction", category = "Window")
@TopComponent.OpenActionRegistration(displayName = "#ACTION_NodeSearchResults", preferredID = ResultView.ID)
@ActionReferences({
        @ActionReference(path = "Shortcuts", name = "DS-8"),
        @ActionReference(path = "Menu/Window")
})
public final class ResultView extends TopComponent {

    private static final boolean isMacLaf = "Aqua".equals(UIManager.getLookAndFeel().getID()); //NOI18N
    private static final Color macBackground = UIManager.getColor("NbExplorerView.background"); //NOI18N
    private static final String CARD_NAME_EMPTY = "empty";              //NOI18N
    private static final String CARD_NAME_TABS = "tabs";                //NOI18N
    private static final String CARD_NAME_SINGLE = "single";            //NOI18N

    /**
     * unique ID of <code>TopComponent</code> (singleton)
     */
    static final String ID = "node-search-results";                  //NOI18N

    private JPopupMenu pop;
    private PopupListener popL;
    private CloseListener closeL;

    private JPanel emptyPanel;
    private JPanel singlePanel;
    private JTabbedPane tabs;
    private PanelPropL propL = new PanelPropL();
    private WeakReference<JPanel> tabToReuse;
    private CurrentLookupProvider lookupProvider = new CurrentLookupProvider();

    /**
     * Returns a singleton of this class.
     *
     * @return singleton of this <code>TopComponent</code>
     */
    public static synchronized ResultView getInstance() {
        ResultView view;
        view = (ResultView) WindowManager.getDefault().findTopComponent(ID);
        if (view == null) {
            view = new ResultView(); // should not happen
        }
        return view;
    }

    private final CardLayout contentCards;

    public ResultView() {
        setLayout(contentCards = new CardLayout());

        setName("Search Results");                                      //NOI18N
        setDisplayName(Bundle.TITLE_SearchResults());

        pop = new JPopupMenu();
        pop.add(new Close());
        pop.add(new CloseAll());
        pop.add(new CloseAllButCurrent());
        popL = new PopupListener();
        closeL = new CloseListener();

        emptyPanel = new JPanel();
        singlePanel = new JPanel();
        singlePanel.setLayout(new BoxLayout(singlePanel, BoxLayout.PAGE_AXIS));
        emptyPanel.setOpaque(true);
        tabs = TabbedPaneFactory.createCloseButtonTabbedPane();
        tabs.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                updateLookup();
            }
        });
        tabs.setMinimumSize(new Dimension(0, 0));
        tabs.addMouseListener(popL);
        tabs.addPropertyChangeListener(closeL);
        add(emptyPanel, CARD_NAME_EMPTY);
        add(tabs, CARD_NAME_TABS);
        add(singlePanel, CARD_NAME_SINGLE);
        if (isMacLaf) {
            emptyPanel.setBackground(macBackground);
            tabs.setBackground(macBackground);
            tabs.setOpaque(true);
            setBackground(macBackground);
            setOpaque(true);
        } else {
            emptyPanel.setBackground(
                    UIManager.getColor("Tree.background"));         //NOI18N
        }
        contentCards.show(this, CARD_NAME_EMPTY);
        associateLookup(Lookups.proxy(lookupProvider));
    }

    @Deprecated
    final public static class ResolvableHelper implements java.io.Serializable {
        static final long serialVersionUID = 7398708142639457544L;

        public Object readResolve() {
            return null;
        }
    }

    /**
     * This method exists just to make the <code>close()</code> method
     * accessible via <code>Class.getDeclaredMethod(String, Class[])</code>.
     * It is used in <code>Manager</code>.
     */
    void closeResults() {
        close();
    }

    @NbBundle.Messages({
            "TITLE_SearchResults=Node Searches",
            "TOOLTIP_SearchResults=Node Search Results"
    })
    @Override
    protected void componentOpened() {
        assert EventQueue.isDispatchThread();

        SearchResultsEntry entry = getCurrentResultEntry();
        if (entry != null) {
            entry.viewOpened();
        }
        setToolTipText(Bundle.TOOLTIP_SearchResults());
    }

    @Override
    public void requestFocus() {
        JPanel panel = getCurrentResultViewPanel();
        if (panel != null) {
            panel.requestFocus();
        }
    }

    @Override
    public boolean requestFocusInWindow() {
        JPanel panel = getCurrentResultViewPanel();
        if (panel != null) {
            return panel.requestFocusInWindow();
        } else {
            return false;
        }
    }

    private JPanel getCurrentResultViewPanel() {
        JComponent comp = null;

        if (singlePanel.getComponents().length == 1) {
            comp = (JComponent) singlePanel.getComponents()[0];
        } else if (tabs.getTabCount() > 0) {
            comp = (JComponent) tabs.getSelectedComponent();
        }
        return comp instanceof JPanel ? (JPanel) comp : null;
    }

    private SearchResultsEntry getCurrentResultEntry() {
        JComponent comp = null;

        if (singlePanel.getComponents().length == 1) {
            comp = (JComponent) singlePanel.getComponents()[0];
        } else if (tabs.getTabCount() > 0) {
            comp = (JComponent) tabs.getSelectedComponent();
        }
        if (comp == null) {
            return null;
        }
        return panel2SearchEntry(comp);
    }


    private void updateTabTitle(JPanel panel) {
        if (getComponentCount() != 0) {
            if (tabs.getTabCount() > 0) {
                int index = tabs.indexOfComponent(panel);
                tabs.setTitleAt(index, panel.getName());
                tabs.setToolTipTextAt(index, panel.getToolTipText());
            }
        }
    }

    private SearchResultsEntry panel2SearchEntry(Component comp) {
        if (!(comp instanceof JComponent)) {
            return null;
        }
        Object o = ((JComponent) comp).getClientProperty("search.entry"); // NOI18N
        if (o instanceof SearchResultsEntry) {
            return (SearchResultsEntry) o;
        } else {
            return null;
        }
    }

    private class PanelPropL implements PropertyChangeListener {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (!(evt.getSource() instanceof JPanel) || evt.getPropertyName() == null) {
                return;
            }
            JPanel jp = (JPanel) evt.getSource();
            int index = tabs.indexOfComponent(jp);
            if (index == -1) {
                if (singlePanel.getComponentCount() == 0 || jp != singlePanel.getComponents()[0]) {
                    // dangling listener - remove:
                    jp.removePropertyChangeListener(this);
                }
                return;
            }
            switch (evt.getPropertyName()) {
                case "name":
                    tabs.setTitleAt(index, jp.getName());
                    break;
                case TOOL_TIP_TEXT_KEY:
                    tabs.setToolTipTextAt(index, jp.getToolTipText());
                    break;
            }
        }
    }

    private void removePanel(Component panel) {
        panel.removePropertyChangeListener(propL);
        if (tabs.getTabCount() > 0) {
            closeEntry(panel2SearchEntry(panel));
            tabs.remove(panel);
        } else if (singlePanel.getComponents().length == 1) {
            Component comp = singlePanel.getComponents()[0];
            closeEntry(panel2SearchEntry(comp));
            singlePanel.remove(comp);
        } else {
            close();
        }
        if (tabs.getTabCount() == 0) {
            contentCards.show(this, CARD_NAME_EMPTY);
        } else if (tabs.getTabCount() == 1) {
            Component c = tabs.getComponentAt(0);
            singlePanel.add(c);
            contentCards.show(this, CARD_NAME_SINGLE);
        }
        updateLookup();
        validate();
        updateTooltip();
        // this.repaint();
    }

    private void closeEntry(SearchResultsEntry en) {
        if (en != null) {
            en.cancelSearch();
            en.viewClosed();
        }
    }

    @Override
    protected void componentClosed() {
        assert EventQueue.isDispatchThread();
        closeAll(false); // #170545
    }

    /**
     *
     */
    void showAllDetailsFinished() {
        assert EventQueue.isDispatchThread();
//        mainPanel.updateShowAllDetailsBtn();
    }

    private void closeAll(boolean butCurrent) {
        if (tabs.getTabCount() > 0) {
            Component current = tabs.getSelectedComponent();
            Component[] c = tabs.getComponents();
            for (int i = 0; i < c.length; i++) {
                if (butCurrent && c[i] == current) {
                    continue;
                }
                if (panel2SearchEntry(c[i]) != null) {
                    removePanel(c[i]);
                }
            }
        } else if (singlePanel.getComponents().length > 0) {
            Component comp = singlePanel.getComponents()[0];
            if (panel2SearchEntry(comp) != null) { // #172546
                removePanel(comp);
            }
        }
    }

    private class CloseListener implements PropertyChangeListener {
        @Override
        public void propertyChange(java.beans.PropertyChangeEvent evt) {
            if (TabbedPaneFactory.PROP_CLOSE.equals(evt.getPropertyName())) {
                removePanel((JPanel) evt.getNewValue());
            }
        }
    }

    private class PopupListener extends MouseUtils.PopupMouseAdapter {
        @Override
        protected void showPopup(MouseEvent e) {
            pop.show(ResultView.this, e.getX(), e.getY());
        }
    }

    @NbBundle.Messages({
            "LBL_CloseWindow=Close",
            "LBL_CloseAll=Close All",
            "LBL_CloseAllButCurrent=Close Other Tabs",
    })
    private class Close extends AbstractAction {
        public Close() {
            super(NbBundle.getMessage(ResultView.class, "LBL_CloseWindow"));  //NOI18N
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            removePanel(null);
        }
    }

    private final class CloseAll extends AbstractAction {
        public CloseAll() {
            super(NbBundle.getMessage(ResultView.class, "LBL_CloseAll"));  //NOI18N
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            closeAll(false);
        }
    }

    private class CloseAllButCurrent extends AbstractAction {
        public CloseAllButCurrent() {
            super(NbBundle.getMessage(ResultView.class, "LBL_CloseAllButCurrent"));  //NOI18N
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            closeAll(true);
        }
    }

    /**
     * Add a tab for a new displayer.
     */
    JPanel addTab(SearchResultsEntry entry) {
        int tabIndex = tryReuse();
        JPanel panel = entry.getResultsPanel();
        if (panel == null) {
            return null;
        }
        panel.putClientProperty("search.entry", entry);
        if (singlePanel.getComponents().length == 0
                && tabs.getTabCount() == 0) {
            singlePanel.add(panel);
            contentCards.show(this, CARD_NAME_SINGLE);
            updateLookup();
        } else if (singlePanel.getComponents().length == 1) {
            JPanel comp =
                    (JPanel) singlePanel.getComponents()[0];
            tabs.insertTab(comp.getName(), null, comp,
                    comp.getToolTipText(), 0);
            tabs.setToolTipTextAt(0, comp.getToolTipText());
            int tabToInsert = tabIndex > -1 ? tabIndex : 1;
            tabs.insertTab(comp.getName(), null, panel, panel.getToolTipText(),
                    tabToInsert);
            tabs.setToolTipTextAt(tabToInsert, panel.getToolTipText());
            tabs.setSelectedIndex(tabIndex > -1 ? tabIndex : 1);
            contentCards.show(this, CARD_NAME_TABS);
            comp.addPropertyChangeListener(propL);
            panel.addPropertyChangeListener(propL);
        } else {
            tabs.insertTab(panel.getName(), null, panel,
                    panel.getToolTipText(),
                    tabIndex > -1 ? tabIndex : tabs.getTabCount());
            tabs.setToolTipTextAt(
                    tabIndex > -1 ? tabIndex : tabs.getTabCount() - 1,
                    panel.getToolTipText());
            tabs.setSelectedComponent(panel);
            tabs.validate();
            panel.addPropertyChangeListener(propL);
        }
        entry.viewOpened();
        validate();
        requestActive();
        updateTooltip();
        return panel;
    }

    /**
     * Return tab index to reuse, or -1 to disable reusing.
     */
    private int tryReuse() {
        JPanel toReuse = getTabToReuse();
        if (toReuse == null) {
            return -1;
        } else if (singlePanel.getComponents().length == 1
                && singlePanel.getComponent(0) == toReuse) {
            removePanel(toReuse);
            clearReusableTab();
            return 0;
        } else if (tabs.getTabCount() > 0) {
            int index = tabs.indexOfComponent(toReuse);
            if (index >= 0) {
                removePanel(toReuse);
                clearReusableTab();
                return index;
            }
        }
        return tabs.getTabCount();
    }

    public boolean isFocused() {
        JPanel rvp = getCurrentResultViewPanel();
        if (rvp != null) {
            Component owner = FocusManager.getCurrentManager().getFocusOwner();
            return owner != null && SwingUtilities.isDescendingFrom(owner, rvp);
        } else {
            return false;
        }
    }

    private synchronized void setTabToReuse(JPanel resultViewPanel) {
        tabToReuse = resultViewPanel == null
                ? null
                : new WeakReference<>(resultViewPanel);
    }

    private synchronized JPanel getTabToReuse() {
        return tabToReuse == null || tabToReuse.get() == null
                ? null
                : tabToReuse.get();
    }

    /**
     * Mark the currenly selected tab as reusable.
     */
    public synchronized void markCurrentTabAsReusable() {
        setTabToReuse(getCurrentResultViewPanel());
    }

    /**
     * Set that no tab should be reused. Clears effect of the last invocation of
     * method {@link #markCurrentTabAsReusable() }
     */
    public synchronized void clearReusableTab() {
        setTabToReuse(null);
    }

    private void updateLookup() {
        SearchResultsEntry en = getCurrentResultEntry();
        lookupProvider.setLookup(en == null ? Lookup.EMPTY : en.getLookup());
        getLookup().lookup(Object.class); //refresh lookup
    }

    private void updateTooltip() {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><b>");                                         //NOI18N
        sb.append(Bundle.TOOLTIP_SearchResults());
        sb.append("</b>");                                              //NOI18N
        if (singlePanel.getComponents().length == 1) {
            appendTabToToolTip(singlePanel.getComponent(0), sb);
        } else if (tabs.getComponents().length > 0) {
            Component[] comps = tabs.getComponents();
            for (int i = 0; i < comps.length; i++) {
                appendTabToToolTip(comps[i], sb);
            }
        }
        sb.append("</html>");                                           //NOI18N
        setToolTipText(sb.toString());
    }

    private void appendTabToToolTip(Component c, StringBuilder sb) {
        if (c instanceof JPanel) {
            JPanel rvp = (JPanel) c;
            if (rvp.getToolTipText() != null) {
                sb.append("<br>&nbsp;&nbsp;");                          //NOI18N
                sb.append(StringUtils.escapeHTML(rvp.getToolTipText()));
                sb.append("&nbsp;");                                    //NOI18N
            }
        }
    }

    private static class CurrentLookupProvider implements Lookup.Provider {
        private Lookup currentLookup = Lookup.EMPTY;

        public void setLookup(Lookup lookup) {
            this.currentLookup = lookup;
        }

        @Override
        public Lookup getLookup() {
            return currentLookup;
        }
    }
}
