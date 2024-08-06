/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.meta;

import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderTool;
import jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.TypePlugin;
import jdk.graal.compiler.word.Word;
import jdk.graal.compiler.word.WordOperationPlugin;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This plugin does HotSpot-specific customization of bytecode parsing:
 * <ul>
 * <li>{@link Word}-type rewriting for {@link GraphBuilderContext#parsingIntrinsic intrinsic}
 * functions (snippets and method substitutions), by forwarding to the {@link WordOperationPlugin}.
 * Note that we forward the {@link NodePlugin} and {@link TypePlugin} methods, but not the
 * {@link InlineInvokePlugin} methods implemented by {@link WordOperationPlugin}. The latter is not
 * necessary because HotSpot only uses the {@link Word} type in methods that are force-inlined,
 * i.e., there are never non-inlined invokes that involve the {@link Word} type.</li>
 * <li>Constant folding of field loads.</li>
 * </ul>
 */
public final class HotSpotNodePlugin implements NodePlugin, TypePlugin {

    protected final WordOperationPlugin wordOperationPlugin;

    public HotSpotNodePlugin(WordOperationPlugin wordOperationPlugin) {
        this.wordOperationPlugin = wordOperationPlugin;
    }

    @Override
    public boolean canChangeStackKind(GraphBuilderContext b) {
        if (b.parsingIntrinsic()) {
            return wordOperationPlugin.canChangeStackKind(b);
        }
        return false;
    }

    @Override
    public StampPair interceptType(GraphBuilderTool b, JavaType declaredType, boolean nonNull) {
        if (b.parsingIntrinsic()) {
            return wordOperationPlugin.interceptType(b, declaredType, nonNull);
        }
        return null;
    }

    @Override
    public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        if (b.parsingIntrinsic() && wordOperationPlugin.handleInvoke(b, method, args)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean handleLoadField(GraphBuilderContext b, ValueNode object, ResolvedJavaField field) {
        if (b.parsingIntrinsic() && wordOperationPlugin.handleLoadField(b, object, field)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean handleLoadStaticField(GraphBuilderContext b, ResolvedJavaField field) {
        if (b.parsingIntrinsic() && wordOperationPlugin.handleLoadStaticField(b, field)) {
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
    public boolean handleLoadIndexed(GraphBuilderContext b, ValueNode array, ValueNode index, GuardingNode boundsCheck, JavaKind elementKind) {
        if (b.parsingIntrinsic() && wordOperationPlugin.handleLoadIndexed(b, array, index, boundsCheck, elementKind)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean handleStoreIndexed(GraphBuilderContext b, ValueNode array, ValueNode index, GuardingNode boundsCheck, GuardingNode storeCheck, JavaKind elementKind, ValueNode value) {
        if (b.parsingIntrinsic() && wordOperationPlugin.handleStoreIndexed(b, array, index, boundsCheck, storeCheck, elementKind, value)) {
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
