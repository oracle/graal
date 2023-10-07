package com.oracle.graal.pointsto.reports;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.AbstractVirtualInvokeTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisElement;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.reports.causality.Graph;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Signature;
import org.graalvm.collections.Pair;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipOutputStream;

public class CausalityExport {
    protected CausalityExport() {
    }

    public static synchronized void dump(PointsToAnalysis bb, ZipOutputStream zip, boolean exportTypeflowNames) throws java.io.IOException {
        Graph g = get().createCausalityGraph(bb);
        g.export(bb, zip, exportTypeflowNames);
    }

    protected static AbstractImpl get() {
        return CausalityExportActivation.get();
    }

    public static class AbstractImpl {
        protected void addVirtualInvokeTypeFlow(AbstractVirtualInvokeTypeFlow invocation) {}

        protected void registerVirtualInvocation(PointsToAnalysis bb, AbstractVirtualInvokeTypeFlow invocation, AnalysisMethod concreteTargetMethod, AnalysisType concreteTargetType) {}

        protected void registerTypeFlowEdge(TypeFlow<?> from, TypeFlow<?> to) {}

        protected NonThrowingAutoCloseable setSaturationHappening() {
            return null;
        }

        protected void registerEdge(Event cause, Event consequence) {}

        protected void registerConjunctiveEdge(Event cause1, Event cause2, Event consequence) {}

        protected void registerEdgeFromHeapObject(BigBang bb, JavaConstant heapObject, ObjectScanner.ScanReason reason, Event consequence) {}

        protected void registerEdgeFromHeapObject(Object heapObject, ObjectScanner.ScanReason reason, Event consequence) {}

        protected Event getHeapFieldAssigner(BigBang analysis, JavaConstant receiver, AnalysisField field, JavaConstant value) {
            return null;
        }

        protected Event getHeapArrayAssigner(BigBang analysis, JavaConstant array, int elementIndex, JavaConstant value) {
            return null;
        }

        protected void registerTypeEntering(PointsToAnalysis bb, Event cause, TypeFlow<?> destination, AnalysisType type) {}

        protected void registerObjectReplacement(Object source, Object destination) {}

        protected NonThrowingAutoCloseable setCause(Event event, CausalityExport.HeapTracing level, boolean overwriteSilently) {
            return null;
        }

        protected Event getCause() {
            return null;
        }

        protected Graph createCausalityGraph(PointsToAnalysis bb) {
            throw new UnsupportedOperationException();
        }
    }

    public enum HeapTracing {
        None,
        Allocations,
        Full
    }

    public static void addVirtualInvokeTypeFlow(AbstractVirtualInvokeTypeFlow invocation) {
        get().addVirtualInvokeTypeFlow(invocation);
    }

    public static void registerVirtualInvocation(PointsToAnalysis bb, AbstractVirtualInvokeTypeFlow invocation, AnalysisMethod concreteTargetMethod, AnalysisType concreteTargetType) {
        get().registerVirtualInvocation(bb, invocation, concreteTargetMethod, concreteTargetType);
    }

    public static void registerTypeFlowEdge(TypeFlow<?> from, TypeFlow<?> to) {
        get().registerTypeFlowEdge(from, to);
    }

    public static NonThrowingAutoCloseable setSaturationHappening() {
        return get().setSaturationHappening();
    }

    public static void registerEvent(Event event) {
        registerEdge(null, event);
    }

    public static void registerEdge(Event cause, Event consequence) {
        get().registerEdge(cause, consequence);
    }

    public static void registerConjunctiveEdge(Event cause1, Event cause2, Event consequence) {
        get().registerConjunctiveEdge(cause1, cause2, consequence);
    }

    public static void registerEdgeFromHeapObject(BigBang bb, JavaConstant heapObject, ObjectScanner.ScanReason reason, Event consequence) {
        get().registerEdgeFromHeapObject(bb, heapObject, reason, consequence);
    }

