/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.nodes;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.impl.Accessor;

import java.util.Set;
import java.util.concurrent.locks.Lock;

final class NodeAccessor extends Accessor {

    static final NodeAccessor ACCESSOR = new NodeAccessor();

    private NodeAccessor() {
    }

    @Override
    protected ThreadLocal<Object> createFastThreadLocal() {
        return super.createFastThreadLocal();
    }

    @Override
    protected void onLoopCount(Node source, int iterations) {
        super.onLoopCount(source, iterations);
    }

    @Override
    protected IndirectCallNode createUncachedIndirectCall() {
        IndirectCallNode callNode = super.createUncachedIndirectCall();
        assert !callNode.isAdoptable();
        return callNode;
    }

    static final class AccessNodes extends NodeSupport {

        @Override
        public boolean isInstrumentable(RootNode rootNode) {
            return rootNode.isInstrumentable();
        }

        @Override
        public void setCallTarget(RootNode rootNode, RootCallTarget callTarget) {
            rootNode.setCallTarget(callTarget);
        }

        @SuppressWarnings("deprecation")
        @Override
        public boolean isTaggedWith(Node node, Class<?> tag) {
            return node.isTaggedWith(tag);
        }

        @Override
        public boolean isCloneUninitializedSupported(RootNode rootNode) {
            return rootNode.isCloneUninitializedSupported();
        }

        @Override
        public RootNode cloneUninitialized(RootNode rootNode) {
            return rootNode.cloneUninitialized();
        }

        @Override
        public int adoptChildrenAndCount(RootNode rootNode) {
            return rootNode.adoptChildrenAndCount();
        }

        @Override
        public Object getEngineObject(LanguageInfo languageInfo) {
            return languageInfo.getEngineObject();
        }

        @Override
        public LanguageInfo createLanguage(Object vmObject, String id, String name, String version, String defaultMimeType, Set<String> mimeTypes, boolean internal, boolean interactive) {
            return new LanguageInfo(vmObject, id, name, version, defaultMimeType, mimeTypes, internal, interactive);
        }

        @Override
        public Object getSourceVM(RootNode rootNode) {
            return rootNode.sourceVM;
        }

        @Override
        public TruffleLanguage<?> getLanguage(RootNode rootNode) {
            return rootNode.language;
        }

        @Override
        public int getRootNodeBits(RootNode root) {
            return root.instrumentationBits;
        }

        @Override
        public void setRootNodeBits(RootNode root, int bits) {
            assert ((byte) bits) == bits : "root bits currently limit to a byte";
            root.instrumentationBits = (byte) bits;
        }

        @Override
        public Lock getLock(Node node) {
            return node.getLock();
        }

        @Override
        public void makeSharableRoot(RootNode rootNode) {
            rootNode.sourceVM = null;
        }

    }
}
