package com.oracle.svm.core;

import org.graalvm.collections.PrefixTree;

public interface ProfilingSampler {

    void registerSampler();

    PrefixTree prefixTree();
}
