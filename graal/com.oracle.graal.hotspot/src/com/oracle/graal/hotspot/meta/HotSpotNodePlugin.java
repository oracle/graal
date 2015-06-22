/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.meta;

import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.hotspot.meta.HotSpotGraalConstantReflectionProvider.*;
import jdk.internal.jvmci.meta.*;

import com.oracle.graal.api.replacements.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.Node.*;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.word.*;

/**
 * This plugin handles the HotSpot-specific customizations of bytecode parsing:
 * <p>
 * {@link Word}-type rewriting for {@link GraphBuilderContext#parsingIntrinsic intrinsic} functions
 * (snippets and method substitutions), by forwarding to the {@link WordOperationPlugin}. Note that
 * we forward the {@link NodePlugin} and {@link ParameterPlugin} methods, but not the
 * {@link InlineInvokePlugin} methods implemented by {@link WordOperationPlugin}. The latter is not
 * necessary because HotSpot only uses the {@link Word} type in methods that are force-inlined,
 * i.e., there are never non-inlined invokes that involve the {@link Word} type.
 * <p>
 * Handling of {@link Fold} and {@link NodeIntrinsic} annotated methods, by forwarding to the
 * {@link NodeIntrinsificationPlugin} when parsing intrinsic functions.
 * <p>
 * Constant folding of field loads.
 */
public final class HotSpotNodePlugin implements NodePlugin, ParameterPlugin {
    protected final WordOperationPlugin wordOperationPlugin;
    protected final NodeIntrinsificationPlugin nodeIntrinsificationPlugin;

    public HotSpotNodePlugin(WordOperationPlugin wordOperationPlugin, NodeIntrinsificationPlugin nodeIntrinsificationPlugin) {
        this.wordOperationPlugin = wordOperationPlugin;
        this.nodeIntrinsificationPlugin = nodeIntrinsificationPlugin;
    }

    @Override
    public boolean canChangeStackKind(GraphBuilderContext b) {
        if (b.parsingIntrinsic()) {
            return wordOperationPlugin.canChangeStackKind(b) || nodeIntrinsificationPlugin.canChangeStackKind(b);
        }
        return false;
    }

    @Override
    public FloatingNode interceptParameter(GraphBuilderContext b, int index, Stamp stamp) {
        if (b.parsingIntrinsic()) {
            return wordOperationPlugin.interceptParameter(b, index, stamp);
        }
        return null;
    }

    @Override
    public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        if (b.parsingIntrinsic() && wordOperationPlugin.handleInvoke(b, method, args)) {
            return true;
        }
        if (b.parsingIntrinsic() && nodeIntrinsificationPlugin.handleInvoke(b, method, args)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean handleLoadField(GraphBuilderContext b, ValueNode object, ResolvedJavaField field) {
        if (!ImmutableCode.getValue() || b.parsingIntrinsic()) {
            if (object.isConstant()) {
                JavaConstant asJavaConstant = object.asJavaConstant();
                if (tryReadField(b, field, asJavaConstant)) {
                    return true;
                }
            }
        }
        if (b.parsingIntrinsic() && wordOperationPlugin.handleLoadField(b, object, field)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean handleLoadStaticField(GraphBuilderContext b, ResolvedJavaField field) {
        if (!ImmutableCode.getValue() || b.parsingIntrinsic()) {
            if (tryReadField(b, field, null)) {
                return true;
            }
        }
        if (b.parsingIntrinsic() && wordOperationPlugin.handleLoadStaticField(b, field)) {
            return true;
        }
        return false;
    }

    private static boolean tryReadField(GraphBuilderContext b, ResolvedJavaField field, JavaConstant object) {
        // FieldReadEnabledInImmutableCode is non null only if assertions are enabled
        if (FieldReadEnabledInImmutableCode != null && ImmutableCode.getValue()) {
            FieldReadEnabledInImmutableCode.set(Boolean.TRUE);
            try {
                return tryConstantFold(b, field, object);
            } finally {
                FieldReadEnabledInImmutableCode.set(null);
            }
        } else {
            return tryConstantFold(b, field, object);
        }
    }

    private static boolean tryConstantFold(GraphBuilderContext b, ResolvedJavaField field, JavaConstant object) {
        JavaConstant result = b.getConstantReflection().readConstantFieldValue(field, object);
        if (result != null) {
            ConstantNode constantNode = ConstantNode.forConstant(result, b.getMetaAccess(), b.getGraph());
            b.push(field.getKind(), constantNode);
            return true;
        }
        return false;
    }

    @Override
    public boolean handleStoreField(GraphBuilderContext b, ValueNode object, ResolvedJavaField field, ValueNode value) {
        if (b.parsingIntrinsic() && wordOperationPlugin.handleStoreField(b, object, field, value)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean handleStoreStaticField(GraphBuilderContext b, ResolvedJavaField field, ValueNode value) {
        if (b.parsingIntrinsic() && wordOperationPlugin.handleStoreStaticField(b, field, value)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean handleLoadIndexed(GraphBuilderContext b, ValueNode array, ValueNode index, Kind elementKind) {
        if (b.parsingIntrinsic() && wordOperationPlugin.handleLoadIndexed(b, array, index, elementKind)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean handleStoreIndexed(GraphBuilderContext b, ValueNode array, ValueNode index, Kind elementKind, ValueNode value) {
        if (b.parsingIntrinsic() && wordOperationPlugin.handleStoreIndexed(b, array, index, elementKind, value)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean handleCheckCast(GraphBuilderContext b, ValueNode object, ResolvedJavaType type, JavaTypeProfile profile) {
        if (b.parsingIntrinsic() && wordOperationPlugin.handleCheckCast(b, object, type, profile)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean handleInstanceOf(GraphBuilderContext b, ValueNode object, ResolvedJavaType type, JavaTypeProfile profile) {
        if (b.parsingIntrinsic() && wordOperationPlugin.handleInstanceOf(b, object, type, profile)) {
            return true;
        }
        return false;
    }
}
