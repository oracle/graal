/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.reports.causality.events;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.reports.causality.CausalityExport;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.Signature;
import org.graalvm.collections.Pair;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Executable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess;

public final class CausalityEvents {
    private CausalityEvents() { }

    public interface EventFactory<T> {
        CausalityEvent create(T data);
    }

    public interface EventFactory2<T1, T2> {
        CausalityEvent create(T1 arg1, T2 arg2);
    }

    public interface JniCallVariantWrapperEventFactory {
        CausalityEvent create(Signature signature, boolean isVirtual);
    }

    public interface CodeEventFactory {
        CausalityEvent create(BytecodePosition pos);

        CausalityEvent create(AnalysisMethod m);
    }



    private static class InterningEventFactory<TData> implements EventFactory<TData> {
        private final ConcurrentHashMap<TData, CausalityEvent> internedEvents = new ConcurrentHashMap<>();
        private final Function<TData, CausalityEvent> eventConstructor;

        private InterningEventFactory(Function<TData, CausalityEvent> eventConstructor) {
            this.eventConstructor = eventConstructor;
        }

        public CausalityEvent create(TData data) {
            return internedEvents.computeIfAbsent(data, eventConstructor);
        }
    }

    private static final class InterningEventFactory2<T1, T2> extends InterningEventFactory<Pair<T1, T2>> implements EventFactory2<T1, T2> {
        private InterningEventFactory2(BiFunction<T1, T2, CausalityEvent> constructor) {
            super(pair -> constructor.apply(pair.getLeft(), pair.getRight()));
        }

        public CausalityEvent create(T1 arg1, T2 arg2) {
            return create(Pair.create(arg1, arg2));
        }
    }

    private static final class InterningJniCallVariantWrapperEventFactory extends InterningEventFactory<InterningJniCallVariantWrapperEventFactory.Key> implements JniCallVariantWrapperEventFactory {
        private InterningJniCallVariantWrapperEventFactory() {
            super(k -> new JniCallVariantWrapper(k.signature, k.isVirtual));
        }

        @Override
        public CausalityEvent create(Signature signature, boolean isVirtual) {
            return create(new Key(signature, isVirtual));
        }

        public record Key(Signature signature, boolean isVirtual) {}
    }

    private static final class InterningCodeEventFactory extends InterningEventFactory<InterningCodeEventFactory.InlinedMethods> implements CodeEventFactory {
        private record InlinedMethods(AnalysisMethod[] context) {
            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                InlinedMethods that = (InlinedMethods) o;
                return Arrays.equals(context, that.context);
            }

            @Override
            public int hashCode() {
                return InterningCodeEventFactory.class.hashCode() ^ Arrays.hashCode(context);
            }
        }

        private InterningCodeEventFactory() {
            super(k -> new InlinedMethodCode(k.context));
        }

        @Override
        public CausalityEvent create(BytecodePosition invokePos) {
            ArrayList<AnalysisMethod> context = new ArrayList<>();
            while (invokePos != null) {
                if (invokePos.getBCI() != BytecodeFrame.UNWIND_BCI || invokePos.getCaller() == null) {
                    context.add((AnalysisMethod) invokePos.getMethod());
                }
                invokePos = invokePos.getCaller();
            }

            if (context.isEmpty()) {
                throw new RuntimeException();
            }

            return create(new InlinedMethods(context.toArray(AnalysisMethod[]::new)));
        }