    public static void registerEdgeFromHeapObject(Object heapObject, ObjectScanner.ScanReason reason, Event consequence) {
        get().registerEdgeFromHeapObject(heapObject, reason, consequence);
    }

    public static Event getHeapFieldAssigner(BigBang analysis, JavaConstant receiver, AnalysisField field, JavaConstant value) {
        return get().getHeapFieldAssigner(analysis, receiver, field, value);
    }

    public static Event getHeapArrayAssigner(BigBang analysis, JavaConstant array, int elementIndex, JavaConstant value) {
        return get().getHeapArrayAssigner(analysis, array, elementIndex, value);
    }

    public static void registerTypeEntering(PointsToAnalysis bb, Event cause, TypeFlow<?> destination, AnalysisType type) {
        get().registerTypeEntering(bb, cause, destination, type);
    }

    public static void registerObjectReplacement(Object source, Object destination) {
        get().registerObjectReplacement(source, destination);
    }

    public static NonThrowingAutoCloseable setCause(Event event, HeapTracing level) {
        return get().setCause(event, level, false);
    }

    public static NonThrowingAutoCloseable setCause(Event event) {
        return setCause(event, HeapTracing.None);
    }

    public static NonThrowingAutoCloseable overwriteCause(Event event) {
        return get().setCause(event, HeapTracing.None, true);
    }

    public static NonThrowingAutoCloseable overwriteCause(Event event, HeapTracing level) {
        return get().setCause(event, level, true);
    }

    public static NonThrowingAutoCloseable resetCause() {
        return overwriteCause(null);
    }

    public static Event getCause() {
        return get().getCause();
    }

    // Allows the simple usage of accountRootRegistrationsTo() in a try-with-resources statement
    public interface NonThrowingAutoCloseable extends AutoCloseable {
        @Override
        void close();
    }




    public static abstract class Event {
        /**
         * Indicates whether this Event has never occured and thus can be removed from the CausalityGraph
         */
        public boolean unused() {
            return false;
        }

        /**
         * Indicates whether this Event is always reachable.
         * Such events are still useful for providing more details
         */
        public boolean root() { return false; }

        /**
         * Used to distinguish nodes that are part of the CausalityGraph-API vs. implementation detail nodes.
         * Non-essential events may be removed from the Graph, if the change doesn't affect the reachability of essential events.
         */
        public boolean essential() {
            return true;
        }

        public String toString(AnalysisMetaAccess metaAccess) {
            return this.toString();
        }
    }

    public static abstract class ReachableEvent<T extends AnalysisElement> extends Event {
        public final T element;

        private ReachableEvent(T element) {
            this.element = element;
        }
    }

    public static final class MethodReachable extends ReachableEvent<AnalysisMethod> {
        private MethodReachable(AnalysisMethod method) {
            super(method);
        }

        @Override
        public String toString() {
            return element.format("%H.%n(%P):%R");
        }

        @Override
        public boolean unused() {
            return !element.isReachable();
        }
    }

    public static final class MethodImplementationInvoked extends Event {
        public final AnalysisMethod method;

        private MethodImplementationInvoked(AnalysisMethod method) {
            this.method = method;
        }

        @Override
        public String toString() {
            return method.format("%H.%n(%P):%R") + " [Implementation Invoked]";
        }
    }

    public static final class MethodInlined extends Event {
        public final AnalysisMethod method;

        private MethodInlined(AnalysisMethod method) {
            this.method = method;
        }

        @Override
        public String toString() {
            return method.format("%H.%n(%P):%R") + " [Inlined]";
        }
    }

    public static final class InlinedMethodCode extends Event {
        public final AnalysisMethod[] context;

        private InlinedMethodCode(AnalysisMethod[] context) {
            this.context = context;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(context[0].format("%H.%n(%P):%R"));

            for (int i = 1; i < context.length; i++) {
                sb.append(';');
                AnalysisMethod m = context[i];
                sb.append(m.format("%H.%n(%P):%R"));
            }
            sb.append(" [Impl]");

            return sb.toString();
        }
    }

