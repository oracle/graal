package com.oracle.svm.core;

import java.util.function.BooleanSupplier;

public class TrackDynamicAccess implements BooleanSupplier {

    @Override
    public boolean getAsBoolean() {
        return SubstrateOptions.TrackDynamicAccess.hasBeenSet();
    }
}
