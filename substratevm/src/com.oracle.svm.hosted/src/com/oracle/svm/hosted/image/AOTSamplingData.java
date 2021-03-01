package com.oracle.svm.hosted.image;

public interface AOTSamplingData {

    void addEntry(long adr, int methodId);
}
