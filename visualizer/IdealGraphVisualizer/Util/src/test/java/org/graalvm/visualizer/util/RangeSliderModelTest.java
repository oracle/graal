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

package org.graalvm.visualizer.util;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

/**
 * @author Ondřej Douda <ondrej.douda@oracle.com>
 */
public class RangeSliderModelTest {
    private static final List<String> initPositions = Arrays.asList("a", "b", "c", "d");
    private static final List<Color> initColors = Arrays.asList(Color.BLUE, Color.RED, Color.CYAN, Color.GREEN);
    private static final int initFirstPos = 1;
    private static final int initSecondPos = 3;

    private static final Map<String, Integer> indices = new HashMap<>();

    static {
        // -ab--c---d
        indices.put("a", 1);
        indices.put("b", 2);
        indices.put("c", 5);
        indices.put("d", 9);
    }

    private final RangeSliderModel instance;

    private int changedFires;
    private int colorFires;

    @Rule
    public TestName name = new TestName();

    public RangeSliderModelTest() {
        instance = new RangeSliderModel(initPositions);

        init();

        instance.getChangedEvent().addListener(
                source -> changedFires++
        );
        instance.getColorChangedEvent().addListener(
                source -> colorFires++
        );
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    public void init() {
        instance.setPositions(initPositions);
        instance.setColors(initColors);
        instance.setPositions(initFirstPos, initSecondPos);

        testInitGetters();
    }

    @After
    public void tearDown() {
    }

    private static final String colorName = "color_";

    private void assertFired(int changedFire, int colorFire) {
        assertFired(changedFire, colorFire, name.getMethodName());
    }

    private void assertFired(int changedFire, int colorFire, String name) {
        assertEquals(name, changedFire, changedFires);
        assertEquals(colorName + name, colorFire, colorFires);
    }

    /**
     * Test of setData method, of class RangeSliderModel.
     */
    @Test
    public void testSetData() {
        List<String> positions = Arrays.asList("1", "2", "3", "4", "5");
        List<Color> colors = Arrays.asList(Color.BLUE, Color.CYAN, Color.GREEN, Color.ORANGE, Color.YELLOW);
        int fp = 2;
        int sp = 2;

        RangeSliderModel model = new RangeSliderModel(positions);
        model.setColors(colors);
        model.setPositions(fp, sp);

        assertFired(0, 0);

        instance.setData(model);

        assertFired(1, 1);

        testGetters(model);

        assertFired(1, 1);
    }

    public void testGetters(RangeSliderModel model) {
        testGetters(model.getPositions(), model.getColors(), model.getFirstPosition(), model.getSecondPosition());
    }

    public void testInitGetters() {
        testGetters(initPositions, initColors, initFirstPos, initSecondPos);
    }

    public void testGetters(List<String> positions, List<Color> colors, int firstPosition, int secondPosition) {
        testGetColors(colors);
        testGetPositions(positions);
        testGetFirstPosition(firstPosition);
        testGetSecondPosition(secondPosition);
    }

    /**
     * Test of setPositions method, of class RangeSliderModel.
     */
    @Test
    public void testSetPositions_List() {
        List<String> positions = Arrays.asList("t", "r", "a", "w", "w");

        assertFired(0, 0);

        instance.setPositions(positions);

        assertFired(1, 1);

        testGetters(positions, Collections.nCopies(5, Color.BLACK), 2, 2);

        assertFired(1, 1);
    }

    /**
     * Test of setColors method, of class RangeSliderModel.
     */
    @Test
    public void testSetColors() {
        List<Color> colors = new ArrayList<>(initColors);
        Collections.swap(colors, 0, initColors.size() - 1);


        assertFired(0, 0);

        instance.setColors(colors);

        assertFired(0, 1);

        testGetters(initPositions, colors, initFirstPos, initSecondPos);

        assertFired(0, 1);
    }

    /**
     * Test of getColors method, of class RangeSliderModel.
     */
    @Test
    public void testGetColors() {
        testGetColors(initColors);

        assertFired(0, 0);
    }

    public void testGetColors(List<Color> expResult) {
        assertEquals(expResult, instance.getColors());
    }

    /**
     * Test of copy method, of class RangeSliderModel.
     */
    @Test
    public void testCopy() {
        RangeSliderModel result = instance.copy();

        assertNotSame(instance, result);

        testGetters(result);

        assertFired(0, 0);
    }

    /**
     * Test of getPositions method, of class RangeSliderModel.
     */
    @Test
    public void testGetPositions() {
        testGetPositions(initPositions);

        assertFired(0, 0);
    }

    public void testGetPositions(List<String> expResult) {
        assertEquals(expResult, instance.getPositions());
    }

    /**
     * Test of getFirstPosition method, of class RangeSliderModel.
     */
    @Test
    public void testGetFirstPosition() {
        testGetFirstPosition(initFirstPos);

        assertFired(0, 0);
    }

    public void testGetFirstPosition(int expResult) {
        assertEquals(expResult, instance.getFirstPosition());
    }

    /**
     * Test of getSecondPosition method, of class RangeSliderModel.
     */
    @Test
    public void testGetSecondPosition() {
        testGetSecondPosition(initSecondPos);

        assertFired(0, 0);
    }

    public void testGetSecondPosition(int expResult) {
        assertEquals(expResult, instance.getSecondPosition());
    }

    /**
     * Test of setPositions method, of class RangeSliderModel.
     */
    @Test
    public void testSetPositions_int_int() {
        int fp = 0;
        int sp = 2;

        assertFired(0, 0);

        instance.setPositions(fp, sp);

        assertFired(1, 0);

        testGetters(initPositions, initColors, fp, sp);

        assertFired(1, 0);
    }

    /**
     * Checks that slot count is reported correctly when no indices are set.
     */
    @Test
    public void testBasicPositionCount() throws Exception {
        assertEquals(initPositions.size(), instance.getPositionCount(true));
        assertEquals(initPositions.size(), instance.getPositionCount(false));
    }

    /**
     * @throws Exception
     */
    @Test
    public void testPositionWithSpacesBasic() throws Exception {
        instance.setIndices(indices);
        assertEquals(initPositions, instance.getPositions());
        assertFired(1, 0);
    }

    @Test
    public void testSpacesBefore() throws Exception {
        instance.setIndices(indices);
        assertEquals(1, instance.gapSizeBefore("a"));
        assertEquals(0, instance.gapSizeBefore("b"));
        assertEquals(2, instance.gapSizeBefore("c"));
        assertEquals(3, instance.gapSizeBefore("d"));

        assertEquals(4, instance.getPositionCount(false));
        assertEquals(4 + 1 + 2 + 3, instance.getPositionCount(true));
    }

    @Test
    public void testExtraIndices() throws Exception {
        Map<String, Integer> extra = new HashMap<>(indices);
        extra.put("f", 6);
        instance.setIndices(extra);

        assertEquals(1, instance.gapSizeBefore("a"));
        assertEquals(0, instance.gapSizeBefore("b"));
        assertEquals(2, instance.gapSizeBefore("c"));
        assertEquals(3, instance.gapSizeBefore("d"));

        assertEquals(4, instance.getPositionCount(false));
        assertEquals(4 + 1 + 2 + 3, instance.getPositionCount(true));
    }

    @Test
    public void testMissingIndices() throws Exception {
        Map<String, Integer> extra = new HashMap<>(indices);
        extra.remove("c");

        instance.setIndices(extra);

        assertEquals(1, instance.gapSizeBefore("a"));
        assertEquals(0, instance.gapSizeBefore("b"));
        assertEquals(0, instance.gapSizeBefore("c"));
        assertEquals(6, instance.gapSizeBefore("d"));
        assertEquals(0, instance.gapSizeBefore("e"));

        assertEquals(4, instance.getPositionCount(false));
        assertEquals(4 + 1 + 6, instance.getPositionCount(true));
    }

    private void assertPositionString(String expected, List<String> lst) {
        StringBuilder sb = new StringBuilder();
        for (String s : lst) {
            if (s == null) {
                sb.append("-");
            } else {
                sb.append(s);
            }
        }
        assertEquals(expected, sb.toString());
    }

    @Test
    public void testSpacedPositions() {
        instance.setIndices(indices);
        assertPositionString("-ab--c---d", instance.getPositions(true));

    }

    @Test
    public void testPositionExtraIndices() {
        Map<String, Integer> extra = new HashMap<>(indices);
        extra.put("f", 6);
        instance.setIndices(extra);

        assertPositionString("-ab--c---d", instance.getPositions(true));
    }

    @Test
    public void testPositionMissingIndices() {
        Map<String, Integer> extra = new HashMap<>(indices);
        extra.remove("c");

        instance.setIndices(extra);
        assertPositionString("-abc------d", instance.getPositions(true));
    }
}
