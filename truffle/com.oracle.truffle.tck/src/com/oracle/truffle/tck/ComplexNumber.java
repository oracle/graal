/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.ForeignAccess.Factory;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.RootNode;

public class ComplexNumber implements TruffleObject {

    public static final String REAL_IDENTIFIER = "real";
    public static final String IMAGINARY_IDENTIFIER = "imaginary";

    private final double[] data = new double[2];

    public ComplexNumber(double real, double imaginary) {
        data[0] = real;
        data[1] = imaginary;
    }

    public ForeignAccess getForeignAccess() {
        return ForeignAccess.create(new ComplexForeignAccessFactory());
    }

    private static int identifierToIndex(String identifier) {
        switch (identifier) {
            case REAL_IDENTIFIER:
                return 0;
            case IMAGINARY_IDENTIFIER:
                return 1;
            default:
                throw new IllegalArgumentException();
        }
    }

    public void set(String identifier, double value) {
        data[identifierToIndex(identifier)] = value;
    }

    public double get(String identifier) {
        return data[identifierToIndex(identifier)];
    }

    private static class ComplexForeignAccessFactory implements Factory {

        public boolean canHandle(TruffleObject obj) {
            return obj instanceof ComplexNumber;
        }

        public CallTarget accessMessage(Message tree) {
            if (Message.IS_NULL.equals(tree)) {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(false));
            } else if (Message.IS_EXECUTABLE.equals(tree)) {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(false));
            } else if (Message.IS_BOXED.equals(tree)) {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(false));
            } else if (Message.HAS_SIZE.equals(tree)) {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(false));
            } else if (Message.READ.equals(tree)) {
                return Truffle.getRuntime().createCallTarget(new ComplexReadNode());
            } else if (Message.WRITE.equals(tree)) {
                return Truffle.getRuntime().createCallTarget(new ComplexWriteNode());
            } else {
                throw new IllegalArgumentException(tree.toString() + " not supported");
            }
        }
    }

    private static class ComplexWriteNode extends RootNode {
        protected ComplexWriteNode() {
            super(TckLanguage.class, null, null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            ComplexNumber complex = (ComplexNumber) ForeignAccess.getReceiver(frame);
            String identifier = (String) ForeignAccess.getArguments(frame).get(0);
            Number value = (Number) ForeignAccess.getArguments(frame).get(1);
            complex.set(identifier, value.doubleValue());
            return value;
        }
    }

    private static class ComplexReadNode extends RootNode {
        protected ComplexReadNode() {
            super(TckLanguage.class, null, null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            ComplexNumber complex = (ComplexNumber) ForeignAccess.getReceiver(frame);
            String identifier = (String) ForeignAccess.getArguments(frame).get(0);
            return complex.get(identifier);
        }

    }
}
