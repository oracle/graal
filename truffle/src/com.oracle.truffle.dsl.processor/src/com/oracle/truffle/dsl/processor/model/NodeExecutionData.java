/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.dsl.processor.model;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.model.NodeChildData.Cardinality;

public class NodeExecutionData {

    private final NodeChildData child;
    private final String name;
    private final int index;
    private final int childArrayIndex;
    private final List<TypeMirror> typeRestrictions = new ArrayList<>();

    public NodeExecutionData(NodeChildData child, int index, int childArrayIndex) {
        this.child = child;
        this.index = index;
        this.childArrayIndex = childArrayIndex;
        this.name = createName();
    }

    private String createName() {
        return child != null ? createName(child.getName(), childArrayIndex) : ("arg" + index);
    }

    public int getIndex() {
        return index;
    }

    public List<TypeMirror> getTypeRestrictions() {
        return typeRestrictions;
    }

    public TypeMirror getNodeType() {
        if (child == null) {
            return null;
        }
        TypeMirror type;
        if (child.getCardinality() == Cardinality.MANY && child.getNodeType().getKind() == TypeKind.ARRAY) {
            type = ((ArrayType) child.getNodeType()).getComponentType();
        } else {
            type = child.getNodeType();
        }
        return type;
    }

    public String getName() {
        return name;
    }

    public NodeChildData getChild() {
        return child;
    }

    public int getChildArrayIndex() {
        return childArrayIndex;
    }

    public boolean hasChildArrayIndex() {
        return childArrayIndex > -1;
    }

    public String getIndexedName() {
        return createIndexedName(child, childArrayIndex);
    }

    public static String createIndexedName(NodeChildData child, int varArgsIndex) {
        String shortCircuitName = child.getName();
        if (child.getCardinality().isMany()) {
            shortCircuitName = shortCircuitName + "[" + varArgsIndex + "]";
        }
        return shortCircuitName;
    }

    public static String createName(String childName, int index) {
        if (index > -1) {
            return childName + index;
        }
        return childName;
    }

}
