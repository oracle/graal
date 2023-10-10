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

public final class CausalityEvents {
    private CausalityEvents() {}

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

    private static class InterningEventFactory2<T1, T2> extends InterningEventFactory<Pair<T1, T2>> implements EventFactory2<T1, T2> {
        private InterningEventFactory2(BiFunction<T1, T2, CausalityEvent> constructor) {
            super(pair -> constructor.apply(pair.getLeft(), pair.getRight()));
        }

        public CausalityEvent create(T1 arg1, T2 arg2) {
            return create(Pair.create(arg1, arg2));
        }
    }

    private static class InterningJniCallVariantWrapperEventFactory extends InterningEventFactory<InterningJniCallVariantWrapperEventFactory.Key> implements JniCallVariantWrapperEventFactory {
        private InterningJniCallVariantWrapperEventFactory() {
            super(k -> new JniCallVariantWrapper(k.signature, k.isVirtual));
        }

        @Override
        public CausalityEvent create(Signature signature, boolean isVirtual) {
            return create(new Key(signature, isVirtual));
        }

        public record Key(Signature signature, boolean isVirtual) {}
    }

    private static class InterningCodeEventFactory extends InterningEventFactory<InterningCodeEventFactory.InlinedMethods> implements CodeEventFactory {
        private record InlinedMethods(AnalysisMethod[] context) {
            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
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

            if (context.isEmpty())
                throw new RuntimeException();

            return create(new InlinedMethods(context.toArray(AnalysisMethod[]::new)));
        }

        @Override
        public CausalityEvent create(AnalysisMethod m) {
            return create(new InlinedMethods(new AnalysisMethod[] { m }));
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



    public static final EventFactory<AnalysisMethod> MethodReachable = factory(com.oracle.graal.pointsto.reports.causality.events.MethodReachable::new);
    public static final EventFactory<AnalysisMethod> MethodImplementationInvoked = factory(com.oracle.graal.pointsto.reports.causality.events.MethodImplementationInvoked::new);
    public static final EventFactory<AnalysisMethod> MethodInlined = factory(com.oracle.graal.pointsto.reports.causality.events.MethodInlined::new);
    public static final EventFactory<AnalysisMethod> MethodSnippet = factory(com.oracle.graal.pointsto.reports.causality.events.MethodSnippet::new);
    public static final EventFactory<AnalysisMethod> RootMethodRegistration = factory(com.oracle.graal.pointsto.reports.causality.events.RootMethodRegistration::new);
    public static final EventFactory<AnalysisMethod> VirtualMethodInvoked = factory(com.oracle.graal.pointsto.reports.causality.events.VirtualMethodInvoked::new);
    public static final EventFactory<AnalysisMethod> MethodGraphParsed = factory(com.oracle.graal.pointsto.reports.causality.events.MethodGraphParsed::new);
    public static final EventFactory<AnalysisType> TypeReachable = factory(com.oracle.graal.pointsto.reports.causality.events.TypeReachable::new);
    public static final EventFactory<AnalysisType> TypeInstantiated = factory(com.oracle.graal.pointsto.reports.causality.events.TypeInstantiated::new);
    public static final EventFactory<AnalysisType> TypeInHeap = factory(com.oracle.graal.pointsto.reports.causality.events.TypeInHeap::new);
    public static final EventFactory<AnalysisField> FieldRead = factory(com.oracle.graal.pointsto.reports.causality.events.FieldRead::new);
    public static final EventFactory<Consumer<org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess>> ReachabilityNotificationCallback = factory(com.oracle.graal.pointsto.reports.causality.events.ReachabilityNotificationCallback::new);
    public static final EventFactory<BiConsumer<org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess, Class<?>>> SubtypeReachableNotificationCallback = factory(com.oracle.graal.pointsto.reports.causality.events.SubtypeReachableNotificationCallback::new);
    public static final EventFactory<BiConsumer<org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess, Executable>> OverrideReachableNotificationCallback = factory(com.oracle.graal.pointsto.reports.causality.events.OverrideReachableNotificationCallback::new);
    public static final EventFactory<String> ConfigurationCondition = factory(com.oracle.graal.pointsto.reports.causality.events.ConfigurationCondition::new);
    public static final EventFactory<URI> ConfigurationFile = factory(com.oracle.graal.pointsto.reports.causality.events.ConfigurationFile::new);
    public static final EventFactory<Class<?>> UnknownHeapObject = factory(com.oracle.graal.pointsto.reports.causality.events.UnknownHeapObject::new);
    public static final EventFactory<Class<?>> BuildTimeClassInitialization = factory(com.oracle.graal.pointsto.reports.causality.events.BuildTimeClassInitialization::new);
    public static final EventFactory<Class<?>> HeapObjectDynamicHub = factory(com.oracle.graal.pointsto.reports.causality.events.HeapObjectDynamicHub::new);
    public static final EventFactory<Class<?>> HeapObjectClass = factory(com.oracle.graal.pointsto.reports.causality.events.HeapObjectClass::new);
    public static final EventFactory<org.graalvm.nativeimage.hosted.Feature> Feature = factory(com.oracle.graal.pointsto.reports.causality.events.Feature::new);
    public static final CodeEventFactory InlinedMethodCode = CausalityExport.isEnabled() ? new InterningCodeEventFactory() : new DummyCodeEventFactory();
    public static final EventFactory2<BiConsumer<org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess, Executable>, AnalysisMethod> OverrideReachableNotificationCallbackInvocation = factory(com.oracle.graal.pointsto.reports.causality.events.OverrideReachableNotificationCallbackInvocation::new);
    public static final EventFactory2<BiConsumer<org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess, Class<?>>, AnalysisType> SubtypeReachableNotificationCallbackInvocation = factory(com.oracle.graal.pointsto.reports.causality.events.SubtypeReachableNotificationCallbackInvocation::new);
    public static final JniCallVariantWrapperEventFactory JniCallVariantWrapper = CausalityExport.isEnabled() ? new InterningJniCallVariantWrapperEventFactory() : new DummyJniCallVariantWrapperEventFactory();
    public static final EventFactory<AnnotatedElement> JNIRegistration = factory(com.oracle.graal.pointsto.reports.causality.events.JNIRegistration::new);
    public static final EventFactory<AnnotatedElement> ReflectionRegistration = factory(com.oracle.graal.pointsto.reports.causality.events.ReflectionRegistration::new);
    public static final EventFactory<AnnotatedElement> ReflectionObjectInHeap = factory(com.oracle.graal.pointsto.reports.causality.events.ReflectionObjectInHeap::new);
    public static final CausalityEvent AutomaticFeatureRegistration = new RootEvent("[Automatic Feature Registration]");
    public static final CausalityEvent UserEnabledFeatureRegistration = new RootEvent("[User-Requested Feature Registration]");
    public static final CausalityEvent InitialRegistration = new RootEvent("[Initial Registrations]");
    public static final CausalityEvent Ignored = new Ignored();
}
