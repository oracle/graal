package com.oracle.truffle.api.impl;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * An interface between Truffle API and hosting virtual machine. Not interesting for regular Truffle
 * API users. Acronym for Truffle Virtual Machine Compiler Interface.
 *
 * @since 0.12
 */
public abstract class TVMCI {
    /**
     * Only useful for virtual machine implementors.
     *
     * @since 0.12
     */
    protected TVMCI() {
        // export only for com.oracle.graal.truffle and com.oracle.truffle.api.impl
        assert getClass().getPackage().getName().equals("com.oracle.graal.truffle") || getClass().getPackage().getName().equals("com.oracle.truffle.api.impl");
    }

    // the class is a member class to not be (easily) visible in Javadoc

    /**
     * Reports the execution count of a loop.
     *
     * @param source the Node which invoked the loop.
     * @param iterations the number iterations to report to the runtime system
     * @since 0.12
     */
    protected abstract void onLoopCount(Node source, int iterations);

    /**
     * Makes sure the <code>rootNode</code> is initialized.
     *
     * @param rootNode
     * @since 0.12
     */
    protected void onFirstExecution(RootNode rootNode) {
        Accessor.SPI.onFirstExecution(rootNode);
    }

    /**
     * Finds the language associated with given root node.
     *
     * @param root the node
     * @return the language of the node
     * @since 0.12
     */
    @SuppressWarnings({"rawtypes"})
    protected Class<? extends TruffleLanguage> findLanguageClass(RootNode root) {
        return Accessor.SPI.findLanguage(root);
    }

}