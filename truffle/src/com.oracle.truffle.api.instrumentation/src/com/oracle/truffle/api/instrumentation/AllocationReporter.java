/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.LinkedList;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LanguageInfo;

/**
 * Reporter of guest language value allocations. Language implementation ought to use this class to
 * report all allocations and re-allocations of guest language values. An instance of this class can
 * be obtained from {@link Env#lookup(java.lang.Class) Env.lookup(AllocationReporter.class)}. If
 * used from compiled code paths, then the allocation reporter must be stored in a compilation final
 * or final field.
 * <p>
 * Usage example: {@link AllocationReporterSnippets#example}
 *
 * @since 0.27
 */
public final class AllocationReporter {

    /**
     * Constant specifying an unknown size. Use it when it's not possible to estimate size of the
     * memory being allocated.
     *
     * @since 0.27
     */
    public static final long SIZE_UNKNOWN = Long.MIN_VALUE;

    /**
     * Name of a property that is fired when an {@link #isActive() active} property of this reporter
     * changes.
     *
     * @since 0.27
     * @see #isActive()
     * @see #addPropertyChangeListener(java.beans.PropertyChangeListener)
     */
    public static final String PROPERTY_ACTIVE = "active";

    final LanguageInfo language;
    private final PropertyChangeSupport propSupport = new PropertyChangeSupport(this);
    private ThreadLocal<LinkedList<Reference<Object>>> valueCheck;

    @CompilationFinal private volatile Assumption listenersNotChangedAssumption = Truffle.getRuntime().createAssumption();
    @CompilationFinal(dimensions = 1) private volatile AllocationListener[] listeners = null;

    AllocationReporter(LanguageInfo language) {
        this.language = language;
        assert (valueCheck = new ThreadLocal<>()) != null;
    }

    /**
     * Add a property change listener that is notified when a property of this reporter changes. Use
     * it to get notified when {@link #isActive()} changes.
     *
     * @since 0.27
     * @see #PROPERTY_ACTIVE
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propSupport.addPropertyChangeListener(listener);
    }

    /**
     * Remove a property change listener that is notified when state of this reporter changes.
     *
     * @since 0.27
     * @see #addPropertyChangeListener(java.beans.PropertyChangeListener)
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propSupport.removePropertyChangeListener(listener);
    }

    /**
     * Test if the reporter instance is actually doing some reporting when notify methods are
     * called. Methods {@link #onEnter(java.lang.Object, long, long)} and
     * {@link #onReturnValue(java.lang.Object, long, long)} have no effect when this method returns
     * false. A {@link PropertyChangeListener} can be
     * {@link #addPropertyChangeListener(java.beans.PropertyChangeListener) added} to listen on
     * changes of this property.
     *
     * @return <code>true</code> when there are some {@link AllocationListener}s attached,
     *         <code>false</code> otherwise.
     * @since 0.27
     */
    public boolean isActive() {
        if (!listenersNotChangedAssumption.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
        }
        return listeners != null;
    }

    void addListener(AllocationListener l) {
        CompilerAsserts.neverPartOfCompilation();
        boolean hadListeners;
        synchronized (this) {
            if (listeners == null) {
                listeners = new AllocationListener[]{l};
                hadListeners = false;
            } else {
                int index = listeners.length;
                AllocationListener[] newListeners = Arrays.copyOf(listeners, index + 1);
                newListeners[index] = l;
                listeners = newListeners;
                hadListeners = true;
            }
            Assumption assumption = listenersNotChangedAssumption;
            listenersNotChangedAssumption = Truffle.getRuntime().createAssumption();
            assumption.invalidate();
        }
        if (!hadListeners) {
            propSupport.firePropertyChange(PROPERTY_ACTIVE, false, true);
        }
    }

    void removeListener(AllocationListener l) {
        CompilerAsserts.neverPartOfCompilation();
        boolean hasListeners = true;
        synchronized (this) {
            final int len = listeners.length;
            if (len == 1) {
                if (listeners[0] == l) {
                    listeners = null;
                    hasListeners = false;
                }
            } else {
                for (int i = 0; i < len; i++) {
                    if (listeners[i] == l) {
                        if (i == (len - 1)) {
                            listeners = Arrays.copyOf(listeners, i);
                        } else if (i == 0) {
                            listeners = Arrays.copyOfRange(listeners, 1, len);
                        } else {
                            AllocationListener[] newListeners = new AllocationListener[len - 1];
                            System.arraycopy(listeners, 0, newListeners, 0, i);
                            System.arraycopy(listeners, i + 1, newListeners, i, len - i - 1);
                            listeners = newListeners;
                        }
                        break;
                    }
                }
            }
            Assumption assumption = listenersNotChangedAssumption;
            listenersNotChangedAssumption = Truffle.getRuntime().createAssumption();
            assumption.invalidate();
        }
        if (!hasListeners) {
            propSupport.firePropertyChange(PROPERTY_ACTIVE, true, false);
        }
    }

