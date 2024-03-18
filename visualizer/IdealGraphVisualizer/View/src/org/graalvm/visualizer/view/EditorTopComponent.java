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
package org.graalvm.visualizer.view;

import org.graalvm.visualizer.view.api.DiagramViewerLocator;
import org.graalvm.visualizer.view.api.DiagramViewer;
import org.graalvm.visualizer.data.*;
import org.graalvm.visualizer.data.Properties.PropertyMatcher;
import org.graalvm.visualizer.data.services.InputGraphProvider;
import org.graalvm.visualizer.filter.FilterChain;
import org.graalvm.visualizer.graph.Diagram;
import org.graalvm.visualizer.graph.Figure;
import org.graalvm.visualizer.graph.services.DiagramProvider;
import org.graalvm.visualizer.data.services.GraphSelections;
import org.graalvm.visualizer.svg.BatikSVG;
import org.graalvm.visualizer.util.LookupHistory;
import java.awt.*;
import java.awt.event.HierarchyBoundsListener;
import java.awt.event.HierarchyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.util.List;
import java.util.*;
import javax.swing.*;
import javax.swing.border.Border;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.Toolbar;
import org.openide.awt.ToolbarPool;
import org.openide.awt.UndoRedo;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.openide.util.actions.Presenter;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.util.lookup.ProxyLookup;
import org.openide.windows.TopComponent;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.graalvm.visualizer.data.Properties;
import org.graalvm.visualizer.filter.FilterSequence;
import org.graalvm.visualizer.settings.layout.LayoutSettings.LayoutSettingBean;
import org.graalvm.visualizer.util.ExternalDropTarget;
import org.graalvm.visualizer.util.RangeSliderModel;
import org.graalvm.visualizer.view.impl.Colorizer;
import org.graalvm.visualizer.view.impl.GraphCoordinator;
import org.graalvm.visualizer.view.api.TimelineModel;
import org.netbeans.api.visual.action.WidgetAction;
import org.netbeans.api.visual.widget.Widget;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ToolbarWithOverflow;
import org.openide.util.Exceptions;
import org.openide.util.WeakListeners;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.lang.reflect.Field;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import org.graalvm.visualizer.data.Properties.Entity;
import org.graalvm.visualizer.data.Properties.MutableOwner;
import org.graalvm.visualizer.filter.DataFilterSelector;
import org.graalvm.visualizer.settings.layout.LayoutSettings;
import org.graalvm.visualizer.util.ListenerSupport;
import org.openide.filesystems.FileObject;

//@RetainLocation(EditorTopComponent.MODE)
@ActionReferences({
    @ActionReference(
                    path = EditorTopComponent.TOOLBAR_ACTIONS,
                    id = @ActionID(category = "Edit", id = "org-openide-actions-UndoAction"),
                    position = 8000, separatorBefore = 7900
    ),
    @ActionReference(
                    path = EditorTopComponent.TOOLBAR_ACTIONS,
                    id = @ActionID(category = "Edit", id = "org-openide-actions-RedoAction"),
                    position = 8500, separatorAfter = 8600
    )
})
@NbBundle.Messages({
    "# {0} - the error message",
    "EXPORT_BATIK_ErrorExportingSVG=Error exporting SVG: {0}"
})
public final class EditorTopComponent extends TopComponent implements PropertyChangeListener, ChangeListener, UndoRedo.Provider {
    public static String PROP_SATELLITE_SHOWN = "satelliteShown"; // NOI18N

    /**
     * Name of the default mode where this TopComponent will dock into
     */
    public static final String MODE = "mainArea"; // NOI18N

    /**
     * Location on the cfg filesystem, where actions can be plugged into this
     * TC. Each graph viewer will load all actions; if the action supports
     * {@link ContextAwareAction} interface, the viewer will clone the action
     * using its own TC Lookup.
     */
    public static final String TOOLBAR_ACTIONS = "NodeGraphViewer/Actions"; // NOI18N

