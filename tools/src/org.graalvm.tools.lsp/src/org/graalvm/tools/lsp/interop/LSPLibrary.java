/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.lsp.interop;

import com.oracle.truffle.api.CompilerDirectives;
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
@GenerateLibrary()
public abstract class LSPLibrary extends Library {

    static final LibraryFactory<LSPLibrary> FACTORY = LibraryFactory.resolve(LSPLibrary.class);

    /**
     * Get the documentation information about an object. The returned object is either a String, or
     * an object providing <code>MarkupContent</code> via invocation of member whose name represents
     * the markup kind. Currently <code>plaintext</code> and <code>markdown</code> are the supported
     * kinds. If the format is <code>markdown</code>, then the value can contain fenced code blocks
     * like in GitHub issues. When a String is returned, <code>plaintext</code> kind is assumed.
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

}
