package org.graalvm.compiler.truffle.test.operation;

import java.util.concurrent.TimeUnit;

import org.graalvm.compiler.test.GraalTest;
import org.graalvm.compiler.truffle.runtime.BytecodeOSRMetadata;
import org.graalvm.compiler.truffle.test.TestWithSynchronousCompiling;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.operation.GenerateOperations;
import com.oracle.truffle.api.operation.Operation;
import com.oracle.truffle.api.operation.OperationConfig;
import com.oracle.truffle.api.operation.OperationNodes;
import com.oracle.truffle.api.operation.OperationParser;
import com.oracle.truffle.api.operation.OperationRootNode;

public class OperationOSRTest extends TestWithSynchronousCompiling {
    private static final OperationOSRTestLanguage LANGUAGE = null;

    private static OperationOSRTestRootNode parseNode(OperationParser<OperationOSRTestRootNodeGen.Builder> builder) {
        OperationNodes<OperationOSRTestRootNode> nodes = OperationOSRTestRootNodeGen.create(OperationConfig.DEFAULT, builder);
        return nodes.getNodes().get(nodes.getNodes().size() - 1);
    }

    @Rule public TestRule timeout = GraalTest.createTimeout(30, TimeUnit.SECONDS);

    @Before
    @Override
    public void before() {
        setupContext("engine.MultiTier", "false",
                        "engine.OSR", "true",
                        "engine.OSRCompilationThreshold", String.valueOf(10 * BytecodeOSRMetadata.OSR_POLL_INTERVAL),
                        "engine.OSRMaxCompilationReAttempts", String.valueOf(1),
                        "engine.ThrowOnMaxOSRCompilationReAttemptsReached", "true");
    }

    @Test
    public void testInfiniteInterpreterLoop() {
        OperationOSRTestRootNode root = parseNode(b -> {
            b.beginRoot(LANGUAGE);

            b.beginWhile();
            b.emitInInterpreterOperation();

            b.beginBlock();
            b.endBlock();

            b.endWhile();

            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();

            b.endRoot();
        });

        Assert.assertEquals(42L, root.getCallTarget().call());
    }

}

@TruffleLanguage.Registration(id = "OperationOSRTestLanguage")
class OperationOSRTestLanguage extends TruffleLanguage<Object> {
    @Override
    protected Object createContext(Env env) {
        return new Object();
    }
}

@GenerateOperations(languageClass = OperationOSRTestLanguage.class)
abstract class OperationOSRTestRootNode extends RootNode implements OperationRootNode {

    protected OperationOSRTestRootNode(TruffleLanguage<?> language, FrameDescriptor fd) {
        super(language, fd);
    }

    @Operation
    static final class InInterpreterOperation {
        @Specialization
        public static boolean doBoolean() {
            return CompilerDirectives.inInterpreter();
        }
    }

}
