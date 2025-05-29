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

import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.PROPNAME_DUPLICATE;
import static org.graalvm.visualizer.settings.TestUtils.assertNot;
import static org.junit.Assert.*;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.swing.*;

import org.graalvm.visualizer.data.DataTestUtil;
import org.graalvm.visualizer.difference.Difference;
import org.graalvm.visualizer.filter.ColorFilter;
import org.graalvm.visualizer.filter.FilterChain;
import org.graalvm.visualizer.filter.FilterSequence;
import org.graalvm.visualizer.graph.Diagram;
import org.graalvm.visualizer.graph.Figure;
import org.graalvm.visualizer.graph.GraphTestUtil;
import org.graalvm.visualizer.settings.layout.LayoutSettings;
import org.graalvm.visualizer.settings.layout.LayoutSettings.LayoutSettingBean;
import org.graalvm.visualizer.util.RangeSliderModel;
import org.graalvm.visualizer.view.api.TimelineModel;
import org.graalvm.visualizer.view.impl.Colorizer;
import org.graalvm.visualizer.view.impl.DiagramCache;
import org.graalvm.visualizer.view.impl.TimelineModelImpl;
import org.junit.*;
import org.junit.rules.TestName;

import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputEdge;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;
import junit.framework.AssertionFailedError;

/**
 * @author Ondřej Douda <ondrej.douda@oracle.com>
 */
public class DiagramViewModelTest {
    private static final List<Color> initColors = Arrays.asList(Color.BLUE, Color.RED, Color.CYAN);
    private static final int initFirstPos = 1;
    private static final int initSecondPos = 2;
    private static final LayoutSettingBean layoutSetting = LayoutSettings.getBean();

    /**
     * 1
     * / \
     * 2   3
     * \  |  5
     * \ | /
     * \|/
     * 4
     */
    private static InputGraph referenceGraph;
    private static InputGraph duplicateGraph;
    private static InputGraph emptyGraph;

    private static final InputNode N1 = new InputNode(1);
    private static final InputNode N2 = new InputNode(2);
    private static final InputNode N3 = new InputNode(3);
    private static final InputNode N4 = new InputNode(4);
    private static final InputNode N5 = new InputNode(5);

    private static final InputEdge E12 = new InputEdge((char) 0, 1, 2);
    private static final InputEdge E13 = new InputEdge((char) 0, 1, 3);
    private static final InputEdge E24 = new InputEdge((char) 0, 2, 4);
    private static final InputEdge E34 = new InputEdge((char) 0, 3, 4);
    private static final InputEdge E54 = new InputEdge((char) 0, 5, 4);

    private static Group initGroup;

    private final FilterChain initFilterChain = new FilterChain();

    private final DiagramViewModel instance;
    private final RangeSliderModel slider;

    private int changedFires;
    private int colorFires;
    private int diagramFires;
    private int hiddenNodesFires;
    private int viewChangedFires;
    private int viewPropertiesFires;

    @Rule
    public TestName name = new TestName();

    private final TimelineModel timeline;

    public DiagramViewModelTest() throws Exception {
        DiagramCache.flush();
        timeline = new TimelineModelImpl(initGroup, new TestGraphClassifier(), TestGraphClassifier.TEST_TYPE);
        slider = timeline.getPrimaryRange();
        instance = new DiagramViewModel(timeline, initFilterChain, layoutSetting);

        // let the timeline refresh, so that we can set colors and positions manually
        // a pending refresh could overwrite this initial setup.
        CountDownLatch stable = new CountDownLatch(1);
        timeline.whenStable().execute(() -> stable.countDown());
        stable.await();
        slider.setColors(initColors);
        slider.setPositions(initFirstPos, initSecondPos);
    }

