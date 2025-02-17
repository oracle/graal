/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.shared.verifier;

import static com.oracle.truffle.espresso.classfile.descriptors.ParserSymbols.ParserTypes;
import static com.oracle.truffle.espresso.shared.verifier.MethodVerifier.failVerify;
import static com.oracle.truffle.espresso.shared.verifier.VerifierError.fatal;

import java.util.ArrayList;

import com.oracle.truffle.espresso.classfile.JavaKind;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.shared.meta.FieldAccess;
import com.oracle.truffle.espresso.shared.meta.MethodAccess;
import com.oracle.truffle.espresso.shared.meta.RuntimeAccess;
import com.oracle.truffle.espresso.shared.meta.TypeAccess;

@SuppressWarnings({"unchecked", "rawtypes"})
abstract class Operand<R extends RuntimeAccess<C, M, F>, C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> {
    private static final Operand[] EMPTY_ARRAY = new Operand[0];

    public static <R extends RuntimeAccess<C, M, F>, C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> Operand<R, C, M, F>[] emptyArray() {
        return EMPTY_ARRAY;
    }

    protected final JavaKind kind;

    Operand(JavaKind kind) {
        this.kind = kind;
    }

    final boolean isTopOperand() {
        return kind == JavaKind.Illegal;
    }

    final JavaKind getKind() {
        return kind;
    }

    final int slots() {
        return isType2() ? 2 : 1;
    }

    final boolean isType2() {
        return (kind == JavaKind.Long || kind == JavaKind.Double);
    }

    boolean isArrayType() {
        return false;
    }

    boolean isReference() {
        return false;
    }

    boolean isPrimitive() {
        return false;
    }

    boolean isReturnAddress() {
        return false;
    }

    Operand<R, C, M, F> getComponent() {
        throw fatal("Calling getComponent of a non-array Operand");
    }

    Operand<R, C, M, F> getElemental() {
        throw fatal("Calling getElemental of a non-array Operand");
    }

    int getDimensions() {
        throw fatal("Calling getDimensions of a non-array Operand");
    }

    Symbol<Type> getType() {
        return null;
    }

    @SuppressWarnings("unused")
    C getKlass(MethodVerifier<R, C, M, F> methodVerifier) {
        return null;
    }

    boolean isUninit() {
        return false;
    }

    boolean isUninitThis() {
        return false;
    }

    boolean isNull() {
        return false;
    }

    abstract boolean compliesWith(Operand<R, C, M, F> other, MethodVerifier<R, C, M, F> methodVerifier);

    boolean compliesWithInMerge(Operand<R, C, M, F> other, MethodVerifier<R, C, M, F> methodVerifier) {
        return compliesWith(other, methodVerifier);
    }

    // Called only after compliesWith returned false, as finding common superType is expensive.
    abstract Operand<R, C, M, F> mergeWith(Operand<R, C, M, F> other, MethodVerifier<R, C, M, F> methodVerifier);
}

class PrimitiveOperand<R extends RuntimeAccess<C, M, F>, C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> extends Operand<R, C, M, F> {
    PrimitiveOperand(JavaKind kind) {
        super(kind);
    }

    @Override
    boolean isPrimitive() {
        return true;
    }

    @Override
    boolean compliesWith(Operand<R, C, M, F> other, MethodVerifier<R, C, M, F> methodVerifier) {
        return (other.isTopOperand()) || other == this;
    }

    @Override
    Operand<R, C, M, F> mergeWith(Operand<R, C, M, F> other, MethodVerifier<R, C, M, F> methodVerifier) {
        assert !compliesWithInMerge(other, methodVerifier) : "mergeWith method should only be called for non-compatible operands.";
        return this == other ? this : null;
    }

    @Override
    public String toString() {
        return kind.toString();
    }

    public PrimitiveOperand<R, C, M, F> toStack() {
        return this;
    }
}

