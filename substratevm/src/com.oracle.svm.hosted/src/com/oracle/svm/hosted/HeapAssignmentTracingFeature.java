package com.oracle.svm.hosted;

import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.reports.AnalysisReportsOptions;
import com.oracle.graal.pointsto.reports.causality.HeapAssignmentTracing;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.hosted.annotation.CustomSubstitutionMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.bytecode.Bytecodes;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import java.util.ArrayList;
import java.util.List;

@AutomaticallyRegisteredFeature
public class HeapAssignmentTracingFeature implements InternalFeature {
    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        // Add CausalityExporter as a dependency such that this onAnalysisExit()
        // is called after that of the CausalityExporter
        ArrayList<Class<? extends Feature>> a = new ArrayList<>();
        a.add(CausalityExporter.class);
        return a;
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        OptionValues options = HostedOptionValues.singleton();
        Object heapAssignmentTracingAgentValue = AnalysisReportsOptions.HeapAssignmentTracingAgent.getValue(options);
        boolean result = heapAssignmentTracingAgentValue == null && AnalysisReportsOptions.PrintCausalityGraph.getValue(options)
                || heapAssignmentTracingAgentValue == Boolean.TRUE;

        if (result != HeapAssignmentTracing.isActive()) {
            String msg = result
                    ? "HeapAssignmentTracingAgent failed to load properly!"
                    : "HeapAssignmentTracingAgent is linked despite not being requested!";
            throw AnalysisError.shouldNotReachHere(msg);
        }

        return result;
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        var rci = ImageSingletons.lookup(org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport.class);
        rci.initializeAtBuildTime("HeapAssignmentTracingHooks", "Avoid generation of EnsureClassInitializedNode");
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        var impl = (FeatureImpl.DuringSetupAccessImpl) access;
        // Be careful not to trigger lookup of Object.<init>() into the AnalysisUniverse
        ResolvedJavaMethod constructor = impl.bb.getUniverse().objectType().getWrapped().getDeclaredConstructors(false)[0];
        impl.registerSubstitutionProcessor(new ObjectConstructorSubstitutionProcessor(constructor));
    }

    @Override
    public void onAnalysisExit(OnAnalysisExitAccess access) {
        HeapAssignmentTracing.getInstance().dispose();
    }

    /**
     * Is necessary to hide the instrumented {@link Object#Object()} from the analysis
     */
    private static class ObjectConstructorSubstitutionProcessor extends com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor {
        public final ResolvedJavaMethod original;
        public final EmptyMethod substitution;

        public ObjectConstructorSubstitutionProcessor(ResolvedJavaMethod original) {
            this.original = original;
            this.substitution = new EmptyMethod(original);
        }

        public ResolvedJavaMethod lookup(ResolvedJavaMethod method) {
            return original.equals(method) ? substitution : method;
        }

        public ResolvedJavaMethod resolve(ResolvedJavaMethod method) {
            return method == substitution ? original : method;
        }

        private static class EmptyMethod extends CustomSubstitutionMethod {
            public EmptyMethod(ResolvedJavaMethod original) {
                super(original);
            }

            @Override
            public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
                return null;
            }

            @Override
            public byte[] getCode() {
                return switch(original.getSignature().getReturnKind()) {
                    case Boolean, Byte, Short, Char, Int -> new byte[] { Bytecodes.ICONST_0, (byte)Bytecodes.IRETURN };
                    case Long -> new byte[] { Bytecodes.LCONST_0, (byte)Bytecodes.LRETURN };
                    case Float -> new byte[] { Bytecodes.FCONST_0, (byte)Bytecodes.FRETURN };
                    case Double -> new byte[] { Bytecodes.DCONST_0, (byte)Bytecodes.DRETURN };
                    case Object -> new byte[] { Bytecodes.ACONST_NULL, (byte)Bytecodes.ARETURN };
                    case Void -> new byte[] { (byte)Bytecodes.RETURN };
                    case Illegal -> null;
                };
            }

            @Override
            public int getCodeSize() {
                byte[] code = getCode();
                return code == null ? 0 : code.length;
            }
        }
    }
}