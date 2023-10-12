/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.phases;

import com.oracle.graal.pointsto.constraints.UnresolvedElementException;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.graphbuilderconf.ClassInitializationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;

import java.util.function.Supplier;

/**
 * Do not trigger any class initialization, and do not emit any class initialization checks during
 * bytecode parsing.
 */
public class NoClassInitializationPlugin implements ClassInitializationPlugin {

    private boolean printWarnings;

    public NoClassInitializationPlugin() {
        this(false);
    }

    public NoClassInitializationPlugin(boolean printWarnings) {
        this.printWarnings = printWarnings;
    }

    @Override
    public boolean supportsLazyInitialization(ConstantPool cp) {
        return true;
    }

    @Override
    public void loadReferencedType(GraphBuilderContext builder, ConstantPool cp, int cpi, int bytecode) {
        /* Do not trigger class initialization. */
        try {
            cp.loadReferencedType(cpi, bytecode, false);
        } catch (UnresolvedElementException uee) {
            // Report warning message when PrintUnresolvedElementWarning option is set. So user
            // knows what happens when
            // the analysis result is not the expected.
            if (printWarnings) {
                System.out.println("Warning: " + uee.getMessage());
            }
        } catch (Throwable ex) {
            /* Plugin should be non-intrusive. Therefore we ignore missing class-path failures. */
        }
    }

    @Override
    public boolean apply(GraphBuilderContext builder, ResolvedJavaType type, Supplier<FrameState> frameState) {
        return false;
    }
}