    /**
     * Actions which will be displayed in the context menu invoked on the
     * diagram component.
     */
    public static final String CONTEXT_ACTIONS = "NodeGraphViewer/ContextActions"; // NOI18N

    /**
     * Actions which will show up in context menu IF some nodes are selected.
     */
    public static final String SELECTION_ACTIONS = "NodeGraphViewer/SelectionActions"; // NOI18N

    private DiagramScene scene;
    private InstanceContent graphContent;
    private boolean notFirstTime;
    private final JComponent satelliteComponent;
    private final JPanel centerPanel;
    private final CardLayout cardLayout;
    private final TimelineModel timeline;
    private JComponent rangeSliderArea;
    private static final String PREFERRED_ID = "EditorTopComponent";
    private static final String SATELLITE_STRING = "satellite";
    private static final String SCENE_STRING = "scene";
    private DiagramViewModel rangeSliderModel;
    private Component quicksearch;
    private JComponent toolbarContainer;
    private Toolbar toolbar;
    
    /**
     * Colorizes the primary range slider
     */
    private Colorizer timelineColorizer;
    
    // Unused, but prevents graphProvider from GC.
    private InputGraphProvider graphProvider;

    private DropTarget dt;
    
    private final TitleUpdater titleUpdater;

    private ExportCookie exportCookie = new ExportCookie() {

        @Override
        public void export(File f) {

            Graphics2D svgGenerator = BatikSVG.createGraphicsObject();
            scene.paint(svgGenerator);
            FileOutputStream os = null;
            try {
                os = new FileOutputStream(f);
                Writer out = new OutputStreamWriter(os, "UTF-8");
                BatikSVG.printToStream(svgGenerator, out, true);
            } catch (IOException e) {
                NotifyDescriptor message = new NotifyDescriptor.Message(
                        Bundle.EXPORT_BATIK_ErrorExportingSVG(e.toString()), NotifyDescriptor.ERROR_MESSAGE);
                DialogDisplayer.getDefault().notifyLater(message);
            } finally {
                if (os != null) {
                    try {
                        os.close();
                    } catch (IOException e) {
                    }
                }
            }
        }
    };

    private GraphSelections selectionsBridge = new GraphSelections() {
        @Override
        public InputGraph getGraph() {
            return getModel().getGraphToView();
        }

        @Override
        public Collection<InputNode> getSelectedNodes() {
            return getModel().getSelectedNodes();
        }

        @Override
        public void setSelectedNodes(Collection<InputNode> nodes) {
            EditorTopComponent.this.scene.gotoSelection(new HashSet<>(nodes));
        }

        @Override
        public void extractNodes(Collection<InputNode> nodes) {
            getModel().showOnlyNodes(nodes);
        }

        @Override
        public void scrollToVisible(Collection<InputNode> nodes) {
            EditorTopComponent.this.scene.gotoNodes(new HashSet<>(nodes), false);
        }
    };

    private DiagramProvider diagramProvider = new DiagramProvider() {

        @Override
        public Diagram getDiagram() {
            return getModel().getDiagramToView();
        }

        @Override
        public ChangedEvent<DiagramProvider> getChangedEvent() {
            return diagramChangedEvent;
        }

        @Override
        public Future<Diagram> withDiagram(Consumer<Diagram> task) {
            return getModel().withDiagramToView(task);
        }
    };

    private ChangedEvent<DiagramProvider> diagramChangedEvent = new ChangedEvent<>(diagramProvider);
    
    @Override
    protected void componentOpened() {
        super.componentOpened();
        GraphCoordinator c = Lookup.getDefault().lookup(GraphCoordinator.class);
        c.registerSynchronizedModel(timeline, this);
    }
    
    private final DataCollectionListener dcl = (DataCollectionEvent ev) -> {
        Group g = getModel().getContainer().getContentOwner();
        for (FolderElement e : ev.getItems()) {
            if (e instanceof Folder) {
                if (((Folder) e).isParentOf(g)) {
                    SwingUtilities.invokeLater(EditorTopComponent.this::close);
                    return;
                }
            }
        }
    };