        @Override
        public CausalityEvent create(AnalysisMethod m) {
            return create(new InlinedMethods(new AnalysisMethod[] {m}));
        }
    }



    private static class DummyEventFactory<T> implements EventFactory<T> {
        public CausalityEvent create(T data) {
            return null;
        }
    }

    private static class DummyEventFactory2<T1, T2> implements EventFactory2<T1, T2> {
        public CausalityEvent create(T1 arg1, T2 arg2) {
            return null;
        }
    }

    private static class DummyJniCallVariantWrapperEventFactory implements JniCallVariantWrapperEventFactory {
        @Override
        public CausalityEvent create(Signature signature, boolean isVirtual) {
            return null;
        }
    }

    private static class DummyCodeEventFactory implements CodeEventFactory {
        @Override
        public CausalityEvent create(BytecodePosition pos) {
            return null;
        }

        @Override
        public CausalityEvent create(AnalysisMethod m) {
            return null;
        }
    }



    private static <T> EventFactory<T> factory(Function<T, CausalityEvent> constructor) {
        if (CausalityExport.isEnabled()) {
            return new InterningEventFactory<>(constructor);
        } else {
            return new DummyEventFactory<>();
        }
    }

    private static <T1, T2> EventFactory2<T1, T2> factory(BiFunction<T1, T2, CausalityEvent> constructor) {
        if (CausalityExport.isEnabled()) {
            return new InterningEventFactory2<>(constructor);
        } else {
            return new DummyEventFactory2<>();
        }
    }

    public static final EventFactory<AnalysisMethod> MethodReachable = factory(MethodReachable::new);
    public static final EventFactory<AnalysisMethod> MethodImplementationInvoked = factory(MethodImplementationInvoked::new);
    public static final EventFactory<AnalysisMethod> MethodInlined = factory(MethodInlined::new);
    public static final EventFactory<AnalysisMethod> MethodSnippet = factory(MethodSnippet::new);
    public static final EventFactory<AnalysisMethod> RootMethodRegistration = factory(RootMethodRegistration::new);
    public static final EventFactory<AnalysisMethod> VirtualMethodInvoked = factory(VirtualMethodInvoked::new);
    public static final EventFactory<AnalysisMethod> MethodGraphParsed = factory(MethodGraphParsed::new);
    public static final EventFactory<AnalysisType> TypeReachable = factory(TypeReachable::new);
    public static final EventFactory<AnalysisType> TypeInstantiated = factory(TypeInstantiated::new);
    public static final EventFactory<AnalysisType> TypeInHeap = factory(TypeInHeap::new);
    public static final EventFactory<AnalysisField> FieldRead = factory(FieldRead::new);
    public static final EventFactory<Consumer<DuringAnalysisAccess>> ReachabilityNotificationCallback = factory(ReachabilityNotificationCallback::new);
    public static final EventFactory<BiConsumer<DuringAnalysisAccess, Class<?>>> SubtypeReachableNotificationCallback = factory(SubtypeReachableNotificationCallback::new);
    public static final EventFactory<BiConsumer<DuringAnalysisAccess, Executable>> OverrideReachableNotificationCallback = factory(OverrideReachableNotificationCallback::new);
    public static final EventFactory<String> ConfigurationCondition = factory(ConfigurationCondition::new);
    public static final EventFactory<URI> ConfigurationFile = factory(ConfigurationFile::new);
    public static final EventFactory<Class<?>> UnknownHeapObject = factory(UnknownHeapObject::new);
    public static final EventFactory<Class<?>> BuildTimeClassInitialization = factory(BuildTimeClassInitialization::new);
    public static final EventFactory<Class<?>> HeapObjectDynamicHub = factory(HeapObjectDynamicHub::new);
    public static final EventFactory<Class<?>> HeapObjectClass = factory(HeapObjectClass::new);
    public static final EventFactory<org.graalvm.nativeimage.hosted.Feature> Feature = factory(Feature::new);
    public static final CodeEventFactory InlinedMethodCode = CausalityExport.isEnabled() ? new InterningCodeEventFactory() : new DummyCodeEventFactory();
    public static final EventFactory2<BiConsumer<DuringAnalysisAccess, Executable>, AnalysisMethod> OverrideReachableNotificationCallbackInvocation = factory(OverrideReachableNotificationCallbackInvocation::new);
    public static final EventFactory2<BiConsumer<DuringAnalysisAccess, Class<?>>, AnalysisType> SubtypeReachableNotificationCallbackInvocation = factory(SubtypeReachableNotificationCallbackInvocation::new);
    public static final JniCallVariantWrapperEventFactory JniCallVariantWrapper = CausalityExport.isEnabled() ? new InterningJniCallVariantWrapperEventFactory() : new DummyJniCallVariantWrapperEventFactory();
    public static final EventFactory<AnnotatedElement> JNIRegistration = factory(JNIRegistration::new);
    public static final EventFactory<AnnotatedElement> ReflectionRegistration = factory(ReflectionRegistration::new);
    public static final EventFactory<AnnotatedElement> ReflectionObjectInHeap = factory(ReflectionObjectInHeap::new);
    public static final CausalityEvent AutomaticFeatureRegistration = new RootEvent("[Automatic Feature Registration]");
    public static final CausalityEvent UserEnabledFeatureRegistration = new RootEvent("[User-Requested Feature Registration]");
    public static final CausalityEvent InitialRegistration = new RootEvent("[Initial Registrations]");
    public static final CausalityEvent Ignored = new Ignored();
}
