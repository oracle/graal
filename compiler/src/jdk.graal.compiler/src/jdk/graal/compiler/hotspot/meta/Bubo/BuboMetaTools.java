package jdk.graal.compiler.hotspot.meta.Bubo;

import static jdk.graal.compiler.core.common.CompilationRequestIdentifier.asCompilationRequest;

import java.lang.reflect.Method;


import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.GraalCompiler;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Builder;
import jdk.graal.compiler.debug.DebugDumpScope;
import jdk.graal.compiler.lir.asm.CompilationResultBuilderFactory;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.runtime.RuntimeProvider;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class BuboMetaTools {

    // /home/hburchell/Repos/graal-dev/graal-instrumentation/compiler/src/jdk.internal.vm.compiler.test/src/org/graalvm/compiler/core/test/tutorial/GraalTutorial.java
        /**
     * Look up a method using Java reflection and convert it to the Graal API method object.
     */
    public ResolvedJavaMethod findMethod(Class<?> declaringClass, String methodName, MetaAccessProvider metaAccess) {
        Method reflectionMethod = null;
        for (Method m : declaringClass.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                assert reflectionMethod == null : "More than one method with name " + methodName + " in class " + declaringClass.getName();
                reflectionMethod = m;
                continue;
            }
        }
        assert reflectionMethod != null : "No method with name " + methodName + " in class " + declaringClass.getName();
        return metaAccess.lookupJavaMethod(reflectionMethod);
    }
}