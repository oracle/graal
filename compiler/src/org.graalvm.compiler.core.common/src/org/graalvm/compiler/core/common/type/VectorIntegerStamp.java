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
package org.graalvm.compiler.core.common.type;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.spi.LIRKindTool;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.BinaryOp;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.MetaAccessProvider;

public final class VectorIntegerStamp extends VectorPrimitiveStamp {

    public static final ArithmeticOpTable OPS = new ArithmeticOpTable(
                    new ArithmeticOpTable.UnaryOp.Neg() {
                        @Override
                        public Constant foldConstant(Constant a) {
                            return null;
                        }

                        @Override
                        public Stamp foldStamp(Stamp a) {
                            if (a.isEmpty()) {
                                return a;
                            }

                            // Can only be unrestricted so return a
                            return a;
                        }
                    },
                    new BinaryOp.Add(true, true) {
                        @Override
                        public Constant foldConstant(Constant a, Constant b) {
                            return null;
                        }

                        @Override
                        public Stamp foldStamp(Stamp a, Stamp b) {
                            if (a.isEmpty()) {
                                return a;
                            }

                            if (b.isEmpty()) {
                                return b;
                            }

                            // Can only be unrestricted so return a
                            return a;
                        }
                    },
                    new BinaryOp.Sub(true, false) {
                        @Override
                        public Constant foldConstant(Constant a, Constant b) {
                            return null;
                        }

                        @Override
                        public Stamp foldStamp(Stamp a, Stamp b) {
                            if (a.isEmpty()) {
                                return a;
                            }

                            if (b.isEmpty()) {
                                return b;
                            }

                            // Can only be unrestricted so return a
                            return a;
                        }
                    },
                    new BinaryOp.Mul(true, true) {
                        @Override
                        public Constant foldConstant(Constant a, Constant b) {
                            return null;
                        }

                        @Override
                        public Stamp foldStamp(Stamp a, Stamp b) {
                            if (a.isEmpty()) {
                                return a;
                            }

                            if (b.isEmpty()) {
                                return b;
                            }

                            // Can only be unrestricted so return a
                            return a;
                        }
                    },
                    null,
                    null,
                    null,
                    null,
                    null,
                    new BinaryOp.And(true, true) {
                        @Override
                        public Constant foldConstant(Constant a, Constant b) {
                            return null;
                        }

                        @Override
                        public Stamp foldStamp(Stamp a, Stamp b) {
                            if (a.isEmpty()) {
                                return a;
                            }

                            if (b.isEmpty()) {
                                return b;
                            }

                            // Can only be unrestricted so return a
                            return a;
                        }
                    },
                    new BinaryOp.Or(true, true) {
                        @Override
                        public Constant foldConstant(Constant a, Constant b) {
                            return null;
                        }

                        @Override
                        public Stamp foldStamp(Stamp a, Stamp b) {
                            if (a.isEmpty()) {
                                return a;
                            }

                            if (b.isEmpty()) {
                                return b;
                            }

                            // Can only be unrestricted so return a
                            return a;
                        }
                    },
                    new BinaryOp.Xor(true, true) {
                        @Override
                        public Constant foldConstant(Constant a, Constant b) {
                            return null;
                        }

                        @Override
                        public Stamp foldStamp(Stamp a, Stamp b) {
                            if (a.isEmpty()) {
                                return a;
                            }

                            if (b.isEmpty()) {
                                return b;
                            }

                            // Can only be unrestricted so return a
                            return a;
                        }
                    },
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);

    public static VectorIntegerStamp create(IntegerStamp scalar, int elementCount) {
        return new VectorIntegerStamp(scalar, elementCount, OPS);
    }

    private VectorIntegerStamp(IntegerStamp scalar, int elementCount, ArithmeticOpTable ops) {
        super(scalar, elementCount, ops);
    }

    @Override
    public IntegerStamp getScalar() {
        return (IntegerStamp) super.getScalar();
    }

    @Override
    public LIRKind getLIRKind(LIRKindTool tool) {
        return tool.getVectorIntegerKind(getScalar().getBits(), getElementCount());
    }

    @Override
    public Stamp meet(Stamp otherStamp) {
        if (otherStamp == this) {
            return this;
        }

        if (isEmpty()) {
            return otherStamp;
        }

        final VectorIntegerStamp other = (VectorIntegerStamp) otherStamp;
        final int newElementCount = Math.max(getElementCount(), other.getElementCount());

        return VectorIntegerStamp.create((IntegerStamp) getScalar().meet(other.getScalar()), newElementCount);
    }

    @Override
    public Stamp join(Stamp otherStamp) {
        if (otherStamp == this) {
            return this;
        }

        final VectorIntegerStamp other = (VectorIntegerStamp) otherStamp;
        final int newElementCount = Math.min(getElementCount(), other.getElementCount());

        return VectorIntegerStamp.create(getScalar().join(other.getScalar()), newElementCount);
    }

    @Override
    public Stamp unrestricted() {
        return VectorIntegerStamp.create(getScalar().unrestricted(), getElementCount());
    }

    @Override
    public Stamp empty() {
        return VectorIntegerStamp.create(getScalar().empty(), getElementCount());
    }

    @Override
    public Stamp constant(Constant c, MetaAccessProvider meta) {
        // Constant not supported
        return this;
    }

    @Override
    public boolean isCompatible(Stamp stamp) {
        if (this == stamp) {
            return true;
        }

        if (stamp instanceof VectorIntegerStamp) {
            final VectorIntegerStamp other = (VectorIntegerStamp) stamp;
            return getScalar().isCompatible(other.getScalar()) && getElementCount() == other.getElementCount();
        }

        return false;
    }

    @Override
    public boolean isCompatible(Constant constant) {
        return false;
    }

    @Override
    public boolean hasValues() {
        return getScalar().hasValues() && getElementCount() > 0;
    }

}
