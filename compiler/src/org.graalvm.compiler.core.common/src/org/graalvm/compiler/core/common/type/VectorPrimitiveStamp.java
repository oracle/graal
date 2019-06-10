package org.graalvm.compiler.core.common.type;

import java.nio.ByteBuffer;
import java.util.Objects;

import org.graalvm.compiler.debug.GraalError;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SerializableConstant;

public abstract class VectorPrimitiveStamp extends ArithmeticStamp {

    private final PrimitiveStamp scalar;
    private final int elementCount;

    protected VectorPrimitiveStamp(PrimitiveStamp scalar, int elementCount, ArithmeticOpTable ops) {
        super(ops);
        this.scalar = scalar;
        this.elementCount = elementCount;
    }

    @Override
    public void accept(Visitor v) {
        v.visitInt(elementCount * scalar.getBits());
    }

    public int getElementCount() {
        return elementCount;
    }

    public PrimitiveStamp getScalar() {
        return scalar;
    }

    @Override
    public SerializableConstant deserialize(ByteBuffer buffer) {
        throw GraalError.shouldNotReachHere("deserialization not supported for integer vector");
    }

    @Override
    public ResolvedJavaType javaType(MetaAccessProvider metaAccess) {
        throw GraalError.shouldNotReachHere("vector has no java type");
    }

    @Override
    public JavaKind getStackKind() {
        throw GraalError.shouldNotReachHere("vector does not have stack kind");
    }

    @Override
    public Constant readConstant(MemoryAccessProvider provider, Constant base, long displacement) {
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        VectorPrimitiveStamp that = (VectorPrimitiveStamp) o;
        return elementCount == that.elementCount &&
                scalar.equals(that.scalar);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), scalar, elementCount);
    }
}
