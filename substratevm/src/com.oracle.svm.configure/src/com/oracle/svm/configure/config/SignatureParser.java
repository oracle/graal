package com.oracle.svm.configure.config;

import java.util.ArrayList;
import java.util.List;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaUtil;

class SignatureParser {
    public static String[] getParameterTypes(String signature) {
        List<String> list = new ArrayList<>();
        int position = 1;
        int arrayDimensions = 0;
        while (signature.charAt(position) != ')') {
            String type = null;
            if (signature.charAt(position) == '[') {
                arrayDimensions++;
            } else if (signature.charAt(position) == 'L') {
                int end = signature.indexOf(';', position + 1);
                type = MetaUtil.internalNameToJava(signature.substring(position, end + 1), true, false);
                position = end;
            } else {
                type = JavaKind.fromPrimitiveOrVoidTypeChar(signature.charAt(position)).toString();
            }
            position++;
            if (type != null) {
                String s = type;
                for (; arrayDimensions > 0; arrayDimensions--) {
                    s += "[]";
                }
                list.add(s);
            }
        }
        // ignore return type
        if (arrayDimensions > 0) {
            throw new IllegalArgumentException("Invalid array in signature: " + signature);
        }
        return list.toArray(new String[0]);
    }
}
