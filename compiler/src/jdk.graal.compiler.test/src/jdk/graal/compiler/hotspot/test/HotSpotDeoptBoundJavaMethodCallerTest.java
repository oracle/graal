package jdk.graal.compiler.hotspot.test;

import org.junit.Before;
import org.junit.Test;

import jdk.graal.compiler.hotspot.meta.HotSpotForeignCallsProviderImpl;
import jdk.graal.compiler.hotspot.nodes.InvokeStaticJavaMethodNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Verifies that deoptimization functions correctly when triggered during a method call invoked by
 * an {@link InvokeStaticJavaMethodNode}.
 */
public class HotSpotDeoptBoundJavaMethodCallerTest extends HotSpotInvokeBoundJavaMethodBaseTest {

    /**
     * Calling {@link #getForeignCallInvokerMethod()} will deoptimize the calling frame. We will
     * deoptimize to {@link InvokeStaticJavaMethodNode#stateBefore()}.
     */
    @Override
    protected boolean invocationPluginApply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode arg, JavaKind kind) {
        InvokeStaticJavaMethodNode node = InvokeStaticJavaMethodNode.create(b, getForeignCallInvokerMethod(), b.bci());
        b.add(node);
        // add the arg to the stack as the method has a return value
        b.addPush(kind, arg);
        return false;
    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        invocationPlugins.register(HotSpotDeoptBoundJavaMethodCallerTest.class, new InvocationPlugin("testCallInt", int.class) {

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                ForeignCallNode node = new ForeignCallNode(HotSpotForeignCallsProviderImpl.TEST_DEOPTIMIZE_CALLER_OF_CALLER, arg);
                b.addPush(JavaKind.Int, node);
                return true;
            }
        });
        super.registerInvocationPlugins(invocationPlugins);
    }

    public static int testCallInt(int value) {
        return value;
    }

    public static void invokeForeignCall() {
        testCallInt(3);
    }

    public ResolvedJavaMethod getForeignCallInvokerMethod() {
        return getResolvedJavaMethod(HotSpotDeoptBoundJavaMethodCallerTest.class, "invokeForeignCall");
    }

    @Before
    public void before() {
        getCode(getForeignCallInvokerMethod(), null, true, true, getInitialOptions());
    }

    @Test
    @Override
    public void testMany() {
        super.testMany();
    }

}
