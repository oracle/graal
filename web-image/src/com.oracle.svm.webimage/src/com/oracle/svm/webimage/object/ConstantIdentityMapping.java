/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.webimage.object;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import com.oracle.svm.core.util.HostedStringDeduplication;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.webimage.JSNameGenerator;
import com.oracle.svm.webimage.object.ObjectInspector.ClassFieldList;
import com.oracle.svm.webimage.object.ObjectInspector.MethodPointerType;
import com.oracle.svm.webimage.object.ObjectInspector.ObjectDefinition;
import com.oracle.svm.webimage.object.ObjectInspector.StringType;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Class encapsulating meta information for constant object lowering.
 */
public final class ConstantIdentityMapping {
    /**
     * Store a mapping between heap constants and identity nodes.
     */
    private final EconomicMap<JavaConstant, IdentityNode> objectToIdentityLink;
    /**
     * Maps a hosted object type to a list of fields that should be included in the image for this
     * type.
     */
    private final EconomicMap<HostedType, ClassFieldList> typeToFieldList;

    /**
     * A set of all statically collected interned strings.
     */
    private final Set<StringType> internedStrings = new HashSet<>();

    /**
     * A table of method pointers that are used in the constant map.
     *
     * {@link MethodPointerType}
     */
    private final ArrayList<ResolvedJavaMethod> methodPointers = new ArrayList<>();

    private boolean allowInternStrings = true;

    public ConstantIdentityMapping() {
        objectToIdentityLink = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
        typeToFieldList = EconomicMap.create();
    }

    public void putString(final StringType str) {
        if (HostedStringDeduplication.isInternedString(str.stringVal)) {
            assert allowInternStrings : "Can no longer add interned strings: '" + str + "'";
            internedStrings.add(str);
        }
    }

    public int addMethodPointer(ResolvedJavaMethod method) {
        int i = methodPointers.indexOf(method);
        if (i == -1) {
            methodPointers.add(method);
            return methodPointers.size() - 1;
        } else {
            return i;
        }
    }

    public ResolvedJavaMethod[] getMethodPointers() {
        return methodPointers.toArray(new ResolvedJavaMethod[0]);
    }

    public String[] getInternedStrings() {
        return internedStrings.stream().map(s -> s.stringVal).sorted().toArray(String[]::new);
    }

    public void disallowInternStrings() {
        allowInternStrings = false;
    }

    public void putFieldList(ClassFieldList list) {
        assert !hasListForType(list.type) : "Type " + list.type + " already has a field list";
        assert list.type.getStorageKind() == JavaKind.Object : list.type;
        typeToFieldList.put(list.type, list);
    }

    public boolean hasListForType(HostedType ty) {
        return typeToFieldList.containsKey(ty);
    }

    public ClassFieldList getListForType(HostedType ty) {
        assert hasListForType(ty) : "Type " + ty + " does not have a field list";
        return typeToFieldList.get(ty);
    }

    public Iterator<ClassFieldList> classFieldListIterator() {
        return typeToFieldList.getValues().iterator();
    }

    public void putObjectDef(ObjectDefinition def) {
        JavaConstant c = def.getConstant();
        assert !objectToIdentityLink.containsKey(c) : "An identity mapping for object '" + c + "' already exists";
        objectToIdentityLink.put(c, new IdentityNode(def));
    }

    public boolean hasMappingForObject(JavaConstant c) {
        return objectToIdentityLink.containsKey(c);
    }

    public ObjectDefinition getDefByObject(JavaConstant c) {
        return getIdentityNode(c).getDefinition();
    }

    public IdentityNode getIdentityNode(JavaConstant c) {
        assert objectToIdentityLink.containsKey(c) : "An identity mapping for object '" + c + "' does not exists";
        return objectToIdentityLink.get(c);
    }

    public Iterable<IdentityNode> identityNodes() {
        return objectToIdentityLink.getValues();
    }

    public static final class IdentityNode {
        private final ObjectDefinition definition;

        /**
         * Unique ID for this node.
         */
        private final int num;

        /**
         * Used for codegen naming.
         * <p>
         * Initially nodes don't have names, just numbers. Once they are referenced in code, they
         * are given a name.
         */
        private String nodeName;

        private static final JSNameGenerator.NameCache<Integer> nameCache = new JSNameGenerator.NameCache<>("c");

        /**
         * Counter for identity nodes to create unique IDs.
         */
        private static final AtomicInteger constantsIndex = new AtomicInteger(0);

        public IdentityNode(ObjectDefinition definition) {
            this.definition = definition;
            this.num = constantsIndex.getAndIncrement();
        }

        /**
         * Get the name of this node and name the node if it doesn't have a name yet.
         * <p>
         * Only call this if you want to reference this node by name in code.
         */
        public String requestName() {
            if (!hasName()) {
                this.nodeName = nameCache.get(this.num);
            }
            return nodeName;
        }

        /**
         * Get the name of this node.
         * <p>
         * Must be called on a node with a name.
         */
        public String getName() {
            assert hasName() : "Identity node " + this + " does not have a name";
            return nodeName;
        }

        public boolean hasName() {
            return this.nodeName != null;
        }

        public int getNum() {
            return num;
        }

        public ObjectDefinition getDefinition() {
            return definition;
        }
    }
}
