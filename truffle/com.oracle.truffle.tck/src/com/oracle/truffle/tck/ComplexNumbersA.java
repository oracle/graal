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
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.ForeignAccess.Factory;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

public final class ComplexNumbersA implements TruffleObject {

    private final double[] data;

    public ComplexNumbersA(double[] data) {
        assert data.length % 2 == 0;
        this.data = data;
    }

    public double[] getData() {
        return data;
    }

    public ForeignAccess getForeignAccess() {
        return ForeignAccess.create(new ComplexNumbersAForeignAccessFactory());
    }

    private static class ComplexNumbersAForeignAccessFactory implements Factory {

        public boolean canHandle(TruffleObject obj) {
            return obj instanceof ComplexNumbersA;
        }

        public CallTarget accessMessage(Message tree) {
            if (Message.IS_NULL.equals(tree)) {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(false));
            } else if (Message.IS_EXECUTABLE.equals(tree)) {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(false));
            } else if (Message.IS_BOXED.equals(tree)) {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(false));
            } else if (Message.HAS_SIZE.equals(tree)) {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(true));
            } else if (Message.READ.equals(tree)) {
                return Truffle.getRuntime().createCallTarget(new ComplexNumbersAReadNode());
            } else if (Message.WRITE.equals(tree)) {
                return Truffle.getRuntime().createCallTarget(new ComplexNumbersAWriteNode());
            } else if (Message.GET_SIZE.equals(tree)) {
                return Truffle.getRuntime().createCallTarget(new ComplexNumbersASizeNode());
            } else {
                throw new IllegalArgumentException(tree.toString() + " not supported");
            }
        }
    }

    private static class ComplexNumbersAWriteNode extends RootNode {
        protected ComplexNumbersAWriteNode() {
            super(TckLanguage.class, null, null);
        }

        @Child private Node readReal;
        @Child private Node readImag;

        @Override
        public Object execute(VirtualFrame frame) {
            ComplexNumbersA complexNumbers = (ComplexNumbersA) ForeignAccess.getReceiver(frame);
            Number index = (Number) ForeignAccess.getArguments(frame).get(0);
            TruffleObject value = (TruffleObject) ForeignAccess.getArguments(frame).get(1);
            if (readReal == null || readImag == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.readReal = insert(Message.READ.createNode());
                this.readImag = insert(Message.READ.createNode());
            }
            Number realPart = (Number) ForeignAccess.execute(readReal, frame, value, new Object[]{ComplexNumber.REAL_IDENTIFIER});
            Number imagPart = (Number) ForeignAccess.execute(readImag, frame, value, new Object[]{ComplexNumber.IMAGINARY_IDENTIFIER});

            complexNumbers.data[index.intValue() * 2] = realPart.doubleValue();
            complexNumbers.data[index.intValue() * 2 + 1] = imagPart.doubleValue();
            return value;
        }
    }

    private static class ComplexNumbersAReadNode extends RootNode {
        protected ComplexNumbersAReadNode() {
            super(TckLanguage.class, null, null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            ComplexNumbersA complexNumbers = (ComplexNumbersA) ForeignAccess.getReceiver(frame);
            Number index = (Number) ForeignAccess.getArguments(frame).get(0);
            return new ComplexNumberAEntry(complexNumbers, index.intValue());
        }
    }

    private static class ComplexNumbersASizeNode extends RootNode {
        protected ComplexNumbersASizeNode() {
            super(TckLanguage.class, null, null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            ComplexNumbersA complexNumbers = (ComplexNumbersA) ForeignAccess.getReceiver(frame);
            return complexNumbers.data.length / 2;
        }

    }

    private static class ComplexNumberAEntry implements TruffleObject {

        private final ComplexNumbersA numbers;
        private final int index;

        public ComplexNumberAEntry(ComplexNumbersA numbers, int index) {
            this.numbers = numbers;
            this.index = index;
        }

        public ForeignAccess getForeignAccess() {
            return ForeignAccess.create(new ComplexNumberAEntryForeignAccessFactory());
        }

        private static class ComplexNumberAEntryForeignAccessFactory implements Factory {

            public boolean canHandle(TruffleObject obj) {
                return obj instanceof ComplexNumberAEntry;
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
                    return Truffle.getRuntime().createCallTarget(new ComplexNumbersAEntryReadNode());
                } else if (Message.WRITE.equals(tree)) {
                    return Truffle.getRuntime().createCallTarget(new ComplexNumbersAEntryWriteNode());
                } else {
                    throw new IllegalArgumentException(tree.toString() + " not supported");
                }
            }

            private static class ComplexNumbersAEntryReadNode extends RootNode {
                protected ComplexNumbersAEntryReadNode() {
                    super(TckLanguage.class, null, null);
                }

                @Child private Node readReal;
                @Child private Node readImag;

                @Override
                public Object execute(VirtualFrame frame) {
                    ComplexNumberAEntry complexNumber = (ComplexNumberAEntry) ForeignAccess.getReceiver(frame);
                    String name = (String) ForeignAccess.getArguments(frame).get(0);
                    if (name.equals(ComplexNumber.IMAGINARY_IDENTIFIER)) {
                        return complexNumber.numbers.data[complexNumber.index * 2 + 1];
                    } else if (name.equals(ComplexNumber.REAL_IDENTIFIER)) {
                        return complexNumber.numbers.data[complexNumber.index * 2];
                    } else {
                        throw new IllegalArgumentException();
                    }
                }
            }

            private static class ComplexNumbersAEntryWriteNode extends RootNode {
                protected ComplexNumbersAEntryWriteNode() {
                    super(TckLanguage.class, null, null);
                }

                @Override
                public Object execute(VirtualFrame frame) {
                    ComplexNumberAEntry complexNumber = (ComplexNumberAEntry) ForeignAccess.getReceiver(frame);
                    String name = (String) ForeignAccess.getArguments(frame).get(0);
                    Number value = (Number) ForeignAccess.getArguments(frame).get(1);
                    if (name.equals(ComplexNumber.IMAGINARY_IDENTIFIER)) {
                        complexNumber.numbers.data[complexNumber.index * 2 + 1] = value.doubleValue();
                    } else if (name.equals(ComplexNumber.REAL_IDENTIFIER)) {
                        complexNumber.numbers.data[complexNumber.index * 2] = value.doubleValue();
                    } else {
                        throw new IllegalArgumentException();
                    }
                    return value;
                }

            }
        }

    }
}