    public static final class VirtualMethodInvoked extends Event {
        public final AnalysisMethod method;

        private VirtualMethodInvoked(AnalysisMethod method) {
            this.method = method;
        }

        @Override
        public String toString() {
            return method.format("%H.%n(%P):%R") + " [Virtual Invoke]";
        }
    }

    public static final class MethodSnippet extends Event {
        public final AnalysisMethod method;

        private MethodSnippet(AnalysisMethod method) {
            this.method = method;
        }

        @Override
        public String toString() {
            return method.format("%H.%n(%P):%R") + " [Snippet]";
        }
    }

    public static final class TypeReachable extends ReachableEvent<AnalysisType> {
        private TypeReachable(AnalysisType type) {
            super(type);
        }

        @Override
        public String toString() {
            return element.toJavaName();
        }

        @Override
        public boolean unused() {
            return !element.isReachable();
        }
    }

    public static final class TypeInstantiated extends Event {
        public final AnalysisType type;

        private TypeInstantiated(AnalysisType type) {
            this.type = type;
        }

        @Override
        public boolean unused() {
            return !type.isInstantiated();
        }

        @Override
        public String toString() {
            return type.toJavaName() + " [Instantiated]";
        }
    }

    public static abstract class ReflectionObjectRegistration extends Event {
        public final AnnotatedElement element;

        private ReflectionObjectRegistration(AnnotatedElement element) {
            this.element = element;
        }

        protected abstract String getSuffix();

        @Override
        public String toString() {
            return reflectionObjectToString(element) + getSuffix();
        }

        @Override
        public String toString(AnalysisMetaAccess metaAccess) {
            return reflectionObjectToGraalLikeString(metaAccess, element) + getSuffix();
        }
    }

    public static final class JNIRegistration extends ReflectionObjectRegistration {
        private JNIRegistration(AnnotatedElement element) {
            super(element);
        }

        @Override
        protected String getSuffix() {
            return " [JNI Registration]";
        }
    }

    public static final class ReflectionRegistration extends ReflectionObjectRegistration {
        private ReflectionRegistration(AnnotatedElement element) {
            super(element);
        }

        @Override
        protected String getSuffix() {
            return " [Reflection Registration]";
        }
    }

    public static final class ReachabilityNotificationCallback extends Event {
        public final Consumer<org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess> callback;

        private ReachabilityNotificationCallback(Consumer<org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess> callback) {
            this.callback = callback;
        }

        @Override
        public boolean essential() {
            return false;
        }

        @Override
        public String toString() {
            return callback + " [Reachability Callback]";
        }
    }

    public static final class BuildTimeClassInitialization extends Event {
        public final Class<?> clazz;

        private BuildTimeClassInitialization(Class<?> clazz) {
            this.clazz = clazz;
        }

        @Override
        public boolean essential() {
            return false;
        }

        @Override
        public String toString() {
            return clazz.getTypeName() + ".<clinit>() [Build-Time]";
        }

        private String getTypeName(AnalysisMetaAccess metaAccess) {
            return metaAccess.getWrapped().lookupJavaType(clazz).toJavaName();
        }

        @Override
        public String toString(AnalysisMetaAccess metaAccess) {
            return getTypeName(metaAccess) + ".<clinit>() [Build-Time]";
        }
    }

    public static final class HeapObjectClass extends Event {
        public final Class<?> clazz;

        private HeapObjectClass(Class<?> clazz) {
            this.clazz = clazz;
        }

        @Override
        public boolean essential() {
            return false;
        }

        @Override
        public String toString() {
            return clazz.getTypeName() + " [Class-Object in Heap]";
        }
    }

    public static final class HeapObjectDynamicHub extends Event {
        public final Class<?> forClass;


