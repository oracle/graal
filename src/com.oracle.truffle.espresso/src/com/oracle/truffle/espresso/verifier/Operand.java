package com.oracle.truffle.espresso.verifier;

import static com.oracle.truffle.espresso.verifier.MethodVerifier.Invalid;
import static com.oracle.truffle.espresso.verifier.MethodVerifier.jlObject;

import java.util.ArrayList;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.JavaKind;

abstract class Operand {
    static public Operand[] EMPTY_ARRAY = new Operand[0];

    protected JavaKind kind;

    Operand(JavaKind kind) {
        this.kind = kind;
    }

    JavaKind getKind() {
        return kind;
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
        return null;
    }

    Operand getElemental() {
        return null;
    }

    Symbol<Type> getType() {
        return null;
    }

    Klass getKlass() {
        return null;
    }

    int getDimensions() {
        return -1;
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
        return (other == Invalid) || other == this;
    }

    @Override
    Operand mergeWith(Operand other) {
        return this == other ? this : null;
    }

    @Override
    public String toString() {
        return kind.toString();
    }
}

class ReturnAddressOperand extends PrimitiveOperand {
    ArrayList<Integer> targetBCIs = new ArrayList<>();

    ReturnAddressOperand(int target) {
        super(JavaKind.ReturnAddress);
        targetBCIs.add(target);
    }

    private ReturnAddressOperand(ArrayList<Integer> bcis) {
        super(JavaKind.ReturnAddress);
        targetBCIs.addAll(bcis);
    }

    @Override
    boolean isReturnAddress() {
        return true;
    }

    @Override
    boolean compliesWith(Operand other) {
        if (other == Invalid) {
            return true;
        }
        if (other.isReturnAddress()) {
            ReturnAddressOperand ra = (ReturnAddressOperand) other;
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
        ReturnAddressOperand ra = new ReturnAddressOperand(((ReturnAddressOperand) other).targetBCIs);
        for (Integer target : targetBCIs) {
            if (!ra.targetBCIs.contains(target)) {
                ra.targetBCIs.add(target);
            }
        }
        return ra;
    }
}

class ReferenceOperand extends Operand {
    protected Symbol<Type> type;
    Klass thisKlass;

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
    Klass getKlass() {
        if (klass == null) {
            try {
                if (getType() == thisKlass.getType()) {
                    klass = thisKlass;
                } else {
                    klass = thisKlass.getMeta().loadKlass(type, thisKlass.getDefiningClassLoader());
                }
            } catch (Exception e) {
                // TODO(garcia) fine grain this catch
            }
            if (klass == null) {
                throw new NoClassDefFoundError(type.toString());
            }
        }
        return klass;
    }

    @Override
    boolean compliesWith(Operand other) {
        if (other.isReference()) {
            if (type == null || other.getType() == this.type || other.getType() == Type.Object) {
                return true;
            }
            if (other.getType() == null) {
                return false;
            }
            Klass otherKlass = other.getKlass();
            if (otherKlass.isInterface()) {
                /**
                 * 4.10.1.2. For assignments, interfaces are treated like Object.
                 */
                return true;
            }
            return otherKlass.isAssignableFrom(getKlass());
        }
        return other == Invalid;
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

class ArrayOperand extends Operand {
    private int dimensions;
    private Operand elemental;
    private Operand component = null;

    ArrayOperand(Operand elemental, int dimensions) {
        super(JavaKind.Object);
        assert !elemental.isArrayType();
        if (dimensions > 255) {
            throw new VerifyError("Creating array of dimension > 255");
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
                return other.getElemental().isReference() && (other.getElemental().getType() == Type.Object ||
                                other.getElemental().getType() == Type.Cloneable ||
                                other.getElemental().getType() == Type.Serializable);
            } else if (other.getDimensions() == getDimensions()) {
                return elemental.compliesWith(other.getElemental());
            }
            return false;
        }
        return (other == Invalid) || (other.isReference() && (other.getType() == Type.Object ||
                        other.getType() == Type.Cloneable ||
                        other.getType() == Type.Serializable));
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
        if (smallestElemental.getType() == Type.Cloneable || smallestElemental.getType() == Type.Serializable) {
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

class UninitReferenceOperand extends ReferenceOperand {
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
        return other == Invalid;
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