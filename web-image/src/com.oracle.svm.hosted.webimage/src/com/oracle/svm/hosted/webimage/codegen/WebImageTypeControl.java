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
package com.oracle.svm.hosted.webimage.codegen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.stream.StreamSupport;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;

import com.oracle.svm.hosted.meta.HostedClass;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.webimage.codegen.heap.ConstantMap;
import com.oracle.svm.webimage.NamingConvention;
import com.oracle.svm.webimage.type.TypeControl;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * WebImageTypeControl implements a more precise type reachability analysis.
 * <p>
 * It guarantees that a class is generated after all the classes it depends on for its class
 * declaration. This includes the superclass and the declaring classes of its vtable entries. For
 * this it maintains an acyclic dependency graph.
 * <p>
 * The reason the Web Image JS backend does not use SVM reachability analysis result for types is
 * that it does not take optimizations into consideration, thus is less precise. The type
 * reachability analysis implemented in WebImageTypeControl does take advantage of the SVM
 * reachability analysis for methods.
 * <p>
 * A type only has to be registered here, if the JS syntax requires the type to exist. This means
 * only if a type is referenced by name in the generated JS code, does its class definition need to
 * be emitted.
 *
 * For this reason access to the names used in the JS image goes through this class. As soon as a
 * name is requested, the type is marked as reachable.
 * <p>
 * WebImageTypeControl is shared between all instances of {@link JSCodeGenTool}.
 */
public class WebImageTypeControl implements Iterator<HostedType>, TypeControl {
    private final HostedUniverse hostedUniverse;

    private final WebImageJSProviders providers;

    private final NamingConvention namingConvention;

    /**
     * Used for constant resolve.
     */
    private final ConstantMap constantMap;

    /**
     * Types that were already generated.
     */
    private final List<HostedType> emittedTypes;

    /**
     * Meta access to resolve {@link Class} objects to their {@link HostedType} in the compiler.
     */
    private final DependencyGraph graph;

    /**
     * The reason used when requesting a type.
     *
     * Matches {@link #lastType}, but can be temporarily changed through
     * {@link #setDefaultReason(Object)}
     */
    private Object defaultReason = null;

    /**
     * Type that is currently in the process of being emitted.
     *
     * A type is in the process of being emitted between the time it is returned from
     * {@link #next()} and the next call to {@link #next()} or once {@link #hasNext()} returns false
     */
    private HostedType lastType = null;

    public ConstantMap getConstantMap() {
        return constantMap;
    }

    /**
     * Lowers the constants found during code generation.
     *
     * @param jsLTools the {@link JSCodeGenTool} for lowering the constants.
     */
    public void postProcess(JSCodeGenTool jsLTools) {
        assert jsLTools != null;
        constantMap.lower(jsLTools);
    }

    /**
     * @return types for which no code was generated, as no call "escaped" to them.
     */
    public Collection<HostedType> queryOmittedTypes() {
        Set<HostedType> hostedTypes = new HashSet<>(hostedUniverse.getTypes());
        emittedTypes.forEach(hostedTypes::remove);
        return hostedTypes;
    }

    public Collection<HostedType> queryEmittedTypes() {
        return Collections.unmodifiableList(emittedTypes);
    }

    public Iterable<Object> getReasons(HostedType type) {
        return this.graph.getReasons(type);
    }

    public WebImageTypeControl(HostedUniverse hostedUniverse, WebImageJSProviders providers, ConstantMap constantMap, NamingConvention namingConvention) {
        assert hostedUniverse != null;
        this.hostedUniverse = hostedUniverse;
        this.providers = providers;
        this.constantMap = constantMap;
        this.namingConvention = namingConvention;

        int numTypes = hostedUniverse.getTypes().size();

        this.emittedTypes = new ArrayList<>(numTypes);
        this.graph = new DependencyGraph(numTypes);
    }

    /**
     * Returns the list of types that were emitted so far.
     */
    public List<HostedType> emittedTypes() {
        return emittedTypes;
    }

    public HostedUniverse getHUniverse() {
        return hostedUniverse;
    }

