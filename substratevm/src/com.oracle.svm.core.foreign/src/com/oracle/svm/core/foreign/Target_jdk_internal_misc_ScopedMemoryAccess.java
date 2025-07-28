/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.foreign;

import java.io.FileDescriptor;
import java.lang.ref.Reference;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.ArenaIntrinsics;
import com.oracle.svm.core.ForeignSupport;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.foreign.ForeignAPIPredicates.SharedArenasDisabled;
import com.oracle.svm.core.foreign.ForeignAPIPredicates.SharedArenasEnabled;
import com.oracle.svm.core.nodes.foreign.MemoryArenaValidInScopeNode;
import com.oracle.svm.core.util.BasedOnJDKFile;

import jdk.internal.access.foreign.MappedMemoryUtilsProxy;
import jdk.internal.foreign.AbstractMemorySegmentImpl;
import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.misc.ScopedMemoryAccess;
import jdk.internal.misc.ScopedMemoryAccess.ScopedAccessError;
import jdk.internal.vm.vector.VectorSupport;

/**
 * Support for shared arenas on SVM:
 * <p>
 * Shared arenas are implemented at safepoints in SVM. A shared arena can only be closed at a
 * safepoint, ensuring that any other thread using the closed arena will only see the closure at a
 * safepoint. To achieve this, SVM relies on compiler support. When accessing scoped memory (marked
 * with {@link MemoryArenaValidInScopeNode}), all dominated accesses within that scope will check
 * for closed arenas.
 * <p>
 * To avoid eager creation of scope checks on all usages, we introduce validity checks
 * ({@link MemorySessionImpl#checkValidStateRaw()}) at points guaranteed to be followed by a
 * safepoint. For proper exception control flow when accessing a closed session, we use
 * substitutions of methods in {@link ScopedMemoryAccess}. Each scoped access is substituted with
 * the pattern
 * {@link SubstrateForeignUtil#sessionExceptionHandler(MemorySessionImpl, Object, long)}, which
 * marks the entry of a scoped region. This code must dominate all other code in the scoped method;
 * otherwise, dominance problems may occur. Note that this constraint is not enforced, and modifying
 * the generated code without understanding the implications may render it unschedulable.
 * <p>
 * The {@link ArenaIntrinsics#checkValidArenaInScope(MemorySessionImpl, Object, long)} call signals
 * the compiler to check all naturally dominated memory accesses and kills. Every such access will
 * then be checked for session validity, ensuring that all session objects requiring scope checks
 * are properly validated.
 * <p>
 * The logic for code duplication, dominance, and node state checking is implemented in
 * {@link com.oracle.svm.hosted.foreign.phases.SubstrateOptimizeSharedArenaAccessPhase}.
 * <p>
 * Two assumptions are made about the @Scoped-annotated methods in the original JDK class:
 * <ol>
 * <li>They must follow a specific code pattern (see
 * {@code com.oracle.svm.hosted.phases.SharedGraphBuilderPhase.SharedBytecodeParser#instrumentScopedMethod}).
 * If the pattern changes, consider adding an explicit substitution or adapting the parser.</li>
 * <li>The actual operation using the native address (e.g., {@code Unsafe.setMemory}) will be
 * recursively inlined, allowing the compiler phase to see all safepoints. However, some calls may
 * remain (e.g., due to explicit @NeverInline annotations). These remaining calls must not access
 * the native memory associated with the session. As this is verified, such remaining calls must be
 * explicitly allowed by registering them in
 * {@code com.oracle.svm.hosted.foreign.ForeignFunctionsFeature#initSafeArenaAccessors}.</li>
 * </ol>
 *
 * @noinspection CaughtExceptionImmediatelyRethrown
 */
@SuppressWarnings("javadoc")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+15/src/java.base/share/classes/jdk/internal/misc/X-ScopedMemoryAccess-bin.java.template")
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+13/src/java.base/share/classes/jdk/internal/misc/X-ScopedMemoryAccess.java.template")
@TargetClass(className = "jdk.internal.misc.ScopedMemoryAccess", onlyWith = ForeignAPIPredicates.Enabled.class)
public final class Target_jdk_internal_misc_ScopedMemoryAccess {

