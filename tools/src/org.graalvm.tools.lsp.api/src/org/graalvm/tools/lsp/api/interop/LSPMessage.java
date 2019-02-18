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
package org.graalvm.tools.lsp.api.interop;

import com.oracle.truffle.api.interop.Message;

/**
 * LSP-specific messages.
 *
 * @since 1.0
 */
public abstract class LSPMessage extends Message {

    /**
     * Message to get the documentation information about an object. The returned object is either a
     * String, or an object providing <code>MarkupContent</code> via one <code>getValue</code>
     * method that takes the markup <code>kind</code> as an argument. Currently
     * <code>plaintext</code> and <code>markdown</code> are the supported kinds. If the format is
     * <code>markdown</code>, then the value can contain fenced code blocks like in GitHub issues.
     * When no format argument is provided, <code>plaintext</code> is assumed.
     */
    public static final Message GET_DOCUMENTATION = GetDocumentation.INSTANCE;

    /**
     * Message to get the signature information about object representing a callable. The returned
     * object have a structure that corresponds to <code>SignatureInformation<code> protocol
     * interface. The <code>TruffleLanguage.toString(Object, Object)</code> should provide the
     * String representation of the signature, displayed as a label.
     * <p>
     * Description of properties of the returned object:
     * <ul>
     * <li><b><code>documentation</code></b> - Either a String providing the callable documentation,
     * or an object providing <code>MarkupContent</code>, see {@link #GET_DOCUMENTATION} for
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
     * {@link #GET_DOCUMENTATION} for details.</li>
     * </ul>
     * </ul>
     */
    public static final Message GET_SIGNATURE = GetSignature.INSTANCE;

    LSPMessage() {
    }

}
