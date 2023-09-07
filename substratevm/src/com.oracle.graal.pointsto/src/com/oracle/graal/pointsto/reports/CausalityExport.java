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
import com.oracle.graal.pointsto.reports.causality.Impl;
import com.oracle.graal.pointsto.reports.causality.Graph;
import com.oracle.graal.pointsto.reports.causality.TypeflowImpl;
import com.oracle.graal.pointsto.util.AnalysisError;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Signature;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.ZipOutputStream;

public class CausalityExport {
    protected CausalityExport() {
    }

    public enum Level {
        DISABLED,
        ENABLED_WITHOUT_TYPEFLOW,
        ENABLED
    }

    private static Level requestedLevel = Level.DISABLED;

    public static final class InitializationOnDemandHolder {
        private static final Level frozenLevel = CausalityExport.requestedLevel;
        private static final AbstractImpl instance = switch(CausalityExport.requestedLevel) {
            case ENABLED -> TypeflowImpl.createWithTypeflowTracking();
            case ENABLED_WITHOUT_TYPEFLOW -> Impl.create();
            case DISABLED -> new AbstractImpl();
        };
    }

    /**
     * Must be called before any usage of {@link #get()}
     */
    public static void activate(Level level) {
        requestedLevel = level;
        if (level != InitializationOnDemandHolder.frozenLevel) {
            throw AnalysisError.shouldNotReachHere("Causality Export must have been activated before the first usage of CausalityExport");
        }
    }

    public static Level getActivationStatus() {
        return InitializationOnDemandHolder.frozenLevel;
    }

    public static synchronized void dump(PointsToAnalysis bb, ZipOutputStream zip, boolean exportTypeflowNames) throws java.io.IOException {
        Graph g = get().createCausalityGraph(bb);
        g.export(bb, zip, exportTypeflowNames);
    }

