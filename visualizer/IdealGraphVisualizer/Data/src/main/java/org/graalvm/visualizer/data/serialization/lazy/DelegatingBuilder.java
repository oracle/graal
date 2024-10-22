/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package org.graalvm.visualizer.data.serialization.lazy;

import static jdk.graal.compiler.graphio.parsing.BinaryReader.Method;

import java.util.List;

import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.api.annotations.common.NonNull;

import jdk.graal.compiler.graphio.parsing.Builder;
import jdk.graal.compiler.graphio.parsing.ConstantPool;
import jdk.graal.compiler.graphio.parsing.NameTranslator;
import jdk.graal.compiler.graphio.parsing.model.*;

/**
 * Helper class which delegates to another builder. Used to switch processing
 * for different data containers. All interface methods are intentionally
 * overridable.
 */
class DelegatingBuilder implements Builder {
    private ModelControl poolExchange;
    /**
     * The current delegate
     */
    private Builder delegate;

    /**
     * Switches the delegate. Subsequent calls to {@link DelegatingBuilder}
     * implemetations will delegate to the new instance. Pay attention whether
     * {@code super.* methods} are called before or after this or, or are called
     * at all.
     *
     * @param newDelegate the new instance of builder to delegate to
     * @return the builder
     */
    protected final Builder delegateTo(Builder newDelegate) {
        this.delegate = newDelegate;
        if (delegate != null && poolExchange != null) {
            delegate.setModelControl(poolExchange);
        }
        return newDelegate;
    }

    protected final Builder delegate() {
        return delegate;
    }

    @Override
    public final GraphDocument rootDocument() {
        return delegate.rootDocument();
    }

    @Override
    public void startDocumentHeader() {
        delegate.startDocumentHeader();
    }

    @Override
    public void endDocumentHeader() {
        delegate.endDocumentHeader();
    }

    @Override
    public void setProperty(String key, Object value) {
        delegate.setProperty(key, value);
    }

    @Override
    public void startNestedProperty(String propertyKey) {
        delegate.startNestedProperty(propertyKey);
    }

    @Override
    public void setPropertySize(int size) {
        delegate.setPropertySize(size);
    }

    @Override
    public void startGraphContents(InputGraph g) {
        delegate.startGraphContents(g);
    }

    @Override
    @CheckForNull
    public InputGraph startGraph(int dumpId, String format, Object[] args) {
        checkConstantPool();
        return delegate.startGraph(dumpId, format, args);
    }

    @Override
    @CheckForNull
    public InputGraph endGraph() {
        return delegate.endGraph();
    }

    @Override
    public void start() {
        delegate.start();
    }

    @Override
    public void end() {
        delegate.end();
    }

    @Override
    @CheckForNull
    public Group startGroup() {
        checkConstantPool();
        return delegate.startGroup();
    }

    @Override
    public void startGroupContent() {
        delegate.startGroupContent();
    }

    @Override
    public void endGroup() {
        delegate.endGroup();
    }

    @Override
    public void graphContentDigest(byte[] digest) {
        delegate.graphContentDigest(digest);
    }

    @Override
    public void markGraphDuplicate() {
        delegate.markGraphDuplicate();
    }

    @Override
    public void startNode(int nodeId, boolean hasPredecessors, NodeClass nodeClass) {
        delegate.startNode(nodeId, hasPredecessors, nodeClass);
    }

    @Override
    public void endNode(int nodeId) {
        delegate.endNode(nodeId);
    }

    @Override
    public void setGroupName(String name, String shortName) {
        delegate.setGroupName(name, shortName);
    }

    @Override
    public void setNodeName(NodeClass nodeClass) {
        delegate.setNodeName(nodeClass);
    }

    @Override
    public void setNodeProperty(String key, Object value) {
        delegate.setNodeProperty(key, value);
    }

    @Override
    public void inputEdge(Port p, int from, int to, char num, int index) {
        delegate.inputEdge(p, from, to, num, index);
    }

    @Override
    public void successorEdge(Port p, int from, int to, char num, int index) {
        delegate.successorEdge(p, from, to, num, index);
    }

    @Override
    public void makeGraphEdges() {
        delegate.makeGraphEdges();
    }

    @Override
    @CheckForNull
    public InputBlock startBlock(int id) {
        return delegate.startBlock(id);
    }

    @Override
    @CheckForNull
    public InputBlock startBlock(String name) {
        return delegate.startBlock(name);
    }

    @Override
    public void endBlock(int id) {
        delegate.endBlock(id);
    }

    @Override
    @CheckForNull
    public Properties getNodeProperties(int nodeId) {
        return delegate.getNodeProperties(nodeId);
    }

    @Override
    public void addNodeToBlock(int nodeId) {
        delegate.addNodeToBlock(nodeId);
    }

    @Override
    public void addBlockEdge(int from, int to) {
        delegate.addBlockEdge(from, to);
    }

    @Override
    public void makeBlockEdges() {
        delegate.makeBlockEdges();
    }

    @Override
    public void setMethod(String name, String shortName, int bci, Method method) {
        delegate.setMethod(name, shortName, bci, method);
    }

    @Override
    public void resetStreamData() {
        delegate.resetStreamData();
    }

    @Override
    @NonNull
    public ConstantPool getConstantPool() {
        return delegate.getConstantPool();
    }

    @Override
    public void startRoot() {
        delegate.startRoot();
    }

    @Override
    public void setModelControl(ModelControl poolExchange) {
        this.poolExchange = poolExchange;
        if (delegate != null && poolExchange != null) {
            delegate.setModelControl(poolExchange);
        }
    }

    private void checkConstantPool() {
        assert getConstantPool() == poolExchange.getConstantPool();
    }

    @Override
    public NameTranslator prepareNameTranslator() {
        return delegate.prepareNameTranslator();
    }

    @Override
    public void reportLoadingError(String logMessage, List<String> parentNames) {
        delegate.reportLoadingError(logMessage, parentNames);
    }
}
