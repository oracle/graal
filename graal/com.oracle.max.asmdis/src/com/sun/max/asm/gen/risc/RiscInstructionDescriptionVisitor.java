/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.asm.gen.risc;

import java.util.*;

import com.sun.max.asm.gen.*;
import com.sun.max.asm.gen.risc.field.*;
import com.sun.max.program.*;

/**
 */
public interface RiscInstructionDescriptionVisitor {

    void visitField(RiscField field);

    void visitConstant(RiscConstant constant);

    void visitString(String string);

    void visitConstraint(InstructionConstraint constraint);

    public static final class Static {
        private Static() {
        }

        private static void visitSpecification(RiscInstructionDescriptionVisitor visitor, Object specification) {
            if (specification instanceof RiscField) {
                visitor.visitField((RiscField) specification);
            } else if (specification instanceof RiscConstant) {
                visitor.visitConstant((RiscConstant) specification);
            } else if (specification instanceof String) {
                visitor.visitString((String) specification);
            } else if (specification instanceof InstructionConstraint) {
                visitor.visitConstraint((InstructionConstraint) specification);
            } else {
                throw ProgramError.unexpected("unknown instructionDescription specification: " + specification);
            }
        }

        private static void visitSpecifications(RiscInstructionDescriptionVisitor visitor, List<Object> specifications) {
            for (Object specification : specifications) {
                visitSpecification(visitor, specification);
            }
        }

        public static void visitInstructionDescription(RiscInstructionDescriptionVisitor visitor, InstructionDescription instructionDescription) {
            visitSpecifications(visitor, instructionDescription.specifications());
        }
    }

}