    private final DataCollectionListener wDcl;

    private static class PL extends ProxyLookup {
        public void accessSetLookups(Lookup[] lookups) {
            super.setLookups(lookups);
        }
    }

   //
   // ------------- Extracted from NbEditorToolbar implementation
   //
   
   private static final Insets BUTTON_INSETS = new Insets(2, 1, 0, 1);

   /** Shared mouse listener used for setting the border painting property
    * of the toolbar buttons and for invoking the popup menu.
    */
   private static final MouseListener sharedMouseListener
       = new MouseAdapter() {
           public @Override void mouseEntered(MouseEvent evt) {
               Object src = evt.getSource();
               
               if (src instanceof AbstractButton) {
                   AbstractButton button = (AbstractButton)evt.getSource();
                   if (button.isEnabled()) {
                       button.setContentAreaFilled(true);
                       button.setBorderPainted(true);
                   }
               }
           }
           
           public @Override void mouseExited(MouseEvent evt) {
               Object src = evt.getSource();
               if (src instanceof AbstractButton)
               {
                   AbstractButton button = (AbstractButton)evt.getSource();
                   removeButtonContentAreaAndBorder(button);
               }
           }
       };
   
   private void processButton(AbstractButton button) {
       if (button == null) {
           return;
       }
       removeButtonContentAreaAndBorder(button);
       button.setMargin(BUTTON_INSETS);
       button.addMouseListener(sharedMouseListener);
       //fix of issue #69642. Focus shouldn't stay in toolbar
       button.setFocusable(false);
   }

   private static void removeButtonContentAreaAndBorder(AbstractButton button) {
       boolean canRemove = true;
       if (button instanceof JToggleButton) {
           canRemove = !button.isSelected();
       }
       if (canRemove) {
           button.setContentAreaFilled(false);
           button.setBorderPainted(false);
       }
   }
   
   //-------------------------------------------------------------------------

   /**
    * Fixes toolbar's visual behaviour.
    * Reports minimum size, so icons can be wrapped. Configures popup parent
    * window.
    */
   static class FixedToolbar extends Toolbar implements PopupMenuListener {
       private JPopupMenu popup;
       FixedToolbar() {
           try {
               Field f = ToolbarWithOverflow.class.getDeclaredField("popup");
               f.setAccessible(true);
               JPopupMenu popupMenu = (JPopupMenu)f.get(this);
               if (popupMenu != null) {
                   popupMenu.addPopupMenuListener(this);
               }
               this.popup = popupMenu;
           } catch (ReflectiveOperationException ex) {
               Exceptions.printStackTrace(ex);
           }
       }

       public Dimension getMinimumSize() {
           Dimension d = super.getMinimumSize();
           d.width = 60;
           return d;
       }

       /**
        * Configures the parent window.
        * Changes in focusability should be done before the window shows up,
        * see {@link Window#setFocusableWindowState}
        * @param e
        */
       @Override
       public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
           popup.setInvoker(this);
           Window win = SwingUtilities.windowForComponent(this);
           if (win instanceof JWindow && win.getType() == Window.Type.POPUP) {
               win.setFocusableWindowState(true);
           }            
       }

