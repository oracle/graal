/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replaycomp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxy;
import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.GraalError;

/**
 * Records the results of operations during a recorded compilation.
 * <p>
 * An operation is a method call with a JVMCI object proxy as a receiver. The JVMCI objects for
 * which proxies are created are registered in {@link CompilerInterfaceDeclarations}.
 * {@link CompilerInterfaceDeclarations} also declares the behavior of the proxies during recording,
 * i.e., which operations should be recorded or prefetched.
 * <p>
 * This class is also used to ensure that the results of operations return stable values during a
 * recorded compilation. The first invocation of a method should record the result and all
 * subsequent invocations with the same arguments should reuse the result obtained from
 * {@link #getRecordedResultOrMarker}.
 * <p>
 * The recorder tracks the context of the current compiler thread. There are the following contexts:
 * <ul>
 * <li>global context - the initial context in which all results are recorded (e.g., compiler
 * initialization),</li>
 * <li>compilation context ({@link #enterCompilationContext()}) - a recorded compilation of a
 * method,</li>
 * <li>ignored context ({@link #enterIgnoredContext()}) - in this context, the results of operations
 * are not recorded and the results of previous operations are not returned (useful for snippet
 * parsing).</li>
 * </ul>
 * A context is open until the respective is scope is closed. A compilation context also considers
 * the operations recorded in the global context (i.e., {@link #getRecordedResultOrMarker} and
 * {@link #getCurrentRecordedOperations()} also returns the operations from the global context).
 *
 * @see ReplayCompilationSupport
 * @see RecordingCompilationProxies
 */
public class OperationRecorder {
    /**
     * A recorded operation.
     * <p>
     * Represents the receiver object, arguments, and the result of a method call from a recorded
     * compilation. The result of an operation is either a return value or a thrown exception.
     * Results other that non-null return values are represented with {@link SpecialResultMarker}.
     * <p>
     * During a recorded compilation, the instances of this class reference raw JVMCI objects
     * (without proxies). During a replayed compilation, the instances reference proxy objects.
     *
     * @param receiver the receiver object of the operation
     * @param method the invoked method
     * @param args the arguments passed to the method
     * @param resultOrMarker the return value or a marker for the result (e.g., a thrown exception)
     */
    public record RecordedOperation(Object receiver, CompilationProxy.SymbolicMethod method, Object[] args, Object resultOrMarker) {
        public RecordedOperation {
            Objects.requireNonNull(receiver);
            Objects.requireNonNull(method);
            Objects.requireNonNull(resultOrMarker);
        }
    }

    /**
     * The key of a recorded operation.
     *
     * @param receiver the receiver object of the operation
     * @param method the invoked methods
     * @param args the arguments passed to the method
     */
    public record RecordedOperationKey(Object receiver, CompilationProxy.SymbolicMethod method, Object[] args) {
        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (object == null || getClass() != object.getClass()) {
                return false;
            }
            RecordedOperationKey that = (RecordedOperationKey) object;
            return receiver.equals(that.receiver) && method.equals(that.method) && Arrays.equals(args, that.args);
        }

