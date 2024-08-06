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

package org.graalvm.visualizer.source.impl.ui;

import java.awt.BorderLayout;
import org.graalvm.visualizer.data.InputGraph;
import org.graalvm.visualizer.data.InputNode;
import static org.graalvm.visualizer.data.KnownPropertyNames.PROPNAME_NAME;
import org.graalvm.visualizer.graph.Figure;
import org.graalvm.visualizer.source.GraphSource;
import org.graalvm.visualizer.source.Language;
import org.graalvm.visualizer.source.Location;
import org.graalvm.visualizer.source.spi.LocatorUI;
import org.graalvm.visualizer.source.NodeLocationContext;
import org.graalvm.visualizer.source.NodeLocationEvent;
import org.graalvm.visualizer.source.NodeLocationListener;
import org.graalvm.visualizer.source.NodeStack;
import org.graalvm.visualizer.source.impl.actions.GoStackUpDownAction;
import org.graalvm.visualizer.source.impl.actions.GotoNodeAction;
import org.graalvm.visualizer.source.impl.actions.GotoSourceAction;
import org.graalvm.visualizer.util.swing.DropdownButton;
import org.graalvm.visualizer.util.PropertiesSheet;
import org.graalvm.visualizer.view.api.DiagramViewer;
import org.graalvm.visualizer.view.api.DiagramViewerLocator;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.Actions;
import org.openide.awt.NotificationDisplayer;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.explorer.view.ChoiceView;
import org.openide.explorer.view.NodeListModel;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.ContextAwareAction;
import org.openide.util.Exceptions;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.Presenter;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.BeanInfo;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyVetoException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.*;
import javax.swing.*;
import javax.swing.border.Border;

/**
 * Top component which displays something.
 */
@ConvertAsProperties(
                dtd = "-//org.graalvm.visualizer.source.impl//StackView//EN",
                autostore = false
)
@TopComponent.Description(
                preferredID = "StackView",
                iconBase = "org/graalvm/visualizer/source/resources/callview.png",
                persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "rightNavigatingMode", openAtStartup = true)