        private HeapObjectDynamicHub(Class<?> forClass) {
            this.forClass = forClass;
        }

        @Override
        public boolean essential() {
            return false;
        }

        @Override
        public String toString() {
            return forClass.getTypeName() + " [DynamicHub-Object in Heap]";
        }
    }

    public static final class UnknownHeapObject extends Event {
        public final Class<?> heapObjectType;

        private UnknownHeapObject(Class<?> heapObjectType) {
            this.heapObjectType = heapObjectType;
        }

        @Override
        public boolean root() {
            return true;
        }

        @Override
        public String toString() {
            return heapObjectType.getTypeName() + " [Unknown Heap Object]";
        }

        @Override
        public boolean essential() {
            return false;
        }

        @Override
        public String toString(AnalysisMetaAccess metaAccess) {
            return metaAccess.lookupJavaType(heapObjectType).toJavaName() + " [Unknown Heap Object]";
        }
    }

    public static final class TypeInHeap extends Event {
        public final AnalysisType type;

        private TypeInHeap(AnalysisType type) {
            this.type = type;
        }

        @Override
        public String toString() {
            return type.toJavaName() + " [Type In Heap]";
        }
    }

    public static final class ReflectionObjectInHeap extends ReflectionObjectRegistration {
        private ReflectionObjectInHeap(AnnotatedElement element) {
            super(element);
        }

        @Override
        public boolean essential() {
            return false;
        }

        @Override
        protected String getSuffix() {
            return " [Reflection Object In Heap]";
        }
    }

    // Can be used in Rerooting to indicate that registrations simply should be ignored
    public static final class Ignored extends Event {
        public static final Ignored Instance = new Ignored();

        private Ignored() {}

        @Override
        public boolean unused() {
            return true;
        }

        @Override
        public String toString() {
            throw new RuntimeException("[Ignored dummy node that never happens]");
        }
    }

    public static final class Feature extends Event {
        public final org.graalvm.nativeimage.hosted.Feature f;

        private Feature(org.graalvm.nativeimage.hosted.Feature f) {
            this.f = f;
        }

        @Override
        public String toString() {
            String str = f.getClass().getTypeName();
            String description = f.getDescription();
            if(description != null)
                str += " [Feature: " + description + "]";
            else
                str += " [Feature]";
            return str;
        }
    }

    public static final class RootEvent extends Event {
        public final String label;

        private RootEvent(String label) {
            this.label = label;
        }

        public static final Event AutomaticFeatureRegistration = new RootEvent("[Automatic Feature Registration]");
        public static final Event UserEnabledFeatureRegistration = new RootEvent("[User-Requested Feature Registration]");
        public static final Event InitialRegistration = new RootEvent("[Initial Registrations]");

        @Override
        public String toString() {
            return label;
        }

        @Override
        public boolean root() {
            return true;
        }
    }

    public static final class ConfigurationFile extends Event {
        public final URI uri;

        private ConfigurationFile(URI uri) {
            this.uri = uri;
        }

        @Override
        public String toString() {
            String path;

            if(uri.getPath() != null)
                path = uri.getPath();
            else
            {
                path = uri.toString();
                if(path.startsWith("jar:file:"))
                    path = path.substring(9);
            }

            return path + " [Configuration File]";
        }

        @Override
        public boolean root() {
            return true;
        }
    }

    public static final class RootMethodRegistration extends Event {
        public final AnalysisMethod method;

        private RootMethodRegistration(AnalysisMethod method) {
            this.method = method;
        }

        @Override
        public boolean essential() {
            return false;
        }

        @Override
        public String toString() {
            return method.format("%H.%n(%P):%R") + " [Root Registration]";
        }
    }

    public static final class ConfigurationCondition extends Event {
        public final String typeName;

        private ConfigurationCondition(String typeName) {
            this.typeName = typeName;
        }

        @Override
        public boolean essential() {
            return false;
        }

