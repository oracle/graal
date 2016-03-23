package com.oracle.truffle.api.impl;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

final class DefaultTVMCI extends TVMCI {

    @Override
    protected void onLoopCount(Node source, int count) {
        // do nothing
    }

    @SuppressWarnings("rawtypes")
    Class<? extends TruffleLanguage> findLanguage(RootNode root) {
        return super.findLanguageClass(root);
    }

    void initCallTarget(DefaultCallTarget callTarget) {
        super.onFirstExecution(callTarget.getRootNode());
    }

}