final class ReturnAddressOperand<R extends RuntimeAccess<C, M, F>, C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>>
                extends PrimitiveOperand<R, C, M, F> {
    final ArrayList<Integer> targetBCIs = new ArrayList<>();
    final int subroutineBCI;

    ReturnAddressOperand(int target, int subroutineBCI) {
        super(JavaKind.ReturnAddress);
        targetBCIs.add(target);
        this.subroutineBCI = subroutineBCI;
    }

    private ReturnAddressOperand(ArrayList<Integer> bcis, int subroutineBCI) {
        super(JavaKind.ReturnAddress);
        targetBCIs.addAll(bcis);
        this.subroutineBCI = subroutineBCI;
    }

    @Override
    boolean isReturnAddress() {
        return true;
    }

    @Override
    boolean compliesWith(Operand<R, C, M, F> other, MethodVerifier<R, C, M, F> methodVerifier) {
        if (other.isTopOperand()) {
            return true;
        }
        if (other.isReturnAddress()) {
            ReturnAddressOperand<R, C, M, F> ra = (ReturnAddressOperand<R, C, M, F>) other;
            if (ra.subroutineBCI != subroutineBCI) {
                return false;
            }
            for (Integer target : targetBCIs) {
                if (!ra.targetBCIs.contains(target)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    Operand<R, C, M, F> mergeWith(Operand<R, C, M, F> other, MethodVerifier<R, C, M, F> methodVerifier) {
        assert !compliesWithInMerge(other, methodVerifier) : "mergeWith method should only be called for non-compatible operands.";
        if (!other.isReturnAddress()) {
            return null;
        }
        ReturnAddressOperand<R, C, M, F> otherRA = (ReturnAddressOperand<R, C, M, F>) other;
        if (otherRA.subroutineBCI != subroutineBCI) {
            return null;
        }
        ReturnAddressOperand<R, C, M, F> ra = new ReturnAddressOperand<>(otherRA.targetBCIs, subroutineBCI);
        for (Integer target : targetBCIs) {
            if (!ra.targetBCIs.contains(target)) {
                ra.targetBCIs.add(target);
            }
        }
        return ra;
    }
}

class ReferenceOperand<R extends RuntimeAccess<C, M, F>, C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> extends Operand<R, C, M, F> {
    protected final Symbol<Type> type;
    static final int CPI_UNKNOWN = -1;
    // Load if needed.
    protected C klass = null;
    final int cpi;

    ReferenceOperand(Symbol<Type> type) {
        this(type, CPI_UNKNOWN);
    }

    ReferenceOperand(Symbol<Type> type, int cpi) {
        super(JavaKind.Object);
        this.type = type;
        this.cpi = cpi;
    }

    ReferenceOperand(C klass) {
        super(JavaKind.Object);
        this.type = klass.getSymbolicType();
        this.klass = klass;
        this.cpi = CPI_UNKNOWN;
    }

    @Override
    boolean isReference() {
        return true;
    }

    @Override
    Symbol<Type> getType() {
        return type;
    }

    @Override
    C getKlass(MethodVerifier<R, C, M, F> methodVerifier) {
        if (klass == null) {
            if (getType() == methodVerifier.getThisKlass().getSymbolicType()) {
                klass = methodVerifier.getThisKlass();
            } else if (cpi != CPI_UNKNOWN) {
                klass = methodVerifier.getThisKlass().resolveClassConstantInPool(cpi);
            } else {
                klass = methodVerifier.runtime.lookupOrLoadType(type, methodVerifier.getThisKlass());
            }
            assert klass != null;
        }
        return klass;
    }

    @Override
    boolean compliesWith(Operand<R, C, M, F> other, MethodVerifier<R, C, M, F> methodVerifier) {
        if (other.isReference()) {
            if (type == null || other.getType() == ParserTypes.java_lang_Object) {
                return true;
            }
            if (other.getType() == null) {
                return false;
            }
            if (other.getType() == type) {
                /*
                 * If the two operand have the same type, we can shortcut a few cases:
                 * 
                 * - Both are not loaded -> would load using same CL.
                 * 
                 * - Only one of the two is loaded and in same CL as thisKlass.
                 */
                C otherKlass = ((ReferenceOperand<R, C, M, F>) other).klass;
                if (otherKlass == null || klass == null) {
                    C k = klass == null ? otherKlass : klass;
                    if (k == null || k.hasSameDefiningClassLoader(methodVerifier.getThisKlass())) {
                        return true;
                    }
                }

            }
            C otherKlass = other.getKlass(methodVerifier);
            if (otherKlass.isInterface()) {
                /*
                 * 4.10.1.2. For assignments, interfaces are treated like Object.
                 */
                return true;
            }
            return otherKlass.isAssignableFrom(getKlass(methodVerifier));
        }
        return other.isTopOperand();

    }

    @Override
    boolean compliesWithInMerge(Operand<R, C, M, F> other, MethodVerifier<R, C, M, F> methodVerifier) {
        if (other.isUninit()) {
            return false;
        }
        return compliesWith(other, methodVerifier);
    }

    @Override
    Operand<R, C, M, F> mergeWith(Operand<R, C, M, F> other, MethodVerifier<R, C, M, F> methodVerifier) {
        assert !compliesWithInMerge(other, methodVerifier) : "mergeWith method should only be called for non-compatible operands.";
        if (!other.isReference()) {
            return null;
        }
        if (other.isUninit()) {
            return null;
        }
        if (other.isArrayType()) {
            return methodVerifier.jlObject;
        }
        if (other.isNull()) {
            return this;
        }
        C result = getKlass(methodVerifier).findLeastCommonAncestor(other.getKlass(methodVerifier));
        return result == null ? null : new ReferenceOperand<>(result);
    }

    @Override
    public String toString() {
        return type == null ? "null" : type.toString();
    }
}

final class ArrayOperand<R extends RuntimeAccess<C, M, F>, C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> extends Operand<R, C, M, F> {
    private final int dimensions;
    private final Operand<R, C, M, F> elemental;
    private Operand<R, C, M, F> component = null;

    ArrayOperand(Operand<R, C, M, F> elemental, int dimensions) {
        super(JavaKind.Object);
        assert !elemental.isArrayType();
        if (dimensions > 255) {
            throw failVerify("Creating array of dimension > 255");
        }
        this.dimensions = dimensions;
        this.elemental = elemental;
    }

    ArrayOperand(Operand<R, C, M, F> elemental) {
        this(elemental, 1);
    }

    @Override
    boolean compliesWith(Operand<R, C, M, F> other, MethodVerifier<R, C, M, F> methodVerifier) {
        if (other.isArrayType()) {
            if (other.getDimensions() < getDimensions()) {
                return other.getElemental().isReference() && (other.getElemental().getType() == ParserTypes.java_lang_Object ||
                                other.getElemental().getType() == ParserTypes.java_lang_Cloneable ||
                                other.getElemental().getType() == ParserTypes.java_io_Serializable);
            } else if (other.getDimensions() == getDimensions()) {
                return elemental.compliesWith(other.getElemental(), methodVerifier);
            }
            return false;
        }
        return (other.isTopOperand()) || (other.isReference() && (other.getType() == ParserTypes.java_lang_Object ||
                        other.getType() == ParserTypes.java_lang_Cloneable ||
                        other.getType() == ParserTypes.java_io_Serializable));
    }

    @Override
    Operand<R, C, M, F> mergeWith(Operand<R, C, M, F> other, MethodVerifier<R, C, M, F> methodVerifier) {
        assert !compliesWithInMerge(other, methodVerifier) : "mergeWith method should only be called for non-compatible operands.";
        if (!other.isReference()) {
            return null;
        }
        if (other.isNull()) {
            return this;
        }
        if (!other.isArrayType()) {
            return methodVerifier.jlObject;
        }
        Operand<R, C, M, F> thisElemental = getElemental();
        Operand<R, C, M, F> otherElemental = other.getElemental();
        int otherDim = other.getDimensions();
        int thisDim = getDimensions();
        if (otherDim == thisDim) {
            if (thisElemental.isPrimitive() || otherElemental.isPrimitive()) {
                // We already know they are both different primitives, as this was called because
                // this and other are not compatible.
                assert !compliesWithInMerge(other, methodVerifier);
                if (thisDim == 1) {
                    // example: (byte[] U int[]) -> Object
                    return methodVerifier.jlObject;
                }
                // example: (byte[][] U int[][]) -> Object[]
                return new ArrayOperand<>(methodVerifier.jlObject, thisDim - 1);
            }
            // example: (A[][] U B[][]) -> (A U B)[][]
            return new ArrayOperand<>(thisElemental.mergeWith(otherElemental, methodVerifier), thisDim);
        }
        Operand<R, C, M, F> smallestElemental;
        if (thisDim < otherDim) {
            smallestElemental = thisElemental;
        } else {
            smallestElemental = otherElemental;
        }
        int newDim = Math.min(thisDim, otherDim);
        if (smallestElemental.isPrimitive()) {
            if (newDim == 1) {
                // example: (byte[] U _[][]) -> Object
                return methodVerifier.jlObject;
            }
            // example: (byte[][] U _[][][]) -> Object[]
            return new ArrayOperand<>(methodVerifier.jlObject, newDim - 1);
        }
        if (smallestElemental.getType() == ParserTypes.java_lang_Cloneable || smallestElemental.getType() == ParserTypes.java_io_Serializable) {
            // example: (Cloneable[][] U _[][][]) -> Cloneable[][]
            return new ArrayOperand<>(smallestElemental, newDim);
        }
        // example: (Object[][] U _[][][]) -> Object[][]
        return new ArrayOperand<>(methodVerifier.jlObject, newDim);
    }

    @Override
    boolean isReference() {
        return true;
    }

    @Override
    boolean isArrayType() {
        return true;
    }

    @Override
    Operand<R, C, M, F> getComponent() {
        if (component == null) {
            if (dimensions == 1) {
                component = elemental;
            } else {
                component = new ArrayOperand<>(elemental, dimensions - 1);
            }
        }
        return component;
    }

    @Override
    Operand<R, C, M, F> getElemental() {
        return elemental;
    }

    @Override
    public String toString() {
        if (dimensions == 1) {
            return "[" + getElemental();
        }
        return "[dim:" + dimensions + "]" + getElemental();
    }

    @Override
    int getDimensions() {
        return dimensions;
    }
}

final class UninitReferenceOperand<R extends RuntimeAccess<C, M, F>, C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>>
                extends ReferenceOperand<R, C, M, F> {
    final int newBCI;

    UninitReferenceOperand(Symbol<Type> type) {
        super(type);
        this.newBCI = -1;
    }

    UninitReferenceOperand(Symbol<Type> type, int newBCI) {
        super(type);
        this.newBCI = newBCI;
    }

    UninitReferenceOperand(C klass) {
        super(klass);
        this.newBCI = -1;
    }

    @Override
    boolean isUninit() {
        return true;
    }

    @Override
    boolean compliesWithInMerge(Operand<R, C, M, F> other, MethodVerifier<R, C, M, F> methodVerifier) {
        if (other.isUninit()) {
            return compliesWith(other, methodVerifier);
        }
        return other.isTopOperand();
    }

    @Override
    Operand<R, C, M, F> mergeWith(Operand<R, C, M, F> other, MethodVerifier<R, C, M, F> methodVerifier) {
        assert !compliesWithInMerge(other, methodVerifier) : "mergeWith method should only be called for non-compatible operands.";
        if (other.isUninit()) {
            return super.mergeWith(other, methodVerifier);
        }
        return null;
    }

    ReferenceOperand<R, C, M, F> init() {
        if (klass == null) {
            return new ReferenceOperand<>(type);
        } else {
            return new ReferenceOperand<>(klass);
        }
    }

    @Override
    boolean isUninitThis() {
        return newBCI == -1;
    }
}
