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

import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.phases.EscapeAnalysisPhase.EscapeField;
import com.oracle.max.graal.compiler.phases.EscapeAnalysisPhase.EscapeOp;
import com.oracle.max.graal.compiler.value.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code NewInstance} instruction represents the allocation of an instance class object.
 */
public final class NewInstance extends FixedNodeWithNext {
    private static final EscapeOp ESCAPE = new NewInstanceEscapeOp();

    private static final int INPUT_COUNT = 0;
    private static final int SUCCESSOR_COUNT = 0;

    final RiType instanceClass;
    public final int cpi;
    public final RiConstantPool constantPool;

    /**
     * Constructs a NewInstance instruction.
     * @param type the class being allocated
     * @param cpi the constant pool index
     * @param graph
     */
    public NewInstance(RiType type, int cpi, RiConstantPool constantPool, Graph graph) {
        super(CiKind.Object, INPUT_COUNT, SUCCESSOR_COUNT, graph);
        this.instanceClass = type;
        this.cpi = cpi;
        this.constantPool = constantPool;
    }

    /**
     * Gets the instance class being allocated by this instruction.
     * @return the instance class allocated
     */
    public RiType instanceClass() {
        return instanceClass;
    }

    /**
     * Gets the exact type produced by this instruction. For allocations of instance classes, this is
     * always the class allocated.
     * @return the exact type produced by this instruction
     */
    @Override
    public RiType exactType() {
        return instanceClass;
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitNewInstance(this);
    }

    @Override
    public void print(LogStream out) {
        out.print("new instance ").print(CiUtil.toJavaName(instanceClass()));
    }

    @Override
    public Map<Object, Object> getDebugProperties() {
        Map<Object, Object> properties = super.getDebugProperties();
        properties.put("instanceClass", instanceClass);
        properties.put("cpi", cpi);
        return properties;
    }

    @Override
    public Node copy(Graph into) {
        NewInstance x = new NewInstance(instanceClass, cpi, constantPool, into);
        return x;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Op> T lookup(Class<T> clazz) {
        if (clazz == EscapeOp.class) {
            return (T) ESCAPE;
        }
        return super.lookup(clazz);
    }

    private static class NewInstanceEscapeOp implements EscapeOp {

        @Override
        public boolean canAnalyze(Node node) {
            return ((NewInstance) node).instanceClass().isResolved();
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
            } else if (usage instanceof LoadField) {
                LoadField x = (LoadField) usage;
                assert x.object() == node;
                return x.field().isResolved() == false;
            } else if (usage instanceof StoreField) {
                StoreField x = (StoreField) usage;
                return x.value() == node;
            } else if (usage instanceof StoreIndexed) {
                StoreIndexed x = (StoreIndexed) usage;
                assert x.value() == node;
                return true;
            } else if (usage instanceof AccessMonitor) {
                AccessMonitor x = (AccessMonitor) usage;
                assert x.object() == node;
                return false;
            } else if (usage instanceof VirtualObject) {
                return false;
            } else if (usage instanceof RegisterFinalizer) {
                RegisterFinalizer x = (RegisterFinalizer) usage;
                assert x.object() == node;
                return false;
            } else {
                return true;
            }
        }

        @Override
        public EscapeField[] fields(Node node) {
            NewInstance x = (NewInstance) node;
            RiField[] riFields = x.instanceClass().fields();
            EscapeField[] fields = new EscapeField[riFields.length];
            for (int i = 0; i < riFields.length; i++) {
                RiField field = riFields[i];
                fields[i] = new EscapeField(field.name(), field, field.kind().stackKind());
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
                assert x.type() == ((NewInstance) node).instanceClass();
                if (x.usages().size() == 1 && x.usages().get(0) instanceof FixedGuard) {
                    FixedGuard guard = (FixedGuard) x.usages().get(0);
                    guard.replaceAndDelete(guard.next());
                }
                x.delete();
            } else if (usage instanceof AccessMonitor) {
                AccessMonitor x = (AccessMonitor) usage;
                x.replaceAndDelete(x.next());
            } else if (usage instanceof RegisterFinalizer) {
                RegisterFinalizer x = (RegisterFinalizer) usage;
                x.replaceAndDelete(x.next());
            }
        }

        @Override
        public EscapeField updateState(Node node, Node current, Map<Object, EscapeField> fields, Map<EscapeField, Value> fieldState) {
            if (current instanceof AccessField) {
                EscapeField field = fields.get(((AccessField) current).field());
                if (current instanceof LoadField) {
                    LoadField x = (LoadField) current;
                    if (x.object() == node) {
                        assert fieldState.get(field) != null : field + ", " + ((AccessField) current).field() + ((AccessField) current).field().hashCode();
                        for (Node usage : new ArrayList<Node>(x.usages())) {
                            usage.inputs().replace(x, fieldState.get(field));
                        }
                        assert x.usages().size() == 0;
                        x.replaceAndDelete(x.next());
                    }
                } else if (current instanceof StoreField) {
                    StoreField x = (StoreField) current;
                    if (x.object() == node) {
                        fieldState.put(field, x.value());
                        assert x.usages().size() == 0;
                        x.replaceAndDelete(x.next());
                        return field;
                    }
                }
            }
            return null;
        }
    }
}
