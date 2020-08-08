/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.impl.Accessor;

final class NodeAccessor extends Accessor {

    private static final NodeAccessor ACCESSOR = new NodeAccessor();

    static final InteropSupport INTEROP = ACCESSOR.interopSupport();
    static final EngineSupport ENGINE = ACCESSOR.engineSupport();
    static final LanguageSupport LANGUAGE = ACCESSOR.languageSupport();
    static final RuntimeSupport RUNTIME = ACCESSOR.runtimeSupport();
    static final InstrumentSupport INSTRUMENT = ACCESSOR.instrumentSupport();

    private NodeAccessor() {
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
        public Object getPolyglotLanguage(LanguageInfo languageInfo) {
            return languageInfo.getPolyglotLanguage();
        }

        @Override
        public LanguageInfo createLanguage(Object polyglotLanguage, String id, String name, String version, String defaultMimeType, Set<String> mimeTypes, boolean internal, boolean interactive) {
            return new LanguageInfo(polyglotLanguage, id, name, version, defaultMimeType, mimeTypes, internal, interactive);
        }

        @Override
        public Object getPolyglotEngine(RootNode rootNode) {
            return rootNode.getEngine();
        }

        @Override
        public TruffleLanguage<?> getLanguage(RootNode rootNode) {
            return rootNode.getLanguage();
        }

        @Override
        public List<TruffleStackTraceElement> findAsynchronousFrames(CallTarget target, Frame frame) {
            CompilerAsserts.neverPartOfCompilation();
            return ((RootCallTarget) target).getRootNode().findAsynchronousFrames(frame);
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
        public void applyPolyglotEngine(RootNode from, RootNode to) {
            to.applyEngineRef(from);
        }

        @Override
        public void forceAdoption(Node parent, Node child) {
            child.setParent(parent);
        }

    }

}
