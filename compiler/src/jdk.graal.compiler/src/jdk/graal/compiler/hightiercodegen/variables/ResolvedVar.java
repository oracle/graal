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
package jdk.graal.compiler.hightiercodegen.variables;

import jdk.graal.compiler.graph.Node;

import jdk.vm.ci.common.JVMCIError;

public final class ResolvedVar {
    private final Node node;
    private boolean unborn;
    private String resolvedVarName;

    /** Whether the variable's definition is already lowered? */
    private boolean definitionLowered = false;

    public ResolvedVar(Node node) {
        this.node = node;
        unborn = true;
    }

    public void birth(String name) {
        unborn = false;
        this.resolvedVarName = name;
    }

    public void setDefinitionLowered() {
        assert !definitionLowered;
        definitionLowered = true;
    }

    public boolean isDefinitionLowered() {
        return definitionLowered;
    }

    public boolean born() {
        return !unborn;
    }

    public String getName() {
        JVMCIError.guarantee(born(), "Var is not born yet for node:%s", node);
        if (resolvedVarName.endsWith("-1")) {
            JVMCIError.shouldNotReachHere("Var has invalid id " + node.toString());
        }
        return resolvedVarName;
    }

    public Node getOrig() {
        return node;
    }

}
