/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.meta;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Executable;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import jdk.graal.compiler.debug.GraalError;
import org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.ObjectScanner.MethodParsing;
import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AnalysisFuture;
import com.oracle.graal.pointsto.util.ConcurrentLightHashSet;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.ModifiersProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public abstract class AnalysisElement implements AnnotatedElement {

    public abstract AnnotatedElement getWrapped();

    protected abstract AnalysisUniverse getUniverse();

    @Override
    public final boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return getUniverse().getAnnotationExtractor().hasAnnotation(getWrapped(), annotationClass);
    }

    @Override
    public final <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return getUniverse().getAnnotationExtractor().extractAnnotation(getWrapped(), annotationClass, false);
    }

    @Override
    public final <T extends Annotation> T getDeclaredAnnotation(Class<T> annotationClass) {
        return getUniverse().getAnnotationExtractor().extractAnnotation(getWrapped(), annotationClass, true);
    }

    @Override
    public final Annotation[] getAnnotations() {
        throw GraalError.shouldNotReachHere("Getting all annotations is not supported because it initializes all annotation classes and their dependencies");
    }

    @Override
    public final Annotation[] getDeclaredAnnotations() {
        throw GraalError.shouldNotReachHere("Getting all annotations is not supported because it initializes all annotation classes and their dependencies");
    }

    /**
     * Contains reachability handlers that are notified when the element is marked as reachable.
     * Each handler is notified only once, and then it is removed from the set.
     */

    private static final AtomicReferenceFieldUpdater<AnalysisElement, Object> reachableNotificationsUpdater = AtomicReferenceFieldUpdater.newUpdater(AnalysisElement.class, Object.class,
                    "elementReachableNotifications");

    @SuppressWarnings("unused") private volatile Object elementReachableNotifications;

    public void registerReachabilityNotification(ElementNotification notification) {
        ConcurrentLightHashSet.addElement(this, reachableNotificationsUpdater, notification);
    }

    public void notifyReachabilityCallback(AnalysisUniverse universe, ElementNotification notification) {
        notification.notifyCallback(universe, this);
        ConcurrentLightHashSet.removeElement(this, reachableNotificationsUpdater, notification);
    }

    protected void notifyReachabilityCallbacks(AnalysisUniverse universe, List<AnalysisFuture<Void>> futures) {
        ConcurrentLightHashSet.forEach(this, reachableNotificationsUpdater, (ElementNotification c) -> futures.add(c.notifyCallback(universe, this)));
        ConcurrentLightHashSet.removeElementIf(this, reachableNotificationsUpdater, ElementNotification::isNotified);
    }

    public abstract boolean isReachable();

    protected abstract void onReachable();

    /** Return true if reachability handlers should be executed for this element. */
    public boolean isTriggered() {
        return isReachable();
    }

    public static final class ElementNotification {

        private final Consumer<DuringAnalysisAccess> callback;
        private final AtomicReference<AnalysisFuture<Void>> notified = new AtomicReference<>();

        public ElementNotification(Consumer<DuringAnalysisAccess> callback) {
            this.callback = callback;
        }

        public boolean isNotified() {
            return notified.get() != null;
        }

        /**
         * Notify the callback exactly once. Note that this callback can be shared by multiple
         * triggers, the one that triggers it is passed into triggeredElement for debugging.
         */
        AnalysisFuture<Void> notifyCallback(AnalysisUniverse universe, AnalysisElement triggeredElement) {
            assert triggeredElement.isTriggered() : triggeredElement;
            var existing = notified.get();
            if (existing != null) {
                return existing;
            }

            AnalysisFuture<Void> newValue = new AnalysisFuture<>(() -> {
                callback.accept(universe.getConcurrentAnalysisAccess());
                return null;
            });

            existing = notified.compareAndExchange(null, newValue);
            if (existing != null) {
                return existing;
            }

            execute(universe, newValue);
            return newValue;
        }
    }

    public static final class SubtypeReachableNotification {
        private final BiConsumer<DuringAnalysisAccess, Class<?>> callback;
        private final Map<AnalysisType, AnalysisFuture<Void>> seenSubtypes = new ConcurrentHashMap<>();

        public SubtypeReachableNotification(BiConsumer<DuringAnalysisAccess, Class<?>> callback) {
            this.callback = callback;
        }

        /** Notify the callback exactly once for each reachable subtype. */
        public AnalysisFuture<Void> notifyCallback(AnalysisUniverse universe, AnalysisType reachableSubtype) {
            assert reachableSubtype.isReachable() : reachableSubtype;
            return seenSubtypes.computeIfAbsent(reachableSubtype, k -> {
                AnalysisFuture<Void> newValue = new AnalysisFuture<>(() -> {
                    callback.accept(universe.getConcurrentAnalysisAccess(), reachableSubtype.getJavaClass());
                    return null;
                });
                execute(universe, newValue);
                return newValue;
            });
        }
    }

    public static final class MethodOverrideReachableNotification {
        private final BiConsumer<DuringAnalysisAccess, Executable> callback;
        private final Set<AnalysisMethod> seenOverride = ConcurrentHashMap.newKeySet();

        public MethodOverrideReachableNotification(BiConsumer<DuringAnalysisAccess, Executable> callback) {
            this.callback = callback;
        }

        /** Notify the callback exactly once for each reachable method override. */
        public void notifyCallback(AnalysisUniverse universe, AnalysisMethod reachableOverride) {
            assert reachableOverride.isReachable() : reachableOverride;
            if (seenOverride.add(reachableOverride)) {
                Executable javaMethod = reachableOverride.getJavaMethod();
                if (javaMethod != null) {
                    execute(universe, () -> {
                        callback.accept(universe.getConcurrentAnalysisAccess(), javaMethod);
                    });
                }
            }
        }
    }

    protected static void execute(AnalysisUniverse universe, Runnable task) {
        /*
         * Post the tasks to the analysis executor. This ensures that even for elements registered
         * as reachable early, before the analysis is started, the reachability callbacks are run
         * during the analysis.
         */
        universe.getBigbang().postTask((d) -> task.run());
    }

    private static void execute(AnalysisUniverse universe, AnalysisFuture<?> task) {
        /*
         * Post the tasks to the analysis executor. This ensures that even for elements registered
         * as reachable early, before the analysis is started, the reachability callbacks are run
         * during the analysis.
         */
        universe.getBigbang().postTask((d) -> task.ensureDone());
    }

    /**
     * Used to validate the reason why an analysis element is registered as reachable.
     */
    boolean isValidReason(Object reason) {
        if (reason == null) {
            return false;
        }
        if (reason instanceof String s) {
            return !s.isEmpty();
        }
        /*
         * ModifiersProvider is a common interface of ResolvedJavaField, ResolvedJavaMethod and
         * ResolvedJavaType.
         */
        return reason instanceof AnalysisElement || reason instanceof ModifiersProvider || reason instanceof ObjectScanner.ScanReason ||
                        reason instanceof BytecodePosition;
    }

    public static class ReachabilityReason {

    }

    public static class ReachabilityTraceBuilder {
        private final String header;
        private final Object rootReason;
        private final BigBang bb;
        private final StringBuilder reasonTrace;
        private final ArrayDeque<Object> reasonStack;
        private final HashSet<Object> seen;

        ReachabilityTraceBuilder(String traceHeader, Object reason, BigBang bigBang) {
            header = traceHeader;
            rootReason = reason;
            bb = bigBang;
            reasonTrace = new StringBuilder();
            reasonStack = new ArrayDeque<>();
            seen = new HashSet<>();
        }

        public static String buildReachabilityTrace(BigBang bb, Object reason, String header) {
            ReachabilityTraceBuilder builder = new ReachabilityTraceBuilder(header, reason, bb);
            builder.build();
            return builder.reasonTrace.toString();
        }

        public static String buildReachabilityTrace(BigBang bb, Object reason) {
            ReachabilityTraceBuilder builder = new ReachabilityTraceBuilder("Reached by", reason, bb);
            builder.build();
            return builder.reasonTrace.toString();
        }

        static boolean indentAllLines = true;

        private void build() {
            reasonTrace.append(header);
            maybeExpandReasonStack(rootReason);
            String indent = ReportUtils.EMPTY_INDENT;
            String prevIndent = indent;
            while (!reasonStack.isEmpty()) {
                boolean expanded;
                Object top = reasonStack.peekLast();
                if (top instanceof CompoundReason) {
                    CompoundReason compoundReason = (CompoundReason) top;
                    if (compoundReason.isFirst()) {
                        compoundReason.storeCurrentIndent(indent);
                    }
                    Object next = compoundReason.next();
                    if (next != null) {
                        indent = compoundReason.getIndent() + (compoundReason.hasNext() ? ReportUtils.CONNECTING_INDENT : ReportUtils.EMPTY_INDENT);
                        String infix = compoundReason.hasNext() ? ReportUtils.CHILD : ReportUtils.LAST_CHILD;
                        expanded = processReason(next, compoundReason.getIndent() + infix);
                    } else {
                        reasonStack.removeLast();
                        expanded = false;
                        if (indentAllLines) {
                            prevIndent = compoundReason.getIndent();
                        } else {
                            indent = compoundReason.getIndent();
                        }
                    }
                } else {
                    expanded = processReason(reasonStack.pollLast(), indent);
                }
                if (indentAllLines) {
                    if (expanded) {
                        prevIndent = indent;
                        indent = indent + ReportUtils.EMPTY_INDENT;
                    } else {
                        indent = prevIndent;
                    }
                }
            }
        }

        static class CompoundReason {
            final Object[] reasons;
            int index = 0;
            String indent;

            CompoundReason(Object... reasons) {
                this.reasons = reasons;
            }

            boolean isFirst() {
                return index == 0;
            }

            Object next() {
                return index < reasons.length ? reasons[index++] : null;
            }

            boolean hasNext() {
                return index < reasons.length;
            }

            public void storeCurrentIndent(String indentStr) {
                this.indent = indentStr;
            }

            public String getIndent() {
                return indent;
            }
        }

        private boolean processReason(Object current, String prefix) {
            String reasonStr;
            boolean expanded = false;
            if (current instanceof String) {
                reasonStr = "str: " + current;

            } else if (current instanceof AnalysisMethod) {
                AnalysisMethod method = (AnalysisMethod) current;
                reasonStr = "at " + method.format("%f method %H.%n(%p)") + ", " + methodReasonStr(method);
                expanded = methodReason((AnalysisMethod) current);

            } else if (current instanceof AnalysisField) {
                AnalysisField field = (AnalysisField) current;
                reasonStr = "field " + field.format("%H.%n") + " " + fieldReasonStr(field);
                expanded = fieldReason(field);

            } else if (current instanceof AnalysisType) {
                AnalysisType type = (AnalysisType) current;
                reasonStr = "type " + (type).toJavaName() + " " + typeReasonStr(type);
                expanded = typeReason(type);

            } else if (current instanceof ResolvedJavaMethod) {
                reasonStr = ((ResolvedJavaMethod) current).format("%f method %H.%n");

            } else if (current instanceof ResolvedJavaField field) {
                /**
                 * In {@link AnalysisUniverse#lookupAllowUnresolved(JavaField}} we may register a
                 * ResolvedJavaField as reason.
                 *
                 * We convert it to AnalysisField to print more information about why the field is
                 * reachable.
                 */
                AnalysisField analysisField = bb.getUniverse().lookup(field);
                if (analysisField != null) {
                    return processReason(analysisField, prefix);
                } else {
                    reasonStr = "field " + ((ResolvedJavaField) current).format("%H.%n");
                }

            } else if (current instanceof ResolvedJavaType) {
                reasonStr = "type " + ((ResolvedJavaType) current).getName();

            } else if (current instanceof BytecodePosition) {
                BytecodePosition position = (BytecodePosition) current;
                ResolvedJavaMethod method = position.getMethod();
                reasonStr = "at " + method.format("%f") + " method " + method.asStackTraceElement(position.getBCI()) + ", " + methodReasonStr(method);
                expanded = methodReason(position.getMethod());

            } else if (current instanceof MethodParsing) {
                MethodParsing methodParsing = (MethodParsing) current;
                AnalysisMethod method = methodParsing.getMethod();
                reasonStr = "at " + method.format("%f method %H.%n(%p)") + ", " + methodReasonStr(method);
                expanded = methodReason(methodParsing.getMethod());

            } else if (current instanceof ObjectScanner.ScanReason) {
                ObjectScanner.ScanReason scanReason = (ObjectScanner.ScanReason) current;
                reasonStr = scanReason.toString(bb);
                expanded = maybeExpandReasonStack(scanReason.getPrevious());

            } else {
                throw AnalysisError.shouldNotReachHere("Unknown reachability reason.");

            }
            print(prefix, reasonStr);
            return expanded;
        }

        private void print(String prefix, String reasonStr) {
            String reasonStr2 = String.join(System.lineSeparator() + prefix, reasonStr.lines().toArray(String[]::new));
            reasonTrace.append(System.lineSeparator()).append(prefix).append(reasonStr2);
        }

        private boolean typeReason(AnalysisType type) {
            if (type.isInstantiated()) {
                return maybeExpandReasonStack(type.getInstantiatedReason());
            } else {
                return maybeExpandReasonStack(type.getReachableReason());
            }
        }

        private static String typeReasonStr(AnalysisType type) {
            if (type.isInstantiated()) {
                return "is marked as instantiated";
            }
            return "is reachable";
        }

        private boolean fieldReason(AnalysisField field) {
            if (field.getWrittenReason() != null) {
                return maybeExpandReasonStack(field.getWrittenReason());
            } else if (field.getReadReason() != null) {
                return maybeExpandReasonStack(field.getReadReason());
            } else if (field.getAccessedReason() != null) {
                return maybeExpandReasonStack(field.getAccessedReason());
            } else if (field.getFoldedReason() != null) {
                return maybeExpandReasonStack(field.getFoldedReason());
            }
            return false;
        }

        private static String fieldReasonStr(AnalysisField field) {
            if (field.getWrittenReason() != null) {
                return "is written";
            }
            if (field.getReadReason() != null) {
                return "is read";
            }
            if (field.getAccessedReason() != null) {
                return "is accessed";
            }
            if (field.getFoldedReason() != null) {
                return "is folded";
            }
            return "";
        }

        private boolean methodReason(ResolvedJavaMethod method) {
            if (method instanceof AnalysisMethod) {
                AnalysisMethod aMethod = (AnalysisMethod) method;
                if (aMethod.isSimplyImplementationInvoked()) {
                    if (aMethod.isStatic()) {
                        return maybeExpandReasonStack(aMethod.getImplementationInvokedReason());
                    } else {
                        /* For virtual methods we follow back type and caller reachability. */
                        return maybeExpandReasonStack(new CompoundReason(aMethod.getImplementationInvokedReason(), aMethod.getDeclaringClass()));
                    }
                } else if (aMethod.isInlined()) {
                    if (aMethod.isStatic()) {
                        return maybeExpandReasonStack(aMethod.getInlinedReason());
                    } else {
                        /* For virtual methods we follow back type and caller reachability. */
                        return maybeExpandReasonStack(new CompoundReason(aMethod.getInlinedReason(), aMethod.getDeclaringClass()));
                    }
                } else if (aMethod.isIntrinsicMethod()) {
                    return maybeExpandReasonStack(aMethod.getIntrinsicMethodReason());
                } else {
                    return maybeExpandReasonStack(aMethod.getInvokedReason());
                }
            }
            return false;
        }

        private boolean maybeExpandReasonStack(Object reason) {
            if (reason != null && seen.add(reason)) {
                return reasonStack.add(reason);
            }
            return false;
        }

        private static String methodReasonStr(ResolvedJavaMethod method) {
            if (method instanceof AnalysisMethod) {
                AnalysisMethod aMethod = (AnalysisMethod) method;
                if (aMethod.isSimplyImplementationInvoked()) {
                    if (aMethod.isStatic()) {
                        return "implementation invoked";
                    } else {
                        /* For virtual methods we follow back type reachability. */
                        AnalysisType declaringClass = aMethod.getDeclaringClass();
                        assert declaringClass.isInstantiated() || declaringClass.isAbstract() ||
                                        (declaringClass.isInterface() && aMethod.isDefault()) || declaringClass.isReachable() : declaringClass + " is not reachable";
                        return "implementation invoked";
                    }
                } else if (aMethod.isInlined()) {
                    if (aMethod.isStatic()) {
                        return "inlined";
                    } else {
                        AnalysisType declaringClass = aMethod.getDeclaringClass();
                        assert declaringClass.isInstantiated() || declaringClass.isAbstract() ||
                                        (declaringClass.isInterface() && aMethod.isDefault()) || declaringClass.isReachable() : declaringClass + " is not reachable";
                        return "inlined";
                    }
                } else if (aMethod.isIntrinsicMethod()) {
                    return "intrinsified";
                }
            }
            return "<no available reason>";
        }
    }
}
