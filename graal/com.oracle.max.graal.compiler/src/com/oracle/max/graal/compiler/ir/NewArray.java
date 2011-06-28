/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.max.graal.compiler.ir;

import java.util.*;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.phases.EscapeAnalysisPhase.EscapeField;
import com.oracle.max.graal.compiler.phases.EscapeAnalysisPhase.EscapeOp;
import com.oracle.max.graal.compiler.value.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;

/**
 * The {@code NewArray} class is the base of all instructions that allocate arrays.
 */
public abstract class NewArray extends FixedNodeWithNext {

    private static final EscapeOp ESCAPE = new NewArrayEscapeOp();

    private static final int INPUT_COUNT = 1;
    private static final int INPUT_LENGTH = 0;

    private static final int SUCCESSOR_COUNT = 0;

    @Override
    protected int inputCount() {
        return super.inputCount() + INPUT_COUNT;
    }

    @Override
    protected int successorCount() {
        return super.successorCount() + SUCCESSOR_COUNT;
    }

    /**
     * The instruction that produces the length of this array.
     */
     public Value length() {
        return (Value) inputs().get(super.inputCount() + INPUT_LENGTH);
    }

    public Value setLength(Value n) {
        return (Value) inputs().set(super.inputCount() + INPUT_LENGTH, n);
    }

    /**
     * Constructs a new NewArray instruction.
     * @param length the instruction that produces the length for this allocation
     * @param inputCount
     * @param successorCount
     * @param graph
     */
    NewArray(Value length, int inputCount, int successorCount, Graph graph) {
        super(CiKind.Object, inputCount + INPUT_COUNT, successorCount + SUCCESSOR_COUNT, graph);
        setLength(length);
    }

    /**
     * The list of instructions which produce input for this instruction.
     */
    public Value dimension(int index) {
        assert index == 0;
        return length();
    }

    /**
     * The rank of the array allocated by this instruction, i.e. how many array dimensions.
     */
    public int dimensionCount() {
        return 1;
    }

    public abstract CiKind elementKind();

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Op> T lookup(Class<T> clazz) {
        if (clazz == EscapeOp.class) {
            return (T) ESCAPE;
        }
        return super.lookup(clazz);
    }

    private static class NewArrayEscapeOp implements EscapeOp {

        @Override
        public boolean canAnalyze(Node node) {
            NewArray x = (NewArray) node;
            CiConstant length = x.dimension(0).asConstant();
            return length != null && length.asInt() >= 0 && length.asInt() < GraalOptions.MaximumEscapeAnalysisArrayLength;
        }

        @Override
        public boolean escape(Node node, Node usage) {
            if (usage instanceof IsNonNull) {
                IsNonNull x = (IsNonNull) usage;
                assert x.object() == node;
                return false;
            } else if (usage instanceof IsType) {
                IsType x = (IsType) usage;
                assert x.object() == node;
                return false;
            } else if (usage instanceof FrameState) {
                FrameState x = (FrameState) usage;
                assert x.inputs().contains(node);
                return true;
            } else if (usage instanceof LoadIndexed) {
                LoadIndexed x = (LoadIndexed) usage;
                assert x.array() == node;
                CiConstant index = x.index().asConstant();
                CiConstant length = ((NewArray) node).dimension(0).asConstant();
                if (index == null || length == null || index.asInt() < 0 || index.asInt() >= length.asInt()) {
                    return true;
                }
                return false;
            } else if (usage instanceof StoreField) {
                StoreField x = (StoreField) usage;
                assert x.value() == node;
                return true;
            } else if (usage instanceof StoreIndexed) {
                StoreIndexed x = (StoreIndexed) usage;
                CiConstant index = x.index().asConstant();
                CiConstant length = ((NewArray) node).dimension(0).asConstant();
                if (index == null || length == null || index.asInt() < 0 || index.asInt() >= length.asInt()) {
                    return true;
                }
                return x.value() == node;
            } else if (usage instanceof AccessMonitor) {
                AccessMonitor x = (AccessMonitor) usage;
                assert x.object() == node;
                return false;
            } else if (usage instanceof ArrayLength) {
                ArrayLength x = (ArrayLength) usage;
                assert x.array() == node;
                return false;
            } else if (usage instanceof VirtualObject) {
                return false;
            } else {
                return true;
            }
        }

        @Override
        public EscapeField[] fields(Node node) {
            NewArray x = (NewArray) node;
            int length = x.dimension(0).asConstant().asInt();
            EscapeField[] fields = new EscapeField[length];
            for (int i = 0; i < length; i++) {
                Integer representation = i;
                fields[i] = new EscapeField("[" + i + "]", representation, ((NewArray) node).elementKind());
            }
            return fields;
        }

        @Override
        public void beforeUpdate(Node node, Node usage) {
            if (usage instanceof IsNonNull) {
                IsNonNull x = (IsNonNull) usage;
                if (x.usages().size() == 1 && x.usages().get(0) instanceof FixedGuard) {
                    FixedGuard guard = (FixedGuard) x.usages().get(0);
                    guard.replaceAndDelete(guard.next());
                }
                x.delete();
            } else if (usage instanceof IsType) {
                IsType x = (IsType) usage;
                assert x.type() == ((NewArray) node).exactType();
                if (x.usages().size() == 1 && x.usages().get(0) instanceof FixedGuard) {
                    FixedGuard guard = (FixedGuard) x.usages().get(0);
                    guard.replaceAndDelete(guard.next());
                }
                x.delete();
            } else if (usage instanceof AccessMonitor) {
                AccessMonitor x = (AccessMonitor) usage;
                x.replaceAndDelete(x.next());
            } else if (usage instanceof ArrayLength) {
                ArrayLength x = (ArrayLength) usage;
                x.replaceAndDelete(((NewArray) node).dimension(0));
            }
        }

        @Override
        public void updateState(Node node, Node current, Map<Object, EscapeField> fields, Map<EscapeField, Node> fieldState) {
            if (current instanceof AccessIndexed) {
                int index = ((AccessIndexed) current).index().asConstant().asInt();
                EscapeField field = fields.get(index);
                if (current instanceof LoadIndexed) {
                    LoadIndexed x = (LoadIndexed) current;
                    if (x.array() == node) {
                        for (Node usage : new ArrayList<Node>(x.usages())) {
                            assert fieldState.get(field) != null;
                            usage.inputs().replace(x, fieldState.get(field));
                        }
                        assert x.usages().size() == 0;
                        x.replaceAndDelete(x.next());
                    }
                } else if (current instanceof StoreIndexed) {
                    StoreIndexed x = (StoreIndexed) current;
                    if (x.array() == node) {
                        fieldState.put(field, x.value());
                        assert x.usages().size() == 0;
                        x.replaceAndDelete(x.next());
                    }
                }
            }
        }
    }
}
