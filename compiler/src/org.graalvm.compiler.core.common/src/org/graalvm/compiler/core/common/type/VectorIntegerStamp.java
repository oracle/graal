package org.graalvm.compiler.core.common.type;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.spi.LIRKindTool;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.MetaAccessProvider;

public class VectorIntegerStamp extends VectorPrimitiveStamp {

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
        return new VectorIntegerStamp((IntegerStamp) getScalar().meet(other.getScalar()), newElementCount, null);
    }

    @Override
    public Stamp join(Stamp otherStamp) {
        if (otherStamp == this) {
            return this;
        }

        final VectorIntegerStamp other = (VectorIntegerStamp) otherStamp;
        final int newElementCount = Math.min(getElementCount(), other.getElementCount());

        // TODO: Figure out OPS
        return new VectorIntegerStamp(getScalar().join(other.getScalar()), newElementCount, null);
    }

    @Override
    public Stamp unrestricted() {
        // TODO: Figure out OPS
        return new VectorIntegerStamp(getScalar().unrestricted(), getElementCount(), null);
    }

    @Override
    public Stamp empty() {
        // TODO: Figure out OPS
        return new VectorIntegerStamp(getScalar().empty(), getElementCount(), null);
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

    @Override
    public String toString() {
        final StringBuilder str = new StringBuilder();
        if (hasValues()) {
            str.append("vector of ");
            str.append(getScalar().toString());
        } else {
            str.append("<empty>");
        }
        return str.toString();
    }

}
