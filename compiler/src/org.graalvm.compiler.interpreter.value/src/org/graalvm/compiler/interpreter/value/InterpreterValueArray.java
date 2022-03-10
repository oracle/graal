/*
 * Copyright (c) 2009, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.interpreter.value;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

public class InterpreterValueArray extends InterpreterValue {
    private final ResolvedJavaType componentType;
    private final int length;
    private final InterpreterValue[] contents;

    public InterpreterValueArray(ResolvedJavaType componentType, int length, JavaKind storageKind) {
        this(componentType, length, storageKind, true);
    }

    public InterpreterValueArray(ResolvedJavaType componentType, int length, JavaKind storageKind, boolean populateDefault) {
        if (length < 0) {
            throw new IllegalArgumentException("Negative array length");
        }

        this.componentType = componentType;
        this.length = length;
        this.contents = new InterpreterValue[length];
        if (populateDefault) {
            populateContentsWithDefaultValues(storageKind);
        }
    }

    private void populateContentsWithDefaultValues(JavaKind storageKind) {
        for (int i = 0; i < length; i++) {
            contents[i] = InterpreterValue.createDefaultOfKind(storageKind);
        }
    }

    public int getLength() {
        return length;
    }

    @Override
    public ResolvedJavaType getObjectType() {
        return componentType.getArrayClass();
    }

    public ResolvedJavaType getComponentType() {
        return componentType;
    }

    @Override
    public JavaKind getJavaKind() {
        return JavaKind.Object;
    }

    @Override
    public boolean isNull() {
        return false;
    }

    @Override
    public Object asObject() {
        // TODO: figure out how to do this
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isArray() {
        return true;
    }

    public InterpreterValue getAtIndex(int index) {
        checkBounds(index);
        if (contents[index] == null) {
            throw new IllegalStateException();
        }
        return contents[index];
    }

    public void setAtIndex(int index, InterpreterValue value) {
        checkBounds(index);
        // TODO: should we bother checking type compatbilitity?
        contents[index] = value;
    }

    private void checkBounds(int index) {
        if (index < 0 || index >= length) {
            throw new IllegalArgumentException("Invalid array access index");
        }
    }
}
