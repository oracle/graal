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
import com.oracle.truffle.api.bytecode.AbstractBytecodeTruffleException;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLabel;
import com.oracle.truffle.api.bytecode.BytecodeNodes;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.BytecodeOSRMetadata;

public class BytecodeDSLOSRTest extends TestWithSynchronousCompiling {
    private static final BytecodeDSLOSRTestLanguage LANGUAGE = null;

    private static BytecodeDSLOSRTestRootNode parseNode(BytecodeParser<BytecodeDSLOSRTestRootNodeGen.Builder> builder) {
        BytecodeNodes<BytecodeDSLOSRTestRootNode> nodes = BytecodeDSLOSRTestRootNodeGen.create(BytecodeConfig.DEFAULT, builder);
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
        BytecodeDSLOSRTestRootNode root = parseNode(b -> {
            b.beginRoot(LANGUAGE);
            b.beginBlock();
            b.beginWhile();
            b.emitLoadConstant(true);
            b.emitThrowsInCompiledCode();
            b.endWhile();
            b.endBlock();
            b.endRoot();
        });

        try {
            root.getCallTarget().call();
            Assert.fail("Should not reach here.");
        } catch (BytecodeDSLOSRTestRootNode.InCompiledCodeException ex) {
            // expected
        }
    }

}

@TruffleLanguage.Registration(id = "BytecodeDSLOSRTestLanguage")
class BytecodeDSLOSRTestLanguage extends TruffleLanguage<Object> {
    @Override
    protected Object createContext(Env env) {
        return new Object();
    }
}

@GenerateBytecode(languageClass = BytecodeDSLOSRTestLanguage.class)
abstract class BytecodeDSLOSRTestRootNode extends RootNode implements BytecodeRootNode {

    static class InCompiledCodeException extends AbstractBytecodeTruffleException {
        private static final long serialVersionUID = 1L;
    }

    protected BytecodeDSLOSRTestRootNode(TruffleLanguage<?> language, FrameDescriptor fd) {
        super(language, fd);
    }

    @Operation
    static final class ThrowsInCompiledCode {
        @Specialization
        public static void perform() {
            if (CompilerDirectives.inCompiledCode()) {
                throw new InCompiledCodeException();
            }
        }
    }

}