        @Override
        public String toString() {
            return typeName + " [Configuration Condition]";
        }
    }

    public static final class JniCallVariantWrapper extends Event {
        public final Signature signature;
        public final boolean virtual;

        private JniCallVariantWrapper(Signature signature, boolean virtual) {
            this.signature = signature;
            this.virtual = virtual;
        }

        @Override
        public boolean essential() {
            return false;
        }

        @Override
        public String toString() {
            return signature + (virtual ? " [Virtual JNI Call Variant Wrapper]" : " [JNI Call Variant Wrapper]");
        }
    }

    public static final class OverrideReachableNotificationCallback extends Event {
        public final BiConsumer<org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess, Executable> callback;

        private OverrideReachableNotificationCallback(BiConsumer<org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess, Executable> callback) {
            this.callback = callback;
        }

        @Override
        public boolean essential() {
            return false;
        }

        @Override
        public String toString() {
            return callback + " [Method Override Reachable Callback]";
        }
    }

    public static final class OverrideReachableNotificationCallbackInvocation extends Event {
        public final BiConsumer<org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess, Executable> callback;
        public final AnalysisMethod override;

        private OverrideReachableNotificationCallbackInvocation(BiConsumer<org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess, Executable> callback, AnalysisMethod override) {
            this.callback = callback;
            this.override = override;
        }

        @Override
        public boolean essential() {
            return false;
        }

        @Override
        public String toString() {
            return callback + " + " + override.format("%H.%n(%P):%R") + " [Method Override Reachable Callback Invocation]";
        }
    }

    public static final class SubtypeReachableNotificationCallback extends Event {
        public final BiConsumer<org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess, Class<?>> callback;

        private SubtypeReachableNotificationCallback(BiConsumer<org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess, Class<?>> callback) {
            this.callback = callback;
        }

        @Override
        public boolean essential() {
            return false;
        }

        @Override
        public String toString() {
            return callback + " [Subtype Reachable Callback]";
        }
    }

    public static final class SubtypeReachableNotificationCallbackInvocation extends Event {
        public final BiConsumer<org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess, Class<?>> callback;
        public final AnalysisType subtype;

        private SubtypeReachableNotificationCallbackInvocation(BiConsumer<org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess, Class<?>> callback, AnalysisType subtype) {
            this.callback = callback;
            this.subtype = subtype;
        }

        @Override
        public boolean essential() {
            return false;
        }

        @Override
        public String toString() {
            return callback + " + " + subtype.toJavaName() + " [Subtype Reachable Callback Invocation]";
        }
    }

    public static final class FieldRead extends Event {
        public final AnalysisField field;

        private FieldRead(AnalysisField field) {
            this.field = field;
        }

        @Override
        public String toString() {
            return field.format("%H.%n [Read]");
        }
    }

    private static String reflectionObjectToString(AnnotatedElement reflectionObject)
    {
        if(reflectionObject instanceof Class<?> clazz) {
            return clazz.getTypeName();
        } else if(reflectionObject instanceof Constructor<?> c) {
            return c.getDeclaringClass().getTypeName() + ".<init>(" + Arrays.stream(c.getParameterTypes()).map(Class::getTypeName).collect(Collectors.joining(", ")) + ')';
        } else if(reflectionObject instanceof Method m) {
            return m.getDeclaringClass().getTypeName() + '.' + m.getName() + '(' + Arrays.stream(m.getParameterTypes()).map(Class::getTypeName).collect(Collectors.joining(", ")) + ')';
        } else {
            Field f = ((Field) reflectionObject);
            return f.getDeclaringClass().getTypeName() + '.' + f.getName();
        }
    }

