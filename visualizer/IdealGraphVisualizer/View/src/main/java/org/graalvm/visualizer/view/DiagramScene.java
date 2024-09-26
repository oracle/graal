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

import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.PROPNAME_NAME;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import org.graalvm.visualizer.data.ControllableChangedListener;
import org.graalvm.visualizer.data.Source;
import org.graalvm.visualizer.data.services.GraphSelections;
import org.graalvm.visualizer.data.src.ImplementationClass;
import org.graalvm.visualizer.graph.*;
import org.graalvm.visualizer.selectioncoordinator.SelectionCoordinator;
import org.graalvm.visualizer.settings.layout.LayoutSettings.LayoutSettingBean;
import org.graalvm.visualizer.util.ColorIcon;
import org.graalvm.visualizer.util.DoubleClickAction;
import org.graalvm.visualizer.util.PropertiesSheet;
import org.graalvm.visualizer.view.api.DiagramViewer;
import org.graalvm.visualizer.view.api.DiagramViewerEvent;
import org.graalvm.visualizer.view.api.DiagramViewerListener;
import org.graalvm.visualizer.view.widgets.*;
import org.graalvm.visualizer.view.widgets.actions.CustomizablePanAction;
import org.netbeans.api.visual.action.*;
import org.netbeans.api.visual.animator.AnimatorEvent;
import org.netbeans.api.visual.animator.AnimatorListener;
import org.netbeans.api.visual.layout.Layout;
import org.netbeans.api.visual.layout.LayoutFactory;
import org.netbeans.api.visual.model.*;
import org.netbeans.api.visual.widget.ComponentWidget;
import org.netbeans.api.visual.widget.LayerWidget;
import org.netbeans.api.visual.widget.Widget;
import org.openide.awt.UndoRedo;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.BaseUtilities;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.Pair;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.windows.TopComponent;

import jdk.graal.compiler.graphio.parsing.model.*;
import jdk.graal.compiler.graphio.parsing.model.Properties;

/**
 * DiagramScene creates and manages the widgets representing graph structures.
 * Updates to the widgets are processed by {@link SceneUpdater}, which computes
 * layout and propagates it to widgets asynchronously, or interleaves the
 * updates with regular EDT processing to maintain responsiveness.
 * <p/>
 * Threading: unless explicitly noted, methods should be called in EDT thread
 * only.
 */
public class DiagramScene extends ObjectScene implements DiagramViewer {
    private static final Logger LOG = Logger.getLogger(DiagramScene.class.getName());

    private final TopComponent topComponent;
    private final CustomizablePanAction panAction;
    private final WidgetAction hoverAction;
    private final WidgetAction selectAction;
    private final Lookup lookup;
    private final InstanceContent content;
    private final JScrollPane scrollPane;
    private UndoRedo.Manager undoRedoManager;

    /**
     * Widget that centers the diagram within the view, if the
     * diagram is smaller than the viewport.
     */
    private final LayerWidget centeringLayer;

    // Layers that make up the diagram's contents. The following layers
    // must be placed into centeringLayer:
    private final LayerWidget connectionLayer;

    /**
     * Layer to draw figures.
     */
    private final LayerWidget mainLayer;

    /**
     * Layer for blocks.
     */
    private final LayerWidget blockLayer;

    /**
     * Layer for selection decorations, should be after other layers to
     * get precedence.
     */
    private final LayerWidget selectLayer;

    //------------- end of content layers ----------------

    /**
     * Overlay layer is used to display progress icon and message.
     */
    private final Overlay overlayLayer;
    /**
     * Background layer is used to display Fog of Work.
     */
    private final LayerWidget backgroundLayer;

    /**
     * Widget that defines topleft place in the scene, to prevent
     * Scene implementation from moving drawing to 0,0
     */
    private final Widget topLeft;
    private final Widget bottomRight;
    private final FogWidget fogOfWorkWiget;

    private DiagramViewModel model;
    private DiagramViewModel modelCopy;
    private final WidgetAction zoomAction;
    private volatile boolean rebuilding;

    /**
     * Used during diagram transition to maintain view position. Possibly {@code null}.
     */
    private Point previousViewportPosition;

    /**
     * Viewports which affect the scene's update
     */
    private final List<VL> viewports = new ArrayList<>(3);

    private SelectionCoordinator selectionCoordinator;

    enum SelMode {
        /**
         * default mode. selection will be propagated to SelectionCoordinator,
         * unless the component is updating since selection may change in
         * unexpected ways during update
         */
        DEFAULT,
        /**
         * selection API call. Will be propagated even if the component is being
         * updated.
         */
        API,
        /**
         * Accepts foreign selection, possibly narrowed to diagram's contents,
         * but does not propagate it outside.
         */
        ACCEPT
    }

    /**
     * Bypasses check for "rebuild", which may remove objects and therefore fire
     * selection events. Selection events from rebuilt are not meant to be
     * propagated to SelectionCoordinator, but API calls should, even during
     * rebuild.
     */
    private final ThreadLocal<SelMode> forceSelectionMode = new ThreadLocal<SelMode>() {
        @Override
        protected SelMode initialValue() {
            return SelMode.DEFAULT;
        }
    };

    /**
     * Tracks the current diagram.
     */
    private Diagram currentDiagram;
    private InputGraph currentGraph;

    /**
     * The alpha level of partially visible figures.
     */
    public static final float ALPHA = 0.4f;

    /**
     * The offset of the graph to the border of the window showing it.
     */
    public static final int BORDER_SIZE = 20;

    public static final int UNDOREDO_LIMIT = 100;
    public static final int SCROLL_UNIT_INCREMENT = 80;
    public static final int SCROLL_BLOCK_INCREMENT = 400;
    public static final float ZOOM_MAX_FACTOR = 3.0f;
    public static final float ZOOM_MIN_FACTOR = 0.0f;// 0.15f;
    public static final float ZOOM_INCREMENT = 1.5f;
    public static final int SLOT_OFFSET = 8;
    public static final int ANIMATION_LIMIT = 40;

    private final PopupMenuProvider popupMenuProvider = (Widget widget, Point localLocation) -> createPopupMenu();

    /**
     * Helper instance that manages updates to layout and widgets
     */
    private final SceneUpdater updater;

    /**
     * Watches viewport changes
     */
    private final ViewportCenteringBridge viewportCenteringBridge;

    private final RectangularSelectDecorator rectangularSelectDecorator = () -> {
        Widget widget = new Widget(DiagramScene.this);
        widget.setBorder(BorderFactory.createLineBorder(Color.black, 2));
        widget.setForeground(Color.red);
        return widget;
    };

    @SuppressWarnings("unchecked")
    public <T> T getWidget(Object o) {
        Widget w = this.findWidget(o);
        return (T) w;
    }

    @SuppressWarnings("unchecked")
    public <T> T getWidget(Object o, Class<T> klass) {
        Widget w = this.findWidget(o);
        return (T) w;
    }

    @Override
    public void zoomOut() {
        double zoom = getZoomFactor();
        zoomTo(zoom / DiagramScene.ZOOM_INCREMENT);
    }

    @Override
    public JComponent createSatelliteView() {
        return new ExtendedSatelliteComponent(this);
    }

    /**
     * Sets viewport position so that selected figures are "centered". Actually
     * centers the centerpoint of a selection bounding rectangle.
     */
    public void centerSelectedFigures() {
        // if the layout manager does not set viewport.view.size, the position will fail
        // to work during zoom-in, as the view will report still smaller X-size
        getScrollPane().getViewport().invalidate();
        getScrollPane().getViewport().validate();

        Collection<Figure> figures = (Collection<Figure>) getSelectedObjects().stream().filter(o -> o instanceof Figure).collect(Collectors.toList());
        Rectangle u = unionRectangle(figures);

        if (u == null) {
            u = getScrollPane().getViewport().getViewRect();
            u = convertViewToScene(u);
        }
        Point rCenter = new Point((int) u.getCenterX(), (int) u.getCenterY());
        Dimension size = getScrollPane().getViewport().getExtentSize();
        Point scenePoint = convertSceneToView(rCenter);
        Point newPos = new Point(
                Math.max(0, scenePoint.x - (size.width / 2)),
                Math.max(0, scenePoint.y - (size.height / 2))
        );
        getScrollPane().getViewport().setViewPosition(newPos);
    }

