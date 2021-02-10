package com.oracle.truffle.espresso.processor;

/**
 * To keep in sync with com.oracle.truffle.espresso._native.NativeType.
 */
public enum NativeType {

    VOID,
    BOOLEAN, // 1 byte
    BYTE,
    CHAR, // unsigned short
    SHORT,
    INT,
    LONG,
    FLOAT,
    DOUBLE,

    OBJECT, // word-sized handle
    POINTER,
}
