/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.model;

import java.util.*;

public final class GuardExpression {

    private GuardData resolvedGuard;
    private NodeExecutionData[] resolvedChildren;

    private final String guardName;
    private final boolean negated;
    private final String[] childNames;

    public GuardExpression(String expression, boolean allowArguments) {
        String exp = expression;
        if (exp.startsWith("!")) {
            exp = exp.substring(1, exp.length());
            negated = true;
        } else {
            negated = false;
        }

        int argumentStart = exp.indexOf('(');
        int endIndex = exp.lastIndexOf(')');
        if (allowArguments && argumentStart != -1 && endIndex != -1) {
            guardName = exp.substring(0, argumentStart).trim();
            String arguments = exp.substring(argumentStart + 1, endIndex);
            String[] children = arguments.split(",");
            for (int i = 0; i < children.length; i++) {
                children[i] = children[i].trim();
            }
            if (children.length == 1 && children[0].isEmpty()) {
                childNames = new String[0];
            } else {
                childNames = children;
            }
        } else {
            guardName = exp;
            childNames = null;
        }
    }

    public String[] getChildNames() {
        return childNames;
    }

    public boolean isResolved() {
        return resolvedGuard != null;
    }

    public String getGuardName() {
        return guardName;
    }

    public NodeExecutionData[] getResolvedChildren() {
        return resolvedChildren;
    }

    public void setResolvedChildren(NodeExecutionData[] resolvedChildren) {
        this.resolvedChildren = resolvedChildren;
    }

    public void setResolvedGuard(GuardData guard) {
        this.resolvedGuard = guard;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj instanceof GuardExpression) {
            GuardExpression other = (GuardExpression) obj;
            if (isResolved() && other.isResolved()) {
                return resolvedGuard.equals(other.resolvedGuard) && negated == other.negated && Arrays.equals(resolvedChildren, other.resolvedChildren);
            } else {
                boolean equal = guardName.equals(other.guardName) && negated == other.negated;
                if (childNames != null && other.childNames != null) {
                    equal &= Arrays.equals(childNames, other.childNames);
                }
                return equal;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(guardName, negated, resolvedGuard, resolvedChildren);
    }

    public final boolean implies(GuardExpression other) {
        if (equals(other)) {
            return true;
        }

        if (isResolved() && other.isResolved()) {
            for (GuardExpression implies : getResolvedGuard().getImpliesExpressions()) {
                if (implies.getGuardName().equals(other.getGuardName())) {
                    if (implies.isNegated() == other.isNegated()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return (negated ? "!" : "") + guardName;
    }

    public boolean isNegated() {
        return negated;
    }

    public GuardData getResolvedGuard() {
        return resolvedGuard;
    }

}