    @Substitute
    static void registerNatives() {
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+20/src/java.base/share/classes/java/nio/MappedMemoryUtils.java#L50-L77")
    @SuppressWarnings("static-method")
    @Substitute
    @TargetElement(onlyWith = SharedArenasEnabled.class)
    @ForeignSupport.Scoped
    @AlwaysInline("Safepoints must be visible in caller")
    public void loadInternal(MemorySessionImpl session, MappedMemoryUtilsProxy mappedUtils, long address, boolean isSync, long size) {
        SubstrateForeignUtil.checkIdentity(mappedUtils, Target_java_nio_MappedMemoryUtils.PROXY);
        try {
            SubstrateForeignUtil.sessionExceptionHandler(session, null, address);
            try {
                SubstrateForeignUtil.checkSession(session);
                SubstrateMappedMemoryUtils.load(address, isSync, size);
            } finally {
                Reference.reachabilityFence(session);
            }
        } catch (ScopedAccessError e) {
            throw e;
        }
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+14/src/java.base/share/classes/java/nio/MappedMemoryUtils.java#L182-L185")
    @SuppressWarnings("static-method")
    @Substitute
    @TargetElement(onlyWith = SharedArenasEnabled.class)
    @ForeignSupport.Scoped
    @AlwaysInline("Safepoints must be visible in caller")
    public boolean isLoadedInternal(MemorySessionImpl session, MappedMemoryUtilsProxy mappedUtils, long address, boolean isSync, long size) {
        SubstrateForeignUtil.checkIdentity(mappedUtils, Target_java_nio_MappedMemoryUtils.PROXY);
        try {
            SubstrateForeignUtil.sessionExceptionHandler(session, null, address);
            try {
                SubstrateForeignUtil.checkSession(session);
                // originally: 'mappedUtils.isLoaded(address, isSync, size)'
                return Target_java_nio_MappedMemoryUtils.isLoaded(address, isSync, size);
            } finally {
                Reference.reachabilityFence(session);
            }
        } catch (ScopedAccessError e) {
            throw e;
        }
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+14/src/java.base/share/classes/java/nio/MappedMemoryUtils.java#L192-L195")
    @SuppressWarnings("static-method")
    @Substitute
    @TargetElement(onlyWith = SharedArenasEnabled.class)
    @ForeignSupport.Scoped
    @AlwaysInline("Safepoints must be visible in caller")
    public void unloadInternal(MemorySessionImpl session, MappedMemoryUtilsProxy mappedUtils, long address, boolean isSync, long size) {
        SubstrateForeignUtil.checkIdentity(mappedUtils, Target_java_nio_MappedMemoryUtils.PROXY);
        try {
            SubstrateForeignUtil.sessionExceptionHandler(session, null, address);
            try {
                SubstrateForeignUtil.checkSession(session);

                // originally: 'mappedUtils.unload(address, isSync, size)'
                Target_java_nio_MappedMemoryUtils.unload(address, isSync, size);
            } finally {
                Reference.reachabilityFence(session);
            }
        } catch (ScopedAccessError e) {
            throw e;
        }
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+14/src/java.base/share/classes/java/nio/MappedMemoryUtils.java#L197-L200")
    @SuppressWarnings("static-method")
    @Substitute
    @TargetElement(onlyWith = SharedArenasEnabled.class)
    @ForeignSupport.Scoped
    @AlwaysInline("Safepoints must be visible in caller")
    public void forceInternal(MemorySessionImpl session, MappedMemoryUtilsProxy mappedUtils, FileDescriptor fd, long address, boolean isSync, long index, long length) {
        SubstrateForeignUtil.checkIdentity(mappedUtils, Target_java_nio_MappedMemoryUtils.PROXY);
        try {
            SubstrateForeignUtil.sessionExceptionHandler(session, null, address);
            try {
                SubstrateForeignUtil.checkSession(session);

                // originally: 'mappedUtils.force(fd, address, isSync, index, length);'
                Target_java_nio_MappedMemoryUtils.force(fd, address, isSync, index, length);
            } finally {
                Reference.reachabilityFence(session);
            }
        } catch (ScopedAccessError e) {
            throw e;
        }
    }

    @SuppressWarnings("unused")
    @Substitute
    @TargetElement(onlyWith = SharedArenasEnabled.class)
    @AlwaysInline("Safepoints must be visible in caller")
    private static <V extends VectorSupport.Vector<E>, E, S extends VectorSupport.VectorSpecies<E>> V loadFromMemorySegmentScopedInternal(MemorySessionImpl session,
                    Class<? extends V> vmClass, Class<E> e, int length,
                    AbstractMemorySegmentImpl msp, long offset,
                    S s,
                    VectorSupport.LoadOperation<AbstractMemorySegmentImpl, V, S> defaultImpl) {
        throw SharedArenasEnabled.vectorAPIUnsupported();
    }

    @SuppressWarnings("unused")
    @Substitute
    @TargetElement(onlyWith = SharedArenasEnabled.class)
    @AlwaysInline("Safepoints must be visible in caller")
    private static <V extends VectorSupport.Vector<E>, E, S extends VectorSupport.VectorSpecies<E>, M extends VectorSupport.VectorMask<E>> V loadFromMemorySegmentMaskedScopedInternal(
                    MemorySessionImpl session, Class<? extends V> vmClass,
                    Class<M> maskClass, Class<E> e, int length,
                    AbstractMemorySegmentImpl msp, long offset, M m,
                    S s, int offsetInRange,
                    VectorSupport.LoadVectorMaskedOperation<AbstractMemorySegmentImpl, V, S, M> defaultImpl) {
        throw SharedArenasEnabled.vectorAPIUnsupported();
    }

    @SuppressWarnings("unused")
    @Substitute
    @TargetElement(onlyWith = SharedArenasEnabled.class)
    @AlwaysInline("Safepoints must be visible in caller")
    public static <V extends VectorSupport.Vector<E>, E> void storeIntoMemorySegment(Class<? extends V> vmClass, Class<E> e, int length,
                    V v,
                    AbstractMemorySegmentImpl msp, long offset,
                    VectorSupport.StoreVectorOperation<AbstractMemorySegmentImpl, V> defaultImpl) {
        throw SharedArenasEnabled.vectorAPIUnsupported();
    }

    @SuppressWarnings("unused")
    @Substitute
    @TargetElement(onlyWith = SharedArenasEnabled.class)
    @AlwaysInline("Safepoints must be visible in caller")
    public static <V extends VectorSupport.Vector<E>, E, M extends VectorSupport.VectorMask<E>> void storeIntoMemorySegmentMasked(Class<? extends V> vmClass, Class<M> maskClass, Class<E> e,
                    int length, V v, M m,
                    AbstractMemorySegmentImpl msp, long offset,
                    VectorSupport.StoreVectorMaskedOperation<AbstractMemorySegmentImpl, V, M> defaultImpl) {
        throw SharedArenasEnabled.vectorAPIUnsupported();
    }

    /**
     * This method synchronizes with all other Java threads in order to be able to safely close the
     * session.
     * <p>
     * On HotSpot, a thread-local handshake (i.e. {@code CloseScopedMemoryClosure}) with all other
     * Java threads is performed. {@code CloseScopedMemoryClosure} can be summarised as follows:
     * Each thread checks the last <code>max_critical_stack_depth</code> (fixed to 10) virtual
     * frames of its own stack trace. If it contains any <code>@Scoped</code>-annotated method
     * called on the sessions being freed, it installs an async exception (a ScopedAccessError
     * provided as argument).
     * <p>
     * In our case, we will force a safepoint to synchronize all threads. The VM operation (i.e.
     * {@link SyncCloseScopeOperation}) is essentially an empty operation but kills the field
     * location of {@link Target_jdk_internal_foreign_MemorySessionImpl#state}.
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+5/src/hotspot/share/prims/scopedMemoryAccess.cpp#L215-L218")
    @SuppressWarnings("static-method")
    @Substitute
    @TargetElement(onlyWith = SharedArenasEnabled.class)
    void closeScope0(Target_jdk_internal_foreign_MemorySessionImpl session, @SuppressWarnings("unused") Target_jdk_internal_misc_ScopedMemoryAccess_ScopedAccessError error) {
        new SyncCloseScopeOperation(session).enqueue();
    }

    @Substitute
    @TargetElement(name = "closeScope0", onlyWith = SharedArenasDisabled.class)
    @SuppressWarnings({"unused", "static-method"})
    void closeScope0Unsupported(Target_jdk_internal_foreign_MemorySessionImpl session, Target_jdk_internal_misc_ScopedMemoryAccess_ScopedAccessError error) {
        throw SharedArenasDisabled.fail();
    }
}
