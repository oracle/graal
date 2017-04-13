/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tck;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.tck.impl.TckLanguage;

@SuppressWarnings("unused")
@MessageResolution(receiverType = ComplexNumbersB.class)
class ComplexNumbersBMessageResolution {
    @Resolve(message = "GET_SIZE")
    abstract static class ComplexNumbersBGetSizeNode extends Node {

        public Object access(ComplexNumbersB complexNumbers) {
            assert complexNumbers.getReals().length == complexNumbers.getImags().length;
            return complexNumbers.getReals().length;
        }
    }

    @Resolve(message = "HAS_SIZE")
    abstract static class ComplexNumbersBHasSizeNode extends Node {

        public Object access(ComplexNumbersB complexNumbers) {
            return true;
        }
    }

    @Resolve(message = "READ")
    abstract static class ComplexNumbersBReadNode extends Node {
        public Object access(ComplexNumbersB complexNumbers, Number index) {
            return new ComplexNumberBEntry(complexNumbers, index.intValue());
        }
    }

    @Resolve(message = "WRITE")
    abstract static class ComplexNumbersBWriteNode extends Node {
        @Child private Node readReal;
        @Child private Node readImag;

        public Object access(VirtualFrame frame, ComplexNumbersB complexNumbers, Number index, TruffleObject value) {
            if (readReal == null || readImag == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.readReal = insert(Message.READ.createNode());
                this.readImag = insert(Message.READ.createNode());
            }
            try {
                Number realPart = TckLanguage.expectNumber(ForeignAccess.sendRead(readReal, value, ComplexNumber.REAL_IDENTIFIER));
                Number imagPart = TckLanguage.expectNumber(ForeignAccess.sendRead(readImag, value, ComplexNumber.IMAGINARY_IDENTIFIER));
                complexNumbers.getReals()[index.intValue()] = realPart.doubleValue();
                complexNumbers.getImags()[index.intValue()] = imagPart.doubleValue();
                return value;
            } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                return null;
            }
        }

    }

}