        @Override
        public int hashCode() {
            return Objects.hash(receiver, method, Arrays.hashCode(args));
        }
    }

    private sealed interface RecordingContext {
        Object getRecordedResultOrMarker(RecordedOperationKey key);

        void recordReturnValue(RecordedOperationKey key, Object result);

        void recordExceptionThrown(RecordedOperationKey key, Throwable throwable);

        List<RecordedOperation> getRecordedOperations();
    }

    private static final class IgnoredRecordingContext implements RecordingContext {
        @Override
        public Object getRecordedResultOrMarker(RecordedOperationKey key) {
            return SpecialResultMarker.NO_RESULT_MARKER;
        }

        @Override
        public void recordReturnValue(RecordedOperationKey key, Object result) {
        }

        @Override
        public void recordExceptionThrown(RecordedOperationKey key, Throwable throwable) {

        }

        @Override
        public List<RecordedOperation> getRecordedOperations() {
            return List.of();
        }
    }

    private static final class GlobalRecordingContext implements RecordingContext {
        private final ConcurrentHashMap<RecordedOperationKey, Object> operationResults;

        private GlobalRecordingContext() {
            operationResults = new ConcurrentHashMap<>();
        }

        @Override
        public Object getRecordedResultOrMarker(RecordedOperationKey key) {
            return operationResults.getOrDefault(key, SpecialResultMarker.NO_RESULT_MARKER);
        }

        @Override
        public void recordReturnValue(RecordedOperationKey key, Object result) {
            operationResults.put(key, (result == null) ? SpecialResultMarker.NULL_RESULT_MARKER : result);
        }

        @Override
        public void recordExceptionThrown(RecordedOperationKey key, Throwable throwable) {
            Objects.requireNonNull(throwable);
            operationResults.put(key, new SpecialResultMarker.ExceptionThrownMarker(throwable));
        }

        @Override
        public List<RecordedOperation> getRecordedOperations() {
            List<RecordedOperation> results = new ArrayList<>();
            for (var entry : operationResults.entrySet()) {
                RecordedOperationKey key = entry.getKey();
                results.add(new RecordedOperation(key.receiver, key.method, key.args, entry.getValue()));
            }
            return results;
        }
    }

    private static final class CompilationRecordingContext implements RecordingContext {
        private final EconomicMap<RecordedOperationKey, Object> operationResults;

        private CompilationRecordingContext() {
            this.operationResults = EconomicMap.create();
        }

        @Override
        public Object getRecordedResultOrMarker(RecordedOperationKey key) {
            return operationResults.get(key, SpecialResultMarker.NO_RESULT_MARKER);
        }

        @Override
        public void recordReturnValue(RecordedOperationKey key, Object result) {
            operationResults.put(key, (result == null) ? SpecialResultMarker.NULL_RESULT_MARKER : result);
        }

        @Override
        public void recordExceptionThrown(RecordedOperationKey key, Throwable throwable) {
            Objects.requireNonNull(throwable);
            operationResults.put(key, new SpecialResultMarker.ExceptionThrownMarker(throwable));
        }

        @Override
        public List<RecordedOperation> getRecordedOperations() {
            List<RecordedOperation> results = new ArrayList<>();
            var cursor = operationResults.getEntries();
            while (cursor.advance()) {
                RecordedOperationKey key = cursor.getKey();
                results.add(new RecordedOperation(key.receiver, key.method, key.args, cursor.getValue()));
            }
            return results;
        }
    }

    private final GlobalRecordingContext globalContext;

    private final ThreadLocal<RecordingContext> currentContext;

    OperationRecorder() {
        this.globalContext = new GlobalRecordingContext();
        this.currentContext = ThreadLocal.withInitial(() -> globalContext);
    }

    /**
     * Gets the result for a given operation.
     *
     * @param key the keu of the operation
     * @return the return value or a marker for the result of the operation
     */
    public Object getRecordedResultOrMarker(RecordedOperationKey key) {
        return currentContext.get().getRecordedResultOrMarker(key);
    }

    /**
     * Records the return value for a recorded operation.
     *
     * @param key the key of the operation
     * @param result the return value of the operation
     */
    public void recordReturnValue(RecordedOperationKey key, Object result) {
        currentContext.get().recordReturnValue(key, result);
    }

    /**
     * Records the exception thrown for a given operation.
     *
     * @param key the key of the operation
     * @param throwable the exception thrown by the operation
     */
    public void recordExceptionThrown(RecordedOperationKey key, Throwable throwable) {
        currentContext.get().recordExceptionThrown(key, throwable);
    }

    /**
     * Gets the recorded operations in the global and current compilation context.
     */
    public List<RecordedOperation> getCurrentRecordedOperations() {
        RecordingContext context = currentContext.get();
        GraalError.guarantee(context instanceof CompilationRecordingContext, "recorded operations can be retrieved in compilation context only");
        List<RecordedOperation> results = globalContext.getRecordedOperations();
        results.addAll(context.getRecordedOperations());
        return results;
    }

    /**
     * Enters the context of a method compilation for the current compilation thread.
     *
     * @return a scope for the context
     */
    public DebugCloseable enterCompilationContext() {
        RecordingContext previousContext = currentContext.get();
        currentContext.set(new CompilationRecordingContext());
        return () -> currentContext.set(previousContext);
    }

    /**
     * Enters a context where recording should be disabled for the current compilation thread.
     *
     * @return a scope for the context
     */
    public DebugCloseable enterIgnoredContext() {
        RecordingContext previousContext = currentContext.get();
        currentContext.set(new IgnoredRecordingContext());
        return () -> currentContext.set(previousContext);
    }
}