    @Override
    public MetaAccessProvider getMetaAccess() {
        return providers.getMetaAccess();
    }

    /**
     * Requests the concrete JS type name for the given type.
     *
     * Calling this method, implicitly marks the type as reachable (since a reference to it now
     * exists).
     */
    @Override
    public String requestTypeName(ResolvedJavaType t, Object reason) {
        assert !t.isArray() : t;

        graph.insertType((HostedType) t, reason);

        return namingConvention.identForType(t);
    }

    @Override
    public String requestTypeName(ResolvedJavaType t) {
        return requestTypeName(t, defaultReason);
    }

    /**
     * Request the global name associated with the hub of the given type.
     */
    @Override
    public String requestHubName(ResolvedJavaType t) {
        return getConstantMap().saveHubGetNode(t).requestName();
    }

    /**
     * Requests the JS property name for the given field.
     */
    @Override
    public String requestFieldName(ResolvedJavaField f) {
        return namingConvention.identForProperty(f);
    }

    /**
     * Requests the JS property name for the given method.
     */
    @Override
    public String requestMethodName(ResolvedJavaMethod m) {
        return namingConvention.identForMethod(m);
    }

    @Override
    public boolean hasNext() {
        if (!graph.hasTypeToEmit()) {
            setLastType(null);
            return false;
        }

        return true;
    }

    @Override
    public HostedType next() {
        HostedType t = graph.getTypeToEmit();
        emittedTypes.add(t);
        setLastType(t);
        return t;
    }

    private void setLastType(HostedType t) {
        this.lastType = t;
        this.resetDefaultReason();
    }

    /**
     * Temporarily set the default reason for type requests.
     *
     * Is automatically reset once the next type is emitted.
     */
    public void setDefaultReason(Object defaultReason) {
        this.defaultReason = defaultReason == null ? this.lastType : defaultReason;
        this.constantMap.setDefaultReason(this.defaultReason);
    }

    /**
     * Reset the default reason to the last emitted type.
     */
    public void resetDefaultReason() {
        setDefaultReason(null);
    }

    /**
     * Represents the dependency graph between registered types.
     *
     * A dependency is only necessary when the class declaration of a type requires some other class
     * to be already defined (e.g. for `extends`).
     *
     * There is no need for a class to pull in classes referenced in its methods because those are
     * not required to be defined when the class is parsed (only when the method is called). Those
     * classes will also be registered once the method is emitted.
     */
    private static final class DependencyGraph {
        /**
         * Maps a type to its node in the graph.
         * <p>
         * Contains all currently registered types and their dependencies.
         */
        private final EconomicMap<HostedType, TypeNode> typeMap;

        /**
         * Nodes that are ready to be emitted (i.e. have no pending dependencies).
         * <p>
         * Once this list is empty, all types were emitted.
         */
        private final Queue<TypeNode> toEmit = new LinkedList<>();

        private boolean isFrozen = false;

        private DependencyGraph(int numTypes) {
            this.typeMap = EconomicMap.create(numTypes);
        }

        private void freeze() {
            isFrozen = true;
        }

        public boolean isFrozen() {
            return isFrozen;
        }

        public Iterable<Object> getReasons(HostedType type) {
            return this.typeMap.get(type).reasons;
        }

        /**
         * Returns a type without pending dependencies from the graph and marks it as emitted.
         *
         * Only call this method if {@link #hasTypeToEmit()} returns true.
         */
        public HostedType getTypeToEmit() {
            TypeNode n = toEmit.remove();

            assert n.dependencies.isEmpty() : n.dependencies;
            assert !n.emitted : "TypeNode " + n + " was already emitted";
            n.emitted = true;

            removeDependencies(n);
            return n.type;
        }

        /**
         * Whether there are any types left to emit.
         */
        public boolean hasTypeToEmit() {
            boolean hasNext = !toEmit.isEmpty();

            if (!hasNext) {
                /*
                 * Once the list is empty all registered types must have been emitted.
                 */
                assert StreamSupport.stream(typeMap.getValues().spliterator(), false).allMatch(n -> n.emitted) : StreamSupport.stream(typeMap.getValues().spliterator(), false).toList();

                /*
                 * Once all types have been emitted, no more types must be registered.
                 */
                freeze();
            }

            return hasNext;
        }