    @Override
    public void zoomIn() {
        double zoom = getZoomFactor();
        zoomTo(zoom * DiagramScene.ZOOM_INCREMENT);
    }

    public void zoomTo(double newZoom) {
        if (newZoom <= DiagramScene.ZOOM_MIN_FACTOR || (newZoom > DiagramScene.ZOOM_MAX_FACTOR)) {
            return;
        }
        setZoomFactor(newZoom);
        // force validation, which calls Scene.layoutScene():
        // ensure that scene.getPreferredSize() is computed & set into the scene Component
        validate();
        executeWithDiagramShown(() -> {
            centerSelectedFigures();
            fireViewerEvent(DiagramViewerListener::displayChanged, new DiagramViewerEvent(this));
        });
    }

    @Override
    public void centerFigures(Collection<Figure> list) {
        centerFigures(list, false);
    }

    private void centerFigures(Collection<Figure> list, boolean ignoreIfVisible) {
        boolean b = getUndoRedoEnabled();
        setUndoRedoEnabled(false);
        gotoFigures(list, ignoreIfVisible);
        setUndoRedoEnabled(b);
    }

    private final ControllableChangedListener<SelectionCoordinator> highlightedCoordinatorListener = new ControllableChangedListener<SelectionCoordinator>() {
        @Override
        public void filteredChanged(SelectionCoordinator source) {
            assert source == selectionCoordinator;
            DiagramScene.this.setHighlightedObjects(idSetToObjectSet(source.getHighlightedObjects()));
            DiagramScene.this.validate();
        }
    };
    private final ControllableChangedListener<SelectionCoordinator> selectedCoordinatorListener = new ControllableChangedListener<SelectionCoordinator>() {
        @Override
        public void filteredChanged(SelectionCoordinator source) {
            assert source == selectionCoordinator;
            DiagramScene.this.gotoSelection(source.getSelectedObjects());
            DiagramScene.this.validate();
        }
    };

    private final RectangularSelectProvider rectangularSelectProvider = (Rectangle rectangle) -> {
        if (rectangle.width < 0) {
            rectangle.x += rectangle.width;
            rectangle.width *= -1;
        }
        if (rectangle.height < 0) {
            rectangle.y += rectangle.height;
            rectangle.height *= -1;
        }
        Set<Object> selectedObjects = new HashSet<>();
        for (Figure f : getDiagram().getFigures()) {
            FigureWidget w = getWidget(f);
            if (w != null) {
                Rectangle r = new Rectangle(w.getBounds());
                r.setLocation(w.getLocation());
                if (r.intersects(rectangle)) {
                    selectedObjects.add(f);
                }
                for (Slot s : f.getSlots()) {
                    // slots should not be cleaned separately from its figure, are created
                    // at the same time as the owning figure.
                    SlotWidget sw = getWidget(s);
                    Rectangle r2 = new Rectangle(sw.getBounds());
                    r2.setLocation(sw.convertLocalToScene(new Point(0, 0)));
                    if (r2.intersects(rectangle)) {
                        selectedObjects.add(s);
                    }
                }
            }
        }
        setSelectedObjects(selectedObjects);
    };

    public Point getScrollPosition() {
        return getScrollPane().getViewport().getViewPosition();
    }

    public void setScrollPosition(Point p) {
        getScrollPane().getViewport().setViewPosition(p);
    }

    private JScrollPane createScrollPane() {
        JComponent comp = this.createView();
        comp.setDoubleBuffered(true);
        comp.setBackground(Color.WHITE);
        comp.setOpaque(true);
        this.setBackground(Color.WHITE);
        this.setOpaque(true);
        JScrollPane result = new JScrollPane(comp);
        result.setBackground(Color.WHITE);
        result.getVerticalScrollBar().setUnitIncrement(SCROLL_UNIT_INCREMENT);
        result.getVerticalScrollBar().setBlockIncrement(SCROLL_BLOCK_INCREMENT);
        result.getHorizontalScrollBar().setUnitIncrement(SCROLL_UNIT_INCREMENT);
        result.getHorizontalScrollBar().setBlockIncrement(SCROLL_BLOCK_INCREMENT);
        return result;
    }

    static void computeSelectionContent(
            InputGraph graph,
            Set<Object> selectedObjects, Collection<Object> fillToLookup,
            Set<InputNode> nodeSelection, Set<Integer> nodeSelectionIds
    ) {
        for (Object o : selectedObjects) {
            InstanceContent nodeContent = new InstanceContent();
            Lookup nodeLookup = new AbstractLookup(nodeContent);
            AbstractNode node = null;
            if (o instanceof Properties.Provider) {
                final Properties.Provider provider = (Properties.Provider) o;
                node = new AbstractNode(Children.LEAF, nodeLookup) {
                    @Override
                    protected Sheet createSheet() {
                        Sheet s = super.createSheet();
                        PropertiesSheet.initializeSheet(provider.getProperties(), s);
                        return s;
                    }
                };
                Object className = provider.getProperties().get("class");
                if (className instanceof String) {
                    fillToLookup.add(new ImplementationClass((String) className));
                }
                node.setDisplayName(provider.getProperties().getString(PROPNAME_NAME, Bundle.NAME_MissingName())); // NOI18N
                nodeContent.add(o);
            }

            if (o instanceof Slot || o instanceof Figure) {
                Source.Provider p = (Source.Provider) o;
                for (InputNode n : p.getSource().getSourceNodes()) {
                    fillToLookup.add(n);
                    nodeContent.add(n);
                }
                nodeSelection.addAll(p.getSource().getSourceNodes());
                nodeSelectionIds.addAll(p.getSource().getSourceNodeIds());
            }
            nodeContent.add(graph);
            if (node != null) {
                fillToLookup.add(node);
            }
        }
    }

    @NbBundle.Messages({
            "NAME_MissingName=<missing name>"
    })
    private final ObjectSceneListener selectionChangedListener = new ObjectSceneListener() {
        @Override
        public void objectAdded(ObjectSceneEvent arg0, Object arg1) {
        }

        @Override
        public void objectRemoved(ObjectSceneEvent arg0, Object arg1) {
        }

        @Override
        public void objectStateChanged(ObjectSceneEvent e, Object o, ObjectState oldState, ObjectState newState) {
        }

        @Override
        public void selectionChanged(ObjectSceneEvent e, Set<Object> oldSet, Set<Object> newSet) {
            DiagramScene scene = (DiagramScene) e.getObjectScene();
            SelMode m = forceSelectionMode.get();
            if (m == SelMode.ACCEPT || (m != SelMode.API && scene.isRebuilding())) {
                return;
            }
            Collection x = new ArrayList<>(newSet);

            Set<InputNode> nodeSelection = new HashSet<>();
            Set<Integer> nodeSelectionIds = new HashSet<>();
            computeSelectionContent(getDiagram().getGraph(), newSet, x, nodeSelection, nodeSelectionIds);
            model.setSelectedNodes(nodeSelection);
            x.add(topComponent);
            content.set(x, null);

            boolean b = selectedCoordinatorListener.isEnabled();
            selectedCoordinatorListener.setEnabled(false);
            selectionCoordinator.setSelectedObjects(nodeSelectionIds);
            selectedCoordinatorListener.setEnabled(b);

            fireViewerEvent(DiagramViewerListener::stateChanged, new DiagramViewerEvent(DiagramScene.this));
        }

        @Override
        public void highlightingChanged(ObjectSceneEvent e, Set<Object> oldSet, Set<Object> newSet) {
            Set<Integer> nodeHighlighting = new HashSet<>();
            for (Object o : newSet) {
                if (o instanceof Source.Provider) {
                    ((Source.Provider) o).getSource().collectIds(nodeHighlighting);
                }
            }
            boolean b = highlightedCoordinatorListener.isEnabled();
            highlightedCoordinatorListener.setEnabled(false);
            selectionCoordinator.setHighlightedObjects(nodeHighlighting);
            highlightedCoordinatorListener.setEnabled(b);

            fireViewerEvent(DiagramViewerListener::stateChanged, new DiagramViewerEvent(DiagramScene.this));
        }

        @Override
        public void hoverChanged(ObjectSceneEvent e, Object oldObject, Object newObject) {
            Set<Object> newHighlightedObjects = new HashSet<Object>(DiagramScene.this.getHighlightedObjects());
            if (oldObject != null) {
                newHighlightedObjects.remove(oldObject);
            }
            if (newObject != null) {
                newHighlightedObjects.add(newObject);
            }
            DiagramScene.this.setHighlightedObjects(newHighlightedObjects);
        }

        @Override
        public void focusChanged(ObjectSceneEvent arg0, Object arg1, Object arg2) {
        }
    };

