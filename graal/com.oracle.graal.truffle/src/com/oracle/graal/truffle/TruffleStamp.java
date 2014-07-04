package com.oracle.graal.truffle;

/**
 * Experimental.
 */
public abstract interface TruffleStamp {

    TruffleStamp join(TruffleStamp p);

    TruffleStamp joinValue(Object value);

    boolean isCompatible(Object value);

    String toStringShort();

}