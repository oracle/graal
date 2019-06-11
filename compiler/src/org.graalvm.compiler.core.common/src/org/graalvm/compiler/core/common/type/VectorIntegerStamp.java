package org.graalvm.compiler.core.common.type;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.spi.LIRKindTool;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.MetaAccessProvider;

import org.graalvm.compiler.core.common.type.ArithmeticOpTable.BinaryOp;

public class VectorIntegerStamp extends VectorPrimitiveStamp {

    public static final ArithmeticOpTable OPS = new ArithmeticOpTable(
            null,
            new BinaryOp.Add(true, true) {
                @Override
                public Constant foldConstant(Constant a, Constant b) {
                    return null;
                }

                @Override
                public Stamp foldStamp(Stamp a, Stamp b) {
                    if (a.isEmpty()) {
                        return a;
                    }

                    if (b.isEmpty()) {
                        return b;
                    }

                    // Can only be unrestricted so return a
                    return a;
                }
            },
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
    );

    public static VectorIntegerStamp create(IntegerStamp scalar, int elementCount) {
        // TODO: Figure out OPS
        return new VectorIntegerStamp(scalar, elementCount, OPS);
    }

    private VectorIntegerStamp(IntegerStamp scalar, int elementCount, ArithmeticOpTable ops) {
        super(scalar, elementCount, ops);
    }

    @Override
    public IntegerStamp getScalar() {
        return (IntegerStamp) super.getScalar();
    }

    @Override
    public LIRKind getLIRKind(LIRKindTool tool) {
        return tool.getVectorIntegerKind(getScalar().getBits(), getElementCount());
    }

    @Override
    public Stamp meet(Stamp otherStamp) {
        if (otherStamp == this) {
            return this;
        }

        if (isEmpty()) {
            return otherStamp;
        }

        final VectorIntegerStamp other = (VectorIntegerStamp) otherStamp;
        final int newElementCount = Math.max(getElementCount(), other.getElementCount());

        // TODO: Figure out OPS
        return VectorIntegerStamp.create((IntegerStamp) getScalar().meet(other.getScalar()), newElementCount);
    }

    @Override
    public Stamp join(Stamp otherStamp) {
        if (otherStamp == this) {
            return this;
        }

        final VectorIntegerStamp other = (VectorIntegerStamp) otherStamp;
        final int newElementCount = Math.min(getElementCount(), other.getElementCount());

        return VectorIntegerStamp.create(getScalar().join(other.getScalar()), newElementCount);
    }

    @Override
    public Stamp unrestricted() {
        return VectorIntegerStamp.create(getScalar().unrestricted(), getElementCount());
    }

    @Override
    public Stamp empty() {
        return VectorIntegerStamp.create(getScalar().empty(), getElementCount());
    }

    @Override
    public Stamp constant(Constant c, MetaAccessProvider meta) {
        // Constant not supported
        return this;
    }

    @Override
    public boolean isCompatible(Stamp stamp) {
        if (this == stamp) {
            return true;
        }

        if (stamp instanceof VectorIntegerStamp) {
            final VectorIntegerStamp other = (VectorIntegerStamp) stamp;
            return getScalar().isCompatible(other.getScalar()) && getElementCount() == other.getElementCount();
        }

        return false;
    }

    @Override
    public boolean isCompatible(Constant constant) {
        return false;
    }

    @Override
    public boolean hasValues() {
        return getScalar().hasValues() && getElementCount() > 0;
    }

}