    @SuppressWarnings("OverridableMethodCallInConstructor")
    public DiagramScene(DiagramViewModel model, TopComponent tc) {
        assert tc != null;
        this.topComponent = tc;
        this.setCheckClipping(true);
        this.getInputBindings().setZoomActionModifiers(KeyEvent.CTRL_MASK);

        selectionCoordinator = SelectionCoordinator.getInstanceForContainer(model.getContainer());
        content = new InstanceContent();
        content.add(tc);
        lookup = new AbstractLookup(content);
        scrollPane = createScrollPane();

        //---------- Initialize scene contents / structure
        backgroundLayer = new LayerWidget(this);
        centeringLayer = new LayerWidget(this);
        blockLayer = new LayerWidget(this);
        connectionLayer = new LayerWidget(this);
        mainLayer = new LayerWidget(this);
        selectLayer = new LayerWidget(this);
        overlayLayer = new Overlay(this);

        topLeft = new Widget(this);
        topLeft.setPreferredLocation(new Point(-BORDER_SIZE, -BORDER_SIZE));
        bottomRight = new Widget(this);
        bottomRight.setPreferredLocation(new Point(-BORDER_SIZE, -BORDER_SIZE));
        // cannot be lambda, updater not initialized yet.
        fogOfWorkWiget = new FogWidget(this);

        this.addChild(backgroundLayer);
        this.addChild(topLeft);
        this.addChild(bottomRight);
        this.setLayout(LayoutFactory.createAbsoluteLayout());
        this.addChild(centeringLayer);
        this.addChild(overlayLayer);
        backgroundLayer.addChild(fogOfWorkWiget);
        centeringLayer.setLayout(new CenteringLayout(LayoutFactory.createAbsoluteLayout()));

        //---------------- Initialize and add actions ---------------
        hoverAction = createObjectHoverAction();
        // This panAction handles the event only when the left mouse button is
        // pressed without any modifier keys, otherwise it will not consume it
        // and the selection action (below) will handle the event
        panAction = new CustomizablePanAction(~0, MouseEvent.BUTTON1_DOWN_MASK);
        selectAction = createSelectAction();
        zoomAction = ActionFactory.createMouseCenteredZoomAction(1.2);

        this.getActions().addAction(panAction);
        this.getActions().addAction(selectAction);
        this.getActions().addAction(zoomAction);
        this.getActions().addAction(ActionFactory.createPopupMenuAction(popupMenuProvider));
        this.getActions().addAction(ActionFactory.createWheelPanAction());
        this.getActions().addAction(ActionFactory.createRectangularSelectAction(rectangularSelectDecorator, selectLayer, rectangularSelectProvider));


        // For initial setup of the model we need to disable undo to prevent
        // unnecessary cloning.
        boolean b = this.getUndoRedoEnabled();
        this.setUndoRedoEnabled(false);
        this.setNewModel(model);
        this.setUndoRedoEnabled(b);

        updater = new SceneUpdater(this);
        viewportCenteringBridge = new ViewportCenteringBridge(getScrollPane().getViewport());

        //--------------- attaches listeners -----------------------------

        updater.addChangeListener((e) -> fogOfWorkWiget.setValidShape(updater.getValidatedShape()));

        this.addObjectSceneListener(selectionChangedListener, ObjectSceneEventType.OBJECT_SELECTION_CHANGED, ObjectSceneEventType.OBJECT_HIGHLIGHTING_CHANGED,
                ObjectSceneEventType.OBJECT_HOVER_CHANGED);

        getSceneAnimator().getPreferredLocationAnimator().addAnimatorListener(new AnimatorListener() {
            @Override
            public void animatorStarted(AnimatorEvent ae) {
            }

            @Override
            public void animatorReset(AnimatorEvent ae) {
            }

            @Override
            public void animatorFinished(AnimatorEvent ae) {
                animationStopped(ae);
            }

            @Override
            public void animatorPreTick(AnimatorEvent ae) {
            }

            @Override
            public void animatorPostTick(AnimatorEvent ae) {
            }
        });
        viewports.add(new VL(vp1));

        getScrollPane().getViewport().addChangeListener(viewportCenteringBridge);
    }

    /**
     * Bridge object from API to DiagramScene to be exposed in the Lookup
     */
    private final SceneViewport vp1 = new SceneViewport() {
        @Override
        public Rectangle getSceneViewRect() {
            return DiagramScene.this.getScrollPane().getVisibleRect();
        }

        @Override
        public Rectangle getViewportRect() {
            return DiagramScene.this.getScrollPane().getViewport().getViewRect();
        }

        @Override
        public void sceneContentsUpdated(boolean finished, Rectangle validRectangle) {
            Rectangle toUpdate = validRectangle.intersection(getSceneViewRect());
            if (!toUpdate.isEmpty()) {
                LOG.log(Level.FINE, "Repainting rect: {0}", toUpdate);
                DiagramScene.this.getScrollPane().getViewport().repaint(toUpdate);
            } else {
                LOG.log(Level.FINE, "Won't update invisible area.");
            }
        }

        @Override
        public void addChangeListener(ChangeListener l) {
            DiagramScene.this.getScrollPane().getViewport().addChangeListener(l);
        }

        @Override
        public void removeChangeListener(ChangeListener l) {
            DiagramScene.this.getScrollPane().getViewport().removeChangeListener(l);
        }
    };

    @Override
    public DiagramViewModel getModel() {
        return model;
    }

    private Diagram getDiagram() {
        return model.getDiagramToView();
    }

    public JScrollPane getScrollPane() {
        return scrollPane;
    }

    @Override
    public Component getComponent() {
        return scrollPane;
    }

    TopComponent getTopComponent() {
        return topComponent;
    }

    public boolean isAllVisible() {
        return model.getHiddenNodes().isEmpty();
    }

