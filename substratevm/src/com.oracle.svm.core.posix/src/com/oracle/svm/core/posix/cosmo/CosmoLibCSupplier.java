package com.oracle.svm.core.posix.cosmo;

import com.oracle.svm.core.SubstrateOptions;

import java.util.function.BooleanSupplier;

public class CosmoLibCSupplier implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        return SubstrateOptions.UseLibC.getValue().equals("cosmo");
    }
}