    private static String reflectionObjectToGraalLikeString(AnalysisMetaAccess metaAccess, AnnotatedElement reflectionObject) {
        if(reflectionObject instanceof Class<?> c) {
            return metaAccess.lookupJavaType(c).toJavaName();
        } else if(reflectionObject instanceof Executable e) {
            return metaAccess.lookupJavaMethod(e).format("%H.%n(%P):%R");
        } else {
            return metaAccess.lookupJavaField((Field) reflectionObject).format("%H.%n");
        }
    }



    public interface EventFactory<T> {
        Event create(T data);
    }

    public interface EventFactory2<T1, T2> {
        Event create(T1 arg1, T2 arg2);
    }

    public interface JniCallVariantWrapperEventFactory {
        Event create(Signature signature, boolean isVirtual);
    }

    public interface CodeEventFactory {
        Event create(BytecodePosition pos);

        Event create(AnalysisMethod m);
    }



    private static class InterningEventFactory<TData> implements EventFactory<TData> {
        private final ConcurrentHashMap<TData, Event> internedEvents = new ConcurrentHashMap<>();
        private final Function<TData, Event> eventConstructor;

        private InterningEventFactory(Function<TData, Event> eventConstructor) {
            this.eventConstructor = eventConstructor;
        }

        public Event create(TData data) {
            return internedEvents.computeIfAbsent(data, eventConstructor);
        }
    }

    private static class InterningEventFactory2<T1, T2> extends InterningEventFactory<Pair<T1, T2>> implements EventFactory2<T1, T2> {
        private InterningEventFactory2(BiFunction<T1, T2, Event> constructor) {
            super(pair -> constructor.apply(pair.getLeft(), pair.getRight()));
        }

        public Event create(T1 arg1, T2 arg2) {
            return create(Pair.create(arg1, arg2));
        }
    }

    private static class InterningJniCallVariantWrapperEventFactory extends InterningEventFactory<InterningJniCallVariantWrapperEventFactory.Key> implements JniCallVariantWrapperEventFactory {
        private InterningJniCallVariantWrapperEventFactory() {
            super(k -> new JniCallVariantWrapper(k.signature, k.isVirtual));
        }

        @Override
        public Event create(Signature signature, boolean isVirtual) {
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
        public Event create(BytecodePosition invokePos) {
            ArrayList<AnalysisMethod> context = new ArrayList<>();
            while (invokePos != null) {
                if (invokePos.getBCI() != BytecodeFrame.UNWIND_BCI || invokePos.getCaller() == null) {
                    context.add((AnalysisMethod) invokePos.getMethod());
                }
                invokePos = invokePos.getCaller();

                if (context.size() >= 2 && context.get(context.size() - 1) == context.get(context.size() - 2))
                    throw new RuntimeException("Didn't expect the same method to appear twice!");
            }

            if (context.isEmpty())
                throw new RuntimeException();

            return create(new InlinedMethods(context.toArray(AnalysisMethod[]::new)));
        }

        @Override
        public Event create(AnalysisMethod m) {
            return create(new InlinedMethods(new AnalysisMethod[] { m }));
        }
    }



    private static class DummyEventFactory<T> implements EventFactory<T> {
        public Event create(T data) {
            return null;
        }
    }

    private static class DummyEventFactory2<T1, T2> implements EventFactory2<T1, T2> {
        public Event create(T1 arg1, T2 arg2) {
            return null;
        }
    }

    private static class DummyJniCallVariantWrapperEventFactory implements JniCallVariantWrapperEventFactory {
        @Override
        public Event create(Signature signature, boolean isVirtual) {
            return null;
        }
    }

    private static class DummyCodeEventFactory implements CodeEventFactory {
        @Override
        public Event create(BytecodePosition pos) {
            return null;
        }

        @Override
        public Event create(AnalysisMethod m) {
            return null;
        }
    }



    private static <T> EventFactory<T> factory(Function<T, Event> constructor) {
        if (CausalityExportActivation.getActivationStatus() == CausalityExportActivation.DISABLED) {
            return new DummyEventFactory<>();
        } else {
            return new InterningEventFactory<>(constructor);
        }
    }

