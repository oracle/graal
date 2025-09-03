/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.test.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.graalvm.collections.Pair;
import org.junit.Before;
import org.junit.Test;

import com.oracle.svm.hosted.webimage.codegen.lowerer.MoveResolver;
import com.oracle.svm.hosted.webimage.codegen.lowerer.MoveResolver.Schedule;

import jdk.graal.compiler.test.AddExports;

/**
 * Test for {@link MoveResolver}.
 */
@AddExports("jdk.graal.compiler/jdk.graal.compiler.hightiercodegen.lowerer")
public class MoveResolverTest {
    List<Pair<Node, Node>> originalMoves;
    MoveResolver<Node, Node>.Schedule schedule;

    @Before
    public void setUp() {
        originalMoves = new ArrayList<>();
        schedule = null;
    }

    /**
     * Symbolic nodes used as sources and targets for moves.
     */
    private static final class Node {
        public final String name;

        private Node(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Calls into {@link MoveResolver} to compute a schedule for the given moves and validates the
     * schedule using {@link #doCheckSchedule(List, Schedule)}.
     *
     * @param expectsTemporaries Whether the use of temporary variables is expected for the current
     *            set of moves.
     */
    public void checkSchedule(boolean expectsTemporaries) {
        MoveResolver<Node, Node> resolver = new MoveResolver<>(originalMoves.stream().map(Pair::getRight).collect(Collectors.toList()));
        originalMoves.forEach(move -> resolver.addMove(move.getLeft(), move.getRight()));
        schedule = resolver.scheduleMoves();
        doCheckSchedule(originalMoves, schedule);
        assertEquals(expectsTemporaries, schedule.needsTemporary());
    }

    /**
     * Checks that schedule has the same semantics as the original list of moves and no non-optimal
     * moves happen.
     * <p>
     * For that, it simulates the moves in the schedule and tracks the current values of all
     * targets.
     * <p>
     * A move is non-optimal if
     * <ul>
     * <li>it assigns a target to itself.</li>
     * <li>it moves the original value of a source even though the current value of the source is
     * the same (this could introduce unnecessary temporary variables).</li>
     * </ul>
     */
    public static void doCheckSchedule(List<Pair<Node, Node>> moves, MoveResolver<Node, Node>.Schedule schedule) {
        if (!schedule.needsTemporary()) {
            assertTrue("Schedules without a temporary variable should not be larger than the number of requested moves.",
                            moves.size() >= schedule.moves.size());
        }

        /*
         * Maps each target to its current value in the simulation. The value in the map signifies
         * that the target node currently holds the original value of the value node.
         */
        Map<Node, Node> targetValues = new HashMap<>();
        moves.forEach(move -> {
            Node prev = targetValues.put(move.getRight(), move.getRight());
            assertNull("Targets should only be assigned once.", prev);
        });

        /*
         * The current value of the temporary variable (if one is used). Just as in the targetValues
         * map, if a node is stored it means its original value is in the temporary variable.
         */
        Node temporaryValue = null;

        /*
         * Simulates the scheduled moves and updates the targetValues map with the new values.
         */
        for (var scheduledMove : schedule.moves) {
            Node source = scheduledMove.source;
            Node target = scheduledMove.target;
            Node newValue;
            if (target == null) {
                /*
                 * This is a move into the temporary variable.
                 */

                assertTrue(schedule.needsTemporary());
                assertTrue("Moves from the original value must come from targets", targetValues.containsKey(source));
                /*
                 * Moves to the temporary value use the current value of source, but the current
                 * value must match the original value.
                 */
                assertEquals(source, targetValues.get(source));
                temporaryValue = source;
            } else {
                if (scheduledMove.useTemporary) {
                    assertTrue(schedule.needsTemporary());
                    /*
                     * Moves from the original value where the source is not a target are always
                     * superfluous original moves since the original value isn't changed by the
                     * schedule.
                     */
                    assertTrue("Moves from the original value must come from targets", targetValues.containsKey(source));
                    assertNotNull("The temporary variable was never set", temporaryValue);
                    /*
                     * If the original value of the source node should be used, we can simply use
                     * the source node.
                     */
                    newValue = temporaryValue;
                    /*
                     * If the current value is the same as the original value, no original-value
                     * move is necessary and this move is not optimal.
                     */
                    assertNotEquals(targetValues.get(target), newValue);
                } else {
                    /*
                     * If the current value of the source should be used, we may need to look up its
                     * current value.
                     *
                     * If the source is also a target, its current value can be read from the
                     * targetValues map. Otherwise, the current value is the same as the original
                     * value.
                     */
                    newValue = targetValues.getOrDefault(source, source);
                }

                assertTrue(targetValues.containsKey(target));
                var oldValue = targetValues.put(target, newValue);
                assertNotEquals("Move assigns same value as already stored.", newValue, oldValue);
            }
        }

        /*
         * Now the target values should match the original source values specified in the moves
         * list.
         */
        for (var move : moves) {
            assertEquals(move.getLeft(), targetValues.get(move.getRight()));
        }
    }

    protected Node createPhi(int i) {
        return new Node("phi" + i);
    }

    protected static Node createValue(int i) {
        return new Node("value" + i);
    }

    protected void addMove(Node source, Node target) {
        originalMoves.add(Pair.create(source, target));
    }

    @Test
    public void noMoves() {
        checkSchedule(false);
        assertEquals("Should produce no moves", 0, schedule.moves.size());
    }

    /**
     * Tests a single move.
     *
     * <pre>
     * value1 -> phi1
     * </pre>
     */
    @Test
    public void singleMove() {
        Node phi = createPhi(1);
        addMove(createValue(1), phi);
        checkSchedule(false);
    }

    /**
     * Tests two independent moves.
     *
     * <pre>
     * value1 -> phi1
     * value2 -> phi2
     * </pre>
     */
    @Test
    public void twoMoves() {
        var phi1 = createPhi(1);
        var phi2 = createPhi(2);

        addMove(createValue(1), phi1);
        addMove(createValue(2), phi2);

        checkSchedule(false);
    }

    /**
     * Tests two moves with a target also being a source.
     *
     * <pre>
     * value1 -> phi1
     * phi1   -> phi2
     * </pre>
     */
    @Test
    public void unorderedMoves() {
        var value1 = createValue(1);
        var phi1 = createPhi(1);
        var phi2 = createPhi(2);

        addMove(value1, phi1);
        addMove(phi1, phi2);

        checkSchedule(false);
    }

    /**
     * Tests phi nodes moving to each other.
     *
     * <pre>
     * phi2 -> phi1
     * phi1 -> phi2
     * </pre>
     */
    @Test
    public void cyclicMoves() {
        var phi1 = createPhi(1);
        var phi2 = createPhi(2);

        addMove(phi2, phi1);
        addMove(phi1, phi2);

        checkSchedule(true);
    }

    /**
     * Tests phi nodes moving to themselves.
     *
     * <pre>
     * phi1 -> phi1
     * phi2 -> phi2
     * </pre>
     */
    @Test
    public void selfMoves() {
        var phi1 = createPhi(1);
        var phi2 = createPhi(2);

        addMove(phi1, phi1);
        addMove(phi2, phi2);
        checkSchedule(false);

        assertEquals("Self moves should produce no moves", 0, schedule.moves.size());
    }

    /**
     * Tests phi nodes moving to each other but only one move requires a temporary.
     *
     * <pre>
     * phi1 -> phi3
     * phi2 -> phi4
     * phi2 -> phi1
     * phi1 -> phi2
     * phi1 -> phi5
     * phi2 -> phi6
     * </pre>
     */
    @Test
    public void cyclicMovesMixed() {
        var phi1 = createPhi(1);
        var phi2 = createPhi(2);
        var phi3 = createPhi(3);
        var phi4 = createPhi(4);
        var phi5 = createPhi(5);
        var phi6 = createPhi(6);

        addMove(phi1, phi3);
        addMove(phi2, phi4);
        addMove(phi2, phi1);
        addMove(phi1, phi2);
        addMove(phi1, phi5);
        addMove(phi2, phi6);

        checkSchedule(true);
    }

    /**
     * Tests two independent cyclic assignments.
     *
     * <pre>
     * phi2 -> phi1
     * phi1 -> phi2
     * phi4 -> phi3
     * phi3 -> phi4
     * </pre>
     */
    @Test
    public void twoCyclicMoves() {
        var phi1 = createPhi(1);
        var phi2 = createPhi(2);
        var phi3 = createPhi(3);
        var phi4 = createPhi(4);

        addMove(phi2, phi1);
        addMove(phi1, phi2);
        addMove(phi4, phi3);
        addMove(phi3, phi4);

        checkSchedule(true);
    }

    /**
     * This testcase comes from a real-world compilation the {@link MoveResolver} could not handle.
     *
     * <pre>
     * phi1   -> phi1
     * phi2   -> phi2
     * value1 -> phi3
     * phi5   -> phi4
     * phi5   -> phi5
     * </pre>
     */
    @Test
    public void moves1() {
        var phi1 = createPhi(1);
        var phi2 = createPhi(2);
        var phi3 = createPhi(3);
        var phi4 = createPhi(4);
        var phi5 = createPhi(5);

        var value1 = createValue(1);

        addMove(phi1, phi1);
        addMove(phi2, phi2);
        addMove(value1, phi3);
        addMove(phi5, phi4);
        addMove(phi5, phi5);

        checkSchedule(false);
    }
}
