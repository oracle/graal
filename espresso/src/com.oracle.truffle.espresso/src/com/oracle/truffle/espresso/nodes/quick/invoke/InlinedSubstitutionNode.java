/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.nodes.quick.invoke;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.bytecode.Bytecodes;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.substitutions.JavaSubstitution;
import com.oracle.truffle.espresso.substitutions.Substitutions;

public final class InlinedSubstitutionNode extends InlinedMethodNode {
    @Child private JavaSubstitution substitution;

    InlinedSubstitutionNode(Method inlinedMethod, int top, int callerBCI, int opcode, int statementIndex, JavaSubstitution substitution) {
        super(inlinedMethod, top, callerBCI, opcode, statementIndex);
        this.substitution = insert(substitution);
    }

    public static InlinedSubstitutionNode lookupInlineSubstitution(Method m, int top, int callerBCI, int opcode, int statementIndex) {
        if (canInline(m, opcode)) {
            JavaSubstitution.Factory factory = Substitutions.lookupSubstitution(m);
            if (factory != null) {
                return InlinedSubstitutionNode.create(m, top, opcode, callerBCI, statementIndex, factory);
            }
        }
        return null;
    }

    public static InlinedSubstitutionNode create(Method inlinedMethod, int top, int callerBCI, int opcode, int statementIndex, JavaSubstitution.Factory factory) {
        return new InlinedSubstitutionNode(inlinedMethod, top, callerBCI, opcode, statementIndex, factory.create());
    }

    public static boolean canInline(Method m, int opcode) {
        if (opcode == Bytecodes.INVOKESTATIC || opcode == Bytecodes.INVOKESPECIAL) {
            return true;
        }
        if (opcode == Bytecodes.INVOKEVIRTUAL && (m.isFinalFlagSet() || m.isPrivate() || m.getDeclaringKlass().isFinalFlagSet())) {
            return true;
        }
        return false;
    }

    @Override
    public int execute(VirtualFrame frame) {
        Object result = substitution.invoke(getArguments(frame));
        return pushResult(frame, result);
    }
}