    @NbBundle.Messages({
            "# {0} - figure's name",
            "# {1} - cluster's name",
            "# {2} - 0: visible, 1: hidden",
            "FMT_GotoFigureActionName_Clustered={0} (B{1}{2,choice,0#|0<, hidden})",
            "# {0} - figure's name",
            "# {1} - 0: visible, 1: hidden",
            "FMT_GotoFigureActionName={0} {1,choice,0#|0< (hidden)}",})
    public Action createGotoAction(final Figure f) {
        final DiagramScene diagramScene = this;
        String l = f.getLines()[0];
        int h = f.isVisible() ? 0 : 1;
        String name = f.getCluster() == null
                ? Bundle.FMT_GotoFigureActionName(l, h)
                : Bundle.FMT_GotoFigureActionName_Clustered(l, f.getCluster().toString(), h);
        Action a = new AbstractAction(name, new ColorIcon(f.getColor())) {
            @Override
            public void actionPerformed(ActionEvent e) {
                diagramScene.gotoFigure(f);
            }
        };

        a.setEnabled(true);
        return a;
    }

    public final void setNewModel(DiagramViewModel model) {
        assert this.model == null : "can set model only once!";
        this.model = model;
        this.modelCopy = model.copy();

        model.getDiagramChangedEvent().addListener(diagramChange);
        model.addPropertyChangeListener(DiagramViewModel.PROP_SELECTED_NODES, selectionChange);
        model.addPropertyChangeListener(DiagramViewModel.PROP_HIDDEN_NODES, hiddenChange);
        model.addPropertyChangeListener(DiagramViewModel.PROP_CONTAINER_CHANGED, containerChanged);
        currentDiagram = model.getDiagramToView();
    }

    private void update() {
        Diagram dg = getDiagram();
        Diagram old = currentDiagram;
        if (dg == null || dg == old) {
            return;
        }

        if (model.isStubDiagram(dg)) {
            if (currentGraph == getGraph()) {
                // transition from some normal diagram to a stub: if NOT changing a graph, doing just relayout, save position
                previousViewportPosition = getScrollPosition();
            } else {
                previousViewportPosition = null;
            }
        } else {
            // transition to a normal diagram again
            boolean b = this.getUndoRedoEnabled();
            this.setUndoRedoEnabled(b && !model.isStubDiagram(currentDiagram));

            addUndo();

            if (currentGraph != getGraph()) {
                // reset position hint if changing a graph.
                previousViewportPosition = null;
            }
            this.currentDiagram = dg;
            this.currentGraph = getGraph();
            this.setUndoRedoEnabled(b);
            this.validate();
            fireViewerEvent(DiagramViewerListener::diagramChanged, new DiagramViewerEvent(this, old));
            executeWithDiagramShown(() -> {
                centerSelectionAfterChange();
                fireViewerEvent(DiagramViewerListener::diagramReady, new DiagramViewerEvent(this, old));
            });
        }
    }

    private void recenterLastNode(boolean selected, Collection<Figure> figs) {
        if (lastSelectedFigureAndScrollPosition != null) {
            Figure f = getDiagram().getFigureById(lastSelectedFigureAndScrollPosition.first().getId());
            if (f != null && f.isVisible() && figs.contains(f)) {
                Point dest = convertSceneToView(f.getPosition());
                dest.translate(lastSelectedFigureAndScrollPosition.second().x,
                        lastSelectedFigureAndScrollPosition.second().y);
                setScrollPosition(dest);
                return;
            }
        }
        if (!selected && previousViewportPosition != null) {
            setScrollPosition(previousViewportPosition);
        } else {
            centerFigures(figs, true);
        }
    }

    public boolean isRebuilding() {
        return rebuilding;
    }

    public void relayout(LayoutSettingBean layoutSetting) {
        model.setLayoutSetting(layoutSetting);
    }

    @Override
    public void setInteractionMode(InteractionMode mode) {
        panAction.setEnabled(mode == InteractionMode.PANNING);
        // When panAction is not enabled, it does not consume the event
        // and the selection action handles it instead

        fireViewerEvent(DiagramViewerListener::interactionChanged, new DiagramViewerEvent(this));
    }

    @Override
    public InteractionMode getInteractionMode() {
        return panAction.isEnabled() ? InteractionMode.PANNING : InteractionMode.SELECTION;
    }

    @Override
    public Lookup getLookup() {
        return lookup;
    }

    void initialize() {
        Figure f = getDiagram().getRootFigure();
        if (f != null) {
            setUndoRedoEnabled(false);
            gotoFigure(f);
            setUndoRedoEnabled(true);
        }
    }

    private Rectangle unionRectangle(final Collection<Figure> figures) {
        // PENDING: force wait on at least 1st layout to complete
        Rectangle overall = null;
        for (Figure f : figures) {
            Rectangle r = f.getBounds();
            if (r == null) {
                // invisible or not laid out figure
                continue;
            }
            overall = union(overall, r);
        }
        return overall;
    }

    /**
     * Executes a task after the layout has been done. May execute immediately,
     * if the layout was already completed, but may be scheduled for later
     * execution. The passed Runnable will execute in EDT.
     *
     * @param r task to execute
     */
    @Override
    public void executeWithDiagramShown(Runnable r) {
        updater.whenDiagramShown(r);
    }

    public void gotoFigures(final List<Figure> figures) {
        gotoFigures(figures, false);
    }

    private void gotoFigures(final Collection<Figure> figures, boolean ignoreIfVisible) {
        model.showFigures(figures);
        executeWithDiagramShown(() -> {
            Rectangle overall = unionRectangle(figures);
            if (overall != null) {
                centerRectangle(overall, ignoreIfVisible);
            }
        });
    }

    private Set<Source.Provider> idSetToObjectSet(Set<Object> ids) {
        return getDiagram().forSources(ids, Source.Provider.class);
    }

    public void gotoSelection(Set<Object> ids) {
        gotoNodes(ids, true);
    }

    public void gotoNodes(Set<Object> ids, boolean select) {
        Set<Integer> unhideIds = new HashSet<>();
        for (Object o : ids) {
            if (o instanceof InputNode) {
                unhideIds.add(((InputNode) o).getId());
            } else if (o instanceof Integer) {
                unhideIds.add((Integer) o);
            }
        }
        InputGraph g = model.getGraphToView();
        Rectangle overall = null;
        Set<Integer> hiddenNodes = new HashSet<>(model.getHiddenNodes());
        if (hiddenNodes.removeAll(unhideIds)) {
            model.showNot(hiddenNodes);
        }

        Set<Source.Provider> objects = idSetToObjectSet((Set) unhideIds);
        for (Source.Provider o : objects) {

            Widget w = getWidget(o);
            Rectangle r;
            Point p;

            if (w != null) {
                r = w.getBounds();
                p = w.convertLocalToScene(new Point(0, 0));
            } else {
                Figure f;
                if (o instanceof Figure) {
                    f = ((Figure) o);
                } else if (o instanceof Slot) {
                    f = ((Slot) o).getFigure();
                } else {
                    continue;
                }
                r = f.getBounds();
                p = r.getLocation();
            }

            if (r == null) {
                continue;
            }
            Rectangle r2 = new Rectangle(p.x, p.y, r.width, r.height);

            if (overall == null) {
                overall = r2;
            } else {
                overall = overall.union(r2);
            }
        }
        Collection c = getSelectedObjects();
        if (overall != null) {
            centerRectangle(overall, false);
        }
        if (c.equals(objects)) {
            return;
        }
        if (select) {
            List<InputNode> toSelect = unhideIds.stream().map(i -> g.getNode(i)).filter(Objects::nonNull).collect(Collectors.toList());
            model.setSelectedNodes(toSelect);
        }
    }