    private void doSetUp() throws Exception {
        instance.setShowBlocks(false);
        instance.setShowNodeHull(false);
        instance.setHideDuplicates(false);

        // wait for the timeline to load the contents and update
        letItSettle();


        // let the slider events to settle.
        letItSettle();
        resetFires();

        instance.getChangedEvent().addListener(
                source -> changedFires++
        );
        instance.getColorChangedEvent().addListener(
                source -> colorFires++
        );
        instance.getDiagramChangedEvent().addListener(
                source -> diagramFires++
        );
        instance.addPropertyChangeListener(DiagramViewModel.PROP_HIDDEN_NODES,
                source -> hiddenNodesFires++
        );
        instance.addPropertyChangeListener(DiagramViewModel.PROP_SELECTED_NODES,
                source -> viewChangedFires++
        );
        instance.addPropertyChangeListener(DiagramViewModel.PROP_SHOW_BLOCKS,
                source -> viewPropertiesFires++
        );
        instance.addPropertyChangeListener(DiagramViewModel.PROP_SHOW_NODE_HULL,
                source -> viewPropertiesFires++
        );
    }

    @BeforeClass
    public static void setUpClass() {
        initGroup = new Group(null);

        emptyGraph = InputGraph.createTestGraph("emptyGraph");
        initGroup.addElement(emptyGraph);
        emptyGraph.ensureNodesInBlocks();

        referenceGraph = InputGraph.createTestGraph("referenceGraph");
        initGroup.addElement(referenceGraph);
        referenceGraph.addNode(N1);
        referenceGraph.addNode(N2);
        referenceGraph.addNode(N3);
        referenceGraph.addNode(N4);
        referenceGraph.addNode(N5);

        referenceGraph.addEdge(E12);
        referenceGraph.addEdge(E13);
        referenceGraph.addEdge(E24);
        referenceGraph.addEdge(E34);
        referenceGraph.addEdge(E54);

        referenceGraph.ensureNodesInBlocks();

        duplicateGraph = InputGraph.createTestGraph("duplicateGraph");
        initGroup.addElement(duplicateGraph);
        duplicateGraph.addNode(N1);
        duplicateGraph.addNode(N2);
        duplicateGraph.addNode(N3);
        duplicateGraph.addNode(N4);
        duplicateGraph.addNode(N5);

        duplicateGraph.addEdge(E12);
        duplicateGraph.addEdge(E13);
        duplicateGraph.addEdge(E24);
        duplicateGraph.addEdge(E34);
        duplicateGraph.addEdge(E54);

        duplicateGraph.ensureNodesInBlocks();

        duplicateGraph.getProperties().setProperty(PROPNAME_DUPLICATE, "true");
        for (InputGraph g : initGroup.getGraphs()) {
            g.setGraphType(TestGraphClassifier.TEST_TYPE);
        }
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() throws Exception {
        doSetUp();
        testEventFires();
        resetFires();
    }

    @After
    public void tearDown() {
        letItSettle();//let finalize diagram
        instance.setShowBlocks(!instance.getShowBlocks());//get rid of diagram
        DiagramCache.flush();
        System.gc();//GC diagram cache
    }

    private void letItSettle() {
        // wait for the timeline to load the content (lazy-loaded initially)
        CountDownLatch w = new CountDownLatch(1);
        try {
            timeline.whenStable().execute(w::countDown);
            w.await(500, TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            Error ase = new AssertionFailedError(ex.getMessage());
            ase.initCause(ex);
            throw ase;
        }
        // change events are fired in EDT. Since there may be some events which do the firing in thread's queue, enqueue our own job that will
        // execute sequentially after all the firing.
        try {
            instance.withDiagramToView(null).get();//await for finished diagram
            SwingUtilities.invokeAndWait(() -> {
            });//await for events to end
        } catch (Exception ex) {
            Error ase = new AssertionFailedError(ex.getMessage());
            ase.initCause(ex);
            throw ase;
        }
    }

    private static final String colorName = "color_";
    private static final String diagramName = "diagram_";
    private static final String hiddenNodesName = "hiddenNodes_";
    private static final String viewChangedName = "viewChanged_";
    private static final String viewPropertiesName = "viewProperties_";

    private void assertFired(int changedFire, int colorFire, int diagramFire, int hiddenNodesFire, int viewChangedFire, int viewPropertiesFire) {
        String name = this.name.getMethodName();

        letItSettle();

        assertEquals(name, changedFire, changedFires);
        assertEquals(colorName + name, colorFire, colorFires);
        assertEquals(diagramName + name, diagramFire, diagramFires);
        assertEquals(hiddenNodesName + name, hiddenNodesFire, hiddenNodesFires);
        assertEquals(viewChangedName + name, viewChangedFire, viewChangedFires);
        assertEquals(viewPropertiesName + name, viewPropertiesFire, viewPropertiesFires);
    }

    private void resetFires() {
        changedFires = 0;
        colorFires = 0;
        diagramFires = 0;
        hiddenNodesFires = 0;
        viewChangedFires = 0;
        viewPropertiesFires = 0;
    }

    /**
     * Test of get/setSelectedNodes/Figures method, of class DiagramViewModel.
     */
    @Test
    public void testSelected() throws Exception {//changes colors!!
        Colorizer timelineColorizer = new Colorizer(timeline.getPrimaryPartition(), timeline.getPrimaryRange());
        // DiagramViewModel do not directly call the colorizer.
        instance.addPropertyChangeListener(DiagramViewModel.PROP_SELECTED_NODES,
                (e) -> timelineColorizer.setTrackedNodes(
                        instance.getSelectedNodes().stream().map(InputNode::getId).collect(Collectors.toSet())));
        Set<InputNode> expResultNodes = new HashSet<>();
        Set<Figure> expResultFigures = new HashSet<>();
        testGetSelectedNodes(expResultNodes);
        testGetSelectedFigures(expResultFigures);

        expResultNodes.add(N1);
        expResultNodes.add(N4);

        testEventFires();

        instance.setSelectedNodes(expResultNodes);

        assertFired(0, 1, 0, 0, 1, 0);

        testGetSelectedNodes(expResultNodes);

        Diagram dg = instance.withDiagramToView(null).get();

        for (Figure f : dg.getFigures()) {
            for (InputNode node : f.getSource().getSourceNodes()) {
                if (expResultNodes.contains(node)) {
                    expResultFigures.add(f);
                }
            }
        }
        testGetSelectedFigures(expResultFigures);

        expResultFigures.removeIf(x -> x.getSource().getSourceNodes().contains(N4));
        instance.withDiagramToView((d) -> d.getFigures().stream()
                .filter((f) -> (f.getSource().getSourceNodes().contains(N3)))
                .forEach(expResultFigures::add)).get();

        assertFired(0, 1, 0, 0, 1, 0);

        instance.setSelectedFigures(new ArrayList<>(expResultFigures));

        assertFired(0, 1, 0, 0, 2, 0);

        testGetSelectedFigures(expResultFigures);

        expResultNodes.remove(N4);
        expResultNodes.add(N3);
        testGetSelectedNodes(expResultNodes);

        assertFired(0, 1, 0, 0, 2, 0);
    }

    public void testGetSelectedNodes(Set<InputNode> expResult) {
        assertEquals(expResult, instance.getSelectedNodes());
    }

    public void testGetSelectedFigures(Set<Figure> expResult) {
        assertEquals(expResult, instance.getSelectedFigures());
    }

    /**
     * Test of getHiddenNodes method, of class DiagramViewModel.
     */
    @Test
    public void testHiddenNodes() {
        Set<Integer> expResult = new HashSet<>();
        testGetHiddenNodes(expResult);

        expResult.add(1);
        expResult.add(4);

        instance.setHiddenNodes(expResult);

        assertFired(0, 0, 2, 1, 0, 0);

        testGetHiddenNodes(expResult);

        expResult.remove(4);
        expResult.add(2);

        instance.setHiddenNodes(expResult);

        assertFired(0, 0, 4, 2, 0, 0);

        testGetHiddenNodes(expResult);
    }

    public void testGetHiddenNodes(Set<Integer> expResult) {
        assertEquals(expResult, instance.getHiddenNodes());
    }

    /**
     * Test of getHiddenGraphNodes method, of class DiagramViewModel.
     */
    @Test
    public void testHiddenGraphNodes() {
        Set<InputNode> expResult = new HashSet<>();
        testGetHiddenGraphNodes(expResult);

        expResult.add(N1);
        expResult.add(N4);

        instance.setHiddenNodes(expResult.stream().map(InputNode::getId).collect(Collectors.toSet()));

        assertFired(0, 0, 2, 1, 0, 0);

        testGetHiddenGraphNodes(expResult);

        expResult.remove(N4);
        expResult.add(N3);

        assertFired(0, 0, 2, 1, 0, 0);

        instance.setHiddenNodes(expResult.stream().map(InputNode::getId).collect(Collectors.toSet()));

        assertFired(0, 0, 4, 2, 0, 0);

        testGetHiddenGraphNodes(expResult);

        assertFired(0, 0, 4, 2, 0, 0);
    }

    public void testGetHiddenGraphNodes(Set<InputNode> expResult) {
        assertEquals(expResult, instance.getHiddenGraphNodes());
    }

    /**
     * Test of getFilterChain method, of class DiagramViewModel.
     */
    @Test
    public void testGetFilterChain() {
        testGetFilterChain(initFilterChain);
        testEventFires();
    }

    public void testGetFilterChain(FilterSequence expResult) {
        assertEquals(expResult.getFilters(), instance.getFilterChain().getFilters());
    }

    /**
     * Test of getFirstGraph method, of class DiagramViewModel.
     */
    @Test
    public void testGetFirstGraph() {
        testGetFirstGraph(referenceGraph);
        testEventFires();
    }

    public void testGetFirstGraph(InputGraph expResult) {
        DataTestUtil.assertInputGraphEquals(expResult, instance.getFirstGraph());
    }

    /**
     * Test of getSecondGraph method, of class DiagramViewModel.
     */
    @Test
    public void testGetSecondGraph() {
        testGetSecondGraph(duplicateGraph);
        testEventFires();
    }

    public void testGetSecondGraph(InputGraph expResult) {
        DataTestUtil.assertInputGraphEquals(expResult, instance.getSecondGraph());
    }

    /**
     * Test of getDiagramToView method, of class DiagramViewModel.
     */
    @Test
    @Ignore
    public void testGetDiagramToView() throws Exception {
        // must clear before withDiagramToView to ensure the proper sequence.
        Diagram result = instance.withDiagramToView(null).get();
        GraphTestUtil.assertDiagramCorrectState(Difference.createDiffGraph(referenceGraph, duplicateGraph), layoutSetting.get(String.class, LayoutSettings.NODE_TEXT), result);
        assertSame(result, instance.getDiagramToView());

        slider.setPositions(1, 1);
        assertFired(1, 0, 2, 0, 0, 0);

        Diagram result2 = instance.withDiagramToView(null).get();
        assertNot(result, result2, GraphTestUtil::assertDiagramEqualsDeep);

        GraphTestUtil.assertDiagramCorrectState(referenceGraph, layoutSetting.get(String.class, LayoutSettings.NODE_TEXT), result2);
        assertSame(result2, instance.getDiagramToView());

        assertFired(1, 0, 2, 0, 0, 0);

        slider.setPositions(0, slider.getPositions().size() - 1);
        assertFired(2, 0, 4, 0, 0, 0);

        Diagram result3 = instance.withDiagramToView(null).get();
        assertNot(result, result3, GraphTestUtil::assertDiagramEqualsDeep);
        assertNot(result2, result3, GraphTestUtil::assertDiagramEqualsDeep);

        GraphTestUtil.assertDiagramCorrectState(Difference.createDiffGraph(emptyGraph, duplicateGraph), layoutSetting.get(String.class, LayoutSettings.NODE_TEXT), result3);
        assertSame(result3, instance.getDiagramToView());

        assertFired(2, 0, 4, 0, 0, 0);
        slider.setPositions(0, slider.getPositions().size() - 1);//3rd changed, no diagram change
        assertFired(2, 0, 4, 0, 0, 0);
        assertSame(result3, instance.getDiagramToView());
        assertFired(2, 0, 4, 0, 0, 0);

        Diagram test = Diagram.createEmptyDiagram(referenceGraph, layoutSetting.get(String.class, LayoutSettings.NODE_TEXT));
        test.replaceFrom(result2);
        GraphTestUtil.assertDiagramEqualsDeep(test, result2);
        test = Diagram.createEmptyDiagram(result.getGraph(), layoutSetting.get(String.class, LayoutSettings.NODE_TEXT));
        test.replaceFrom(result);
        GraphTestUtil.assertDiagramEqualsDeep(test, result);
        test = Diagram.createEmptyDiagram(result3.getGraph(), layoutSetting.get(String.class, LayoutSettings.NODE_TEXT));
        test.replaceFrom(result3);
        GraphTestUtil.assertDiagramEqualsDeep(test, result3);
    }

    /**
     * Test of getGraphToView method, of class DiagramViewModel.
     */
    @Test
    public void testGetGraphToView() {
        testGetGraphToView(Difference.createDiffGraph(referenceGraph, duplicateGraph));
        testEventFires();
    }

    public void testGetGraphToView(InputGraph expResult) {
        DataTestUtil.assertInputGraphEquals(expResult, instance.getGraphToView());
    }

    /**
     * Test of getGraphsForward method, of class DiagramViewModel.
     */
    @Test
    public void testGetGraphsForward() {
        testGetGraphsForward(Collections.singletonList(duplicateGraph));
        testEventFires();
    }

    public void testGetGraphsForward(Iterable<InputGraph> expResult) {
        Iterator<InputGraph> expIt = expResult.iterator();
        Iterator<InputGraph> resIt = instance.getGraphsForward().iterator();
        while (expIt.hasNext()) {
            if (resIt.hasNext()) {
                DataTestUtil.assertInputGraphEquals(expIt.next(), resIt.next());
            } else {
                fail("Iterators are not of same size.");
            }
        }
    }

    /**
     * Test of getGraphsBackward method, of class DiagramViewModel.
     */
    @Test
    public void testGetGraphsBackward() {
        testGetGraphsBackward(Collections.singletonList(emptyGraph));
        testEventFires();
    }

    public void testGetGraphsBackward(Iterable<InputGraph> expResult) {
        Iterator<InputGraph> expIt = expResult.iterator();
        Iterator<InputGraph> resIt = instance.getGraphsBackward().iterator();
        while (expIt.hasNext()) {
            if (resIt.hasNext()) {
                DataTestUtil.assertInputGraphEquals(expIt.next(), resIt.next());
            } else {
                fail("Iterators are not of same size.");
            }
        }
    }

    /**
     * Test of getGroup method, of class DiagramViewModel.
     */
    @Test
    public void testGetGroup() {
        testGetGroup(initGroup);
        testEventFires();
    }

    public void testGetGroup(Group expResult) {
        DataTestUtil.assertGroupEquals(expResult, instance.getContainer().getContentOwner());
    }

    /**
     * Test of getHideDuplicates method, of class DiagramViewModel.
     */
    @Test
    public void testHideDuplicates() {
        assertFalse(instance.getHideDuplicates());
        instance.setHideDuplicates(true);
        assertFired(1, 1, 2, 0, 0, 0);
        assertTrue(instance.getHideDuplicates());

        assertFired(1, 1, 2, 0, 0, 0);
        instance.setHideDuplicates(false);
        assertFalse(instance.getHideDuplicates());

        assertFired(2, 2, 2, 0, 0, 0);
        instance.setHideDuplicates(false);//rehide
        assertFired(2, 2, 2, 0, 0, 0);//utterly wrong
    }

    /**
     * Test of getHideDuplicates method, of class DiagramViewModel.
     */
    @Test
    public void testEventFires() {
        assertFired(0, 0, 0, 0, 0, 0);
    }

    /**
     * Test of copy method, of class DiagramViewModel.
     */
    @Test
    public void testCopy() {
        DiagramViewModel result = instance.copy();

        testDiagramViewModelGetters(result);

        testEventFires();
    }

    /**
     * Test of setData method, of class DiagramViewModel.
     */
    @Test
    public void testSetData() {
        Group group = new Group(initGroup);

        InputGraph empty = InputGraph.createTestGraph("emptyGraph");
        empty.setGraphType(TestGraphClassifier.TEST_TYPE);
        group.addElement(empty);

        InputGraph reference = InputGraph.createTestGraph("referenceGraph");
        reference.setGraphType(TestGraphClassifier.TEST_TYPE);
        reference.addNode(N1);
        reference.addNode(N2);
        reference.addNode(N3);
        reference.addNode(N4);

        reference.addEdge(E12);
        reference.addEdge(E13);
        reference.addEdge(E24);
        reference.addEdge(E34);

        reference.ensureNodesInBlocks();

        group.addElement(reference);

        InputGraph duplicate = InputGraph.createTestGraph("duplicateGraph");
        duplicate.setGraphType(TestGraphClassifier.TEST_TYPE);
        duplicate.addNode(N1);
        duplicate.addNode(N2);
        duplicate.addNode(N3);
        duplicate.addNode(N4);

        duplicate.addEdge(E12);
        duplicate.addEdge(E13);
        duplicate.addEdge(E24);
        duplicate.addEdge(E34);

        duplicate.ensureNodesInBlocks();
        duplicate.getProperties().setProperty(PROPNAME_DUPLICATE, "true");

        group.addElement(duplicate);

        DiagramViewModel newModel = new DiagramViewModel(group, new FilterChain(initFilterChain), layoutSetting);
        newModel.setSelectedNodes(new HashSet<>(Arrays.asList(N1, N2)));
        newModel.setHiddenNodes(new HashSet<>(Arrays.asList(3, 4)));

        instance.setData(newModel);

        testDiagramViewModelGetters(newModel);

        assertFired(1, 1, 2, 1, 1, 1);
    }

    /**
     * Test of getShowBlocks method, of class DiagramViewModel.
     */
    @Test
    public void testShowBlocks() {
        assertFalse(instance.getShowBlocks());

        instance.setShowBlocks(true);
        assertTrue(instance.getShowBlocks());

        assertFired(0, 0, 2, 0, 0, 1);
        instance.setShowBlocks(false);
        assertFalse(instance.getShowBlocks());

        assertFired(0, 0, 3, 0, 0, 2);//diagramFire changes count

        instance.setShowBlocks(false);//reshow
        assertFired(0, 0, 3, 0, 0, 2);//utterly wrong
    }

    /**
     * Test of getShowNodeHull method, of class DiagramViewModel.
     */
    @Test
    public void testShowNodeHull() {
        assertFalse(instance.getShowNodeHull());

        instance.setShowNodeHull(true);
        assertTrue(instance.getShowNodeHull());

        assertFired(0, 0, 1, 0, 0, 1);
        instance.setShowNodeHull(false);
        assertFalse(instance.getShowNodeHull());

        assertFired(0, 0, 2, 0, 0, 2);
        instance.setShowNodeHull(false);//reshow
        assertFired(0, 0, 2, 0, 0, 2);//utterly wrong
    }

    /**
     * Test of showAll method, of class DiagramViewModel.
     */
    @Test
    public void testShowNot() {
        Set<Integer> f = new HashSet<>(Arrays.asList(1, 2, 3));

        instance.showNot(f);
        assertFired(0, 0, 2, 1, 0, 0);

        testGetHiddenNodes(f);

        assertFired(0, 0, 2, 1, 0, 0);
    }

    /**
     * Test of showFigures method, of class DiagramViewModel.
     */
    @Test
    public void testShowFigures() {
        Diagram expResult = Diagram.createDiagram(instance.getGraphToView(), layoutSetting.get(String.class, LayoutSettings.NODE_TEXT));
        Collection<Figure> f = expResult.getFigures().stream()
                .filter(x -> {
                    int id = x.getSource().firstId();
                    return id != 2 && id != 5;
                }).collect(Collectors.toList());

        Set<Integer> exp = new HashSet<>(Arrays.asList(1, 2, 3, 4, 5));
        instance.setHiddenNodes(exp);
        testGetHiddenNodes(exp);
        assertFired(0, 0, 2, 1, 0, 0);
        instance.showFigures(f);
        assertFired(0, 0, 4, 2, 0, 0);

        testGetHiddenGraphNodes(new HashSet<>(Arrays.asList(N2, N5)));

        assertFired(0, 0, 4, 2, 0, 0);
    }

    /**
     * Test of showAll method, of class DiagramViewModel.
     */
    @Test
    public void testShowAll() {
        Diagram expResult = Diagram.createDiagram(instance.getGraphToView(), layoutSetting.get(String.class, LayoutSettings.NODE_TEXT));
        Collection<Figure> f = expResult.getFigures().stream()
                .filter(x -> {
                    int id = x.getSource().firstId();
                    return id != 2 && id != 5;
                }).collect(Collectors.toList());

        testEventFires();
        Set<Integer> exp = new HashSet<>(Arrays.asList(1, 2, 3, 4, 5));
        instance.setHiddenNodes(exp);
        testGetHiddenNodes(exp);
        assertFired(0, 0, 2, 1, 0, 0);
        instance.showAll(f);
        assertFired(0, 0, 4, 2, 0, 0);

        testGetHiddenGraphNodes(new HashSet<>(Arrays.asList(N2, N5)));

        assertFired(0, 0, 4, 2, 0, 0);
    }

    /**
     * Test of showOnly method, of class DiagramViewModel.
     */
    @Test
    public void testShowOnly() {
        Collection<Integer> nodes = Arrays.asList(N1.getId(), N3.getId());

        instance.showOnly(nodes);

        testGetHiddenGraphNodes(new HashSet<>(Arrays.asList(N2, N4, N5)));

        assertFired(0, 0, 2, 1, 0, 0);
    }

    /**
     * Test of setFilterChain method, of class DiagramViewModel.
     */
    @Test
    public void testSetFilterChain() {
        FilterChain chain = new FilterChain(initFilterChain);

        instance.setFilterChain(chain);

        testGetFilterChain(chain);

        assertFired(0, 0, 1, 0, 0, 0);
        instance.setFilterChain(chain);
        assertFired(0, 0, 1, 0, 0, 0);
    }

    /**
     * Test of selectGraph method, of class DiagramViewModel.
     */
    @Test
    public void testSelectGraph() {
        instance.selectGraph(duplicateGraph);

        testGetFirstGraph(duplicateGraph);
        testGetSecondGraph(duplicateGraph);
        testGetGraphToView(duplicateGraph);

        assertFired(1, 0, 2, 0, 0, 0);
        instance.selectGraph(duplicateGraph);
        assertFired(1, 0, 2, 0, 0, 0);
    }

    /**
     * Test of changed method, of class DiagramViewModel.
     */
    @Test
    public void testChanged() {
        assertFired(0, 0, 0, 0, 0, 0);
        instance.changed(null);
        assertFired(1, 0, 0, 0, 0, 0);
        slider.setPositions(0, 0);
        assertFired(2/*setPosition() event*/, 0, 2/* diagram changed once */, 0, 0, 0);
    }

    /**
     * Test of close method, of class DiagramViewModel.
     */
    @Test
    public void testClose() {
        initFilterChain.addFilter(new ColorFilter("blue"));
        assertFired(0, 0, 2, 0, 0, 0);

        instance.close();
        assertFired(0, 0, 2, 0, 0, 0);

        initFilterChain.addFilter(new ColorFilter("black"));
        assertFired(0, 0, 2, 0, 0, 0);

    }

    public void testDiagramViewModelGetters(DiagramViewModel model) {
        letItSettle();

        testGetFilterChain(model.getFilterChain());
        testGetFirstGraph(model.getFirstGraph());
        testGetSecondGraph(model.getSecondGraph());
        testGetGraphToView(model.getGraphToView());
        testGetGraphsBackward(model.getGraphsBackward());
        testGetGraphsForward(model.getGraphsForward());
        testGetGroup(model.getContainer().getContentOwner());
        testGetHiddenGraphNodes(model.getHiddenGraphNodes());
        testGetHiddenNodes(model.getHiddenNodes());
    }
}