@ActionID(category = "Window", id = "org.graalvm.visualizer.source.impl.StackViewTopComponent")
@ActionReference(path = "Menu/Window", position = 91)
@TopComponent.OpenActionRegistration(
                displayName = "#CTL_ProcessingViewAction",
                preferredID = "StackView"
)
@Messages({
    "CTL_ProcessingViewAction=Open Stack View",
    "CTL_ProcessingViewTopComponent=Stack View",
    "HINT_ProcessingViewTopComponent=This is a Stack View showing call stack of selected Graal nodes"
})
public final class StackViewTopComponent extends TopComponent
                implements ExplorerManager.Provider, NodeLocationListener, PropertyChangeListener {
    private final NodeLocationContext locContext;
    private ExplorerManager manager = new ExplorerManager();

    private final ChoiceView choiceView;
    private List<InputNode> orderedContextNodes = new ArrayList<>();
    private final LangSelector lSelector;

    private final InstanceContent exportContent = new InstanceContent();

    private boolean ignorePropChange;

    public StackViewTopComponent() {
        initComponents();
        setName(Bundle.CTL_ProcessingViewTopComponent());
        setToolTipText(Bundle.HINT_ProcessingViewTopComponent());

        Lookup lkp = ExplorerUtils.createLookup(manager, getActionMap());
        Border b = (Border) UIManager.get("Nb.Editor.Toolbar.border"); // NOI18N
        toolBar.setBorder(b);
        locContext = Lookup.getDefault().lookup(NodeLocationContext.class);

        choiceView = new ChoiceView() {
            // replace UI to properly size the popup
            @Override
            public void updateUI() {
                setUI(new CompactComboUI());
                ListCellRenderer renderer = getRenderer();
                if (renderer instanceof Component) {
                    SwingUtilities.updateComponentTreeUI((Component) renderer);
                }
            }
        };
        choiceView.setRenderer(new CompactComboRenderer());
        ((NodeListModel) choiceView.getModel()).setDepth(50);
        selectionPanel.add(choiceView, BorderLayout.CENTER);

        nodeSelector.setRenderer(new InputNodeRenderer());
        nodeSelector.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                InputNode in = (InputNode) nodeSelector.getSelectedItem();
                if (in == null) {
                    return;
                }
                updateActivatedNodes();
                NodeStack ns = findFrameForNode(in);
                if (ns != null) {
                    updateComboContents(ns);
                }
            }
        });
        lSelector = (LangSelector) this.langDrop;
        locContext.addNodeLocationListener(this);
        manager.addPropertyChangeListener(this);
        displayNoContext();

        addAction(GotoSourceAction.CATEGORY, GotoSourceAction.ACTION_ID, lkp);
        addAction(GoStackUpDownAction.CATEGORY, GoStackUpDownAction.ACTION_GO_UP, lkp);
        addAction(GoStackUpDownAction.CATEGORY, GoStackUpDownAction.ACTION_GO_DOWN, lkp);
        addAction(GotoNodeAction.CATEGORY, GotoNodeAction.ACTION_ID, lkp);
        /*
        toolBar.add(new JSeparator(JSeparator.VERTICAL));
        addAction(PreferGuestLanguageAction.CATEGORY, PreferGuestLanguageAction.ACTION_ID, lkp);
         */

        associateLookup(new AbstractLookup(exportContent));

        lSelector.setPopupToolTipText(Bundle.TOOLTIP_LanguageDropDown());
    }

    private void addAction(String category, String id, Lookup lkp) {
        Action a = Actions.forID(category, id);
        if (a == null) {
            return;
        }
        if (a instanceof ContextAwareAction) {
            a = ((ContextAwareAction) a).createContextAwareInstance(lkp);
        }
        if (a instanceof Presenter.Toolbar) {
            toolBar.add(((Presenter.Toolbar) a).getToolbarPresenter());
        } else {
            toolBar.add(a);
        }
    }

    private static String getHTMLColorString(Color color) {
        String red = Integer.toHexString(color.getRed());
        String green = Integer.toHexString(color.getGreen());
        String blue = Integer.toHexString(color.getBlue());

        return "#" // NOI18N
                        + (red.length() == 1 ? "0" + red : red) // NOI18N
                        + (green.length() == 1 ? "0" + green : green) // NOI18N
                        + (blue.length() == 1 ? "0" + blue : blue); // NOI18N
    }

    @NbBundle.Messages({
        "# {0} - node ID",
        "# {1} - node name",
        "FMT_InputNodeLabel=<html><b>{0}</b>: {1}</html>",
        "LAB_NodeNoName=(unnamed)"
    })
    class InputNodeRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel lab = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (!(value instanceof InputNode)) {
                return lab;
            }

            InputNode nn = (InputNode) value;
            InputGraph g = locContext.getGraph();
            if (g == null) {
                return lab;
            }
            DiagramViewerLocator loc = Lookup.getDefault().lookup(DiagramViewerLocator.class);
            List<DiagramViewer> vwrs = loc.find(g);
            if (!vwrs.isEmpty()) {
                DiagramViewer viewer = vwrs.iterator().next();
                Collection<Figure> figs = viewer.figuresForNodes(Collections.singleton(nn));
                if (!figs.isEmpty()) {
                    Figure f = figs.iterator().next();
                    String s = f.getProperties().getString(PROPNAME_NAME, Bundle.LAB_NodeNoName());
                    s = s.replace("<", "&lt;"); // escape html
                    lab.setText(Bundle.FMT_InputNodeLabel(nn.getId(), s));
                }
            }
            return lab;
        }

    }

    class LangSelector extends DropdownButton {
        LangSelector() {
            super("", null, false);
        }

        @Override
        protected void performAction(ActionEvent e) {
            selectLanguage();
        }

        @Override
        protected void populatePopup(JPopupMenu menu) {
            for (String l : languages) {
                Language lng = Language.getRegistry().findLanguageByMime(l);
                if (lng != null) {
                    Icon icon = null;
                    Node ln = lng.getLookup().lookup(Node.class);
                    if (ln != null) {
                        icon = ImageUtilities.image2Icon(ln.getIcon(BeanInfo.ICON_COLOR_16x16));
                    }
                    JMenuItem mi = new JMenuItem(lng.getDisplayName(), icon);
                    mi.addActionListener((e) -> {

                        NodeStack.Frame frame = findCurrentFrame();
                        NodeStack.Frame nframe = frame == null ? null : frame.findPeerFrame(l);
                        if (nframe != null) {
                            locContext.setSelectedLocation(nframe);
                        }
                        locContext.setSelectedLanguage(l);
                    });
                    menu.add(mi);
                }
            }

        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @Override
    public ExplorerManager getExplorerManager() {
        return manager;
    }

    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        outerPanel = new javax.swing.JPanel();
        contextPanel = new javax.swing.JPanel();
        toolBar = new org.openide.awt.Toolbar()
        ;
        jLabel2 = new javax.swing.JLabel();
        nodeSelector = new javax.swing.JComboBox<>();
        langDrop = new LangSelector();
        attach = new javax.swing.JButton();
        selectionPanel = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        noSourcesPanel = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();

        setPreferredSize(new java.awt.Dimension(200, 102));

        outerPanel.setPreferredSize(new java.awt.Dimension(200, 100));
        outerPanel.setLayout(new java.awt.CardLayout());

        toolBar.setRollover(true);

        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, org.openide.util.NbBundle.getMessage(StackViewTopComponent.class, "StackViewTopComponent.jLabel2.text")); // NOI18N

        javax.swing.GroupLayout langDropLayout = new javax.swing.GroupLayout(langDrop);
        langDrop.setLayout(langDropLayout);
        langDropLayout.setHorizontalGroup(
            langDropLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 28, Short.MAX_VALUE)
        );
        langDropLayout.setVerticalGroup(
            langDropLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );

        attach.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/graalvm/visualizer/source/resources/find.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(attach, org.openide.util.NbBundle.getMessage(StackViewTopComponent.class, "StackViewTopComponent.attach.text")); // NOI18N
        attach.setToolTipText(org.openide.util.NbBundle.getMessage(StackViewTopComponent.class, "StackViewTopComponent.attach.toolTipText")); // NOI18N
        attach.setBorder(null);
        attach.setFocusable(false);
        attach.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        attach.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        attach.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                attachActionPerformed(evt);
            }
        });

        selectionPanel.setLayout(new java.awt.BorderLayout());

        org.openide.awt.Mnemonics.setLocalizedText(jLabel3, org.openide.util.NbBundle.getMessage(StackViewTopComponent.class, "StackViewTopComponent.jLabel3.text")); // NOI18N
        jLabel3.setToolTipText(org.openide.util.NbBundle.getMessage(StackViewTopComponent.class, "StackViewTopComponent.jLabel3.toolTipText")); // NOI18N
        jLabel3.setOpaque(true);
        selectionPanel.add(jLabel3, java.awt.BorderLayout.CENTER);

        javax.swing.GroupLayout contextPanelLayout = new javax.swing.GroupLayout(contextPanel);
        contextPanel.setLayout(contextPanelLayout);
        contextPanelLayout.setHorizontalGroup(
            contextPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(contextPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(contextPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jLabel2)
                    .addComponent(langDrop, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(contextPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(contextPanelLayout.createSequentialGroup()
                        .addComponent(attach, javax.swing.GroupLayout.PREFERRED_SIZE, 31, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(selectionPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addComponent(nodeSelector, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
            .addGroup(contextPanelLayout.createSequentialGroup()
                .addComponent(toolBar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(193, 193, 193))
        );
        contextPanelLayout.setVerticalGroup(
            contextPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(contextPanelLayout.createSequentialGroup()
                .addComponent(toolBar, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(contextPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(contextPanelLayout.createSequentialGroup()
                        .addComponent(jLabel2)
                        .addGap(18, 18, 18)
                        .addGroup(contextPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(attach, javax.swing.GroupLayout.DEFAULT_SIZE, 26, Short.MAX_VALUE)
                            .addComponent(langDrop, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(contextPanelLayout.createSequentialGroup()
                        .addComponent(nodeSelector, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(selectionPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        outerPanel.add(contextPanel, "sourceContext");

        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(StackViewTopComponent.class, "StackViewTopComponent.jLabel1.text")); // NOI18N

        javax.swing.GroupLayout noSourcesPanelLayout = new javax.swing.GroupLayout(noSourcesPanel);
        noSourcesPanel.setLayout(noSourcesPanelLayout);
        noSourcesPanelLayout.setHorizontalGroup(
            noSourcesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(noSourcesPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        noSourcesPanelLayout.setVerticalGroup(
            noSourcesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(noSourcesPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        outerPanel.add(noSourcesPanel, "noSources");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(outerPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 211, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(outerPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 102, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void attachActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_attachActionPerformed
        JPopupMenu menu = buildResolverMenu();
        Rectangle bbounds = attach.getBounds();
        menu.show(this, bbounds.x, bbounds.y + bbounds.height);
    }//GEN-LAST:event_attachActionPerformed

    private NodeStack.Frame findCurrentFrame() {
        NodeStack.Frame frame = locContext.getSelectedFrame();
        if (frame == null) {
            if (frameLanguage == null) {
                return null;
            }
            Node[] selNodes = getExplorerManager().getSelectedNodes();
            if (selNodes.length > 0) {
                frame = selNodes[0].getLookup().lookup(NodeStack.Frame.class);
            }
        }
        return frame;
    }

    private void selectLanguage() {
        if (this.languages.size() < 2) {
            return;
        }
        String mime;

        NodeStack.Frame frame = locContext.getSelectedFrame();
        if (frame == null) {
            if (frameLanguage == null) {
                return;
            }
            mime = frameLanguage.getMimeType();
            Node[] selNodes = getExplorerManager().getSelectedNodes();
            if (selNodes.length > 0) {
                frame = selNodes[0].getLookup().lookup(NodeStack.Frame.class);
            }
        } else {
            mime = frame.getLocation().getMimeType();
        }
        if (frame == null) {
            return;
        }
        NodeStack firstStack = null;
        NodeStack.Frame nframe = null;
        for (String m : languages) {
            if (!m.equals(mime)) {
                nframe = frame.findPeerFrame(m);
                if (nframe != null) {
                    break;
                }
                if (firstStack == null) {
                    firstStack = frame.getStack().getOtherStack(m);
                }
            }
        }
        if (nframe == null && firstStack != null) {
            nframe = firstStack.top();
        }
        if (nframe != null) {
            locContext.setSelectedLocation(nframe);
        }
    }

    Node findNode(NodeStack.Frame frame) {
        Queue<Node> toCheck = new ArrayDeque<>();
        toCheck.add(manager.getRootContext());
        while (!toCheck.isEmpty()) {
            Node n = toCheck.poll();
            NodeStack.Frame l = n.getLookup().lookup(NodeStack.Frame.class);
            if (frame.equals(l)) {
                return n;
            }
            toCheck.addAll(Arrays.asList(n.getChildren().getNodes()));
        }
        return null;
    }


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton attach;
    private javax.swing.JPanel contextPanel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JPanel langDrop;
    private javax.swing.JPanel noSourcesPanel;
    private javax.swing.JComboBox<String> nodeSelector;
    private javax.swing.JPanel outerPanel;
    private javax.swing.JPanel selectionPanel;
    private javax.swing.JToolBar toolBar;
    // End of variables declaration//GEN-END:variables
    @Override
    public void componentOpened() {
    }

    @Override
    public void componentClosed() {
    }

    void writeProperties(java.util.Properties p) {
        // better to version settings since initial version as advocated at
        // http://wiki.apidesign.org/wiki/PropertyFiles
        p.setProperty("version", "1.0");
        // TODO store your settings
    }

    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");
        // TODO read your settings according to their version
    }

    AbstractNode noContextNode;

    @NbBundle.Messages("LBL_NoSourceContext=No sources available")
    void displayNoContext() {
        if (noContextNode == null) {
            noContextNode = new AbstractNode(Children.LEAF);
            noContextNode.setName(Bundle.LBL_NoSourceContext());
            noContextNode.setDisplayName(Bundle.LBL_NoSourceContext());
        }
        try {
            ignorePropChange = true;
            manager.setRootContext(noContextNode);
            manager.setExploredContext(noContextNode);
            choiceView.setShowExploredContext(true);
            manager.setSelectedNodes(new Node[]{noContextNode});
            choiceView.setEnabled(false);
            choiceView.setSelectedItem(null);
            ((CardLayout) outerPanel.getLayout()).show(outerPanel, "noSources");
        } catch (PropertyVetoException ex) {
        } finally {
            ignorePropChange = false;
        }
    }

    private boolean nodesChanging;

    private NodeStack findFrameForNode(InputNode n) {
        NodeStack locs = null;
        String lng = frameLanguage == null ? locContext.getSelectedLanguage() : frameLanguage.getMimeType();
        if (lng != null) {
            locs = locContext.getStack(n, lng);
            if (locs != null) {
                return locs;
            }
        }
        if (n == null) {
            return null;
        }
        boolean preferGuest = locContext.isPreferGuestLanguage();
        GraphSource gSource = locContext.getGraphSource();
        if (gSource == null) {
            return null;
        }
        if (gSource.getGraph().getNode(n.getId()) != n) {
            // node from another graph ?
            return null;
        }
        Collection<String> availLangs = gSource.getStackLanguages(n.getId());
        NodeStack first = null;
        for (String m : availLangs) {
            Language l = Language.getRegistry().findLanguageByMime(m);
            if (l == null) {
                continue;
            }
            boolean guest = !l.isHostLanguage();
            locs = locContext.getStack(n, m);
            if (locs != null) {
                if (first == null) {
                    first = locs;
                }
                if (preferGuest == guest) {
                    return locs;
                }
            }
        }
        return first;
    }

    @Override
    public void selectedNodeChanged(NodeLocationEvent evt) {
        InputNode n = evt.getSelectedNode();
        doNodeChange(evt.getNodes(), n);
    }

    private void updateNodeSelector(Collection<InputNode> nodes, InputNode cur) {
        if (nodes == null || nodes.isEmpty()) {
            nodeSelector.setEnabled(false);
            return;
        }
        this.orderedContextNodes = new ArrayList<>(nodes);
        Collections.sort(orderedContextNodes, InputNode.COMPARATOR);
        ComboBoxModel mdl = new DefaultComboBoxModel(
                        orderedContextNodes.toArray(new InputNode[orderedContextNodes.size()]));
        nodeSelector.setModel(mdl);
        int idx = orderedContextNodes.indexOf(cur);
        if (idx != -1) {
            nodeSelector.setSelectedIndex(idx);
        }
        if (cur != null) {
            updateActivatedNodes();
        }
    }

    private void updateActivatedNodes() {
        Object o = nodeSelector.getSelectedItem();
        if (o == null && nodeSelector.getItemCount() > 0) {
            o = nodeSelector.getModel().getElementAt(0);
        }
        if (o instanceof org.graalvm.visualizer.data.Properties.Provider) {
            final org.graalvm.visualizer.data.Properties.Provider provider = (org.graalvm.visualizer.data.Properties.Provider) o;
            AbstractNode node = new AbstractNode(Children.LEAF, Lookup.EMPTY) {
                @Override
                protected Sheet createSheet() {
                    Sheet s = super.createSheet();
                    PropertiesSheet.initializeSheet(provider.getProperties(), s);
                    return s;
                }
            };
            node.setDisplayName(provider.getProperties().getString(PROPNAME_NAME, "")); // NOI18N
            setActivatedNodes(new Node[]{node});
        }
    }

    private void doNodeChange(Collection<InputNode> inputNodes, InputNode n) {
        nodesChanging = true;
        try {
            NodeStack locs = findFrameForNode(n);
            if (locs == null || locs.isEmpty()) {
                current = null;
                displayNoContext();
                return;
            }
            updateNodeSelector(inputNodes, n);
            updateComboContents(locs);
        } finally {
            nodesChanging = false;
        }
    }

    @Override
    public void nodesChanged(NodeLocationEvent evt) {
        Collection<InputNode> inputNodes = locContext.getGraphNodes();
        if (inputNodes.isEmpty()) {
            current = null;
            displayNoContext();
            return;
        }
        InputNode n = locContext.getCurrentNode();
        if (n == null) {
            n = (InputNode) nodeSelector.getSelectedItem();
            if (n == null || !evt.getNodes().contains(n)) {
                List<InputNode> candidates = new ArrayList<>(inputNodes);
                Collections.sort(candidates, InputNode.COMPARATOR);
                n = candidates.iterator().next();
            }
        }
        doNodeChange(inputNodes, n);
    }

    @NbBundle.Messages({
        "# {0} - language name",
        "TOOLTIP_CurrentLanguage=Displayed stack for: {0}. Click the button to change language",
        "TOOLTIP_LanguageDropDown=Click dropdown to display available languages"
    })
    private void updateComboContents(NodeStack locs) {
        Node n = manager.getRootContext();
        if (n != null) {
            Enumeration<Node> en = n.getChildren().nodes();
            if (en.hasMoreElements()) {
                Node r = en.nextElement();
                NodeStack.Frame f = r.getLookup().lookup(NodeStack.Frame.class);
                if (f != null) {
                    if (f.getStack() == locs) {
                        return;
                    }
                }
            }
        }
        final int indentLevel = 24;
        Children.Array arr = new Children.Array();
        for (int i = locs.size() - 1; i > indentLevel; i--) {
            NodeStack.Frame frame = locs.get(i);
            ChainNode node = new ChainNode(frame, frame.getLookup().lookup(Node.class),Children.LEAF);
            arr.add(new Node[]{node});
        }

        NodeStack.Frame head = locs.size() > indentLevel ? locs.get(indentLevel) : locs.bottom();
        Children children = head.getNested() == null ? Children.LEAF : new ChainChildren(head);
        ChainNode node = new ChainNode(head, head.getLookup().lookup(Node.class), children);
        arr.add(new Node[]{node});
        manager.setRootContext(new AbstractNode(arr));
        Node toSelect = findNode(locs.top());

        Collection<String> langs = head.getGraphSource().getStackLanguages(head.getNode().getId());
        // update language
        Language lng = Language.getRegistry().findLanguageByMime(head.getLocation().getMimeType());
        Node ln = lng.getLookup().lookup(Node.class);
        if (ln != null) {
            lSelector.setIcon(ImageUtilities.image2Icon(ln.getIcon(BeanInfo.ICON_COLOR_16x16)));
        }
        this.languages = langs;
//        btnLanguage.setText(lng.getDisplayName());
        this.frameLanguage = lng;
        lSelector.setEnabled(langs.size() > 1);
        lSelector.setToolTipText(Bundle.TOOLTIP_CurrentLanguage(lng.getDisplayName()));
//        btnLanguage.setSize(btnLanguage.getPreferredSize());

        try {
            ignorePropChange = true;
            choiceView.setShowExploredContext(false);
            if (toSelect != null) {
                manager.setSelectedNodes(new Node[]{toSelect});
            }
        } catch (PropertyVetoException ex) {
            Exceptions.printStackTrace(ex);
        } finally {
            ignorePropChange = false;
        }
        current = locs.top();
        //choiceView.setEnabled(!node.isLeaf());
        choiceView.setEnabled(true);
        ((CardLayout) outerPanel.getLayout()).show(outerPanel, "sourceContext");

        NodeStack.Frame frame = getSelectedFrame();
        Location l = frame.getLocation();
        if (l != null && !l.isResolved()) {
            notifyProjectSearch();
        }
    }

    @Override
    public void locationsResolved(NodeLocationEvent evt) {
        if (current != null && evt.getNodes().contains(current.getNode())) {
            refreshButtons();
        }
    }

    private NodeStack.Frame current;
    private Language frameLanguage;
    private Collection<String> languages = Collections.emptyList();

    @Override
    public void selectedLocationChanged(NodeLocationEvent evt) {
        NodeStack.Frame loc = evt.getSelectedFrame();
        if (loc == null || loc.equals(current) || ignorePropChange) {
            return;
        }
        String mime = loc.getLocation().getMimeType();
        if (frameLanguage == null || !Objects.equals(frameLanguage.getMimeType(), mime)) {
            updateComboContents(loc.getStack());
        }
        current = loc;
        Node n = findNode(loc);
        if (n != null && !ignorePropChange) {
            try {
                manager.setSelectedNodes(new Node[]{n});
            } catch (PropertyVetoException ex) {
                Exceptions.printStackTrace(ex);
                return;
            }
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (!ExplorerManager.PROP_SELECTED_NODES.equals(evt.getPropertyName())) {
            return;
        }
        resolverMenu = null;
        Node[] nodes = manager.getSelectedNodes();
        if (nodes.length == 0) {
            return;
        }
        refreshButtons();
        NodeStack.Frame loc = nodes[0].getLookup().lookup(NodeStack.Frame.class);
        if (nodesChanging) {
            return;
        }
        if (loc != null && !loc.isResolved()) {
            notifyProjectSearch();
        }
        locContext.setSelectedLocation(loc);
    }

    private void refreshButtons() {
        Node[] nodes = manager.getSelectedNodes();
        if (nodes.length == 0) {
            return;
        }
        NodeStack.Frame loc = nodes[0].getLookup().lookup(NodeStack.Frame.class);
        if (loc != null) {
            attach.setVisible(!loc.isResolved());
        }
    }

    private NodeStack.Frame getSelectedFrame() {
        Node[] nodes = manager.getSelectedNodes();
        if (nodes.length == 0) {
            return null;
        }
        return nodes[0].getLookup().lookup(NodeStack.Frame.class);
    }

    static class ChainNode extends FilterNode {
        public ChainNode(NodeStack.Frame loc, Node original, org.openide.nodes.Children children) {
            super(original, children, new ProxyLookup(Lookups.fixed(loc, loc.getLocation()), loc.getLookup()));
            disableDelegation(DELEGATE_DESTROY | DELEGATE_SET_NAME | DELEGATE_SET_SHORT_DESCRIPTION | DELEGATE_SET_VALUE | DELEGATE_SET_DISPLAY_NAME);
        }
    }

    private JPopupMenu resolverMenu;

    @NbBundle.Messages({
        "LBL_NoResolversFound=Unsupported type of location"
    })
    private JPopupMenu buildResolverMenu() {
        if (resolverMenu != null) {
            return resolverMenu;
        }
        NodeStack.Frame frame = getSelectedFrame();
        Location l = frame.getLocation();
        JPopupMenu popup = new JPopupMenu();
        Collection<LocatorUI> locatorUIs = new ArrayList<>(Lookup.getDefault().lookupAll(LocatorUI.class));
        for (Iterator<LocatorUI> it = locatorUIs.iterator(); it.hasNext();) {
            LocatorUI ui = it.next();
            if (!ui.accepts(l)) {
                it.remove();
                continue;
            }
            JMenuItem jmi = new JMenuItem(ui.getDisplayName(), ImageUtilities.image2Icon(ui.getIcon()));
            jmi.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    ui.resolve(l);
                }
            });
            popup.add(jmi);
        }
        if (popup.getSubElements().length == 0) {
            JMenuItem jmi = new JMenuItem(Bundle.LBL_NoResolversFound());
            jmi.setEnabled(false);
            popup.add(jmi);
        }
        this.resolverMenu = popup;
        return popup;
    }

    static final Object KEY = new Object();

    static class ChainChildren extends Children.Keys {
        private final NodeStack.Frame loc;

        public ChainChildren(NodeStack.Frame child) {
            this.loc = child;
            setKeys(new Object[]{KEY});
        }

        @Override
        protected Node[] createNodes(Object key) {
            if (key == KEY) {
                NodeStack.Frame nested = loc.getNested();
                if (nested == null) {
                    return new Node[0];
                }
                Node n = nested.getLookup().lookup(Node.class);
                if (n == null) {
                    return new Node[0];
                }
                return new Node[]{
                    new ChainNode(nested, n, nested.getNested() == null ? Children.LEAF : new ChainChildren(nested))
                };
            }
            return null;
        }
    }

    private Reference<InputGraph> graphNotified = new WeakReference<>(null);

    @NbBundle.Messages({
        "TITLE_UnknownSources=Sources not available",
        "# {0} - source file name",
        "HINT_SearchForSources=Project or Suite that contains {0} is not opened. Search..."
    })
    private void notifyProjectSearch() {
        InputGraph seen = graphNotified.get();
        InputGraph now = locContext.getGraph();
        if (now == null || now == seen) {
            return;
        }
        NodeStack.Frame frame = getSelectedFrame();
        Location l = frame.getLocation();
        if (l == null) {
            return;
        }
        NotificationDisplayer.getDefault().notify(
                        Bundle.TITLE_UnknownSources(),
                        ImageUtilities.loadImageIcon("org/graalvm/visualizer/source/resources/find.png", false),
                        Bundle.HINT_SearchForSources(l.getFile().getFileSpec()),
                        this::searchInSpecificLocator
        );
        graphNotified = new WeakReference<>(now);
    }

    /**
     * Initiates search for sources. The implementation is a temporary hack
     * until the search for sources location is not reimplemented to be more
     * intuitive. It will select first non-default Locator that accepts the
     * location and delegates to it.
     *
     * @param ev
     */
    private void searchInSpecificLocator(ActionEvent ev) {
        NodeStack.Frame frame = getSelectedFrame();
        Location l = frame.getLocation();
        Collection<LocatorUI> locatorUIs = new ArrayList<>(Lookup.getDefault().lookupAll(LocatorUI.class));
        for (Iterator<LocatorUI> it = locatorUIs.iterator(); it.hasNext();) {
            LocatorUI ui = it.next();
            if (ui instanceof BasicLocatorUI || !ui.accepts(l)) {
                continue;
            }
            ui.resolve(l);
        }
    }
}
