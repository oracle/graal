package org.graalvm.compiler.truffle.test.bytecode;

import java.util.concurrent.TimeUnit;

import org.graalvm.compiler.test.GraalTest;
import org.graalvm.compiler.truffle.test.TestWithSynchronousCompiling;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeNodes;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.BytecodeOSRMetadata;

public class OperationOSRTest extends TestWithSynchronousCompiling {
    private static final OperationOSRTestLanguage LANGUAGE = null;

    private static OperationOSRTestRootNode parseNode(BytecodeParser<OperationOSRTestRootNodeGen.Builder> builder) {
        BytecodeNodes<OperationOSRTestRootNode> nodes = OperationOSRTestRootNodeGen.create(BytecodeConfig.DEFAULT, builder);
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

@GenerateBytecode(languageClass = OperationOSRTestLanguage.class)
abstract class OperationOSRTestRootNode extends RootNode implements BytecodeRootNode {

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
