package com.oracle.truffle.espresso.types;

import com.oracle.truffle.espresso.meta.MetaUtil;

public final class SignatureDescriptors extends DescriptorCache<SignatureDescriptor> {

    private final TypeDescriptors typeDescriptors;

    public SignatureDescriptors(TypeDescriptors typeDescriptors) {
        this.typeDescriptors = typeDescriptors;
    }

    public TypeDescriptors getTypeDescriptors() {
        return typeDescriptors;
    }

    @Override
    protected SignatureDescriptor create(String key) {
        return new SignatureDescriptor(typeDescriptors, key);
    }

// public SignatureDescriptor create(TypeDescriptor returnType, TypeDescriptor... paramenterTypes) {
//
// }

    public SignatureDescriptor create(Class<?> returnType, Class<?>... paramenterTypes) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> param : paramenterTypes) {
            sb.append(MetaUtil.toInternalName(param.getName()));
        }
        sb.append(")");
        sb.append(MetaUtil.toInternalName(returnType.getName()));
        return new SignatureDescriptor(typeDescriptors, sb.toString());
    }
}