    public void selectNodes(Set<InputNode> nodes) {
        if (nodes.isEmpty()) {
            return;
        }
        Set<Integer> unhideIds = new HashSet<>();
        for (InputNode n : nodes) {
            unhideIds.add(n.getId());
        }
        Rectangle overall = null;
        Rectangle overallFigures = null;
        Set<Integer> hiddenNodes = new HashSet<>(model.getHiddenNodes());
        if (hiddenNodes.removeAll(unhideIds)) {
            model.showNot(hiddenNodes);
        }

        Set<? extends Source.Provider> objects = idSetToObjectSet((Set) unhideIds);
        Set<Figure> figuresToSelect = new HashSet<>();

        for (Source.Provider o : objects) {
            if (o instanceof Figure) {
                figuresToSelect.add((Figure) o);
            }
            Widget w = getWidget(o);
            if (w != null) {
                Rectangle r = w.getBounds();
                if (r == null) {
                    continue;
                }
                Point p = w.convertLocalToScene(new Point(0, 0));
                overall = union(overall, new Rectangle(p.x, p.y, r.width, r.height));
                if (o instanceof Figure) {
                    overallFigures = union(overallFigures, new Rectangle(p.x, p.y, r.width, r.height));
                }
            }
        }
        if (!figuresToSelect.isEmpty()) {
            overall = overallFigures;
        }
        if (overall != null) {
            centerRectangle(overall, true);
        }
        setSelection(figuresToSelect);
    }

    private Rectangle union(Rectangle overall, Rectangle r2) {
        if (overall == null) {
            overall = r2;
        } else {
            overall = overall.union(r2);
        }
        return overall;
    }

    private Point calcCenter(Rectangle r) {
        final JViewport viewport = getScrollPane().getViewport();
        final Rectangle rect = viewport.getViewRect();

        Point center = new Point((int) r.getCenterX(), (int) r.getCenterY());

        center.x -= rect.width / 2;
        center.y -= rect.height / 2;

        // Ensure to be within area
        Dimension viewSize = viewport.getViewSize();
        center.x = Math.min(viewSize.width - rect.width, Math.max(0, center.x));
        center.y = Math.min(viewSize.height - rect.height, Math.max(0, center.y));

        return center;
    }

    private void centerRectangle(Rectangle r, boolean ignoreIfVisible) {
        final JViewport viewport = getScrollPane().getViewport();
        final Rectangle rect = viewport.getViewRect();
        if (rect.isEmpty()) {
            return;
        }

        Rectangle r2 = convertSceneToView(r);

        if (ignoreIfVisible && rect.contains(r2)) {
            return;
        }

        double factor = Math.max((double) r2.width / rect.width, (double) r2.height / rect.height);
        if (factor >= 1.0) {
            setZoomFactor(getZoomFactor() / factor);
        } else {
            setZoomFactor(1.0);
            validate();
        }
        r2 = convertSceneToView(r);
        // GR-18014: we deliberately changing the viewport according to instructions, so reset
        // the 'last known' cache.
        lastSelectedFigureAndScrollPosition = null;
        viewport.setViewPosition(calcCenter(r2));
    }

    @Override
    public void setSelection(Collection<Figure> list) {
        model.setSelectedFigures(list);
    }

    private void setSelected(Collection<Figure> list) {
        if (getSelectedObjects().equals(list)) {
            return;
        }
        SelMode m = forceSelectionMode.get();
        forceSelectionMode.set(SelMode.API);
        try {
            super.setSelectedObjects(new HashSet<>(list));
        } finally {
            forceSelectionMode.set(m);
        }
    }

    @Override
    public GraphContainer getContainer() {
        return model.getContainer();
    }

    @Override
    public List<Figure> getSelection() {
        List<Figure> al = new ArrayList<>((Collection) getSelectedObjects());
        return al;
    }

    private UndoRedo.Manager getUndoRedoManager() {
        if (undoRedoManager == null) {
            undoRedoManager = new UndoRedo.Manager();
            undoRedoManager.setLimit(UNDOREDO_LIMIT);
        }

        return undoRedoManager;
    }

    @Override
    public UndoRedo getUndoRedo() {
        return getUndoRedoManager();
    }

    private boolean isVisible(Figure f) {
        Set<Integer> hiddenNodes = model.getHiddenNodes();
        for (InputNode n : f.getSource().getSourceNodes()) {
            if (hiddenNodes.contains(n.getId())) {
                return false;
            }
        }
        return true;
    }

    public static boolean doesIntersect(Collection<?> s1, Set<?> s2) {
        for (Object o : s1) {
            if (s2.contains(o)) {
                return true;
            }
        }

        return false;
    }

    public static boolean doesIntersect(Set<?> s1, Set<?> s2) {
        if (s1.size() > s2.size()) {
            Set<?> tmp = s1;
            s1 = s2;
            s2 = tmp;
        }
        return doesIntersect((Collection) s1, s2);
    }

    void componentHidden() {
        selectionCoordinator.getHighlightedChangedEvent().removeListener(highlightedCoordinatorListener);
        selectionCoordinator.getSelectedChangedEvent().removeListener(selectedCoordinatorListener);
    }

    void componentShowing() {
        selectionCoordinator.getHighlightedChangedEvent().addListener(highlightedCoordinatorListener);
        selectionCoordinator.getSelectedChangedEvent().addListener(selectedCoordinatorListener);
    }

    private void showFigure(Figure f) {
        HashSet<Integer> newHiddenNodes = new HashSet<>(model.getHiddenNodes());
        newHiddenNodes.removeAll(f.getSource().getSourceNodeIds());
        this.model.setHiddenNodes(newHiddenNodes);
    }

    public void show(final Figure f) {
        showFigure(f);
    }

    public void setSelectedObjects(Object... args) {
        Set<Object> set = new HashSet<>();
        set.addAll(Arrays.asList(args));
        super.setSelectedObjects(set);
    }

    public void gotoFigure(final Figure f) {
        if (!isVisible(f)) {
            showFigure(f);
        }
        executeWithDiagramShown(() -> {
            Rectangle r = f.getBounds();
            centerRectangle(r, false);
            setSelection(List.of(f));
        });
    }

    public JPopupMenu createPopupMenu() {
        return new JPopupMenu();
    }

    private static class DiagramUndoRedo extends AbstractUndoableEdit {

        private final DiagramViewModel oldModel;
        private final DiagramViewModel newModel;
        private final DiagramScene scene;

        public DiagramUndoRedo(DiagramScene scene, DiagramViewModel oldModel, DiagramViewModel newModel) {
            assert oldModel != null;
            assert newModel != null;
            this.oldModel = oldModel;
            this.newModel = newModel;
            this.scene = scene;
        }

        @Override
        public void redo() throws CannotRedoException {
            super.redo();
            boolean b = scene.getUndoRedoEnabled();
            scene.setUndoRedoEnabled(false);
            scene.selectedCoordinatorListener.setEnabled(false);
            try {
                scene.model.setData(newModel);
                updateSelection(newModel.getSelectedNodes());
            } finally {
                scene.selectedCoordinatorListener.setEnabled(true);
                scene.setUndoRedoEnabled(b);
            }
        }

        private void updateSelection(Collection<InputNode> selectedNodes) {
            scene.selectionCoordinator.setSelectedObjects(
                    selectedNodes.stream().map(InputNode::getId).collect(Collectors.toSet())
            );
        }

        @Override
        public void undo() throws CannotUndoException {
            super.undo();
            boolean b = scene.getUndoRedoEnabled();
            scene.setUndoRedoEnabled(false);
            scene.selectedCoordinatorListener.setEnabled(false);
            try {
                scene.model.setData(oldModel);
                updateSelection(oldModel.getSelectedNodes());
            } finally {
                scene.selectedCoordinatorListener.setEnabled(true);
                scene.setUndoRedoEnabled(b);
            }
        }
    }

    private boolean undoRedoEnabled = true;

    public void setUndoRedoEnabled(boolean b) {
        this.undoRedoEnabled = b;
    }

    public boolean getUndoRedoEnabled() {
        return undoRedoEnabled;
    }

    private final ChangedListener<DiagramViewModel> diagramChange = new ChangedListener<>() {
        @Override
        public void changed(DiagramViewModel source) {
            assert source == model : "Receive only changed event from current model!";
            assert source != null;
            update();
        }
    };

