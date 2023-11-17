/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.SnippetCounter;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Extends {@link HotSpotConstantFieldProvider} to override the implementation of
 * {@link #readConstantField} with Graal specific semantics.
 */
public class HotSpotGraalConstantFieldProvider extends HotSpotConstantFieldProvider {

    public HotSpotGraalConstantFieldProvider(GraalHotSpotVMConfig config, MetaAccessProvider metaAccess) {
        super(config, metaAccess);
        this.metaAccess = metaAccess;
    }

    @Override
    protected boolean isStaticFieldConstant(ResolvedJavaField field, OptionValues options) {
        return super.isStaticFieldConstant(field, options);
    }

    @Override
    protected boolean isFinalFieldValueConstant(ResolvedJavaField field, JavaConstant value, ConstantFieldTool<?> tool) {
        if (super.isFinalFieldValueConstant(field, value, tool)) {
            return true;
        }

        if (!field.isStatic()) {
            JavaConstant receiver = tool.getReceiver();
            if (getSnippetCounterType().isInstance(receiver) || getNodeClassType().isInstance(receiver)) {
                return true;
            }
        }

        return false;
    }

    @Override
    protected boolean isStableFieldValueConstant(ResolvedJavaField field, JavaConstant value, ConstantFieldTool<?> tool) {
        if (super.isStableFieldValueConstant(field, value, tool)) {
            return true;
        }

        if (!field.isStatic()) {
            JavaConstant receiver = tool.getReceiver();
            if (getHotSpotVMConfigType().isInstance(receiver)) {
                return true;
            }
        }

        return false;
    }

    private final MetaAccessProvider metaAccess;

    private ResolvedJavaType cachedHotSpotVMConfigType;
    private ResolvedJavaType cachedSnippetCounterType;
    private ResolvedJavaType cachedNodeClassType;

    private ResolvedJavaType getHotSpotVMConfigType() {
        if (cachedHotSpotVMConfigType == null) {
            cachedHotSpotVMConfigType = metaAccess.lookupJavaType(GraalHotSpotVMConfig.class);
        }
        return cachedHotSpotVMConfigType;
    }

    private ResolvedJavaType getSnippetCounterType() {
        if (cachedSnippetCounterType == null) {
            cachedSnippetCounterType = metaAccess.lookupJavaType(SnippetCounter.class);
        }
        return cachedSnippetCounterType;
    }

    private ResolvedJavaType getNodeClassType() {
        if (cachedNodeClassType == null) {
            cachedNodeClassType = metaAccess.lookupJavaType(NodeClass.class);
        }
        return cachedNodeClassType;
    }
}
