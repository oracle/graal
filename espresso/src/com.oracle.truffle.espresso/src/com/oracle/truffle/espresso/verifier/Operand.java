/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.verifier;

import static com.oracle.truffle.espresso.verifier.MethodVerifier.Invalid;
import static com.oracle.truffle.espresso.verifier.MethodVerifier.failNoClassDefFound;
import static com.oracle.truffle.espresso.verifier.MethodVerifier.failVerify;
import static com.oracle.truffle.espresso.verifier.MethodVerifier.isType2;
import static com.oracle.truffle.espresso.verifier.MethodVerifier.jlObject;

import java.util.ArrayList;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.runtime.EspressoException;

abstract class Operand {
    public static final Operand[] EMPTY_ARRAY = new Operand[0];

    protected final JavaKind kind;

    Operand(JavaKind kind) {
        this.kind = kind;
    }

    final boolean isTopOperand() {
        return this == Invalid;
    }

    final JavaKind getKind() {
        return kind;
    }

    final int slots() {
        return isType2(this) ? 2 : 1;
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

    Operand getComponent() {
        throw EspressoError.shouldNotReachHere("Calling getComponent of a non-array Operand");
    }

    Operand getElemental() {
        throw EspressoError.shouldNotReachHere("Calling getElemental of a non-array Operand");
    }

    int getDimensions() {
        throw EspressoError.shouldNotReachHere("Calling getDimensions of a non-array Operand");
    }

    Symbol<Type> getType() {
        return null;
    }

    Klass getKlass() {
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

    abstract boolean compliesWith(Operand other);

    boolean compliesWithInMerge(Operand other) {
        return compliesWith(other);
    }

    // Called only after compliesWith returned false, as finding common superType is expensive.
    abstract Operand mergeWith(Operand other);
}

class PrimitiveOperand extends Operand {
    PrimitiveOperand(JavaKind kind) {
        super(kind);
    }

    @Override
    boolean isPrimitive() {
        return true;
    }

    @Override
    boolean compliesWith(Operand other) {
        return (other.isTopOperand()) || other == this;
    }

    @Override
    Operand mergeWith(Operand other) {
        return this == other ? this : null;
    }

    @Override
    public String toString() {
        return kind.toString();
    }

    public PrimitiveOperand toStack() {
        return this;
    }
}

final class ReturnAddressOperand extends PrimitiveOperand {
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
    boolean compliesWith(Operand other) {
        if (other.isTopOperand()) {
            return true;
        }
        if (other.isReturnAddress()) {
            ReturnAddressOperand ra = (ReturnAddressOperand) other;
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
    Operand mergeWith(Operand other) {
        if (!other.isReturnAddress()) {
            return null;
        }
        ReturnAddressOperand otherRA = (ReturnAddressOperand) other;
        if (otherRA.subroutineBCI != subroutineBCI) {
            return null;
        }
        ReturnAddressOperand ra = new ReturnAddressOperand(otherRA.targetBCIs, subroutineBCI);
        for (Integer target : targetBCIs) {
            if (!ra.targetBCIs.contains(target)) {
                ra.targetBCIs.add(target);
            }
        }
        return ra;
    }
}

class ReferenceOperand extends Operand {
    protected final Symbol<Type> type;
    final Klass thisKlass;

    // Load if needed.
    protected Klass klass = null;

    ReferenceOperand(Symbol<Type> type, Klass thisKlass) {
        super(JavaKind.Object);
        this.type = type;
        this.thisKlass = thisKlass;
    }

    ReferenceOperand(Klass klass, Klass thisKlass) {
        super(JavaKind.Object);
        this.type = klass.getType();
        this.klass = klass;
        this.thisKlass = thisKlass;
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
    @SuppressWarnings("try")
    Klass getKlass() {
        if (klass == null) {
            try {
                if (getType() == thisKlass.getType()) {
                    klass = thisKlass;
                } else {
                    try (EspressoLanguage.DisableSingleStepping ignored = thisKlass.getLanguage().disableStepping()) {
                        klass = thisKlass.getMeta().resolveSymbolOrNull(type, thisKlass.getDefiningClassLoader(), thisKlass.protectionDomain());
                    }
                }
            } catch (EspressoException e) {
                // TODO(garcia) fine grain this catch
                if (thisKlass.getMeta().java_lang_ClassNotFoundException.isAssignableFrom(e.getGuestException().getKlass())) {
                    throw failNoClassDefFound(type.toString());
                }
                throw e;
            }
            if (klass == null) {
                throw failNoClassDefFound(type.toString());
            }
        }
        return klass;
    }

    @Override
    boolean compliesWith(Operand other) {
        if (other.isReference()) {
            if (type == null || other.getType() == Type.java_lang_Object) {
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
                Klass otherKlass = ((ReferenceOperand) other).klass;
                if (otherKlass == null || klass == null) {
                    Klass k = klass == null ? otherKlass : klass;
                    if (k == null || k.getDefiningClassLoader() == thisKlass.getDefiningClassLoader()) {
                        return true;
                    }
                }

            }
            Klass otherKlass = other.getKlass();
            if (otherKlass.isInterface()) {
                /*
                 * 4.10.1.2. For assignments, interfaces are treated like Object.
                 */
                return true;
            }
            return otherKlass.isAssignableFrom(getKlass());
        }
        return other.isTopOperand();

    }

    @Override
    boolean compliesWithInMerge(Operand other) {
        if (other.isUninit()) {
            return false;
        }
        return compliesWith(other);
    }

    @Override
    Operand mergeWith(Operand other) {
        if (!other.isReference()) {
            return null;
        }
        if (other.isUninit()) {
            return null;
        }
        if (other.isArrayType()) {
            return jlObject;
        }
        if (other.isNull()) {
            return this;
        }
        Klass result = getKlass().findLeastCommonAncestor(other.getKlass());
        return result == null ? null : new ReferenceOperand(result, thisKlass);
    }

    @Override
    public String toString() {
        return type == null ? "null" : type.toString();
    }
}

final class ArrayOperand extends Operand {
    private final int dimensions;
    private final Operand elemental;
    private Operand component = null;

    ArrayOperand(Operand elemental, int dimensions) {
        super(JavaKind.Object);
        assert !elemental.isArrayType();
        if (dimensions > 255) {
            throw failVerify("Creating array of dimension > 255");
        }
        this.dimensions = dimensions;
        this.elemental = elemental;
    }

    ArrayOperand(Operand elemental) {
        this(elemental, 1);
    }

    @Override
    boolean compliesWith(Operand other) {
        if (other.isArrayType()) {
            if (other.getDimensions() < getDimensions()) {
                return other.getElemental().isReference() && (other.getElemental().getType() == Type.java_lang_Object ||
                                other.getElemental().getType() == Type.java_lang_Cloneable ||
                                other.getElemental().getType() == Type.java_io_Serializable);
            } else if (other.getDimensions() == getDimensions()) {
                return elemental.compliesWith(other.getElemental());
            }
            return false;
        }
        return (other.isTopOperand()) || (other.isReference() && (other.getType() == Type.java_lang_Object ||
                        other.getType() == Type.java_lang_Cloneable ||
                        other.getType() == Type.java_io_Serializable));
    }

    @Override
    Operand mergeWith(Operand other) {
        if (!other.isReference()) {
            return null;
        }
        if (other.isNull()) {
            return this;
        }
        if (!other.isArrayType()) {
            return jlObject;
        }
        Operand thisElemental = getElemental();
        Operand otherElemental = other.getElemental();
        int otherDim = other.getDimensions();
        int thisDim = getDimensions();
        if (otherDim == thisDim) {
            if (thisElemental.isPrimitive() || otherElemental.isPrimitive()) {
                return new ArrayOperand(jlObject, thisDim);
            }
            return new ArrayOperand(thisElemental.mergeWith(otherElemental), thisDim);
        }
        Operand smallestElemental;
        if (thisDim < otherDim) {
            smallestElemental = thisElemental;
        } else {
            smallestElemental = otherElemental;
        }
        if (smallestElemental.isPrimitive()) {
            return new ArrayOperand(jlObject, Math.min(thisDim, otherDim));
        }
        if (smallestElemental.getType() == Type.java_lang_Cloneable || smallestElemental.getType() == Type.java_io_Serializable) {
            return new ArrayOperand(smallestElemental, Math.min(thisDim, otherDim));
        }
        return new ArrayOperand(jlObject, Math.min(thisDim, otherDim));
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
    Operand getComponent() {
        if (component == null) {
            if (dimensions == 1) {
                component = elemental;
            } else {
                component = new ArrayOperand(elemental, dimensions - 1);
            }
        }
        return component;
    }

    @Override
    Operand getElemental() {
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

final class UninitReferenceOperand extends ReferenceOperand {
    final int newBCI;

    UninitReferenceOperand(Symbol<Type> type, Klass thisKlass) {
        super(type, thisKlass);
        this.newBCI = -1;
    }

    UninitReferenceOperand(Symbol<Type> type, Klass thisKlass, int newBCI) {
        super(type, thisKlass);
        this.newBCI = newBCI;
    }

    UninitReferenceOperand(Klass klass, Klass thisKlass) {
        super(klass, thisKlass);
        this.newBCI = -1;
    }

    @Override
    boolean isUninit() {
        return true;
    }

    @Override
    boolean compliesWithInMerge(Operand other) {
        if (other.isUninit()) {
            return compliesWith(other);
        }
        return other.isTopOperand();
    }

    @Override
    Operand mergeWith(Operand other) {
        if (other.isUninit()) {
            return super.mergeWith(other);
        }
        return null;
    }

    ReferenceOperand init() {
        if (klass == null) {
            return new ReferenceOperand(type, thisKlass);
        } else {
            return new ReferenceOperand(klass, thisKlass);
        }
    }

    @Override
    boolean isUninitThis() {
        return newBCI == -1;
    }
}
