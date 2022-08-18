/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler;

import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;

import jdk.vm.ci.meta.ResolvedJavaField;

public class TruffleImmutableFrameFieldPlugin implements NodePlugin {

    public static class Options {
        @Option(help = "Whether Truffle should mark final frame fields as immutable.")//
        public static final OptionKey<Boolean> TruffleImmutableFrameFields = new OptionKey<>(true);
    }

    TruffleImmutableFrameFieldPlugin() {
    }

    @Override
    public boolean handleLoadField(GraphBuilderContext b, ValueNode object, ResolvedJavaField field) {
        if (isImmutable(b, field)) {
            ValueNode result = LoadFieldNode.createImmutable(b.getConstantFieldProvider(), b.getConstantReflection(), b.getMetaAccess(), b.getOptions(), b.getAssumptions(), object, field);
            b.addPush(field.getJavaKind(), result);
            return true;
        }
        return false;
    }

    private static boolean isImmutable(GraphBuilderContext b, ResolvedJavaField field) {
        TruffleCompilerRuntime runtime = TruffleCompilerRuntime.getRuntimeIfAvailable();
        if (runtime == null) {
            return false;
        }
        if (field.isStatic()) {
            return false;
        }
        if (!field.isFinal()) {
            return false;
        }
        if (field.isVolatile()) {
            /*
             * Do not handle volatile fields.
             */
            return false;
        }
        if (!field.getDeclaringClass().equals(runtime.resolveType(b.getMetaAccess(), "com.oracle.truffle.api.impl.FrameWithoutBoxing"))) {
            return false;
        }
        return true;
    }

    @Override
    public boolean handleStoreField(GraphBuilderContext b, ValueNode object, ResolvedJavaField field, ValueNode value) {
// if (isImmutable(b, field)) {
// StoreFieldNode storeFieldNode = new StoreFieldNode(object, field, maskSubWordValue(value,
// field.getJavaKind()));
// b.push(field.getJavaKind(), storeFieldNode);
// b.setStateAfter(storeFieldNode);
// return true;
// }
        return false;
    }

    public static void install(Plugins plugins, OptionValues options) {
        if (Options.TruffleImmutableFrameFields.getValue(options) && TruffleCompilerRuntime.getRuntimeIfAvailable() != null) {
            plugins.appendNodePlugin(new TruffleImmutableFrameFieldPlugin());
        }
    }

}
