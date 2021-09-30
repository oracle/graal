package com.oracle.svm.core.meta;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.spi.LIRKindTool;
import org.graalvm.compiler.core.common.type.AbstractPointerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

public class SubstrateMethodPointerStamp extends AbstractPointerStamp {

    private static final SubstrateMethodPointerStamp METHOD_NON_NULL = new SubstrateMethodPointerStamp(true, false);
    private static final SubstrateMethodPointerStamp METHOD_ALWAYS_NULL = new SubstrateMethodPointerStamp(false, true);
    private static final SubstrateMethodPointerStamp METHOD = new SubstrateMethodPointerStamp(false, false);

    protected SubstrateMethodPointerStamp(boolean nonNull, boolean alwaysNull) {
        super(nonNull, alwaysNull);
    }

    public static SubstrateMethodPointerStamp methodNonNull() {
        return METHOD_NON_NULL;
    }

    @Override
    protected AbstractPointerStamp copyWith(boolean newNonNull, boolean newAlwaysNull) {
        if (newNonNull) {
            assert !newAlwaysNull;
            return METHOD_NON_NULL;
        } else if (newAlwaysNull) {
            return METHOD_ALWAYS_NULL;
        } else {
            return METHOD;
        }
    }

    @Override
    public ResolvedJavaType javaType(MetaAccessProvider metaAccess) {
        throw GraalError.shouldNotReachHere("pointer has no Java type");
    }

    @Override
    public LIRKind getLIRKind(LIRKindTool tool) {
        return tool.getWordKind();
    }

    @Override
    public Stamp join(Stamp other) {
        return defaultPointerJoin(other);
    }

    @Override
    public Stamp empty() {
        // there is no empty pointer stamp
        return this;
    }

    @Override
    public Stamp constant(Constant c, MetaAccessProvider meta) {
        if (JavaConstant.NULL_POINTER.equals(c)) {
            return METHOD_ALWAYS_NULL;
        } else {
            assert c instanceof SubstrateMethodVMConstant;
            return METHOD_NON_NULL;
        }
    }

    @Override
    public boolean isCompatible(Stamp other) {
        return other instanceof SubstrateMethodPointerStamp;
    }

    @Override
    public boolean isCompatible(Constant constant) {
        return constant instanceof SubstrateMethodVMConstant;
    }

    @Override
    public boolean hasValues() {
        return true;
    }

    @Override
    public Constant readConstant(MemoryAccessProvider provider, Constant base, long displacement) {
        return null;
    }

    @Override
    public String toString() {
        return "SVMMethod*";
    }
}
