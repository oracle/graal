package com.oracle.truffle.espresso.types;

public final class SignatureDescriptors extends DescriptorCache<SignatureDescriptor> {
    @Override
    protected SignatureDescriptor create(String key) {
        return new SignatureDescriptor(key);
    }
}