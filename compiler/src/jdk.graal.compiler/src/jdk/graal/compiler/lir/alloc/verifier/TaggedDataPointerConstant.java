package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.type.DataPointerConstant;

import java.nio.ByteBuffer;

public class TaggedDataPointerConstant extends DataPointerConstant {
    protected DataPointerConstant constant;
    protected int tag;

    protected TaggedDataPointerConstant(DataPointerConstant constant, int tag) {
        super(constant.getAlignment());
        this.constant = constant;
        this.tag = tag;
    }

    @Override
    public int getSerializedSize() {
        return this.constant.getSerializedSize();
    }

    @Override
    public void serialize(ByteBuffer buffer) {
        this.constant.serialize(buffer);
    }

    @Override
    public String toValueString() {
        return this.constant.toValueString();
    }

    @Override
    public int hashCode() {
        return this.tag ^ constant.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o instanceof TaggedDataPointerConstant otherTagged) {
            return otherTagged.tag == tag && this.constant.equals(otherTagged.constant);
        }

        return constant.equals(o);
    }

    @Override
    public String toString() {
        return "t" + this.tag + ":" + constant.toString();
    }
}
