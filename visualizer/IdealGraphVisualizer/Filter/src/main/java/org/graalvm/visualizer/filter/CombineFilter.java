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
package org.graalvm.visualizer.filter;

import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.graalvm.visualizer.graph.*;

import jdk.graal.compiler.graphio.parsing.model.Properties;
import jdk.graal.compiler.graphio.parsing.model.Properties.PropertyMatcher;

public class CombineFilter extends AbstractFilter {

    private final List<CombineRule> rules;
    private final String name;

    public CombineFilter(String name) {
        this.name = name;
        rules = new ArrayList<>();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void apply(Diagram diagram) {

        Properties.PropertySelector<Figure> selector = new Properties.PropertySelector<>(diagram.getFigures());
        for (CombineRule r : rules) {

            List<Figure> list = selector.selectMultiple(r.getFirstMatcher());
            Set<Figure> figuresToRemove = new HashSet<>();
            for (Figure f : list) {

                List<Figure> successors = new ArrayList<>(f.getSuccessors());
                if (r.isReversed()) {
                    if (successors.size() == 1) {
                        checkCancelled();
                        Figure succ = successors.get(0);
                        InputSlot slot = null;

                        for (InputSlot s : succ.getInputSlots()) {
                            for (Connection c : s.getConnections()) {
                                if (c.getOutputSlot().getFigure() == f) {
                                    slot = s;
                                    break;
                                }
                            }
                        }
                        assert slot != null;
                        slot.getSource().addSourceNodes(f.getSource());
                        if (r.getShortProperty() != null) {
                            String s = f.getProperties().getString(r.getShortProperty(), null);
                            if (s != null && s.length() > 0) {
                                slot.setShortName(s);
                                slot.setText(s);
                                slot.setColor(f.getColor());
                            }
                        } else {
                            assert slot != null;
                            slot.setText(f.getProperties().getString(PROPNAME_DUMP_SPEC, null));
                            String n = f.getProperties().getString(PROPNAME_SHORT_NAME, null);
                            if (n != null) {
                                slot.setShortName(n);
                            } else {
                                String s = f.getProperties().getString(PROPNAME_DUMP_SPEC, null);
                                if (s != null && s.length() <= 5) {
                                    slot.setShortName(s);
                                }
                            }
                        }

                        for (InputSlot s : f.getInputSlots()) {
                            for (Connection c : s.getConnections()) {
                                Connection newConn = diagram.createConnection(slot, c.getOutputSlot(), c.getLabel(), c.getType());
                                newConn.setColor(c.getColor());
                                newConn.setStyle(c.getStyle());
                            }
                        }

                        figuresToRemove.add(f);
                    }
                } else {

                    for (Figure succ : successors) {
                        checkCancelled();
                        if (succ.getPredecessors().size() == 1 && succ.getInputSlots().size() == 1) {
                            if (succ.getProperties().selectSingle(r.getSecondMatcher()) != null && succ.getOutputSlots().size() == 1) {

                                OutputSlot oldSlot = null;
                                for (OutputSlot s : f.getOutputSlots()) {
                                    for (Connection c : s.getConnections()) {
                                        if (c.getInputSlot().getFigure() == succ) {
                                            oldSlot = s;
                                            break;
                                        }
                                    }
                                }

                                assert oldSlot != null;

                                OutputSlot nextSlot = succ.getOutputSlots().get(0);
                                int pos = 0;
                                String n = succ.getProperties().getString("con", null);
                                if (n != null) {
                                    pos = Integer.parseInt(n);
                                }
                                OutputSlot slot = f.createOutputSlot(pos);
                                slot.getSource().addSourceNodes(succ.getSource());
                                if (r.getShortProperty() != null) {
                                    String s = succ.getProperties().getString(r.getShortProperty(), null);
                                    if (s != null && s.length() > 0) {
                                        slot.setShortName(s);
                                        slot.setText(s);
                                        slot.setColor(succ.getColor());
                                    }
                                } else {
                                    slot.setText(succ.getProperties().getString(PROPNAME_DUMP_SPEC, null));
                                    n = succ.getProperties().getString(PROPNAME_SHORT_NAME, null);
                                    if (n != null) {
                                        slot.setShortName(n);
                                    } else {
                                        String s = succ.getProperties().getString(PROPNAME_DUMP_SPEC, null);
                                        if (s != null && s.length() <= 2) {
                                            slot.setShortName(s);
                                        } else {
                                            String tmpName = succ.getProperties().get(PROPNAME_NAME, String.class);
                                            if (tmpName != null && tmpName.length() > 0) {
                                                slot.setShortName(tmpName.substring(0, 1));
                                            }
                                        }
                                    }
                                }
                                for (Connection c : nextSlot.getConnections()) {
                                    Connection newConn = diagram.createConnection(c.getInputSlot(), slot, c.getLabel(), c.getType());
                                    newConn.setColor(c.getColor());
                                    newConn.setStyle(c.getStyle());
                                }

                                figuresToRemove.add(succ);

                                if (oldSlot.getConnections().isEmpty()) {
                                    f.removeSlot(oldSlot);
                                }
                            }
                        }
                    }
                }
            }

            diagram.removeAllFigures(figuresToRemove);
        }
    }

    public void addRule(CombineRule combineRule) {
        rules.add(combineRule);
    }

    public static class CombineRule {

        private final PropertyMatcher first;
        private final PropertyMatcher second;
        private final boolean reversed;
        private final String shortProperty;

        public CombineRule(PropertyMatcher first, PropertyMatcher second) {
            this(first, second, false);

        }

        public CombineRule(PropertyMatcher first, PropertyMatcher second, boolean reversed) {
            this(first, second, reversed, null);
        }

        public CombineRule(PropertyMatcher first, PropertyMatcher second, boolean reversed, String shortProperty) {
            this.first = first;
            this.second = second;
            this.reversed = reversed;
            this.shortProperty = shortProperty;
        }

        public boolean isReversed() {
            return reversed;
        }

        public PropertyMatcher getFirstMatcher() {
            return first;
        }

        public PropertyMatcher getSecondMatcher() {
            return second;
        }

        public String getShortProperty() {
            return shortProperty;
        }
    }
}
