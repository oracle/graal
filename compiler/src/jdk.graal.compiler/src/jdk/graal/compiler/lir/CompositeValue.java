/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/**
 * Base class to represent values that need to be stored in more than one register. This is mainly
 * intended to support addresses and not general arbitrary nesting of composite values. Because of
 * the possibility of sharing of CompositeValues they should be immutable.
 */
public abstract class CompositeValue extends Value {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Component {

        LIRInstruction.OperandFlag[] value() default LIRInstruction.OperandFlag.REG;
    }

    public CompositeValue(ValueKind<?> kind) {
        super(kind);
    }

    /**
     * Invoke {@code proc} on each {@link Value} element of this {@link CompositeValue}. If
     * {@code proc} replaces any value then a new CompositeValue should be returned.
     *
     * @param inst
     * @param mode
     * @param proc
     * @return the original CompositeValue or a copy with any modified values
     */
    public abstract CompositeValue forEachComponent(LIRInstruction inst, LIRInstruction.OperandMode mode, InstructionValueProcedure proc);

    protected abstract void visitEachComponent(LIRInstruction inst, LIRInstruction.OperandMode mode, InstructionValueConsumer proc);

    /**
     * Must be implemented by subclasses.
     */
    @Override
    public abstract String toString();

    @Override
    public int hashCode() {
        return 53 * super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CompositeValue) {
            CompositeValue other = (CompositeValue) obj;
            return super.equals(other);
        }
        return false;
    }
}
