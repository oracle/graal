// CheckStyle: start generated
package com.oracle.truffle.api.bytecode.test.basic_interpreter;

import com.oracle.truffle.api.bytecode.BytecodeBuilder;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLabel;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.BytecodeConfig.Builder;
import com.oracle.truffle.api.bytecode.serialization.BytecodeDeserializer;
import com.oracle.truffle.api.bytecode.test.BytecodeDSLTestLanguage;
import com.oracle.truffle.api.source.Source;
import java.io.DataInput;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Supplier;

public abstract class BasicInterpreterBuilder extends BytecodeBuilder {

    protected BasicInterpreterBuilder(Object token) {
        super(token);
    }

    public abstract BytecodeLocal createLocal();

    public abstract BytecodeLocal createLocal(Object name, Object info);

    public abstract BytecodeLabel createLabel();

    public abstract void beginSourceSectionUnavailable();

    public abstract void endSourceSectionUnavailable();

    public abstract void beginBlock();

    public abstract void endBlock();

    public abstract void beginRoot();

    public abstract BasicInterpreter endRoot();

    public abstract void beginIfThen();

    public abstract void endIfThen();

    public abstract void beginIfThenElse();

    public abstract void endIfThenElse();

    public abstract void beginConditional();

    public abstract void endConditional();

    public abstract void beginWhile();

    public abstract void endWhile();

    public abstract void beginTryCatch();

    public abstract void endTryCatch();

    public abstract void beginTryFinally(Runnable finallyParser);

    public abstract void endTryFinally();

    public abstract void beginTryCatchOtherwise(Runnable otherwiseParser);

    public abstract void endTryCatchOtherwise();

    public abstract void emitLabel(BytecodeLabel label);

    public abstract void emitBranch(BytecodeLabel label);

    public abstract void emitLoadConstant(Object constant);

    public abstract void emitLoadNull();

    public abstract void emitLoadArgument(int index);

    public abstract void emitLoadException();

    public abstract void emitLoadLocal(BytecodeLocal local);

    public abstract void beginLoadLocalMaterialized(BytecodeLocal local);

    public abstract void endLoadLocalMaterialized();

    public abstract void beginStoreLocal(BytecodeLocal local);

    public abstract void endStoreLocal();

    public abstract void beginStoreLocalMaterialized(BytecodeLocal local);

    public abstract void endStoreLocalMaterialized();

    public abstract void beginReturn();

    public abstract void endReturn();

    public abstract void beginYield();

    public abstract void endYield();

    public abstract void beginSource(Source source);

    public abstract void endSource();

    public abstract void beginSourceSection(int index, int length);

    public abstract void endSourceSection();

    public abstract void beginTag(Class<?>... newTags);

    public abstract void endTag(Class<?>... newTags);

    public abstract void beginEarlyReturn();

    public abstract void endEarlyReturn();

    public abstract void beginAddOperation();

    public abstract void endAddOperation();

    public abstract void beginCall(BasicInterpreter interpreterValue);

    public abstract void endCall();

    public abstract void beginAddConstantOperation(long constantLhsValue);

    public abstract void endAddConstantOperation();

    public abstract void beginAddConstantOperationAtEnd();

    public abstract void endAddConstantOperationAtEnd(long constantRhsValue);

    public abstract void beginVeryComplexOperation();

    public abstract void endVeryComplexOperation();

    public abstract void beginThrowOperation();

    public abstract void endThrowOperation();

    public abstract void beginReadExceptionOperation();

    public abstract void endReadExceptionOperation();

    public abstract void beginAlwaysBoxOperation();

    public abstract void endAlwaysBoxOperation();

    public abstract void beginAppenderOperation();

    public abstract void endAppenderOperation();

    public abstract void beginTeeLocal(BytecodeLocal setterValue);

    public abstract void endTeeLocal();

    public abstract void beginTeeLocalRange(BytecodeLocal[] setterValue);

    public abstract void endTeeLocalRange();

    public abstract void beginInvoke();

    public abstract void endInvoke();

    public abstract void emitMaterializeFrame();

    public abstract void beginCreateClosure();

    public abstract void endCreateClosure();

    public abstract void emitVoidOperation();

    public abstract void beginToBoolean();

    public abstract void endToBoolean();

    public abstract void emitGetSourcePosition();

    public abstract void beginEnsureAndGetSourcePosition();

    public abstract void endEnsureAndGetSourcePosition();

    public abstract void emitGetSourcePositions();

    public abstract void beginCopyLocalsToFrame();

    public abstract void endCopyLocalsToFrame();

    public abstract void emitGetBytecodeLocation();

    public abstract void emitCollectBytecodeLocations();

    public abstract void emitCollectSourceLocations();

    public abstract void emitCollectAllSourceLocations();

    public abstract void beginContinue();

    public abstract void endContinue();

    public abstract void emitCurrentLocation();

    public abstract void emitPrintHere();

    public abstract void beginIncrementValue();

    public abstract void endIncrementValue();

    public abstract void beginDoubleValue();

    public abstract void endDoubleValue();

    public abstract void emitEnableIncrementValueInstrumentation();

    public abstract void beginAdd();

    public abstract void endAdd();

    public abstract void beginMod();

    public abstract void endMod();

    public abstract void beginLess();

    public abstract void endLess();

    public abstract void emitEnableDoubleValueInstrumentation();

    public abstract void emitExplicitBindingsTest();

    public abstract void emitImplicitBindingsTest();

    public abstract void beginScAnd();

    public abstract void endScAnd();

    public abstract void beginScOr();

    public abstract void endScOr();

    @Override
    public abstract String toString();

    public static Builder invokeNewConfigBuilder(Class<? extends BasicInterpreter> interpreterClass) {
        try {
            Method method = interpreterClass.getMethod("newConfigBuilder");
            return (Builder) method.invoke(null);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException err) {
                throw err;
            } else {
                throw new AssertionError(e.getCause());
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends BasicInterpreter> BytecodeRootNodes<T> invokeCreate(Class<? extends BasicInterpreter> interpreterClass, BytecodeDSLTestLanguage language, BytecodeConfig config, BytecodeParser<? extends BasicInterpreterBuilder> builder) {
        try {
            Method method = interpreterClass.getMethod("create", BytecodeDSLTestLanguage.class, BytecodeConfig.class, BytecodeParser.class);
            return (BytecodeRootNodes<T>) method.invoke(null, language, config, builder);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException err) {
                throw err;
            } else {
                throw new AssertionError(e.getCause());
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends BasicInterpreter> BytecodeRootNodes<T> invokeDeserialize(Class<? extends BasicInterpreter> interpreterClass, BytecodeDSLTestLanguage language, BytecodeConfig config, Supplier<DataInput> input, BytecodeDeserializer callback) {
        try {
            Method method = interpreterClass.getMethod("deserialize", BytecodeDSLTestLanguage.class, BytecodeConfig.class, Supplier.class, BytecodeDeserializer.class);
            return (BytecodeRootNodes<T>) method.invoke(null, language, config, input, callback);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException err) {
                throw err;
            } else {
                throw new AssertionError(e.getCause());
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

}
