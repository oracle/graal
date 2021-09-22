package com.oracle.svm.core;

import org.graalvm.collections.LockFreePrefixTree;

public interface ProfilingSampler {

    void registerSampler();

    LockFreePrefixTree prefixTree();
}