        /**
         * Inserts a new type and all its dependencies transitively into the graph.
         * <p>
         * If the type is already in the graph, its node is returned directly.
         *
         * @return The {@link TypeNode} instance of the given type in the graph.
         */
        private TypeNode insertType(HostedType t, Object reason) {
            TypeNode existing = typeMap.get(t);
            if (existing != null) {
                assert !existing.isConstructing : "Circular dependency in type control: " + t;
                existing.addReason(reason);
                return existing;
            }

            assert !isFrozen() : "No more new types allowed.";

            TypeNode n = new TypeNode(t, reason);
            typeMap.put(t, n);

            /*
             * A class definition depends on its superclass
             */
            HostedClass superClass = t.getSuperclass();
            if (superClass != null) {
                addDependency(n, superClass);
            }

            /*
             * The vtable in a class definition depends on all declaring classes of its methods.
             *
             * We exclude all vtable entries from Object in order to avoid a circular dependency
             * with InvalidVTableEntryHandler. The vtable from Object can only reference Object or
             * InvalidVTableEntryHandler.
             */
            if (!t.getWrapped().getJavaClass().equals(Object.class)) {
                for (HostedMethod m : t.getVTable()) {
                    HostedType declaring = m.getDeclaringClass();

                    if (declaring.equals(t)) {
                        continue;
                    }

                    addDependency(n, declaring);
                }
            }
            n.isConstructing = false;
            if (n.hasNoDependency()) {
                toEmit.offer(n);
            }

            return n;
        }

        /**
         * Adds an edge to the dependency graph so that the type 'to' has to be emitted before the
         * node 'from'.
         */
        private void addDependency(TypeNode from, HostedType to) {
            assert from.isConstructing : "You can only add dependencies while constructing the node";

            TypeNode n = insertType(to, from.type);
            if (n.emitted) {
                return;
            }

            n.requiredBy.add(from);
            from.dependencies.add(n);
        }

        /**
         * Removes the given node from all {@link TypeNode#dependencies} sets.
         */
        private void removeDependencies(TypeNode to) {
            for (TypeNode from : to.requiredBy) {
                from.dependencies.remove(to);

                if (from.hasNoDependency()) {
                    toEmit.offer(from);
                }
            }

            to.requiredBy.clear();
        }

        /**
         * Represents a node in the type control dependency graph.
         */
        private static class TypeNode {
            final HostedType type;

            /**
             * All nodes that need to be emitted before this node can be emitted.
             * <p>
             * Once emitted, the node is removed from this set. Once this is empty, the node is
             * ready to be emitted.
             * <p>
             * This set is populated while searching for dependencies and after that elements are
             * only removed.
             */
            private final EconomicSet<TypeNode> dependencies = EconomicSet.create();

            /**
             * Back edges of the dependency graph.
             * <p>
             * All nodes in this set have this node in their {@link #dependencies} set.
             */
            private final EconomicSet<TypeNode> requiredBy = EconomicSet.create();

            /**
             * Reasons why this type was added to the graph.
             */
            private final EconomicSet<Object> reasons = EconomicSet.create();

            /**
             * Whether this node was already emitted.
             */
            private boolean emitted = false;

            /**
             * Whether this node and its dependencies are currently being constructed.
             * <p>
             * This is used for detecting cycles in the graph.
             */
            private boolean isConstructing = true;

            TypeNode(HostedType type, Object reason) {
                this.type = type;
                addReason(reason);
            }

            public boolean hasNoDependency() {
                return dependencies.isEmpty();
            }

            public void addReason(Object reason) {
                if (reason != null) {
                    reasons.add(reason);
                }
            }

            @Override
            public String toString() {
                return "TypeNode{" +
                                "type=" + type.toJavaName() +
                                ", dependencies=" + dependencies.size() +
                                ", requiredBy=" + requiredBy.size() +
                                ", emitted=" + emitted +
                                ", isConstructing=" + isConstructing +
                                '}';
            }
        }
    }

}
