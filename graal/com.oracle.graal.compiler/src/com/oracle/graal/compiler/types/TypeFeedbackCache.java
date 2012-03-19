/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.types;

import java.util.*;
import java.util.Map.Entry;

import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.types.*;
import com.oracle.graal.nodes.type.*;

public class TypeFeedbackCache implements TypeFeedbackTool, Cloneable {

    public static final boolean NO_OBJECT_TYPES = false;
    public static final boolean NO_SCALAR_TYPES = false;

    private final RiRuntime runtime;
    private final StructuredGraph graph;
    private final HashMap<ValueNode, ScalarTypeFeedbackStore> scalarTypeFeedback;
    private final HashMap<ValueNode, ObjectTypeFeedbackStore> objectTypeFeedback;
    private final TypeFeedbackChanged changed;
    private final boolean negated;

    public TypeFeedbackCache(RiRuntime runtime, StructuredGraph graph, TypeFeedbackChanged changed) {
        this.runtime = runtime;
        this.graph = graph;
        scalarTypeFeedback = new HashMap<>();
        objectTypeFeedback = new HashMap<>();
        negated = false;
        this.changed = changed;
    }

    public TypeFeedbackCache(RiRuntime runtime, StructuredGraph graph, HashMap<ValueNode, ScalarTypeFeedbackStore> scalarTypeFeedback, HashMap<ValueNode, ObjectTypeFeedbackStore> objectTypeFeedback, boolean negated, TypeFeedbackChanged changed) {
        this.runtime = runtime;
        this.graph = graph;
        this.scalarTypeFeedback = scalarTypeFeedback;
        this.objectTypeFeedback = objectTypeFeedback;
        this.negated = negated;
        this.changed = changed;
    }

    @Override
    public ScalarTypeFeedbackTool addScalar(ValueNode value) {
        assert value.kind() == CiKind.Int || value.kind() == CiKind.Long || value.kind() == CiKind.Float || value.kind() == CiKind.Double;
        ScalarTypeFeedbackStore result = scalarTypeFeedback.get(value);
        if (result == null) {
            if (value.stamp().scalarType() != null) {
                result = value.stamp().scalarType().store().clone();
            } else {
                result = new ScalarTypeFeedbackStore(value.kind(), changed);
            }
            scalarTypeFeedback.put(value, result);
        }
        return negated ? new NegateScalarTypeFeedback(result) : result;
    }

    @Override
    public ObjectTypeFeedbackTool addObject(ValueNode value) {
        assert value.kind() == CiKind.Object;
        ObjectTypeFeedbackStore result = objectTypeFeedback.get(value);
        if (result == null) {
            if (value.stamp().objectType() != null) {
                result = value.stamp().objectType().store().clone();
            } else {
                result = new ObjectTypeFeedbackStore(changed);
            }
            objectTypeFeedback.put(value, result);
        }
        return negated ? new NegateObjectTypeFeedback(result) : result;
    }

    @Override
    public TypeFeedbackTool negate() {
        return new TypeFeedbackCache(runtime, graph, scalarTypeFeedback, objectTypeFeedback, !negated, changed);
    }

    @Override
    public RiRuntime runtime() {
        return runtime;
    }

    @Override
    public TypeFeedbackCache clone() {
        return new TypeFeedbackCache(runtime, graph, deepClone(scalarTypeFeedback), deepClone(objectTypeFeedback), negated, changed);
    }