    /**
     * Report an intent to allocate a new guest language value, or re-allocate an existing one. This
     * method delegates to all registered listeners
     * {@link AllocationListener#onEnter(com.oracle.truffle.api.instrumentation.AllocationEvent)}.
     * Only primitive types, String and {@link com.oracle.truffle.api.interop.TruffleObject} are
     * accepted value types. The change in memory consumption caused by the allocation is going to
     * be <code>newSizeEstimate - oldSize</code> when both old size and new size are known. The
     * change can be either positive or negative.
     * <p>
     * A call to this method needs to be followed by a call to
     * {@link #onReturnValue(java.lang.Object, long, long)} with the actual allocated value, or with
     * the same (re-allocated) value. Nested allocations are supported, several calls to
     * <code>onEnter</code> prior every sub-value allocation can be followed by the appropriate
     * number of <code>onReturnValue</code> calls after the sub-values are allocated, in the
     * opposite order.
     *
     * @param valueToReallocate <code>null</code> in case of a new allocation, or the value that is
     *            to be re-allocated.
     * @param oldSize <code>0</code> in case of a new allocation, or the size in bytes of value to
     *            be re-allocated. Can be {@link #SIZE_UNKNOWN} when the value size is not known.
     * @param newSizeEstimate an estimate of the allocation size of the value which is to be created
     *            or re-allocated, in bytes. Can be {@link #SIZE_UNKNOWN} when the allocation size
     *            is not known.
     * @since 0.27
     */
    public void onEnter(Object valueToReallocate, long oldSize, long newSizeEstimate) {
        assert enterSizeCheck(valueToReallocate, oldSize, newSizeEstimate);
        assert valueToReallocate == null || allocateValueCheck(valueToReallocate);
        notifyAllocateOrReallocate(valueToReallocate, oldSize, newSizeEstimate);
    }

    @ExplodeLoop
    private void notifyAllocateOrReallocate(Object value, long oldSize, long newSizeEstimate) {
        assert setValueCheck(value);
        CompilerAsserts.partialEvaluationConstant(this);
        if (!listenersNotChangedAssumption.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
        }
        AllocationListener[] ls = this.listeners;
        if (ls != null) {
            AllocationEvent event = new AllocationEvent(language, value, oldSize, newSizeEstimate);
            for (AllocationListener l : ls) {
                l.onEnter(event);
            }
        }
    }

    /**
     * Report an allocation of a new one or re-allocation of an existing guest language value. This
     * method notifies all registered listeners
     * {@link AllocationListener#onReturnValue(com.oracle.truffle.api.instrumentation.AllocationEvent)}
     * . Only primitive types, String and {@link com.oracle.truffle.api.interop.TruffleObject} are
     * accepted value types. The change in memory consumption caused by the allocation is
     * <code>newSize - oldSize</code> when both old size and new size are known. The change can be
     * either positive or negative.
     * <p>
     * A call to {@link #onEnter(java.lang.Object, long, long)} must precede this call. In case of
     * re-allocation, the value object passed to {@link #onEnter(java.lang.Object, long, long)} must
     * be the same instance as the value passed to this method.
     *
     * @param value the value that was newly allocated, or the re-allocated value. Must not be
     *            <code>null</code>.
     * @param oldSize size in bytes of an old value, if any. Must be <code>0</code> for newly
     *            allocated values. In case of re-allocation it's the size of the original value
     *            before re-allocation. Can be {@link #SIZE_UNKNOWN} when not known.
     * @param newSize the size of the allocated value in bytes. In case of re-allocation, it's the
     *            size of the object after re-allocation. The <code>newSize</code> may be less than
     *            <code>oldSize</code> when the object size shrinks. Can be {@link #SIZE_UNKNOWN}
     *            when not known.
     * @since 0.27
     */
    public void onReturnValue(Object value, long oldSize, long newSize) {
        assert allocateValueCheck(value);
        assert allocatedCheck(value, oldSize, newSize);
        notifyAllocated(value, oldSize, newSize);
    }