       @Override
       public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
       }

       @Override
       public void popupMenuCanceled(PopupMenuEvent e) {
       }
   }
   
    
    
    private PropertyChangeListener colorizerNodesUpdater = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            Collection<InputNode> nodes = rangeSliderModel.getSelectedNodes();
            Set<Integer> ids = new HashSet<>(nodes.size());
            for (InputNode n : nodes) {
                ids.add(n.getId());
            }
            timelineColorizer.setTrackedNodes(ids);
        }
    };
    
    public EditorTopComponent(InputGraph graph, TimelineModel timeline) {
        // in the same mode
        LookupHistory.init(InputGraphProvider.class);
        LookupHistory.init(DiagramProvider.class);
        LookupHistory.init(GraphSelections.class);
        LookupHistory.init(DiagramViewer.class);
        LayoutSettings.obtain().addWeakChangeListener(this);
        this.setFocusable(true);
        FilterSequence filterChain = null;
        DataFilterSelector provider = Lookup.getDefault().lookup(DataFilterSelector.class);
        PL lkp = new PL();
        InstanceContent content = new InstanceContent();

        content.add(diagramProvider);
        content.add(this);
        Lookup cl = new AbstractLookup(content);
        lkp.accessSetLookups(new Lookup[] { cl });
        if (provider == null) {
            filterChain = new FilterChain();
        } else {
            filterChain = provider.getFilterChain(graph, timeline.getPrimaryPartition(), lkp);
        }

        setName(NbBundle.getMessage(EditorTopComponent.class, "CTL_EditorTopComponent"));
        setToolTipText(NbBundle.getMessage(EditorTopComponent.class, "HINT_EditorTopComponent"));

        initComponents();

        ToolbarPool.getDefault().setPreferredIconSize(16);
        Toolbar toolBar = new FixedToolbar();
        this.toolbar = toolBar;
        Border b = (Border) UIManager.get("Nb.Editor.Toolbar.border"); // NOI18N
        toolBar.setBorder(b);
        JPanel container = new JPanel();
        this.add(container, BorderLayout.NORTH);
        this.toolbarContainer = container;
        container.setLayout(new BorderLayout());
        container.add(BorderLayout.NORTH, toolBar);
        this.timeline = timeline;
        rangeSliderModel = new DiagramViewModel(timeline, filterChain, LayoutSettings.getBean());
        // do not select the graph immediately, but wait for the timeline to initialize and select the graph after that
        timeline.whenStable().execute(() -> 
            rangeSliderModel.selectGraph(graph)
        );
        rangeSliderArea = new SliderPanel(timeline.getPrimaryType(), timeline);
        
        JScrollPane pane = new JScrollPane(rangeSliderArea, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        pane.setLayout(new SliderPanel.Layout(timeline));
        container.add(BorderLayout.CENTER, pane);

        scene = new TCScene(rangeSliderModel, this);
        graphContent = new InstanceContent();

        lkp.accessSetLookups(new Lookup[]{scene.getLookup(), new AbstractLookup(graphContent), cl});
        this.associateLookup(lkp);

        Collection<Object> list = new ArrayList<>();
        list.add(newGraphProvider());
        graphContent.set(list, null);

        content.add(timeline);
        content.add(exportCookie);
        // must be there because of actions
        content.add(rangeSliderModel);
        content.add(selectionsBridge);
        content.add(scene);
        content.add(filterChain);
        graphContent.add(graph);

        rangeSliderModel.setLookup(getLookup());
        rangeSliderModel.getDiagramChangedEvent().addListener(diagramChangedListener);
        satelliteComponent = scene.createSatelliteView();

        RangeSliderModel primaryModel = timeline.getPrimaryRange();
        timelineColorizer = new Colorizer(timeline.getPrimaryPartition(), primaryModel);
        rangeSliderModel.addPropertyChangeListener(DiagramViewModel.PROP_SELECTED_NODES, 
                WeakListeners.propertyChange( 
                    colorizerNodesUpdater, DiagramViewModel.PROP_SELECTED_NODES, rangeSliderModel));        
        
        // Component organization into Cards must be done before action init.
        // The PROP_SATELLITE_SHOWN is not fired based on actual visibility of the
        // satelite, but from manipulators. If layout hid the satellite after
        // context actions initialize, they would not get proper change events
        // for the enabled state.
        // The layout is changed only using manipulators after initialization,
        // so the changes are generated.
        centerPanel = new JPanel();
        this.add(centerPanel, BorderLayout.CENTER);
        cardLayout = new CardLayout();
        centerPanel.setLayout(cardLayout);
        centerPanel.add(SCENE_STRING, scene.getComponent());
        centerPanel.setBackground(Color.WHITE);

        satelliteComponent.setSize(200, 200);
        centerPanel.add(SATELLITE_STRING, satelliteComponent);

        ActionMap am = getActionMap();

        for (Action a : Utilities.actionsForPath(TOOLBAR_ACTIONS)) {
            if (a == null) {
                toolBar.addSeparator();
                continue;
            }
            if (a instanceof ContextAwareAction) {
                a = ((ContextAwareAction) a).createContextAwareInstance(getLookup());
                if (a instanceof PropertyChangeListener) {
                    // Bug in action intialization; see NETBEANS-1985. The event will cause the context-bound action
                    // to re-evaluate the status.
                    ((PropertyChangeListener) a).propertyChange(new PropertyChangeEvent(a, Action.SELECTED_KEY, null, Boolean.TRUE));
                }
                a.isEnabled();
            }
            Object item;
            
            if (a instanceof Presenter.Toolbar) {
                item = toolBar.add(((Presenter.Toolbar) a).getToolbarPresenter());
            } else {
                item = toolBar.add(a);
            }
            
            // NbEditorToolbar's button processing
            if (item instanceof AbstractButton) {
                AbstractButton button = (AbstractButton)item;
                processButton(button);
            }
            // end 
            String n = (String) a.getValue(Action.NAME);
            String desc = (String) a.getValue(Action.SHORT_DESCRIPTION);
            if (desc != null && !desc.equals(n)) {
                am.put(n, a);
            }
        }

        toolBar.add(Box.createHorizontalGlue());
        Action action = Utilities.actionsForPath("QuickSearchShadow").get(0);
        quicksearch = ((Presenter.Toolbar) action).getToolbarPresenter();
        try {
            // (aw) workaround for disappearing search bar due to reparenting one shared component
            // instance.
            quicksearch = quicksearch.getClass().getConstructor(KeyStroke.class).newInstance(new Object[]{null});
        } catch (ReflectiveOperationException | IllegalArgumentException | SecurityException e) {
        }
        Dimension preferredSize = quicksearch.getPreferredSize();
        preferredSize = new Dimension((int) preferredSize.getWidth() * 2, (int) preferredSize.getHeight());
        quicksearch.setMinimumSize(preferredSize); // necessary for GTK LAF
        quicksearch.setPreferredSize(preferredSize);
        toolBar.add(quicksearch);

        // TODO: Fix the hot key for entering the satellite view
        scene.getComponent().addHierarchyBoundsListener(new HierarchyBoundsListener() {

            @Override
            public void ancestorMoved(HierarchyEvent e) {
            }

            @Override
            public void ancestorResized(HierarchyEvent e) {
                if (!notFirstTime && scene.getComponent().getBounds().width > 0) {
                    notFirstTime = true;
                    SwingUtilities.invokeLater(new Runnable() {

                        @Override
                        public void run() {
                            EditorTopComponent.this.scene.initialize();
                        }
                    });
                }
            }
        });

        if (graph.getGroup().getGraphsCount() == 1) {
            rangeSliderArea.setVisible(false);
        }

        titleUpdater = new TitleUpdater(rangeSliderModel, this);
        titleUpdater.start();
        
        // hook onto the graph and group 
        GraphDocument doc = graph.getGroup().getOwner();
        if (doc != null) {
            wDcl = WeakListeners.create(DataCollectionListener.class, dcl, doc);
            doc.addDataCollectionListener(wDcl);
        } else {
            wDcl = null;
        }

        dt = ExternalDropTarget.createDropTarget(this);
        setDropTarget(dt);
        dt.setActive(true);
        
        scene.getPriorActions().addAction(new  WidgetAction.Adapter() {
            /*
            @Override
            public WidgetAction.State dragEnter(Widget widget, WidgetAction.WidgetDropTargetDragEvent wdtde) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }
             */

            @Override
            public WidgetAction.State dragOver(Widget widget, WidgetAction.WidgetDropTargetDragEvent wdtde) {
                getDropTarget().dragOver(new DropTargetDragEvent(wdtde.getDropTargetContext(),
                                            wdtde.getPoint(), wdtde.getDropAction(), wdtde.getSourceActions()) {
                    @Override
                    public void rejectDrag() {
                        wdtde.rejectDrag();
                    }

                    @Override
                    public void acceptDrag(int dragOperation) {
                        wdtde.acceptDrag(dragOperation);
                    }
                });
                return WidgetAction.State.CONSUMED;
            }

            @Override
            public WidgetAction.State drop(Widget widget, WidgetAction.WidgetDropTargetDropEvent wdtde) {
                getDropTarget().drop(new DropTargetDropEvent(wdtde.getDropTargetContext(),
                                        wdtde.getPoint(), wdtde.getDropAction(), wdtde.getSourceActions()) {
                    @Override
                    public void dropComplete(boolean success) {
                    }

                    @Override
                    public void rejectDrop() {
                        wdtde.rejectDrop();
                    }

                    @Override
                    public void acceptDrop(int dropAction) {
                        wdtde.acceptDrop(dropAction);
                    }

                });
                return WidgetAction.State.CONSUMED;
            }
        });
    }
    
    public boolean isSatelliteShown() {
        return satelliteComponent.isVisible();
    }

    public void showSatellite() {
        cardLayout.show(centerPanel, SATELLITE_STRING);
        satelliteComponent.requestFocus();
        firePropertyChange(PROP_SATELLITE_SHOWN, false, true);
    }

    public void showScene() {
        cardLayout.show(centerPanel, SCENE_STRING);
        scene.getComponent().requestFocus();
        firePropertyChange(PROP_SATELLITE_SHOWN, true, false);
    }

    public void zoomOut() {
        scene.zoomOut();
    }

    public void zoomIn() {
        scene.zoomIn();
    }

    public void zoomTo(float factor) {
        scene.zoomTo(factor);
    }

    public void showPrevDiagram() {
        int fp = getModel().getDiagramPeers().getFirstPosition();
        int sp = getModel().getDiagramPeers().getSecondPosition();
        if (fp != 0) {
            fp--;
            sp--;
            getModel().getDiagramPeers().setPositions(fp, sp);
        }
    }

    public DiagramViewModel getModel() {
        return rangeSliderModel;
    }

    public FilterSequence getFilterChain() {
        return getModel().getFilterChain();
    }

    public static EditorTopComponent getActive() {
        DiagramViewer v = Lookup.getDefault().lookup(DiagramViewerLocator.class).getActiveViewer();
        TopComponent tc = v.getLookup().lookup(TopComponent.class);
        return tc instanceof EditorTopComponent ? (EditorTopComponent)tc : null;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jCheckBox1 = new javax.swing.JCheckBox();

        org.openide.awt.Mnemonics.setLocalizedText(jCheckBox1, "jCheckBox1");
        jCheckBox1.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));

        setLayout(new java.awt.BorderLayout());
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox jCheckBox1;
    // End of variables declaration//GEN-END:variables

    @Override
    public int getPersistenceType() {
        return TopComponent.PERSISTENCE_NEVER;
    }

    @Override
    public void componentClosed() {
        rangeSliderModel.close();
        // the quicksearch does not unregister from global result list.
        // remove it from the AWT hiearchy, so it does not keep this Component
        // through parent chain.
        this.remove(toolbarContainer);
        this.toolbar.remove(this.quicksearch);
        GraphDocument d = getModel().getContainer().getContentOwner().getOwner();
        if (d != null && wDcl != null) {
            d.removeDataCollectionListener(wDcl);
        }
        super.componentClosed();
    }

    @Override
    protected String preferredID() {
        return PREFERRED_ID;
    }

    private InputGraphProvider newGraphProvider() {
        return graphProvider = new EditorInputGraphProvider(this);
    }

    private ChangedListener<DiagramViewModel> diagramChangedListener = new ChangedListener<DiagramViewModel>() {
        // keep a reference
        @Override
        public void changed(DiagramViewModel source) {
            if (!source.isValid()) {
                // the group has been emptied or something like that
                SwingUtilities.invokeLater(EditorTopComponent.this::close);
                return;
            }
            Collection<Object> list = new ArrayList<>();
            list.add(source.getGraphToView());
            list.add(newGraphProvider());
            graphContent.set(list, null);
            diagramChangedEvent.fire();
        }
    };
    
    public void setSelection(PropertyMatcher matcher) {
        getModel().withDiagramToView((d) -> {
            Properties.PropertySelector<Figure> selector = new Properties.PropertySelector<>(d.getFigures());
            List<Figure> list = selector.selectMultiple(matcher);
            setSelectedFigures(list);
        });
    }

    public DiagramViewer getViewer() {
        return scene;
    }

    public void setSelectedFigures(List<Figure> list) {
        scene.centerFigures(list);
        scene.setSelection(list);
    }

    public void setSelectedNodes(Collection<InputNode> nodes) {
        getModel().withDiagramToView((d) -> {
            setSelectedFigures(new ArrayList<>(scene.figuresForNodes(nodes)));
        });
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
    }

    public void extract() {
        getModel().showOnlyNodes(getModel().getSelectedNodes());
    }

    public void hideNodes() {
        Collection<InputNode> selectedNodes = this.getModel().getSelectedNodes();
        HashSet<Integer> nodes = new HashSet<>(getModel().getHiddenNodes());
        nodes.addAll(selectedNodes.stream().map(InputNode::getId).collect(Collectors.toList()));
        this.getModel().showNot(nodes);
    }

    public void expandPredecessors() {
        Collection<Figure> oldSelection = getModel().getSelectedFigures();
        Set<Figure> figures = new HashSet<>();

        for (Figure f : getDiagram().getFigures()) {
            boolean ok = false;
            if (oldSelection.contains(f)) {
                ok = true;
            } else {
                for (Figure pred : f.getSuccessors()) {
                    if (oldSelection.contains(pred)) {
                        ok = true;
                        break;
                    }
                }
            }

            if (ok) {
                figures.add(f);
            }
        }

        getModel().showAll(figures);
    }

    public void expandSuccessors() {
        Collection<Figure> oldSelection = getModel().getSelectedFigures();
        Set<Figure> figures = new HashSet<>();

        for (Figure f : getDiagram().getFigures()) {
            boolean ok = false;
            if (oldSelection.contains(f)) {
                ok = true;
            } else {
                for (Figure succ : f.getPredecessors()) {
                    if (oldSelection.contains(succ)) {
                        ok = true;
                        break;
                    }
                }
            }

            if (ok) {
                figures.add(f);
            }
        }

        getModel().showAll(figures);
    }

    public void showAll() {
        getModel().showNot(Collections.<Integer>emptySet());
    }

    private Diagram getDiagram() {
        return getModel().getDiagramToView();
    }

    @Override
    protected void componentHidden() {
        super.componentHidden();
        scene.componentHidden();

    }

    @Override
    protected void componentShowing() {
        super.componentShowing();
        scene.componentShowing();
    }

    @Override
    public void requestActive() {
        super.requestActive();
        scene.getComponent().requestFocus();
    }

    @Override
    public UndoRedo getUndoRedo() {
        return scene.getUndoRedo();
    }

    @Override
    protected Object writeReplace() throws ObjectStreamException {
        throw new NotSerializableException();
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        Object source = e.getSource();
        if (source != null && source instanceof LayoutSettingBean) {
            scene.relayout((LayoutSettingBean) source);
        }
    }

    /**
     * Executes tasks after the layout being done finishes.
     *
     * @return executes task on computed layout
     */
    public Executor whenReady() {
        return scene::executeWithDiagramShown;
    }

    static class TCScene extends DiagramScene {
        public TCScene(DiagramViewModel model, TopComponent tc) {
            super(model, tc);
        }

        @Override
        public JPopupMenu createPopupMenu() {
            String p = this.getSelectedObjects().isEmpty() ? CONTEXT_ACTIONS : SELECTION_ACTIONS;
            List<? extends Action> al = Utilities.actionsForPath(p);
            Action[] aa = al.toArray(new Action[al.size()]);
            return Utilities.actionsToPopup(aa, getLookup());
        }
    }
    
    static class TitleUpdater implements ChangedListener<MutableOwner>, PropertyChangeListener {
        private final TimelineModel viewModel;
        private final DiagramViewModel diagramModel;
        private final EditorTopComponent target;
        private final ChangedListener<GraphDocument> docL = (e) -> refresh();

        public TitleUpdater(DiagramViewModel diagramModel, EditorTopComponent target) {
            this.diagramModel = diagramModel;
            this.viewModel = diagramModel.getTimeline();
            this.target = target;
        }
        
        public void start() {
            // add listeners to all levels up to the root
            for (Folder g = viewModel.getPrimaryPartition().getContentOwner(); g != null; g = g.getParent()) {
                if (g instanceof MutableOwner) {
                    ListenerSupport.addWeakListener(this, ((MutableOwner)g).getPropertyChangedEvent());
                }
            }
            diagramModel.addPropertyChangeListener(WeakListeners.propertyChange(this, diagramModel));
            SwingUtilities.invokeLater(this::updateDisplayName);
        }
        
        private void refresh() {
            SwingUtilities.invokeLater(this::updateDisplayName);
        }

        @Override
        public void changed(MutableOwner source) {
            refresh();
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (DiagramViewModel.PROP_SELECTED_GRAPH.equals(evt.getPropertyName())) {
                refresh();
            }
        }
        
        private String getUserLabel(Entity e) {
            return e.getProperties().getString(KnownPropertyNames.PROPNAME_USER_LABEL, null);
        }
        
        private String findContainerTitle() {
            InputGraph ig = diagramModel.getGraphToView();
            String l = getUserLabel(ig);
            if (l != null) {
                return l;
            }
            for (Folder g = viewModel.getPrimaryPartition().getContentOwner(); g != null; g = g.getParent()) {
                if (g instanceof Entity) {
                    l = getUserLabel((Entity)g);
                    if (l != null) {
                        return l;
                    }
                }
            }
            Group g = viewModel.getPrimaryPartition().getContentOwner();
            if (g == null || g.getOwner() == null) {
                return null;
            }
            GraphDocument gdoc = g.getOwner();
            l = getUserLabel(gdoc);
            if (l != null) {
                return l;
            }
            String docName = gdoc.getName();
            // various hacks for documents without IDs:
            if (docName.startsWith("HotSpotCompilation") ||
                docName.startsWith("TruffleHotSpotCompilation")) { // NOI18N
                int bracket = docName.indexOf("["); // NOI18N
                int dash = docName.indexOf('-');
                if (bracket > 0) {
                    if (dash > 0) {
                        return docName.substring(dash + 1, bracket);
                    } else {
                        return docName.substring(0, bracket);
                    }
                }
            }
            if (gdoc instanceof Lookup.Provider) {
                FileObject fo = ((Lookup.Provider)gdoc).getLookup().lookup(FileObject.class);
                if (fo != null) {
                    return fo.getName();
                }
            }
            return null;
        }

        @NbBundle.Messages({
            "# {0} - diagram name",
            "# {1} - container ",
            "FMT_DiagramNameSuffix={0} - {1}",
            
            "# {0} - group name",
            "# {1} - container ",
            "FMT_GroupNameSuffix={0} - {1}"
        })
        private void updateDisplayName() {
            String suffix = findContainerTitle();
            Diagram d = diagramModel.getDiagramToView();
            String n = d.getName();
            String g = d.getGraph().getGroup().getName();
            if (suffix != null) {
                n = Bundle.FMT_DiagramNameSuffix(n, suffix);
                g = Bundle.FMT_GroupNameSuffix(n, suffix);
            }
            target.setDisplayName(n);
            target.setToolTipText(g);
        }
    }
}
