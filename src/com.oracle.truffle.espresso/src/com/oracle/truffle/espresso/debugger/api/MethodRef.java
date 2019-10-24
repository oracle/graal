package com.oracle.truffle.espresso.debugger.api;

import com.oracle.truffle.api.source.Source;

public interface MethodRef {

    long getBCIFromLine(int line);

    Source getSource();

    boolean hasLine(int lineNumber);

    String getSourceFile();

    String getNameAsString();

    String getSignatureAsString();

    int getModifiers();

    int BCItoLineNumber(int bci);

    boolean isMethodNative();

    byte[] getCode();

    klassRef[] getParameters();

    LocalVariableTableRef getLocalVariableTable();

    LineNumberTableRef getLineNumberTable();

    Object invokeMethod(Object callee, Object[] args);
}