    @SuppressWarnings("unchecked")
    private static <KeyT, ValueT extends CloneableTypeFeedback> HashMap<KeyT, ValueT> deepClone(HashMap<KeyT, ValueT> map) {
        HashMap<KeyT, ValueT> result = new HashMap<>();
        for (Map.Entry<KeyT, ValueT> entry : map.entrySet()) {
            if (entry.getValue() == null) {
                System.out.println(entry.getKey());
            } else {
                result.put(entry.getKey(), (ValueT) entry.getValue().clone());
            }
        }
        return result;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder().append("types [\n");
        for (Map.Entry<ValueNode, ScalarTypeFeedbackStore> entry : scalarTypeFeedback.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                str.append("    ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }
        for (Map.Entry<ValueNode, ObjectTypeFeedbackStore> entry : objectTypeFeedback.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                str.append("    ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }
        str.setLength(str.length() - 1);
        return str.append(" ]").toString();
    }

    public static TypeFeedbackCache meet(TypeFeedbackCache[] cacheList, Iterable<PhiNode> phis) {
        TypeFeedbackCache result = new TypeFeedbackCache(cacheList[0].runtime, cacheList[0].graph, cacheList[0].changed);

        for (int i = 1; i < cacheList.length; i++) {
            assert result.runtime == cacheList[i].runtime;
            assert !result.negated && !cacheList[i].negated : "cannot meet negated type feedback caches";
        }

        // meet the scalar types
        for (Entry<ValueNode, ScalarTypeFeedbackStore> entry : cacheList[0].scalarTypeFeedback.entrySet()) {
            ScalarTypeFeedbackStore[] types = new ScalarTypeFeedbackStore[cacheList.length];
            for (int i = 0; i < cacheList.length; i++) {
                types[i] = cacheList[i].scalarTypeFeedback.get(entry.getKey());
            }
            ScalarTypeFeedbackStore scalar = ScalarTypeFeedbackStore.meet(types);
            if (scalar != null && !scalar.isEmpty()) {
                result.scalarTypeFeedback.put(entry.getKey(), scalar);
            }
        }
        // meet the object types
        for (Entry<ValueNode, ObjectTypeFeedbackStore> entry : cacheList[0].objectTypeFeedback.entrySet()) {
            ObjectTypeFeedbackStore[] types = new ObjectTypeFeedbackStore[cacheList.length];
            for (int i = 0; i < cacheList.length; i++) {
                types[i] = cacheList[i].objectTypeFeedback.get(entry.getKey());
            }
            ObjectTypeFeedbackStore object = ObjectTypeFeedbackStore.meet(types);
            if (object != null && !object.isEmpty()) {
                result.objectTypeFeedback.put(entry.getKey(), object);
            }
        }
        // meet the phi nodes
        for (PhiNode phi : phis) {
            assert phi.valueCount() == cacheList.length;
            if (phi.kind() == CiKind.Int || phi.kind() == CiKind.Long) {
                ScalarTypeFeedbackStore[] types = new ScalarTypeFeedbackStore[phi.valueCount()];
                for (int i = 0; i < phi.valueCount(); i++) {
                    ScalarTypeFeedbackStore other = cacheList[i].scalarTypeFeedback.get(phi.valueAt(i));
                    if (other == null && phi.valueAt(i).stamp().scalarType() != null) {
                        other = phi.valueAt(i).stamp().scalarType().store();
                    }
                    types[i] = other;
                }
                ScalarTypeFeedbackStore scalar = ScalarTypeFeedbackStore.meet(types);
                if (scalar != null && !scalar.isEmpty()) {
                    result.scalarTypeFeedback.put(phi, scalar);
                    phi.setStamp(StampFactory.forKind(phi.kind(), scalar.query(), null));
                }
            } else if (phi.kind() == CiKind.Object) {
                ObjectTypeFeedbackStore[] types = new ObjectTypeFeedbackStore[phi.valueCount()];
                for (int i = 0; i < phi.valueCount(); i++) {
                    ObjectTypeFeedbackStore other = cacheList[i].objectTypeFeedback.get(phi.valueAt(i));
                    if (other == null && phi.valueAt(i).stamp().objectType() != null) {
                        other = phi.valueAt(i).stamp().objectType().store();
                    }
                    types[i] = other;
                }
                ObjectTypeFeedbackStore object = ObjectTypeFeedbackStore.meet(types);
                if (object != null && !object.isEmpty()) {
                    result.objectTypeFeedback.put(phi, object);
                    phi.setStamp(StampFactory.forKind(phi.kind(), null, object.query()));
                }
            }
        }
        return result;
    }

    @Override
    public ScalarTypeQuery queryScalar(ValueNode value) {
        assert value.kind() == CiKind.Int || value.kind() == CiKind.Long || value.kind() == CiKind.Float || value.kind() == CiKind.Double;
        if (NO_SCALAR_TYPES) {
            return new ScalarTypeFeedbackStore(value.kind(), changed).query();
        }
        ScalarTypeFeedbackStore result = scalarTypeFeedback.get(value);
        if (result == null) {
            if (value.stamp().scalarType() != null) {
                return value.stamp().scalarType();
            }
            result = new ScalarTypeFeedbackStore(value.kind(), changed);
            scalarTypeFeedback.put(value, result);
        }
        return result.query();
    }

    @Override
    public ObjectTypeQuery queryObject(ValueNode value) {
        assert value != null;
        assert value.kind() == CiKind.Object;
        if (NO_OBJECT_TYPES) {
            return new ObjectTypeFeedbackStore(changed).query();
        }
        ObjectTypeFeedbackStore result = objectTypeFeedback.get(value);
        if (result == null) {
            if (value.stamp().objectType() != null) {
                return value.stamp().objectType();
            }
            result = new ObjectTypeFeedbackStore(changed);
            objectTypeFeedback.put(value, result);
        }
        return result.query();
    }
}
