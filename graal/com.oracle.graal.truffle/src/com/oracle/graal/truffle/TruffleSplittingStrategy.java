package com.oracle.graal.truffle;

public interface TruffleSplittingStrategy {

    void beforeCall(Object[] arguments);

    void afterCall(Object returnValue);

    void forceSplitting();

}