    private static <T1, T2> EventFactory2<T1, T2> factory(BiFunction<T1, T2, Event> constructor) {
        if (CausalityExportActivation.getActivationStatus() == CausalityExportActivation.DISABLED) {
            return new DummyEventFactory2<>();
        } else {
            return new InterningEventFactory2<>(constructor);
        }
    }



    public static final EventFactory<AnalysisMethod> MethodReachable = factory(MethodReachable::new);
    public static final EventFactory<AnalysisMethod> MethodImplementationInvoked = factory(MethodImplementationInvoked::new);
    public static final EventFactory<AnalysisMethod> MethodInlined = factory(MethodInlined::new);
    public static final EventFactory<AnalysisMethod> MethodSnippet = factory(MethodSnippet::new);
    public static final EventFactory<AnalysisMethod> RootMethodRegistration = factory(RootMethodRegistration::new);
    public static final EventFactory<AnalysisMethod> VirtualMethodInvoked = factory(VirtualMethodInvoked::new);
    public static final EventFactory<AnalysisType> TypeReachable = factory(TypeReachable::new);
    public static final EventFactory<AnalysisType> TypeInstantiated = factory(TypeInstantiated::new);
    public static final EventFactory<AnalysisType> TypeInHeap = factory(TypeInHeap::new);
    public static final EventFactory<AnalysisField> FieldRead = factory(FieldRead::new);
    public static final EventFactory<Consumer<org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess>> ReachabilityNotificationCallback = factory(ReachabilityNotificationCallback::new);
    public static final EventFactory<BiConsumer<org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess, Class<?>>> SubtypeReachableNotificationCallback = factory(SubtypeReachableNotificationCallback::new);
    public static final EventFactory<BiConsumer<org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess, Executable>> OverrideReachableNotificationCallback = factory(OverrideReachableNotificationCallback::new);
    public static final EventFactory<String> ConfigurationCondition = factory(ConfigurationCondition::new);
    public static final EventFactory<URI> ConfigurationFile = factory(ConfigurationFile::new);
    public static final EventFactory<Class<?>> UnknownHeapObject = factory(UnknownHeapObject::new);
    public static final EventFactory<Class<?>> BuildTimeClassInitialization = factory(BuildTimeClassInitialization::new);
    public static final EventFactory<Class<?>> HeapObjectDynamicHub = factory(HeapObjectDynamicHub::new);
    public static final EventFactory<Class<?>> HeapObjectClass = factory(HeapObjectClass::new);
    public static final EventFactory<org.graalvm.nativeimage.hosted.Feature> Feature = factory(Feature::new);
    public static final CodeEventFactory InlinedMethodCode = CausalityExportActivation.getActivationStatus() == CausalityExportActivation.DISABLED ? new DummyCodeEventFactory() : new InterningCodeEventFactory();
    public static final EventFactory2<BiConsumer<org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess, Executable>, AnalysisMethod> OverrideReachableNotificationCallbackInvocation = factory(OverrideReachableNotificationCallbackInvocation::new);
    public static final EventFactory2<BiConsumer<org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess, Class<?>>, AnalysisType> SubtypeReachableNotificationCallbackInvocation = factory(SubtypeReachableNotificationCallbackInvocation::new);
    public static final JniCallVariantWrapperEventFactory JniCallVariantWrapper = CausalityExportActivation.getActivationStatus() == CausalityExportActivation.DISABLED ? new DummyJniCallVariantWrapperEventFactory() : new InterningJniCallVariantWrapperEventFactory();
    public static final EventFactory<AnnotatedElement> JNIRegistration = factory(CausalityExport.JNIRegistration::new);
    public static final EventFactory<AnnotatedElement> ReflectionRegistration = factory(ReflectionRegistration::new);
    public static final EventFactory<AnnotatedElement> ReflectionObjectInHeap = factory(ReflectionObjectInHeap::new);
}