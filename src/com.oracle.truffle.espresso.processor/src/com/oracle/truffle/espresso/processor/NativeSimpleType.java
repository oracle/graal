package com.oracle.truffle.espresso.processor;

/**
 * A copy of the NativeSimpleType enum found in
 * {@link com.oracle.truffle.nfi.spi.types.NativeSimpleType}. The two of them must be synchronized.
 */
enum NativeSimpleType {
    VOID,
    UINT8,
    SINT8,
    UINT16,
    SINT16,
    UINT32,
    SINT32,
    UINT64,
    SINT64,
    FLOAT,
    DOUBLE,
    POINTER,
    STRING,
    OBJECT,
    NULLABLE;
}
