package com.oracle.truffle.espresso._native;

/**
 * Encoded native types similar to j* types defined for JNI.
 * 
 * This enum provides an unambiguous mapping from Java to native (similar to JNI j* types), and from
 * native to Java.
 * 
 * {@link #BOOLEAN} encodes a native 1-byte value, a boolean in Java.
 * 
 * {@link #SHORT} and {@link #CHAR} represents the same native 2-byte value, but a short and char
 * types and in Java.
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
