package jdk.graal.compiler.lir.alloc.verifier;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

public class TaggedJavaConstant implements JavaConstant {
    protected JavaConstant constant;
    protected int tag;

    protected TaggedJavaConstant(JavaConstant constant, int tag) {
        this.constant = constant;
        this.tag = tag;
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

        if (o instanceof TaggedJavaConstant otherTagged) {
            return otherTagged.tag == tag && this.constant.equals(otherTagged.constant);
        }

        return constant.equals(o);
    }

    @Override
    public String toString() {
        return "t" + this.tag + ":" + constant.toString();
    }

    @Override
    public JavaKind getJavaKind() {
        return this.constant.getJavaKind();
    }

    @Override
    public boolean isNull() {
        return this.constant.isNull();
    }

    @Override
    public boolean isDefaultForKind() {
        return this.constant.isDefaultForKind();
    }

    @Override
    public Object asBoxedPrimitive() {
        return this.constant.asBoxedPrimitive();
    }

    @Override
    public int asInt() {
        return this.constant.asInt();
    }

    @Override
    public boolean asBoolean() {
        return this.constant.asBoolean();
    }

    @Override
    public long asLong() {
        return this.constant.asLong();
    }

    @Override
    public float asFloat() {
        return this.constant.asFloat();
    }

    @Override
    public double asDouble() {
        return this.constant.asDouble();
    }

    @Override
    public String toValueString() {
        return this.constant.toValueString();
    }
}
