/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.tools.api.lsp;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.GenerateLibrary.Abstract;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;

/**
 * LSP-specific message library.
 *
 * @since 1.0
 */
@GenerateLibrary(receiverType = TruffleObject.class, assertions = LSPLibrary.Asserts.class)
public abstract class LSPLibrary extends Library {

    static final LibraryFactory<LSPLibrary> FACTORY = LibraryFactory.resolve(LSPLibrary.class);

    /**
     * Get the documentation information about an object. The returned object is either a String, or
     * an object providing <code>MarkupContent</code> protocol interface, with two members
     * <code>kind</code> and <code>value</code>. The <code>kind</code> is a String literal of the
     * markup kind, either <code>plaintext</code>, or <code>markdown</code>. The <code>value</code>
     * is the documentation String. If the kind is <code>markdown</code>, then the value can contain
     * fenced code blocks like in GitHub issues. When just a String is returned,
     * <code>plaintext</code> kind is assumed.
     */
    @Abstract
    @SuppressWarnings("unused")
    public Object getDocumentation(Object object) throws UnsupportedMessageException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    /**
     * Get the signature information about object representing a callable. The returned object has a
     * structure that corresponds to <code>SignatureInformation<code> protocol interface. The
     * <code>TruffleLanguage.toString(Object, Object)</code> should provide the String
     * representation of the signature, displayed as a label.
     * <p>
     * Description of properties of the returned object:
     * <ul>
     * <li><b><code>documentation</code></b> - Either a String providing the callable documentation,
     * or an object providing <code>MarkupContent</code>, see {@link #getDocumentation(Object)} for
     * details.</li>
     * <li><b><code>parameters</code></b> - An array of objects representing the
     * <code>ParameterInformation</code> protocol interface describing parameters of the callable
     * signature. Every <code>ParameterInformation</code> object has following properties:
     * <ul>
     * <li><b><code>label</code></b> - String label of this parameter (a substring of its containing
     * signature label), or an integer array of size 2 providing inclusive start and exclusive end
     * offsets within its containing signature label. The intended use case is to highlight the
     * parameter label part in the signature label.</li>
     * <li><b><code>documentation</code></b> - Either a String providing the parameter
     * documentation, or an object providing <code>MarkupContent</code>, see
     * {@link #getDocumentation(Object)} for details.</li>
     * </ul>
     * </ul>
     */
    @Abstract
    @SuppressWarnings("unused")
    public Object getSignature(Object object) throws UnsupportedMessageException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    LSPLibrary() {
    }

    public static LibraryFactory<LSPLibrary> getFactory() {
        return FACTORY;
    }

    static class Asserts extends LSPLibrary {

        @Child private LSPLibrary delegate;

        Asserts(LSPLibrary delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean accepts(Object receiver) {
            assert receiver != null;
            return delegate.accepts(receiver);
        }

        @Override
        public Object getDocumentation(Object object) throws UnsupportedMessageException {
            assert object != null;
            Object doc = delegate.getDocumentation(object);
            assert isDocumentation(doc) : "Wrong documentation of " + object + " : " + doc;
            return doc;
        }

        @Override
        public Object getSignature(Object object) throws UnsupportedMessageException {
            assert InteropLibrary.getFactory().getUncached().isExecutable(object) : "Expecting an executable, got " + object;
            Object signature = delegate.getSignature(object);
            assert isSignature(signature) : "Wrong signature of " + object + " : " + signature;
            return signature;
        }

        private static boolean isDocumentation(Object doc) {
            if (doc instanceof String) {
                return true;
            }
            InteropLibrary interop = InteropLibrary.getFactory().getUncached();
            assert doc instanceof TruffleObject;
            return interop.isMemberInvocable(doc, "markdown") ||
                            interop.isMemberInvocable(doc, "plaintext");
        }

        @CompilerDirectives.TruffleBoundary
        private static boolean isSignature(Object signature) {
            InteropLibrary interop = InteropLibrary.getFactory().getUncached();
            assert signature instanceof TruffleObject;
            try {
                if (interop.isMemberReadable(signature, "documentation")) {
                    if (!isDocumentation(interop.readMember(signature, "documentation"))) {
                        assert false : "Wrong documentation of signature " + signature;
                        return false;
                    }
                }
                if (interop.isMemberReadable(signature, "parameters")) {
                    Object parameters = interop.readMember(signature, "parameters");
                    assert interop.hasArrayElements(parameters) : "Parameters of " + signature + " is not an array";
                    long size = interop.getArraySize(parameters);
                    for (long i = 0; i < size; i++) {
                        assert interop.isArrayElementReadable(parameters, i);
                        Object param = interop.readArrayElement(parameters, i);
                        assert interop.isMemberReadable(param, "label");
                        Object label = interop.readMember(param, "label");
                        assert label instanceof String || interop.hasArrayElements(label);
                        if (interop.hasArrayElements(label)) {
                            long rangeSize = interop.getArraySize(label);
                            assert 2 == rangeSize : "Label range must be an array of size 2, but was " + rangeSize;
                            Object l1 = interop.readArrayElement(label, 0);
                            Object l2 = interop.readArrayElement(label, 1);
                            assert interop.fitsInInt(l1) && interop.fitsInInt(l2);
                            // int i1 = interop.asInt(l1);
                            // int i2 = interop.asInt(l2);
                            // assert 0 <= i1 && i1 <= i2 && i2 <= signatureLabel.length();
                        }
                        if (interop.isMemberReadable(param, "documentation")) {
                            if (!isDocumentation(interop.readMember(signature, "documentation"))) {
                                assert false : "Wrong documentation of parameter " + param + " of signature " + signature;
                                return false;
                            }
                        }
                    }
                }
            } catch (InteropException ex) {
                throw new AssertionError("Checking signature " + signature, ex);
            }
            return true;
        }
    }
}
