/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.nodes.type;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.spi.LIRKindTool;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.SymbolicJVMCIReference;
import jdk.graal.compiler.debug.GraalError;

import jdk.graal.compiler.vector.nodes.simd.SimdStamp;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Describes vector values. A vector value is a immutable one-dimensional array with fixed length
 * and fixed element type.
 */
public class VectorStamp extends Stamp {

    private final Stamp element;

    public VectorStamp(Stamp element) {
        this.element = element;
    }

    @Override
    public void accept(Visitor v) {
        element.accept(v);
    }

    /**
     * Stamp describing the elements of the vector.
     */
    public Stamp getElementStamp() {
        return element;
    }

    public Stamp toSimd(int length) {
        if (length == 1) {
            return element;
        } else {
            return SimdStamp.broadcast(element, length);
        }
    }

    @Override
    public JavaKind getStackKind() {
        return JavaKind.Illegal;
    }

    @Override
    public boolean isCompatible(Stamp other) {
        if (other instanceof VectorStamp) {
            return element.isCompatible(((VectorStamp) other).element);
        } else {
            return false;
        }
    }

    @Override
    public boolean isCompatible(Constant constant) {
        return false;
    }

    @Override
    public boolean isPointerStamp() {
        return element.isPointerStamp();
    }

    @Override
    public boolean isIntegerStamp() {
        return element.isIntegerStamp();
    }

    @Override
    public boolean isFloatStamp() {
        return element.isFloatStamp();
    }

    @Override
    public boolean isObjectStamp() {
        return element.isObjectStamp();
    }

    @Override
    public Stamp unrestricted() {
        return new VectorStamp(element.unrestricted());
    }

    @Override
    public Stamp empty() {
        return new VectorStamp(element.empty());
    }

    @Override
    public Stamp constant(Constant c, MetaAccessProvider meta) {
        throw GraalError.shouldNotReachHere("Constant can not contain vector value"); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public boolean hasValues() {
        return element.hasValues();
    }

    @Override
    public LIRKind getLIRKind(LIRKindTool tool) {
        throw GraalError.unimplementedOverride(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public ResolvedJavaType javaType(MetaAccessProvider metaAccess) {
        throw GraalError.shouldNotReachHere("vector stamp has no Java type"); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public Stamp join(Stamp otherStamp) {
        VectorStamp other = (VectorStamp) otherStamp;
        assert element.isCompatible(other.element) : element + " vs " + other.element;
        Stamp newElement = this.element.join(other.element);
        if (newElement == null) {
            return this.element;
        } else {
            return new VectorStamp(newElement);
        }
    }

    @Override
    public Stamp improveWith(Stamp otherStamp) {
        VectorStamp other = (VectorStamp) otherStamp;
        assert element.isCompatible(other.element) : element + " vs " + other.element;
        Stamp newElement = this.element.tryImproveWith(other.element);
        if (newElement == null) {
            return this;
        } else {
            return new VectorStamp(newElement);
        }
    }

    @Override
    public Stamp meet(Stamp otherStamp) {
        VectorStamp other = (VectorStamp) otherStamp;
        assert element.isCompatible(other.element) : element + " vs " + other.element;
        Stamp newElement = this.element.meet(other.element);
        return new VectorStamp(newElement);
    }

    @Override
    public boolean alwaysDistinct(Stamp otherStamp) {
        return false;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("V ");
        str.append(element);
        return str.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((element == null) ? 0 : element.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof VectorStamp)) {
            return false;
        }

        VectorStamp other = (VectorStamp) obj;
        return this.element.equals(other.element);
    }

    @Override
    public Constant readConstant(MemoryAccessProvider provider, Constant base, long displacement) {
        return null;
    }

    @Override
    public SymbolicJVMCIReference<VectorStamp> makeSymbolic() {
        SymbolicJVMCIReference<? extends Stamp> e = element.makeSymbolic();
        if (e != null) {
            return new SymbolicVectorStamp(e);
        }
        return null;
    }

    static class SymbolicVectorStamp implements SymbolicJVMCIReference<VectorStamp> {
        final SymbolicJVMCIReference<? extends Stamp> element;

        SymbolicVectorStamp(SymbolicJVMCIReference<? extends Stamp> element) {
            this.element = element;
        }

        @Override
        public VectorStamp resolve(ResolvedJavaType accessingClass) {
            return new VectorStamp(element.resolve(accessingClass));
        }
    }
}