    protected static AbstractImpl get() {
        return InitializationOnDemandHolder.instance;
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

    public static NonThrowingAutoCloseable setCause(Event event, HeapTracing level) {
        return get().setCause(event, level, false);
    }

    public static NonThrowingAutoCloseable setCause(Event event) {
        return setCause(event, HeapTracing.None);
    }

    public static NonThrowingAutoCloseable overwriteCause(Event event) {
        return get().setCause(event, HeapTracing.None, true);
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

        public ReachableEvent(T element) {
            this.element = element;
        }

        public static ReachableEvent<?> create(AnalysisElement e) {
            if(e instanceof AnalysisMethod)
                return new MethodReachable((AnalysisMethod) e);
            if(e instanceof AnalysisType)
                return new TypeReachable((AnalysisType) e);
            throw new IllegalArgumentException();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ReachableEvent<?> that = (ReachableEvent<?>) o;
            return element.equals(that.element);
        }

        @Override
        public int hashCode() {
            return getClass().hashCode() ^ element.hashCode();
        }
    }

    public static final class MethodReachable extends ReachableEvent<AnalysisMethod> {
        public MethodReachable(AnalysisMethod method) {
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

        public MethodImplementationInvoked(AnalysisMethod method) {
            this.method = method;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MethodImplementationInvoked that = (MethodImplementationInvoked) o;
            return method.equals(that.method);
        }

        @Override
        public int hashCode() {
            return getClass().hashCode() ^ method.hashCode();
        }

        @Override
        public String toString() {
            return method.format("%H.%n(%P):%R") + " [Implementation Invoked]";
        }
    }

    public static final class MethodInlined extends Event {
        public final AnalysisMethod method;

        public MethodInlined(AnalysisMethod method) {
            this.method = method;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            var that = (MethodInlined) o;
            return method.equals(that.method);
        }

        @Override
        public int hashCode() {
            return getClass().hashCode() ^ method.hashCode();
        }

        @Override
        public String toString() {
            return method.format("%H.%n(%P):%R") + " [Inlined]";
        }
    }

    public static final class InlinedMethodCode extends Event {
        public final AnalysisMethod[] context;

        public InlinedMethodCode(AnalysisMethod method) {
            this.context = new AnalysisMethod[] { method };
        }

        public InlinedMethodCode(BytecodePosition invokePos) {
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

            this.context = context.toArray(AnalysisMethod[]::new);
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            InlinedMethodCode that = (InlinedMethodCode) o;
            return Arrays.equals(context, that.context);
        }

        @Override
        public int hashCode() {
            return getClass().hashCode() ^ Arrays.hashCode(context);
        }
    }

    public static final class VirtualMethodInvoked extends Event {
        public final AnalysisMethod method;

        public VirtualMethodInvoked(AnalysisMethod method) {
            this.method = method;
        }

        @Override
        public String toString() {
            return method.format("%H.%n(%P):%R") + " [Virtual Invoke]";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            VirtualMethodInvoked that = (VirtualMethodInvoked) o;
            return method.equals(that.method);
        }

        @Override
        public int hashCode() {
            return getClass().hashCode() ^ method.hashCode();
        }
    }

    public static final class MethodSnippet extends Event {
        public final AnalysisMethod method;

        public MethodSnippet(AnalysisMethod method) {
            this.method = method;
        }

        @Override
        public String toString() {
            return method.format("%H.%n(%P):%R") + " [Snippet]";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MethodSnippet that = (MethodSnippet) o;
            return method.equals(that.method);
        }

        @Override
        public int hashCode() {
            return getClass().hashCode() ^ method.hashCode();
        }
    }

    public static final class TypeReachable extends ReachableEvent<AnalysisType> {
        public TypeReachable(AnalysisType type) {
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

        public TypeInstantiated(AnalysisType type) {
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TypeInstantiated that = (TypeInstantiated) o;
            return type.equals(that.type);
        }

        @Override
        public int hashCode() {
            return getClass().hashCode() ^ type.hashCode();
        }
    }

    public static abstract class ReflectionObjectRegistration extends Event {
        public final Object element;

        public ReflectionObjectRegistration(Executable method) {
            this.element = method;
        }

        public ReflectionObjectRegistration(Field field) {
            this.element = field;
        }

        public ReflectionObjectRegistration(Class<?> clazz) {
            this.element = clazz;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ReflectionObjectRegistration that = (ReflectionObjectRegistration) o;
            return element.equals(that.element);
        }

        @Override
        public int hashCode() {
            return getClass().hashCode() ^ element.hashCode();
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

    public static class JNIRegistration extends ReflectionObjectRegistration {
        public JNIRegistration(Executable method) {
            super(method);
        }

        public JNIRegistration(Field field) {
            super(field);
        }

        public JNIRegistration(Class<?> clazz) {
            super(clazz);
        }

        @Override
        protected String getSuffix() {
            return " [JNI Registration]";
        }
    }

    public static class ReflectionRegistration extends ReflectionObjectRegistration {
        public ReflectionRegistration(Executable method) {
            super(method);
        }

        public ReflectionRegistration(Field field) {
            super(field);
        }

        public ReflectionRegistration(Class<?> clazz) {
            super(clazz);
        }

        @Override
        protected String getSuffix() {
            return " [Reflection Registration]";
        }
    }

    public static class ReachabilityNotificationCallback extends Event {
        public final Consumer<org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess> callback;

        public ReachabilityNotificationCallback(Consumer<org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess> callback) {
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ReachabilityNotificationCallback that = (ReachabilityNotificationCallback) o;
            return callback.equals(that.callback);
        }

        @Override
        public int hashCode() {
            return getClass().hashCode() ^ callback.hashCode();
        }
    }

    public static class BuildTimeClassInitialization extends Event {
        public final Class<?> clazz;

        public BuildTimeClassInitialization(Class<?> clazz) {
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BuildTimeClassInitialization that = (BuildTimeClassInitialization) o;
            return clazz.equals(that.clazz);
        }

        @Override
        public int hashCode() {
            return getClass().hashCode() ^ clazz.hashCode();
        }

        private String getTypeName(AnalysisMetaAccess metaAccess) {
            return metaAccess.getWrapped().lookupJavaType(clazz).toJavaName();
        }

        @Override
        public String toString(AnalysisMetaAccess metaAccess) {
            return getTypeName(metaAccess) + ".<clinit>() [Build-Time]";
        }
    }

    public static class HeapObjectClass extends Event {
        public final Class<?> clazz;

        public HeapObjectClass(Class<?> clazz) {
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            HeapObjectClass that = (HeapObjectClass) o;
            return clazz.equals(that.clazz);
        }

        @Override
        public int hashCode() {
            return getClass().hashCode() ^  clazz.hashCode();
        }
    }

    public static class HeapObjectDynamicHub extends Event {
        public final Class<?> forClass;


        public HeapObjectDynamicHub(Class<?> forClass) {
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            HeapObjectDynamicHub that = (HeapObjectDynamicHub) o;
            return forClass.equals(that.forClass);
        }

        @Override
        public int hashCode() {
            return getClass().hashCode() ^ forClass.hashCode();
        }
    }

    public static class UnknownHeapObject extends Event {
        public final Class<?> heapObjectType;

        public UnknownHeapObject(Class<?> heapObjectType) {
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
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UnknownHeapObject that = (UnknownHeapObject) o;
            return heapObjectType.equals(that.heapObjectType);
        }

        @Override
        public int hashCode() {
            return getClass().hashCode() ^ heapObjectType.hashCode();
        }

        @Override
        public String toString(AnalysisMetaAccess metaAccess) {
            return metaAccess.lookupJavaType(heapObjectType).toJavaName() + " [Unknown Heap Object]";
        }
    }

    public static class TypeInHeap extends Event {
        public final AnalysisType type;

        public TypeInHeap(AnalysisType type) {
            this.type = type;
        }

        @Override
        public String toString() {
            return type.toJavaName() + " [Type In Heap]";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TypeInHeap that = (TypeInHeap) o;
            return type.equals(that.type);
        }

        @Override
        public int hashCode() {
            return getClass().hashCode() ^ type.hashCode();
        }
    }

    public static final class ReflectionObjectInHeap extends ReflectionObjectRegistration {
        public ReflectionObjectInHeap(Executable method) {
            super(method);
        }

        public ReflectionObjectInHeap(Field field) {
            super(field);
        }

        public ReflectionObjectInHeap(Class<?> clazz) {
            super(clazz);
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
    public static class Ignored extends Event {
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

    public static class Feature extends Event {
        public final org.graalvm.nativeimage.hosted.Feature f;

        public Feature(org.graalvm.nativeimage.hosted.Feature f) {
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Feature feature = (Feature) o;

            return f.equals(feature.f);
        }

        @Override
        public int hashCode() {
            return f.hashCode();
        }
    }

    public static class AutomaticFeatureRegistration extends Event {
        public static final AutomaticFeatureRegistration Instance = new AutomaticFeatureRegistration();

        private AutomaticFeatureRegistration() {}

        @Override
        public String toString() {
            return "[Automatic Feature Registration]";
        }

        @Override
        public boolean root() {
            return true;
        }
    }

    public static class UserEnabledFeatureRegistration extends Event {
        public static final UserEnabledFeatureRegistration Instance = new UserEnabledFeatureRegistration();

        private UserEnabledFeatureRegistration() {}

        @Override
        public String toString() {
            return "[User-Requested Feature Registration]";
        }

        @Override
        public boolean root() {
            return true;
        }
    }

    public static class InitialRegistration extends Event {
        public static InitialRegistration Instance = new InitialRegistration();

        private InitialRegistration() { }

        @Override
        public String toString() {
            return "[Initial Registrations]";
        }

        @Override
        public boolean root() {
            return true;
        }
    }

    public static class ConfigurationFile extends Event {
        public final URI uri;

        public ConfigurationFile(URI uri) {
            this.uri = uri;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ConfigurationFile that = (ConfigurationFile) o;
            return uri.equals(that.uri);
        }

        @Override
        public int hashCode() {
            return uri.hashCode();
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

    public static class RootMethodRegistration extends Event {
        public final AnalysisMethod method;

        public RootMethodRegistration(AnalysisMethod method) {
            this.method = method;
        }

        @Override
        public boolean essential() {
            return false;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RootMethodRegistration that = (RootMethodRegistration) o;
            return method.equals(that.method);
        }

        @Override
        public int hashCode() {
            return getClass().hashCode() ^ method.hashCode();
        }

        @Override
        public String toString() {
            return method.format("%H.%n(%P):%R") + " [Root Registration]";
        }
    }

    public static class ConfigurationCondition extends Event {
        public final String typeName;

        public ConfigurationCondition(String typeName) {
            this.typeName = typeName;
        }

        @Override
        public boolean essential() {
            return false;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ConfigurationCondition that = (ConfigurationCondition) o;
            return typeName.equals(that.typeName);
        }

        @Override
        public int hashCode() {
            return getClass().hashCode() ^ typeName.hashCode();
        }

        @Override
        public String toString() {
            return typeName + " [Configuration Condition]";
        }
    }

    public static class JniCallVariantWrapper extends Event {
        public final Signature signature;
        public final boolean virtual;

        public JniCallVariantWrapper(Signature signature, boolean virtual) {
            this.signature = signature;
            this.virtual = virtual;
        }

        @Override
        public boolean essential() {
            return false;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            JniCallVariantWrapper that = (JniCallVariantWrapper) o;

            if (virtual != that.virtual) return false;
            return signature.equals(that.signature);
        }

        @Override
        public int hashCode() {
            int result = signature.hashCode();
            result = 31 * result + (virtual ? 1 : 0);
            return result;
        }

        @Override
        public String toString() {
            return signature + (virtual ? " [Virtual JNI Call Variant Wrapper]" : " [JNI Call Variant Wrapper]");
        }
    }

    public static class OverrideReachableNotificationCallback extends Event {
        public final BiConsumer<org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess, Executable> callback;

        public OverrideReachableNotificationCallback(BiConsumer<org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess, Executable> callback) {
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OverrideReachableNotificationCallback that = (OverrideReachableNotificationCallback) o;
            return callback.equals(that.callback);
        }

        @Override
        public int hashCode() {
            return getClass().hashCode() ^ callback.hashCode();
        }
    }

    public static class OverrideReachableNotificationCallbackInvocation extends Event {
        public final BiConsumer<org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess, Executable> callback;
        public final AnalysisMethod override;

        public OverrideReachableNotificationCallbackInvocation(BiConsumer<org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess, Executable> callback, AnalysisMethod override) {
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OverrideReachableNotificationCallbackInvocation that = (OverrideReachableNotificationCallbackInvocation) o;
            return callback.equals(that.callback) && override.equals(that.override);
        }

        @Override
        public int hashCode() {
            return getClass().hashCode() ^ callback.hashCode() ^ override.hashCode();
        }
    }

    public static class SubtypeReachableNotificationCallback extends Event {
        public final BiConsumer<org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess, Class<?>> callback;

        public SubtypeReachableNotificationCallback(BiConsumer<org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess, Class<?>> callback) {
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            var that = (SubtypeReachableNotificationCallback) o;
            return callback.equals(that.callback);
        }

        @Override
        public int hashCode() {
            return getClass().hashCode() ^ callback.hashCode();
        }
    }

    public static class SubtypeReachableNotificationCallbackInvocation extends Event {
        public final BiConsumer<org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess, Class<?>> callback;
        public final AnalysisType subtype;

        public SubtypeReachableNotificationCallbackInvocation(BiConsumer<org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess, Class<?>> callback, AnalysisType subtype) {
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            var that = (SubtypeReachableNotificationCallbackInvocation) o;
            return callback.equals(that.callback) && subtype.equals(that.subtype);
        }

        @Override
        public int hashCode() {
            return getClass().hashCode() ^ callback.hashCode() ^ subtype.hashCode();
        }
    }

    public static class FieldRead extends Event {
        public final AnalysisField field;

        public FieldRead(AnalysisField field) {
            this.field = field;
        }

        @Override
        public String toString() {
            return field.format("%H.%n [Read]");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FieldRead fieldRead = (FieldRead) o;
            return field.equals(fieldRead.field);
        }

        @Override
        public int hashCode() {
            return getClass().hashCode() ^ field.hashCode();
        }
    }

    private static String reflectionObjectToString(Object reflectionObject)
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

    private static String reflectionObjectToGraalLikeString(AnalysisMetaAccess metaAccess, Object reflectionObject) {
        if(reflectionObject instanceof Class<?> c) {
            return metaAccess.lookupJavaType(c).toJavaName();
        } else if(reflectionObject instanceof Executable e) {
            return metaAccess.lookupJavaMethod(e).format("%H.%n(%P):%R");
        } else {
            return metaAccess.lookupJavaField((Field) reflectionObject).format("%H.%n");
        }
    }
}