    @ExplodeLoop
    private void notifyAllocated(Object value, long oldSize, long newSize) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (!listenersNotChangedAssumption.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
        }
        AllocationListener[] ls = this.listeners;
        if (ls != null) {
            AllocationEvent event = new AllocationEvent(language, value, oldSize, newSize);
            for (AllocationListener l : ls) {
                l.onReturnValue(event);
            }
        }
    }

    @TruffleBoundary
    private static boolean enterSizeCheck(Object valueToReallocate, long oldSize, long newSizeEstimate) {
        assert (newSizeEstimate == SIZE_UNKNOWN || newSizeEstimate > 0) : "Wrong new size estimate = " + newSizeEstimate;
        assert valueToReallocate != null || oldSize == 0 : "Old size must be 0 for new allocations. Was: " + oldSize;
        assert valueToReallocate == null || (oldSize > 0 || oldSize == SIZE_UNKNOWN) : "Old size of a re-allocated value must be positive or unknown. Was: " + oldSize;
        return true;
    }

    @TruffleBoundary
    private boolean setValueCheck(Object value) {
        LinkedList<Reference<Object>> list = valueCheck.get();
        if (list == null) {
            list = new LinkedList<>();
            valueCheck.set(list);
        }
        list.add(new WeakReference<>(value));
        return true;
    }

    @TruffleBoundary
    private static boolean allocateValueCheck(Object value) {
        if (value == null) {
            throw new NullPointerException("No allocated value.");
        }
        // Strings are O.K.
        if (value instanceof String) {
            return true;
        }
        // Primitive types are O.K.
        if (value instanceof Boolean || value instanceof Byte || value instanceof Character ||
                        value instanceof Short || value instanceof Integer || value instanceof Long ||
                        value instanceof Float || value instanceof Double) {
            return true;
        }
        // TruffleObject is O.K.
        boolean isTO = InstrumentationHandler.ACCESSOR.isTruffleObject(value);
        assert isTO : "Wrong value class, TruffleObject is required. Was: " + value.getClass().getName();
        return isTO;
    }

    @TruffleBoundary
    private boolean allocatedCheck(Object value, long oldSize, long newSize) {
        LinkedList<Reference<Object>> list = valueCheck.get();
        assert list != null && !list.isEmpty() : "onEnter() was not called";
        Object orig = list.removeLast().get();
        assert orig == null || orig == value : "A different reallocated value. Was: " + orig + " now is: " + value;
        assert orig == null && oldSize == 0 || orig != null : "Old size must be 0 for new allocations. Was: " + oldSize;
        assert orig != null && (oldSize > 0 || oldSize == SIZE_UNKNOWN) || orig == null : "Old size of a re-allocated value must be positive or unknown. Was: " + oldSize;
        assert newSize == SIZE_UNKNOWN || newSize > 0 : "New value size must be positive or unknown. Was: " + newSize;
        return true;
    }
}

class AllocationReporterSnippets extends TruffleLanguage<ContextObject> {

    void example() {
    }

    // @formatter:off
    // BEGIN: AllocationReporterSnippets#example
    @Override
    protected ContextObject createContext(Env env) {
        AllocationReporter reporter = env.lookup(AllocationReporter.class);
        return new ContextObject(reporter);
    }

    Object allocateNew() {
        AllocationReporter reporter = getContextReference().get().getReporter();
        // Test if the reporter is active, we should compute the size estimate
        if (reporter.isActive()) {
            long size = findSizeEstimate();
            reporter.onEnter(null, 0, size);
        }
        // Do the allocation itself
        Object newObject = new MyTruffleObject();
        // Test if the reporter is active,
        // we should compute the allocated object size
        if (reporter.isActive()) {
            long size = findSize(newObject);
            reporter.onReturnValue(newObject, 0, size);
        }
        return newObject;
    }

    Object allocateComplex() {
        AllocationReporter reporter = getContextReference().get().getReporter();
        // If the allocated size is a constant, onEnter() and onReturnValue()
        // can be called without a fast-path performance penalty when not active
        reporter.onEnter(null, 0, 16);
        // Do the allocation itself
        Object newObject = createComplexObject();
        // Report the allocation
        reporter.onReturnValue(newObject, 0, 16);
        return newObject;
    }
    // END: AllocationReporterSnippets#example
    // @formatter:on

    @Override
    protected Object findExportedSymbol(ContextObject context, String globalName, boolean onlyExplicit) {
        return null;
    }

    @Override
    protected Object getLanguageGlobal(ContextObject context) {
        return null;
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        return false;
    }

    private static long findSizeEstimate() {
        return 0L;
    }

    private static long findSize(Object newObject) {
        assert newObject != null;
        return 0L;
    }

    private static Object createComplexObject() {
        return null;
    }

    private static class MyTruffleObject {
    }

}

class ContextObject {

    private final AllocationReporter reporter;

    ContextObject(AllocationReporter reporter) {
        this.reporter = reporter;
    }

    public AllocationReporter getReporter() {
        return reporter;
    }

}
