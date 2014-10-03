/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.java;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * The base class of all instructions that access fields.
 */
@NodeInfo
public abstract class AccessFieldNode extends FixedWithNextNode implements Lowerable {

    @OptionalInput ValueNode object;

    protected final ResolvedJavaField field;

    public ValueNode object() {
        return object;
    }

    /**
     * Constructs a new access field object.
     *
     * @param object the instruction producing the receiver object
     * @param field the compiler interface representation of the field
     */
    public AccessFieldNode(Stamp stamp, ValueNode object, ResolvedJavaField field) {
        super(stamp);
        this.object = object;
        this.field = field;
        assert field.getDeclaringClass().isInitialized();
    }

    /**
     * Gets the compiler interface field for this field access.
     *
     * @return the compiler interface field for this field access
     */
    public ResolvedJavaField field() {
        return field;
    }

    /**
     * Checks whether this field access is an access to a static field.
     *
     * @return {@code true} if this field access is to a static field
     */
    public boolean isStatic() {
        return field.isStatic();
    }

    /**
     * Checks whether this field is declared volatile.
     *
     * @return {@code true} if the field is resolved and declared volatile
     */
    public boolean isVolatile() {
        return field.isVolatile();
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name) {
            return super.toString(verbosity) + "#" + field.getName();
        } else {
            return super.toString(verbosity);
        }
    }

    @Override
    public boolean verify() {
        assertTrue((object == null) == isStatic(), "static field must not have object, instance field must have object");
        return super.verify();
    }
}
