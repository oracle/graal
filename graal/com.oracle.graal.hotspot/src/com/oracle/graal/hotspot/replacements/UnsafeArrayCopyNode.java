/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.replacements;

import static com.oracle.graal.api.meta.LocationIdentity.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;

@NodeInfo(allowedUsageTypes = {InputType.Memory})
public class UnsafeArrayCopyNode extends ArrayRangeWriteNode implements Lowerable, MemoryCheckpoint.Single {

    @Input ValueNode src;
    @Input ValueNode srcPos;
    @Input ValueNode dest;
    @Input ValueNode destPos;
    @Input ValueNode length;
    @OptionalInput ValueNode layoutHelper;

    protected Kind elementKind;

    public static UnsafeArrayCopyNode create(ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length, ValueNode layoutHelper, Kind elementKind) {
        return USE_GENERATED_NODES ? new UnsafeArrayCopyNodeGen(src, srcPos, dest, destPos, length, layoutHelper, elementKind) : new UnsafeArrayCopyNode(src, srcPos, dest, destPos, length,
                        layoutHelper, elementKind);
    }

    protected UnsafeArrayCopyNode(ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length, ValueNode layoutHelper, Kind elementKind) {
        super(StampFactory.forVoid());
        assert layoutHelper == null || elementKind == null;
        this.src = src;
        this.srcPos = srcPos;
        this.dest = dest;
        this.destPos = destPos;
        this.length = length;
        this.layoutHelper = layoutHelper;
        this.elementKind = elementKind;
    }

    public static UnsafeArrayCopyNode create(ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length, Kind elementKind) {
        return USE_GENERATED_NODES ? new UnsafeArrayCopyNodeGen(src, srcPos, dest, destPos, length, elementKind) : new UnsafeArrayCopyNode(src, srcPos, dest, destPos, length, elementKind);
    }

    protected UnsafeArrayCopyNode(ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length, Kind elementKind) {
        this(src, srcPos, dest, destPos, length, null, elementKind);
    }

    public static UnsafeArrayCopyNode create(ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length, ValueNode layoutHelper) {
        return USE_GENERATED_NODES ? new UnsafeArrayCopyNodeGen(src, srcPos, dest, destPos, length, layoutHelper) : new UnsafeArrayCopyNode(src, srcPos, dest, destPos, length, layoutHelper);
    }

    protected UnsafeArrayCopyNode(ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length, ValueNode layoutHelper) {
        this(src, srcPos, dest, destPos, length, layoutHelper, null);
    }

    @Override
    public ValueNode getArray() {
        return dest;
    }

    @Override
    public ValueNode getIndex() {
        return destPos;
    }

    @Override
    public ValueNode getLength() {
        return length;
    }

    @Override
    public boolean isObjectArray() {
        return elementKind == Kind.Object;
    }

    @Override
    public boolean isInitialization() {
        return false;
    }

    public Kind getElementKind() {
        return elementKind;
    }

    @Override
    public void lower(LoweringTool tool) {
        if (graph().getGuardsStage() == StructuredGraph.GuardsStage.AFTER_FSA) {
            UnsafeArrayCopySnippets.Templates templates = tool.getReplacements().getSnippetTemplateCache(UnsafeArrayCopySnippets.Templates.class);
            templates.lower(this, tool);
        }
    }

    public void addSnippetArguments(Arguments args) {
        args.add("src", src);
        args.add("srcPos", srcPos);
        args.add("dest", dest);
        args.add("destPos", destPos);
        args.add("length", length);
        if (layoutHelper != null) {
            args.add("layoutHelper", layoutHelper);
        }
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        if (elementKind != null) {
            return NamedLocationIdentity.getArrayLocation(elementKind);
        }
        return ANY_LOCATION;
    }

    @NodeIntrinsic
    public static native void arraycopy(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter Kind elementKind);

    @NodeIntrinsic
    public static native void arraycopyPrimitive(Object src, int srcPos, Object dest, int destPos, int length, int layoutHelper);
}
