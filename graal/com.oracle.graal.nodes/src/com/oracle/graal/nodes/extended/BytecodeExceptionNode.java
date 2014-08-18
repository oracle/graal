/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.extended;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * A node that represents an exception thrown implicitly by a Java bytecode. It can be lowered to
 * either a {@linkplain ForeignCallDescriptor foreign} call or a pre-allocated exception object.
 */
@NodeInfo
public class BytecodeExceptionNode extends AbstractMemoryCheckpoint implements Lowerable, MemoryCheckpoint.Single {

    private final Class<? extends Throwable> exceptionClass;
    @Input private final NodeInputList<ValueNode> arguments;

    public static BytecodeExceptionNode create(MetaAccessProvider metaAccess, Class<? extends Throwable> exceptionClass, ValueNode... arguments) {
        return new BytecodeExceptionNodeGen(metaAccess, exceptionClass, arguments);
    }

    BytecodeExceptionNode(MetaAccessProvider metaAccess, Class<? extends Throwable> exceptionClass, ValueNode... arguments) {
        super(StampFactory.exactNonNull(metaAccess.lookupJavaType(exceptionClass)));
        this.exceptionClass = exceptionClass;
        this.arguments = new NodeInputList<>(this, arguments);
    }

    public Class<? extends Throwable> getExceptionClass() {
        return exceptionClass;
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name) {
            return super.toString(verbosity) + "#" + exceptionClass.getSimpleName();
        }
        return super.toString(verbosity);
    }

    public LocationIdentity getLocationIdentity() {
        return LocationIdentity.ANY_LOCATION;
    }

    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    public NodeInputList<ValueNode> getArguments() {
        return arguments;
    }
}
