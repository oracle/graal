package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.espresso.classfile.Utf8Constant;
import com.oracle.truffle.espresso.types.SignatureDescriptor;

public class MethodInfo {

    private Klass declaringKlass;
    private final int flags;
    private final Utf8Constant name;
    private final SignatureDescriptor signatureDescriptor;
    private final AttributeInfo code;
    private final AttributeInfo[] attributes;

    public MethodInfo(Utf8Constant name, SignatureDescriptor signatureDescriptor, int flags, AttributeInfo... attributes) {
        this.name = name;
        this.signatureDescriptor = signatureDescriptor;
        this.flags = flags;
        this.attributes = attributes;

        AttributeInfo codeAttr = null;
        for (int i = 0; i < attributes.length; ++i) {
            if (attributes[i].getName().getValue().equals("Code")) {
                codeAttr = attributes[i];
                break;
            }
        }

        this.code = codeAttr;
    }

    public Klass getDeclaringClass() {
        return declaringKlass;
    }

    public void setDeclaringClass(Klass declaringKlass) {
        this.declaringKlass = declaringKlass;
    }

    public Utf8Constant getName() {
        return name;
    }

    public SignatureDescriptor getSignature() {
        return signatureDescriptor;
    }

    public AttributeInfo getCode() {
        return code;
    }

    public AttributeInfo[] getAttributes() {
        return attributes;
    }
}
