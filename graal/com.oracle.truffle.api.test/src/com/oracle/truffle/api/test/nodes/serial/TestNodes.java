/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.nodes.serial;

import java.util.*;

import com.oracle.truffle.api.nodes.*;

final class TestNodes {

    private TestNodes() {
    }

    static class StringNode extends Node {

        private final String name;

        public StringNode(String name) {
            this.name = name;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            } else if (obj.getClass() != getClass()) {
                return false;
            } else if ((((Node) obj).getParent() != null) && !((Node) obj).getParent().equals(getParent())) {
                return false;
            } else if (!Objects.equals(name, ((StringNode) obj).name)) {
                return false;
            }
            return true;
        }
    }

    static class EmptyNode extends Node {

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            } else if (obj.getClass() != getClass()) {
                return false;
            } else if ((((Node) obj).getParent() != null) && !((Node) obj).getParent().equals(getParent())) {
                return false;
            }
            return true;
        }
    }

    static class NodeWithOneChild extends EmptyNode {

        @Child Node child;

        public NodeWithOneChild(Node child) {
            this.child = child;
        }

    }

    static class NodeWithTwoChilds extends EmptyNode {

        @Child Node child1;
        @Child Node child2;

        public NodeWithTwoChilds(Node child1, Node child2) {
            this.child1 = child1;
            this.child2 = child2;
        }

    }

    static class NodeWithThreeChilds extends EmptyNode {

        @Child Node child1;
        @Child Node child2;
        @Child Node child3;

        public NodeWithThreeChilds(Node child1, Node child2, Node child3) {
            this.child1 = child1;
            this.child2 = child2;
            this.child3 = child3;
        }

    }

    static class NodeWithArray extends EmptyNode {

        @Children private final Node[] childNodes;

        NodeWithArray(Node[] children) {
            this.childNodes = children;
        }

        Node[] getChildNodes() {
            return childNodes;
        }
    }

    static class NodeWithTwoArray extends EmptyNode {

        @Children private final Node[] childNodes1;
        @Children private final Node[] childNodes2;

        NodeWithTwoArray(Node[] childs1, Node[] childs2) {
            this.childNodes1 = childs1;
            this.childNodes2 = childs2;
        }

        Node[] getChildNodes1() {
            return childNodes1;
        }

        Node[] getChildNodes2() {
            return childNodes2;
        }
    }

    static class NodeWithFields extends EmptyNode {

        String stringField;
        int integerField;
        Integer integerObjectField;
        long longField;
        Long longObjectField;
        float floatField;
        Float floatObjectField;
        double doubleField;
        Double doubleObjectField;
        char charField;
        Character charObjectField;
        short shortField;
        Short shortObjecField;
        byte byteField;
        Byte byteObjectField;
        boolean booleanField;
        Boolean booleanObjectfield;

        public NodeWithFields(String stringField, int integerField, Integer integerObjectField, long longField, Long longObjectField, float floatField, Float floatObjectField, double doubleField,
                        Double doubleObjectField, char charField, Character charObjectField, short shortField, Short shortObjecField, byte byteField, Byte byteObjectField, boolean booleanField,
                        Boolean booleanObjectfield) {
            this.stringField = stringField;
            this.integerField = integerField;
            this.integerObjectField = integerObjectField;
            this.longField = longField;
            this.longObjectField = longObjectField;
            this.floatField = floatField;
            this.floatObjectField = floatObjectField;
            this.doubleField = doubleField;
            this.doubleObjectField = doubleObjectField;
            this.charField = charField;
            this.charObjectField = charObjectField;
            this.shortField = shortField;
            this.shortObjecField = shortObjecField;
            this.byteField = byteField;
            this.byteObjectField = byteObjectField;
            this.booleanField = booleanField;
            this.booleanObjectfield = booleanObjectfield;
        }

        @Override
        public int hashCode() {
            return Objects.hash(stringField, integerField, integerObjectField, longField, longObjectField, floatField, floatObjectField, doubleField, doubleObjectField, charField, charObjectField,
                            shortField, shortObjecField, byteField, byteObjectField, booleanField, booleanObjectfield);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj.getClass() != getClass()) {
                return false;
            }
            NodeWithFields o = (NodeWithFields) obj;
            return Objects.deepEquals(fieldArray(), o.fieldArray());
        }

        private Object[] fieldArray() {
            return array(stringField, integerField, integerObjectField, longField, longObjectField, floatField, floatObjectField, doubleField, doubleObjectField, charField, charObjectField,
                            shortField, shortObjecField, byteField, byteObjectField, booleanField, booleanObjectfield);
        }

        private static Object[] array(Object... values) {
            return values;
        }

    }

}
