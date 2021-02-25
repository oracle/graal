package com.oracle.svm.hosted.image;

public interface AOTSamplingData {

    void addEntry(int adr, int methodId);
}
