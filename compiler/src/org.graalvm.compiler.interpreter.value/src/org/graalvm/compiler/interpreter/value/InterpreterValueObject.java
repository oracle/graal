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
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public abstract class InterpreterValueObject extends InterpreterValue {
    final ResolvedJavaType type;
    private boolean unwindException = false;

    public InterpreterValueObject(ResolvedJavaType type) {
        this.type = type;
    }

    public abstract boolean hasField(ResolvedJavaField field);

    public abstract void setFieldValue(ResolvedJavaField field, InterpreterValue value);

    public abstract InterpreterValue getFieldValue(ResolvedJavaField field);

    @Override
    public boolean isUnwindException() {
        return unwindException;
    }

    @Override
    public void setUnwindException() {
        try {
            if (!Exception.class.isAssignableFrom(Class.forName(type.toClassName()))) {
                throw new IllegalArgumentException("cannot unwind with non-Exception object");
            }
        } catch (ClassNotFoundException e) {
            // TODO: does this ever happen for valid graphs?
            throw new IllegalArgumentException();
        }
        this.unwindException = true;
    }

    @Override
    public Object asObject() {
        if (!type.isCloneableWithAllocation()) {
            throw new IllegalArgumentException("Type is not cloneable with just allocation");
        }
        throw new UnsupportedOperationException("not implemented");

        // TODO: use reflection to construct actual java Object: is this doable?
    }

    @Override
    public ResolvedJavaType getObjectType() {
        return type;
    }

    @Override
    public JavaKind getJavaKind() {
        return JavaKind.Object;
    }

    @Override
    public boolean isNull() {
        return false;
    }
}
