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

import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.tck.impl.TckLanguage;

@SuppressWarnings("unused")
@MessageResolution(receiverType = ComplexNumber.class)
class ComplexNumberMessageResolution {
    @Resolve(message = "GET_SIZE")
    abstract static class ComplexGetSizeNode extends Node {
        public Object access(ComplexNumber complex) {
            return 2;
        }
    }

    @Resolve(message = "HAS_SIZE")
    abstract static class ComplexHasSizeNode extends Node {
        public Object access(ComplexNumber complex) {
            return true;
        }
    }

    @Resolve(message = "INVOKE")
    abstract static class ComplexInvokeNode extends Node {

        private static final String ADD = "add";
        private static final String SUB = "sub";

        public Object access(ComplexNumber complex, String identifier, Object[] arguments) {
            if (arguments.length != 1) {
                throw ArityException.raise(1, arguments.length);
            }
            if (!(arguments[0] instanceof ComplexNumber)) {
                throw UnsupportedTypeException.raise(arguments);
            }
            ComplexNumber a = complex;
            ComplexNumber b = (ComplexNumber) arguments[0];

            switch (identifier) {
                case ADD:
                    return add(a, b);
                case SUB:
                    return sub(a, b);
                default:
                    throw UnknownIdentifierException.raise(identifier);
            }
        }

        private static ComplexNumber add(ComplexNumber a, ComplexNumber b) {
            a.set(ComplexNumber.REAL_IDENTIFIER, a.get(ComplexNumber.REAL_IDENTIFIER) + b.get(ComplexNumber.REAL_IDENTIFIER));
            a.set(ComplexNumber.IMAGINARY_IDENTIFIER, a.get(ComplexNumber.IMAGINARY_IDENTIFIER) + b.get(ComplexNumber.IMAGINARY_IDENTIFIER));
            return a;
        }

        private static ComplexNumber sub(ComplexNumber a, ComplexNumber b) {
            a.set(ComplexNumber.REAL_IDENTIFIER, a.get(ComplexNumber.REAL_IDENTIFIER) - b.get(ComplexNumber.REAL_IDENTIFIER));
            a.set(ComplexNumber.IMAGINARY_IDENTIFIER, a.get(ComplexNumber.IMAGINARY_IDENTIFIER) - b.get(ComplexNumber.IMAGINARY_IDENTIFIER));
            return a;
        }
    }

    @Resolve(message = "IS_NULL")
    abstract static class ComplexIsNullNode extends Node {

        public Object access(ComplexNumber complex) {
            return false;
        }
    }

    @Resolve(message = "READ")
    abstract static class ComplexReadNode extends Node {

        public Object access(ComplexNumber complex, String identifier) {
            return complex.get(identifier);
        }

        public Object access(ComplexNumber complex, int index) {
            if (index == 0) {
                return complex.get(ComplexNumber.REAL_IDENTIFIER);
            } else if (index == 1) {
                return complex.get(ComplexNumber.IMAGINARY_IDENTIFIER);
            }
            throw UnknownIdentifierException.raise("Index " + index + " out of bounds (idx 0 = real; idx 1 = imag");
        }
    }

    @Resolve(message = "WRITE")
    abstract static class ComplexWriteNode extends Node {
        public Object access(ComplexNumber complex, String identifier, Number value) {
            complex.set(identifier, value.doubleValue());
            return value;
        }

        public Object access(ComplexNumber complex, int index, Number value) {
            if (index == 0) {
                complex.set(ComplexNumber.REAL_IDENTIFIER, value.doubleValue());
                return value;
            } else if (index == 1) {
                complex.set(ComplexNumber.IMAGINARY_IDENTIFIER, value.doubleValue());
                return value;
            }
            throw UnknownIdentifierException.raise("Index " + index + " out of bounds (idx 0 = real; idx 1 = imag");
        }
    }

}
