/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements;

import java.util.BitSet;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderTool;
import org.graalvm.compiler.nodes.graphbuilderconf.ParameterPlugin;

/**
 * A {@link ParameterPlugin} that sets non-null stamps for parameters annotated with
 * {@link org.graalvm.compiler.api.replacements.Snippet.NonNullParameter}.
 */
public class NonNullParameterPlugin implements ParameterPlugin {
    private final BitSet nonNullParameters;

    public NonNullParameterPlugin(BitSet nonNullParameters) {
        this.nonNullParameters = nonNullParameters;
    }

    @Override
    public FloatingNode interceptParameter(GraphBuilderTool b, int index, StampPair stamp) {
        if (!nonNullParameters.get(index)) {
            return null;
        }
        Stamp trusted = stamp.getTrustedStamp();
        assert trusted != null;
        Stamp improvedTrusted = trusted.tryImproveWith(StampFactory.objectNonNull());
        if (improvedTrusted == trusted || improvedTrusted == null) {
            GraalError.guarantee(improvedTrusted != null, "Non-Object parameter annotated with @NonNullParameter?");
            return null;
        }
        Stamp uncheckedStamp = stamp.getUncheckedStamp();
        Stamp improvedUntrusted = uncheckedStamp == null ? null : uncheckedStamp.improveWith(StampFactory.objectNonNull());
        return new ParameterNode(index, StampPair.create(improvedTrusted, improvedUntrusted));
    }
}