    private Pair<Figure, Point> lastSelectedFigureAndScrollPosition;

    private final PropertyChangeListener selectionChange = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            LOG.log(Level.FINE, "Diagram selected figures changed.");
            setSelected(model.getSelectedFigures());

            Set<InputNode> olds = (Set<InputNode>) evt.getOldValue();
            if (olds != null) {
                Set<InputNode> news = new HashSet<>((Set<InputNode>) evt.getNewValue());
                news.removeAll(olds);
                if (!news.isEmpty()) {
                    Figure f = getDiagram().getFigureById(news.iterator().next().getId());
                    if (f != null) {
                        Point p = convertSceneToView(f.getPosition());
                        Point scroll = getScrollPosition();
                        lastSelectedFigureAndScrollPosition = Pair.of(f, new Point(scroll.x - p.x, scroll.y - p.y));
                        return;
                    }
                }
                lastSelectedFigureAndScrollPosition = null;
            }
        }
    };

    private final PropertyChangeListener hiddenChange = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            assert evt.getSource() == model;
            LOG.log(Level.FINE, "Diagram hidden figures changed.");
            selectionCoordinator.removeAllSelected((Set) evt.getNewValue());
        }
    };

    private final PropertyChangeListener containerChanged = (PropertyChangeEvent evt) -> {
        assert evt.getSource() == model;
        LOG.log(Level.FINE, "Graph container changed.");
        if (getTopComponent().isShowing()) {
            componentHidden();//detach old listeners
            selectionCoordinator = SelectionCoordinator.getInstanceForContainer(model.getContainer());
            componentShowing();//atach new listeners
        } else {
            selectionCoordinator = SelectionCoordinator.getInstanceForContainer(model.getContainer());
        }
    };

    private void addUndo() {
        DiagramViewModel newModelCopy = model.copy();
        LOG.log(Level.FINE, "Possible undo operation, enabled = {2}, new model: {0}, old model copy: {1}", new Object[]{
                newModelCopy, modelCopy, undoRedoEnabled
        });
        if (undoRedoEnabled && modelCopy != null) {
            LOG.log(Level.FINER, "Undo operation created.");
            this.getUndoRedoManager().undoableEditHappened(new UndoableEditEvent(this, new DiagramUndoRedo(this, modelCopy, newModelCopy)));
        }
        this.modelCopy = newModelCopy;
    }

    /**
     * Overlay widget, which blocks access to the graph widgets during
     * re-layouting and displays a progress message (i.e. please wait). The
     * label widget is repositioned as the visible rectangle changes.
     */
    @NbBundle.Messages({
            "LBL_PleaseWaitPreparingGraph=Please wait, preparing graph ..."
    })
    private static class Overlay extends LayerWidget {
        private final Widget waitWidget;
        private boolean blockScene = true;
        private final Rectangle oldviewRect = new Rectangle(0, 0, 0, 0);

        public Overlay(DiagramScene scene) {
            super(scene);
            setLayout(LayoutFactory.createAbsoluteLayout());
            JLabel label = new JLabel(Bundle.LBL_PleaseWaitPreparingGraph(), JLabel.TRAILING);
            ImageIcon icon = new ImageIcon(getClass().getClassLoader().getResource("org/graalvm/visualizer/view/images/wait16.gif")); // NOI18N
            label.setIcon(icon);
            icon.setImageObserver(label);
            waitWidget = new ComponentWidget(scene, label);
            addChild(waitWidget);
            setOpaque(false);

            scene.getScrollPane().getViewport().addChangeListener((e) -> {
                if (blockScene) {
                    Rectangle newViewRect = scene.getScrollPane().getViewport().getViewRect();
                    if (!oldviewRect.equals(newViewRect)) {
                        LOG.log(Level.FINER, "Viewport change from {1} to {0}, moving label", new Object[]{newViewRect.getBounds(), oldviewRect.getBounds()});
                        moveLabel(newViewRect);
                    } else {
                        LOG.log(Level.FINER, "Viewport dont change from {1} to {0}", new Object[]{newViewRect.getBounds(), oldviewRect.getBounds()});
                    }
                }
            });
        }

        public boolean isBlockScene() {
            return blockScene;
        }

        private void moveLabel(Rectangle newViewRect) {
            if (!blockScene) {
                return;
            }
            Point r = calc(newViewRect);
            if (r != null) {
                waitWidget.setPreferredLocation(r);
            }
        }

        public void setBlockScene(boolean blockScene) {
            assert SwingUtilities.isEventDispatchThread();
            if (blockScene == this.blockScene) {
                return;
            }
            this.blockScene = blockScene;
            if (blockScene) {
                addChild(waitWidget);
                Rectangle newViewRect = ((DiagramScene) getScene()).getScrollPane().getViewport().getViewRect();
                if (!oldviewRect.equals(newViewRect)) {
                    moveLabel(newViewRect);
                }
            } else {
                removeChild(waitWidget);
            }
        }

        private Point calc(Rectangle newViewRect) {
            if (newViewRect.isEmpty()) {
                LOG.log(Level.FINER, "view rect:{0} is empty.", newViewRect.getBounds());
                return null;
            }
            if (oldviewRect.getSize().equals(newViewRect.getSize())) {
                return diffPosition(newViewRect);
            }
            oldviewRect.setBounds(newViewRect);

            Rectangle r = getScene().convertViewToScene(newViewRect);
            LOG.log(Level.FINER, "view rect:{0}, converted:{1}", new Object[]{newViewRect.getBounds(), r.getBounds()});
            Rectangle b = waitWidget.getPreferredBounds();
            assert b != null;

            // place at the middle
            Point out = new Point(r.x + (r.width - b.width) / 2, r.y + (r.height - b.height) / 2);
            Point cur = b.getLocation();
            if (cur.equals(out)) {
                LOG.log(Level.FINER, "Label is already at {0}", cur);
                return null;
            }
            if (LOG.isLoggable(Level.FINER)) {
                LOG.log(Level.FINER, "Label moves from {0} to {1}", new Object[]{cur, out});
            }
            return out;
        }

        private Point diffPosition(Rectangle newViewRect) {
            Point cur = waitWidget.getPreferredLocation();
            assert cur != null;
            Point out = cur.getLocation();
            out.translate(newViewRect.x - oldviewRect.x, newViewRect.y - oldviewRect.y);

            oldviewRect.setBounds(newViewRect);
            if (LOG.isLoggable(Level.FINER)) {
                LOG.log(Level.FINER, "Label moves from {0} to {1}", new Object[]{cur, out});
            }
            return out;
        }

        @Override
        public boolean isHitAt(Point localLocation) {
            return blockScene;
        }
    }

    /**
     * Starts an update; blocks the scene with an opaque widget
     */
    void prepareUpdate() {
        if (!overlayLayer.isBlockScene()) {
            overlayLayer.setBlockScene(true);
            cleanUp();
        } else {
            LOG.log(Level.FINE, "Scene is already prepared.");
        }
    }

    /**
     * Finishes an update, enables all the layers.
     */
    void finishUpdate() {
        assert !model.isStubDiagram(currentDiagram);
        if (overlayLayer.isBlockScene()) {
            // this is necessary to flag immediately
            rebuilding = false;
            Runnable r = () -> {
                overlayLayer.setBlockScene(false);
                centeringLayer.addChild(0, blockLayer);
                centeringLayer.addChild(1, connectionLayer);
                centeringLayer.addChild(2, mainLayer);
                centeringLayer.addChild(3, selectLayer);
                validate();
            };
            if (SwingUtilities.isEventDispatchThread()) {
                r.run();
            } else {
                SwingUtilities.invokeLater(r);
            }
        } else {
            LOG.log(Level.FINE, "Scene is already finished.");
        }
    }

    void addConnection(Widget w) {
        connectionLayer.addChild(w);
    }

    WidgetAction getHoverAction() {
        return hoverAction;
    }

    private static void maybeRemoveChild(Widget from, Widget child, boolean erase) {
        if (child.getParentWidget() != null) {
            from.removeChild(child);
        }
        if (erase) {
            child.removeChildren();
        }
    }

    void cleanupConnections() {
        maybeRemoveChild(centeringLayer, connectionLayer, true);
        // common for both hidden nodes change and new diagram.
        rebuilding = true;
    }

    void cleanUp() {
        maybeRemoveChild(centeringLayer, mainLayer, true);
        maybeRemoveChild(centeringLayer, blockLayer, true);
        maybeRemoveChild(centeringLayer, selectLayer, true);
        cleanupConnections();

        Collection obs = new ArrayList<>(getObjects());

        for (Object o : obs) {
            removeObject(o);
        }
        // shrink the scene size to 0
        setBottomRightLocation(new Point(0, 0));
        // reset viewport, so the "wait" widget does not define the scene's preferred size
        setScrollPosition(new Point(0, 0));
        validate();
    }

    void setBottomRightLocation(Point pt) {
        bottomRight.setPreferredLocation(pt);
    }

    public Dimension getSceneSize() {
        Point pt = bottomRight.getPreferredLocation();
        return new Dimension(pt.x, pt.y);
    }

    private void centerSelectionAfterChange() {
        Diagram dg = getDiagram();
        if (model.isStubDiagram(dg)) {
            return;
        }
        assert SwingUtilities.isEventDispatchThread();
        Set<Figure> figs = new HashSet<>();
        Set<Integer> s = (Set) selectionCoordinator.getSelectedObjects();
        for (int i : s) {
            Optional<Figure> f = dg.getFigure(i);
            if (f.isPresent()) {
                figs.add(f.get());
            }
        }
        // if selection does not contain any figures, i.e. the selection IDs are not present
        // in the graph, use at least SOME figures
        boolean doSelect = !figs.isEmpty();
        if (figs.isEmpty()) {
            List<Integer> ids = new ArrayList<>(dg.getGraph().getNodeIds());
            Collections.sort(ids);
            for (int i : ids) {
                Optional<Figure> f = dg.getFigure(i);
                if (f.isPresent()) {
                    Figure fig = f.get();
                    if (fig.isVisible() && !fig.isBoundary()) {
                        figs.add(fig);
                        break;
                    }
                }
            }
        }
        if (!figs.isEmpty()) {
            if (doSelect) {
                setSelectedObjects(figs);
            }
            recenterLastNode(doSelect, new ArrayList<>(figs));
        } else {
            LOG.log(Level.FINE, "There are no figures to center on.");
        }
    }

    /**
     * Creates or finds the block widget in the diagram.
     *
     * @param d  the target diagram
     * @param bn the block to visualize
     * @return the widget.
     */
    BlockWidget createBlockWidget(InputBlock bn) {
        BlockWidget w = getWidget(bn);
        if (w != null) {
            if (w.getParentWidget() == null) {
                mainLayer.addChild(w);
            }
            return w;
        }
        w = new BlockWidget(this, bn);
        w.setVisible(false);
        this.addObject(bn, w);
        blockLayer.addChild(w);
        return w;
    }

    /**
     * Creates or finds widget for Figure in the target diagram.
     *
     * @param d the target diagram
     * @param f the figure to visualize
     * @return the widget
     */
    FigureWidget createFigureWidget(Figure f) {
        FigureWidget w = getWidget(f);
        if (w != null) {
            if (w.getParentWidget() == null) {
                mainLayer.addChild(w);
            }
            return w;
        }
        if (!f.isVisible()) {
            // do not create widgets for invisible figures
            return null;
        }
        w = new FigureWidget(f, hoverAction, selectAction, this, mainLayer);
        w.getActions().addAction(ActionFactory.createPopupMenuAction(w));
        w.getActions().addAction(selectAction);
        w.getActions().addAction(hoverAction);
        w.setVisible(false);

        this.addObject(f, w);

        for (InputSlot s : f.getInputSlots()) {
            SlotWidget sw = new InputSlotWidget(s, this, w, w);
            addObject(s, sw);
            if (s.getSource().getSourceNodes().size() > 0 || s.getConnections().isEmpty()) {
                sw.getActions().addAction(new DoubleClickAction(sw));
                sw.getActions().addAction(hoverAction);
                sw.getActions().addAction(selectAction);
            }
        }

        for (OutputSlot s : f.getOutputSlots()) {
            SlotWidget sw = new OutputSlotWidget(s, this, w, w);
            addObject(s, sw);
            if (s.getSource().getSourceNodes().size() > 0 || s.getConnections().isEmpty()) {
                sw.getActions().addAction(new DoubleClickAction(sw));
                sw.getActions().addAction(hoverAction);
                sw.getActions().addAction(selectAction);
            }
        }
        return w;
    }

    /**
     * Registers a new viewport for the scene. The scene will optimize widgets
     * so that widgets for all viewports will be materialized. The viewport will
     * receive {@link SceneViewport#sceneContentsUpdated} when widgets are
     * updated.
     * <p/>
     * Viewport can inform the Scene, by firing {@link ChangeEvent} that its
     * bounds have changed; the scene will then refresh/recompute its widgets.
     *
     * @param v the new viewport.
     */
    public void addSceneViewport(SceneViewport v) {
        synchronized (this) {
            for (Reference<SceneViewport> rv : viewports) {
                if (rv.get() == v) {
                    return;
                }
            }
            VL ref = new VL(v);
            viewports.add(ref);
            v.addChangeListener(ref);
        }
        updater.refreshView(false);
    }

    /**
     * Computes bounding rectangle that covers all known viewports. The bounding
     * rectangle always contains scene's own component surface, plus covers all
     * known viewports. The resulting rectangle is in window (Swing) coordinates
     *
     * @return bounding rectangle for all viewports, in window coords
     */
    public synchronized Rectangle getBoundingViewportsRect() {
        assert SwingUtilities.isEventDispatchThread();

        JViewport viewport = getScrollPane().getViewport();
        Rectangle r = viewport.getViewRect();
        for (VL vl : viewports) {
            SceneViewport v = vl.get();
            if (v != null) {
                r.add(v.getViewportRect());
            }
        }
        return r;
    }

    /**
     * Returns rectangle of the visible portion of the scene, in scene
     * coordinates. Unless a satellite view is active, it returns the rectangle
     * of the Scene's viewport, scaled by the scene's zoom factor (using
     * {@link #convertViewToScene(java.awt.Rectangle)}. If a Satellite view is
     * active (or other viewport is registered), the returned rectangle will be
     * the bounding rectangle of all the viewports.
     *
     * @return visible rectangle of the scene, in scene coordinates.
     */
    public synchronized Rectangle getVisibleSceneRect() {
        assert SwingUtilities.isEventDispatchThread();

        JViewport viewport = getScrollPane().getViewport();
        Rectangle r = convertViewToScene(viewport.getViewRect());
        for (VL vl : viewports) {
            SceneViewport v = vl.get();
            if (v != null) {
                r.add(v.getSceneViewRect());
            }
        }
        return r;
    }

    void fireSceneUpdated(boolean finished) {
        SceneViewport[] ll;
        int idx = 0;
        synchronized (this) {
            if (viewports.isEmpty()) {
                return;
            }
            ll = new SceneViewport[viewports.size()];
            for (VL vl : viewports) {
                SceneViewport v = vl.get();
                if (v != null) {
                    ll[idx++] = v;
                }
            }
        }
        if (idx == 0) {
            return;
        }
        for (SceneViewport vp : ll) {
            if (ll == null) {
                break;
            }
            vp.sceneContentsUpdated(finished, updater.getValidatedRectangle());
        }
    }

    /**
     * Removes a registered viewport. If a viewport is actually removed, a
     * clean-up task is scheduled to reclaim possible excess widgets
     *
     * @param v viewport to remove
     */
    public synchronized void removeSceneViewport(SceneViewport v) {
        SceneViewport removed = null;
        for (Iterator<VL> it = viewports.iterator(); it.hasNext(); ) {
            VL vl = it.next();
            if (vl.get() == v) {
                it.remove();
                v.removeChangeListener(vl);
                removed = v;
                break;
            }
        }
        if (removed == null) {
            return;
        }
        updater.cleanViewport();

    }

    class VL extends WeakReference<SceneViewport> implements ChangeListener, Runnable {
        public VL(SceneViewport viewport) {
            super(viewport, BaseUtilities.activeReferenceQueue());
        }

        @Override
        public void stateChanged(ChangeEvent e) {
            updater.refreshView(false);
        }

        @Override
        public void run() {
            synchronized (DiagramScene.this) {
                viewports.remove(this);
            }
        }
    }

    private Rectangle temporaryViewRect;

    Rectangle getSceneViewportSize() {
        Rectangle r;

        if (temporaryViewRect != null) {
            r = convertViewToScene(temporaryViewRect);
        } else {
            r = convertViewToScene(getScrollPane().getViewport().getViewRect());
        }
        return r;
    }

    public void paintOnViewport(SceneViewport v, Graphics2D gr) {
        temporaryViewRect = v.getViewportRect();
        try {
            paint(gr);
        } finally {
            temporaryViewRect = null;
        }
    }

    /**
     * Widgets, which are being animated. If such widget is moved again, before
     * the animation stops, the animator must be informed
     */
    private final Set<Widget> animatingWidgets = new HashSet<>();

    void animationStopped(AnimatorEvent ev) {
        animatingWidgets.clear();
    }

    void moveWidget(Widget w, Rectangle rect) {
        assert SwingUtilities.isEventDispatchThread();
        if (animatingWidgets.contains(w)) {
            getSceneAnimator().animatePreferredBounds(w, rect);
        } else {
            w.setPreferredBounds(rect);
        }
    }

    void moveWidget(Widget w, Point targetLocation) {
        assert SwingUtilities.isEventDispatchThread();
        if (animatingWidgets.contains(w)) {
            getSceneAnimator().animatePreferredLocation(w, targetLocation);
        } else {
            w.setPreferredLocation(targetLocation);
        }
    }

    void animateMoveWidget(Widget w, Point targetLocation) {
        assert SwingUtilities.isEventDispatchThread();
        animatingWidgets.add(w);
        getSceneAnimator().animatePreferredLocation(w, targetLocation);
    }

    void animateBounds(Widget w, Rectangle rect) {
        assert SwingUtilities.isEventDispatchThread();
        animatingWidgets.add(w);
        getSceneAnimator().animatePreferredBounds(w, rect);
    }

    private final List<DiagramViewerListener> viewerListeners = new ArrayList<>();

    @Override
    public void addDiagramViewerListener(DiagramViewerListener l) {
        synchronized (viewerListeners) {
            viewerListeners.add(l);
        }
    }

    @Override
    public void removeDiagramViewerListener(DiagramViewerListener l) {
        synchronized (viewerListeners) {
            viewerListeners.remove(l);
        }
    }

    private void fireViewerEvent(BiConsumer<DiagramViewerListener, DiagramViewerEvent> callback, DiagramViewerEvent ev) {
        DiagramViewerListener[] ll;
        synchronized (viewerListeners) {
            if (viewerListeners.isEmpty()) {
                return;
            }
            ll = viewerListeners.toArray(new DiagramViewerListener[viewerListeners.size()]);
        }
        SwingUtilities.invokeLater(() -> {
            for (DiagramViewerListener l : ll) {
                callback.accept(l, ev);
            }
        });
    }

    @Override
    public GraphSelections getSelections() {
        return this.topComponent.getLookup().lookup(GraphSelections.class);
    }

    @Override
    public Set<InputNode> nodesForFigure(Figure f) {
        assert SwingUtilities.isEventDispatchThread();
        return new HashSet<>(f.getSource().getSourceNodes());
    }

    @Override
    public void requestActive(boolean toFront, boolean attention) {
        if (attention) {
            topComponent.requestAttention(true);
        } else if (toFront) {
            topComponent.requestActive();
        } else {
            topComponent.requestVisible();
        }
    }

    @Override
    public Collection<Figure> figuresForNodes(Collection<InputNode> nodes) {
        assert SwingUtilities.isEventDispatchThread();
        Diagram d = getDiagram();
        Set<Figure> figuresToSelect = new HashSet<>();

        for (InputNode n : nodes) {
            Collection<Source.Provider> provs = d.forSource(n.getId());
            for (Source.Provider p : provs) {
                if (p instanceof Figure) {
                    figuresToSelect.add((Figure) p);
                } else if (p instanceof Slot) {
                    figuresToSelect.add(((Slot) p).getFigure());
                }
            }
        }
        return figuresToSelect;
    }

    @Override
    public InputGraph getGraph() {
        return getModel().getGraphToView();
    }

    @Override
    public void setSelectedNodes(Set<InputNode> nodes) {
        getSelections().setSelectedNodes(nodes);
    }

    @Override
    public Iterable<InputGraph> searchForward() {
        return model.getGraphsForward();
    }

    @Override
    public Iterable<InputGraph> searchBackward() {
        return model.getGraphsBackward();
    }

    private void justifyCenteringLayer() {
        Point log = centeringLayer.getLocation();
        Point max = bottomRight.getLocation();

        int maxX = max.x;
        int maxY = max.y;

        int offx = 0;
        int offy = 0;
        int curWidth = maxX + 2 * BORDER_SIZE;
        int curHeight = maxY + 2 * BORDER_SIZE;

        Rectangle bounds = getScrollPane().getBounds();
        bounds.width /= getZoomFactor();
        bounds.height /= getZoomFactor();
        if (curWidth < bounds.width) {
            offx = (bounds.width - curWidth) / 2;
        }

        if (curHeight < bounds.height) {
            offy = (bounds.height - curHeight) / 2;
        }

        LOG.log(Level.FINE, "Center offset: {0}, {1}", new Object[]{offx, offy});
        Point pt = new Point(offx, offy);
        if (!log.equals(pt)) {
            centeringLayer.setPreferredLocation(pt);
        }
    }

    /**
     * Reacts on size changes to the viewport and rejustifies the
     * centering layer.
     */
    class ViewportCenteringBridge implements ChangeListener {
        private final JViewport viewport;
        private Dimension savedViewportSize;

        public ViewportCenteringBridge(JViewport viewport) {
            this.viewport = viewport;
            savedViewportSize = viewport.getVisibleRect().getSize();
        }

        @Override
        public void stateChanged(ChangeEvent e) {
            if (savedViewportSize.equals(viewport.getVisibleRect().getSize())) {
                updater.whenDiagramShown(this::run);
            }
        }

        public void run() {
            savedViewportSize = viewport.getVisibleRect().getSize();
            justifyCenteringLayer();
        }
    }

    class CenteringLayout implements Layout {
        private final Layout delegate;

        public CenteringLayout(Layout delegate) {
            this.delegate = delegate;
        }

        @Override
        public void layout(Widget widget) {
            delegate.layout(widget);
        }

        @Override
        public boolean requiresJustification(Widget widget) {
            if (widget == centeringLayer) {
                return true;
            } else {
                return delegate.requiresJustification(widget);
            }
        }

        @Override
        public void justify(Widget widget) {
            if (widget != centeringLayer) {
                delegate.justify(widget);
            } else {
                justifyCenteringLayer();
            }
        }
    }
}
