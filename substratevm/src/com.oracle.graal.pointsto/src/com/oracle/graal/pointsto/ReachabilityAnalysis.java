/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto;

import java.lang.reflect.Executable;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.util.UnsafePartitionKind;

/**
 * Interface to be used to query and change the state of the static analysis in Native Image.
 *
 * It is a first small step in a bigger effort to group together all requests changing the analysis
 * state.
 */
public interface ReachabilityAnalysis {

    /**
     * Marks given class and all its superclasses as reachable.
     *
     * @param clazz class to be marked
     * @param addFields if true, all instance fiels are marked as accessed
     * @param addArrayClass if true, the array class is registered as well
     */
    AnalysisType addRootClass(Class<?> clazz, boolean addFields, boolean addArrayClass);

    AnalysisType addRootClass(AnalysisType type, boolean addFields, boolean addArrayClass);

    /**
     * Marks given field as accessed.
     */
    AnalysisType addRootField(Class<?> clazz, String fieldName);

    /**
     * Registers the method as root.
     *
     * Static methods are immediately analyzed and marked as implementation-invoked which will also
     * trigger their compilation.
     *
     * Special and virtual invoked methods are conditionally linked. Only when the receiver type (or
     * one of its subtypes) is marked as instantiated the resolved concrete method is analyzed and
     * marked as implementation-invoked and later compiled. This also means that abstract methods
     * can be marked as virtual invoked roots; only the implementation methods whose declaring class
     * is instantiated will actually be linked. Trying to register an abstract method as a special
     * invoked root will result in an error.
     *
     * @param aMethod the method to register as root
     * @param invokeSpecial if true only the target method is analyzed, even if it has overrides, or
     *            it is itself an override. If the method is static this flag is ignored.
     */
    AnalysisMethod addRootMethod(AnalysisMethod aMethod, boolean invokeSpecial);

    /**
     * @see ReachabilityAnalysis#addRootMethod(AnalysisMethod, boolean)
     */
    AnalysisMethod addRootMethod(Executable method, boolean invokeSpecial);

    default void registerAsFrozenUnsafeAccessed(AnalysisField field) {
        field.setUnsafeFrozenTypeState(true);
    }

    default boolean registerAsUnsafeAccessed(AnalysisField field, UnsafePartitionKind partitionKind, Object reason) {
        if (field.registerAsUnsafeAccessed(partitionKind, reason)) {
            forceUnsafeUpdate(field);
            return true;
        }
        return false;
    }

    default boolean registerTypeAsReachable(AnalysisType type, Object reason) {
        return type.registerAsReachable(reason);
    }

    default boolean registerTypeAsAllocated(AnalysisType type, Object reason) {
        return type.registerAsAllocated(reason);
    }

    default boolean registerTypeAsInHeap(AnalysisType type, Object reason) {
        return type.registerAsInHeap(reason);
    }

    default void markFieldAccessed(AnalysisField field, Object reason) {
        field.registerAsAccessed(reason);
    }

    default void markFieldRead(AnalysisField field, Object reason) {
        field.registerAsRead(reason);
    }

    default void markFieldWritten(AnalysisField field, Object reason) {
        field.registerAsWritten(reason);
    }

    /**
     * Waits until the analysis is done.
     */
    boolean finish() throws InterruptedException;

    /**
     * Clears all intermediary data to reduce the footprint.
     */
    void cleanupAfterAnalysis();

    /**
     * Force update of the unsafe loads and unsafe store type flows when a field is registered as
     * unsafe accessed 'on the fly', i.e., during the analysis.
     *
     * @param field the newly unsafe registered field. We use its declaring type to filter the
     *            unsafe access flows that need to be updated.
     */
    void forceUnsafeUpdate(AnalysisField field);

    /**
     * Performs any necessary additional steps required by the analysis to handle JNI accessed
     * fields.
     */
    void registerAsJNIAccessed(AnalysisField field, boolean writable);

    /**
     * @return all types that need synchronization
     */
    Iterable<AnalysisType> getAllSynchronizedTypes();

    /**
     * @return all instantiated types
     */
    Iterable<AnalysisType> getAllInstantiatedTypes();

    /**
     * @return query interface used for looking up analysis objects from java.lang.reflect
     *         references
     */
    AnalysisMetaAccess getMetaAccess();

    /**
     * @return universe containing all known analysis types
     */
    AnalysisUniverse getUniverse();

    /**
     * @return policy used when running the analysis
     */
    AnalysisPolicy analysisPolicy();
}
