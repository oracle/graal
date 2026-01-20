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
package com.oracle.svm.hosted.image;

import java.util.Collection;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.image.ImageHeapLayoutInfo;
import com.oracle.svm.core.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.core.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.meta.HostedConstantReflectionProvider;
import com.oracle.svm.hosted.meta.HostedField;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.code.CompilationResult;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * This class provides proxies for building and using heap-inclusion reasons. Heap inclusion reasons
 * are used for printing stack traces in case of exceptions related to image-heap objects and
 * {@link ImageHeapConnectedComponentsPrinter printing image heap connected components}.
 *
 * A reason can be one of the following:
 *
 * <ul>
 * <li>{@link NativeImageHeap.ObjectInfo} saying which object refers to another</li>
 * <li>{@link String} explaining why an object is in the heap</li>
 * <li>{@link String} representing the name of the {@link CompilationResult} where an object is
 * constant-folded into</li>
 * <li>{@link ResolvedJavaMethod} representing a method where an object is constant-folded into</li>
 * <li>{@link ImageHeapReasonSupport.HeapInclusionReason} representing a known heap inclusion
 * reason</li>
 * <li>{@link HostedField} representing a static field
 * </ul>
 *
 * Reasons are linked together, forming a tree that represents--for each object--the path in the
 * heap-object graph to that object, as found by Native Image during the traversal used to determine
 * the reachable objects for inclusion in the heap snapshot.
 *
 * Example:
 *
 * Consider the following Java code, which instantiates a {@link org.graalvm.collections.Pair} that
 * stores the current application version. The major version (represented by {@link Integer} 1) is
 * stored in the instance field {@code org.graalvm.collections.Pair#left} while the minor version
 * (represented by {@link Integer} 0) is stored in the instance field
 * {@code org.graalvm.collections.Pair#right}. The pair is stored in a static field `VERSION`.
 *
 * <pre>
 * static Pair&lt;Integer, Integer&gt; VERSION = Pair.create(1, 0);
 * </pre>
 *
 * The code produces the following simplified heap-object graph, where
 * {@link org.graalvm.collections.Pair} is a root:
 *
 * <pre>
 *                 +------+
 *                 | Pair |
 *                 +------+
 *                /        \
 *               /          v
 *              /            +---------+
 *             /             | Integer |
 *            /              +---------+
 *           v
 *     +---------+
 *     | Integer |
 *     +---------+
 * </pre>
 *
 * Each of these objects must be stored in the image heap and hence is associated with a
 * {@link NativeImageHeap.ObjectInfo}:
 *
 * <pre>
 *                 +------+                                   +------------+
 *                 | Pair |<----------------------------------| ObjectInfo |
 *                 +------+                                   +------------+
 *                /        \
 *               /          v
 *              /            +---------+         +------------+
 *             /             | Integer |<--------| ObjectInfo |
 *            /              +---------+         +------------+
 *           v
 *     +---------+                                                   +------------+
 *     | Integer |<--------------------------------------------------| ObjectInfo |
 *     +---------+                                                   +------------+
 * </pre>
 *
 * This class allows connecting these {@link NativeImageHeap.ObjectInfo} and the {@link HostedField}
 * VERSION, indicating that the {@link org.graalvm.collections.Pair} is included in the image heap
 * because it is stored in a static field, and the two {@link Integer}s are reachable via the
 * {@link org.graalvm.collections.Pair}. The {@link NativeImageHeap.ObjectInfo}s and the
 * {@link HostedField} are called `reasons` and will be stored in the
 * {@code NativeImageHeap.ObjectInfo#reason} field. As the figure depicts, the path to the
 * {@link org.graalvm.collections.Pair} (starting from the root {@link HostedField}) is a prefix of
 * the paths to the two {@link Integer}s.
 *
 * <pre>
 *                                                            +-------------+
 *                                                            | HostedField |
 *                                                            +-------------+
 *                                                                   ^
 *                                                                   |
 *                 +------+                                   +------------+
 *                 | Pair |<----------------------------------| ObjectInfo |
 *                 +------+                                   +------------+
 *                /        \                                  ^             ^
 *               /          v                                /               \
 *              /            +---------+         +------------+               \
 *             /             | Integer |<--------| ObjectInfo |                \
 *            /              +---------+         +------------+                 \
 *           v                                                                   \
 *     +---------+                                                   +------------+
 *     | Integer |<--------------------------------------------------| ObjectInfo |
 *     +---------+                                                   +------------+
 * </pre>
 *
 * Consider now an extension of the simplified heap-object graph presented above where we take into
 * account the fact that the two {@link Integer} instances are stored in the
 * {@code Integer.IntegerCache#cache} array of type {@link Integer}[]. We report this heap-object
 * graph with the associated {@link NativeImageHeap.ObjectInfo}s and reasons below:
 *
 * <pre>
 *                                                            +-------------+
 *                                                            | HostedField |
 *                                                            +-------------+
 *                                                                   ^
 *                                                                   |
 *                 +------+                                   +------------+
 *                 | Pair |<----------------------------------| ObjectInfo |
 *                 +------+                                   +------------+
 *                /        \                                  ^             ^
 *               /          v                                /               \
 *              /            +---------+         +------------+               \
 *             /             | Integer |<--------| ObjectInfo |                \
 *            /              +---------+         +------------+                 \
 *           v              ^                                                    \
 *     +---------+        /                                          +------------+
 *     | Integer |<-----/--------------------------------------------| ObjectInfo |
 *     +---------+    /                                              +------------+
 *          ^       /
 *          |     /
 *    +-----------+                                                   +------------+
 *    | Integer[] |<--------------------------------------------------| ObjectInfo |
 *    +-----------+                                                   +------------+
 *                                                                          |
 *                                                                         ...
 * </pre>
 *
 * As the figure shows, the {@link NativeImageHeap.ObjectInfo}s of the {@link Integer}s are not
 * connected with the {@link NativeImageHeap.ObjectInfo} of the {@link Integer}[]. This is because,
 * in this example, Native Image discovered the {@link Integer}s via the
 * {@link org.graalvm.collections.Pair} instance and not via the cache array.
 *
 */
@AutomaticallyRegisteredImageSingleton
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class)
public class ImageHeapReasonSupport {

    protected static final String UNKNOWN_REASON_KIND = "Unknown reason kind";

    @Fold
    public static ImageHeapReasonSupport singleton() {
        return ImageSingletons.lookup(ImageHeapReasonSupport.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    protected ImageHeapReasonSupport() {
    }

    protected enum HeapInclusionReason {
        InternedStringsTable,
        FillerObject,
        StaticObjectFields,
        DataSection,
        StaticPrimitiveFields,
        Resource,
    }

    /**
     * @see com.oracle.svm.core.jdk.StringInternSupport
     */
    public Object internedStringsTable() {
        return HeapInclusionReason.InternedStringsTable;
    }

    /**
     * Provides the inclusion reason for filler objects, i.e., objects used to ensure the alignment.
     *
     * @see com.oracle.svm.core.heap.FillerObject
     */
    public Object fillerObject() {
        return HeapInclusionReason.FillerObject;
    }

    public Object staticObjectFields() {
        return HeapInclusionReason.StaticObjectFields;
    }

    public Object dataSection() {
        return HeapInclusionReason.DataSection;
    }

    public Object staticPrimitiveFields() {
        return HeapInclusionReason.StaticPrimitiveFields;
    }

    public Object resource() {
        return HeapInclusionReason.Resource;
    }

    public Object arrayAccess(Object arrayReason, @SuppressWarnings("unused") int index) {
        return arrayReason;
    }

    public Object fieldAccess(Object objectReason, @SuppressWarnings("unused") HostedField field) {
        return objectReason;
    }

    public Object description(String description) {
        return description;
    }

    public Object compilation(CompilationResult compilation) {
        return compilation.getName();
    }

    public Object staticField(HostedField field) {
        assert field.isStatic();
        return field;
    }

    public Object bytecodePosition(BytecodePosition position) {
        return position.getMethod();
    }

    public Object objectInclusionReason(@SuppressWarnings("unused") NativeImageHeap.ObjectInfo info, Object parent, @SuppressWarnings("unused") HostedConstantReflectionProvider hConstantReflection) {
        // @formatter:off
        /*
         * Return `parent` to indicate that `info` is included in the image heap because of `parent`:
         *
         *                                                            +-------------+
         *                                                            | HostedField |
         *                                                            +-------------+
         *                                                                   ^
         *                                                                   |
         *                 +------+                                   +------------+
         *                 | Pair |<----------------------------------| ObjectInfo |<---------- `parent` parameter
         *                 +------+                                   +------------+
         *                /        \
         *               /          v
         *              /            +---------+         +------------+
         *             /             | Integer |<--------| ObjectInfo |<----------------------- `info` parameter
         *            /              +---------+         +------------+
         *           v
         *     +---------+                                                   +------------+
         *     | Integer |<--------------------------------------------------| ObjectInfo |
         *     +---------+                                                   +------------+
         */
        // @formatter:on
        return parent;
    }

    public Object reasonForInfo(NativeImageHeap.ObjectInfo info) {
        return info;
    }

    public boolean isMethod(Object reason) {
        return reason instanceof ResolvedJavaMethod;
    }

    public boolean isDescription(Object reason) {
        return reason instanceof String;
    }

    public boolean isStaticField(Object reason) {
        return reason instanceof HostedField;
    }

    public boolean isObject(Object reason) {
        return reason instanceof NativeImageHeap.ObjectInfo;
    }

    public boolean isFillerObject(NativeImageHeap.ObjectInfo info) {
        return info.getMainReason().equals(fillerObject());
    }

    public boolean isInternedStringsTable(NativeImageHeap.ObjectInfo info) {
        return info.getMainReason().equals(internedStringsTable());
    }

    /**
     * Extracts the object associated with the provided reason. Invocation to this method should be
     * guarded by {@link #isObject(Object)}.
     */
    public NativeImageHeap.ObjectInfo getObject(Object reason) {
        if (!isObject(reason)) {
            throw unexpectedReason(reason, NativeImageHeap.ObjectInfo.class);
        }
        return (NativeImageHeap.ObjectInfo) reason;
    }

    /**
     * Extracts the field associated with the provided reason. Invocation to this method should be
     * guarded by {@link #isStaticField(Object)}.
     */
    public HostedField getStaticField(Object reason) {
        if (!isStaticField(reason)) {
            throw unexpectedReason(reason, HostedField.class);
        }
        return (HostedField) reason;
    }

    /**
     * Extracts the method associated with the provided reason. Invocation to this method should be
     * guarded by {@link #isMethod(Object)}.
     */
    public String getMethod(Object reason) {
        if (!isMethod(reason)) {
            throw unexpectedReason(reason, ResolvedJavaMethod.class);
        }
        return ((ResolvedJavaMethod) reason).getName();
    }

    /**
     * Extracts the description associated with the provided reason. Invocation to this method
     * should be guarded by {@link #isDescription(Object)}.
     */
    public String getDescription(Object reason) {
        if (!isDescription(reason)) {
            throw unexpectedReason(reason, String.class);
        }
        return reason.toString();
    }

    protected static RuntimeException unexpectedReason(Object reason, Class<?> expected) {
        return VMError.shouldNotReachHere(String.format("Unexpected reason of type: %s. Expected: %s", reason.getClass().getName(), expected.getName()));
    }

    /**
     * Depending on the runtime type of {@code reason}, returns a specific string describing the
     * kind of the given reason object.
     */
    public String kind(Object reason) {
        if (isMethod(reason)) {
            return "method";
        }

        return switch (reason) {
            case String _ -> "description";
            case NativeImageHeap.ObjectInfo _ -> "object";
            case HostedField _ -> "staticField";
            case HeapInclusionReason _ -> "svmInternal";
            default -> {
                assert false : UNKNOWN_REASON_KIND;
                yield UNKNOWN_REASON_KIND;
            }
        };
    }

    /**
     * Obtains a string representation of the value for {@code reason}.
     */
    public String value(Object reason) {
        if (isMethod(reason)) {
            return getMethod(reason);
        }

        return switch (reason) {
            case String reasonString -> reasonString;
            case NativeImageHeap.ObjectInfo info -> String.valueOf(info.getIdentityHashCode());
            case HostedField field -> field.format("%H#%n");
            case HeapInclusionReason _ -> reason.toString();
            default -> {
                assert false : UNKNOWN_REASON_KIND;
                yield UNKNOWN_REASON_KIND;
            }
        };
    }

    public StringBuilder fillReasonStack(StringBuilder msg, Object reason) {
        if (reason instanceof NativeImageHeap.ObjectInfo info) {
            msg.append("    object: ").append(info.getObject()).append("  of class: ").append(info.getClazz().toJavaName()).append(System.lineSeparator());
            return fillReasonStack(msg, info.getMainReason());
        }
        return msg.append("    root: ").append(reason).append(System.lineSeparator());
    }

    public void dumpMetadata(@SuppressWarnings("unused") ImageHeapLayoutInfo heapLayout, @SuppressWarnings("unused") Collection<NativeImageHeap.ObjectInfo> objects) {
        // no metadata to dump
    }
}
