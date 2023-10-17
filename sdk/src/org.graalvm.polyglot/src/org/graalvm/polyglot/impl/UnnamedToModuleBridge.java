/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.polyglot.impl;

import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionType;
import org.graalvm.polyglot.HostAccess.MutableTargetMapping;
import org.graalvm.polyglot.HostAccess.TargetMappingPrecedence;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractContextDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractEngineDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractExceptionDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractExecutionEventDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractExecutionListenerDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractInstrumentDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractLanguageDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractSourceDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractSourceSectionDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractStackFrameImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractValueDispatch;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.MessageEndpoint;
import org.graalvm.polyglot.io.MessageTransport;
import org.graalvm.polyglot.io.ProcessHandler;
import org.graalvm.polyglot.io.ProcessHandler.ProcessCommand;
import org.graalvm.polyglot.io.ProcessHandler.Redirect;

/**
 * Part of the unnamed to module class loader isolation bridge.
 */
public final class UnnamedToModuleBridge {

    /*
     * This field is initialized once when the first module bridge instance is created.
     */
    private static volatile boolean bridgeInitialized;
    private static volatile UnnamedToModuleBridge instance;

    private final Lookup moduleLookup;

    private AbstractPolyglotImpl polyglot;
    private ModuleAccess moduleAccess;

    public AbstractPolyglotImpl getPolyglot() {
        return polyglot;
    }

    public static UnnamedToModuleBridge create(Class<?> modulePolyglotClass, Object modulePolyglotInstance) {
        Lookup lookup;
        try {
            Method m = modulePolyglotClass.getDeclaredMethod("getLookup");
            m.setAccessible(true);
            lookup = (Lookup) m.invoke(modulePolyglotInstance);
        } catch (ReflectiveOperationException e) {
            throw new InternalError(e);
        }
        UnnamedToModuleBridge bridge = instance;
        if (bridge == null) {
            synchronized (UnnamedToModuleBridge.class) {
                bridge = instance;
                if (bridge == null) {
                    if (bridgeInitialized) {
                        throw new InternalError("Classes used for the module bridge are already initialized.");
                    }
                    bridge = instance = new UnnamedToModuleBridge(lookup);
                    bridge.polyglot = new UnnamedToModulePolyglotImplGen(modulePolyglotInstance);
                }
            }
        }
        if (bridge.moduleLookup.equals(lookup)) {
            return bridge;
        } else {
            throw new InternalError("Polyglot bridge was already initialized with a different lookup.");
        }

    }

    UnnamedToModuleBridge(Lookup unnamedLookup) {
        this.moduleLookup = unnamedLookup;
    }

    private static final class UnnamedAccessImpl extends UnnamedAccess {

        @Override
        public Object toSourceDispatch(Object receiver) {
            return new UnnamedToModuleSourceDispatchGen(receiver);
        }

        @Override
        public Object toSourceSectionDispatch(Object dispatch) {
            return new UnnamedToModuleSourceSectionDispatchGen(dispatch);
        }

        @Override
        public Object toStackFrameImpl(Object dispatch) {
            return new UnnamedToModuleStackFrameImplGen(dispatch);
        }

        @Override
        public Object toInstrumentDispatch(Object dispatch) {
            return new UnnamedToModuleInstrumentDispatchGen(dispatch);
        }

        @Override
        public Object toValueDispatch(Object dispatch) {
            return new UnnamedToModuleValueDispatchGen(dispatch);
        }

        @Override
        public Object toLanguageDispatch(Object dispatch) {
            return new UnnamedToModuleLanguageDispatchGen(dispatch);
        }

        @Override
        public Object toExceptionDispatch(Object dispatch) {
            return new UnnamedToModuleExceptionDispatchGen(dispatch);
        }

        @Override
        public Object toEngineDispatch(Object dispatch) {
            return new UnnamedToModuleEngineDispatchGen(dispatch);
        }

        @Override
        public Object toContextDispatch(Object dispatch) {
            return new UnnamedToModuleContextDispatchGen(dispatch);
        }

        @Override
        public Object fromSourceDispatch(Object dispatch) {
            return ((UnnamedToModuleSourceDispatchGen) dispatch).receiver;
        }

        @Override
        public Object fromSourceSectionDispatch(Object dispatch) {
            return ((UnnamedToModuleSourceSectionDispatchGen) dispatch).receiver;
        }

        @Override
        public Object fromStackFrameImpl(Object dispatch) {
            return ((UnnamedToModuleStackFrameImplGen) dispatch).receiver;
        }

        @Override
        public Object fromInstrumentDispatch(Object dispatch) {
            return ((UnnamedToModuleInstrumentDispatchGen) dispatch).receiver;
        }

        @Override
        public Object fromValueDispatch(Object dispatch) {
            return ((UnnamedToModuleValueDispatchGen) dispatch).receiver;
        }

        @Override
        public Object fromLanguageDispatch(Object dispatch) {
            return ((UnnamedToModuleLanguageDispatchGen) dispatch).receiver;
        }

        @Override
        public Object fromExceptionDispatch(Object dispatch) {
            return ((UnnamedToModuleExceptionDispatchGen) dispatch).receiver;
        }

        @Override
        public Object fromEngineDispatch(Object dispatch) {
            return ((UnnamedToModuleEngineDispatchGen) dispatch).receiver;
        }

        @Override
        public Object fromContextDispatch(Object dispatch) {
            return ((UnnamedToModuleContextDispatchGen) dispatch).receiver;
        }

        @Override
        public Object fromMessageEndpoint(Object dispatch) {
            return ((UnnamedToModuleMessageEndpointGen) dispatch).receiver;
        }

        @Override
        public Object toMessageEndpoint(Object dispatch) {
            return new UnnamedToModuleMessageEndpointGen(dispatch);
        }

        @Override
        public Object fromByteSequence(Object value) {
            return ((UnnamedToModuleByteSequenceGen) value).receiver;
        }

        @Override
        public Object fromFileSystem(Object value) {
            if (value instanceof UnnamedToModuleFileSystemGen) {
                return ((UnnamedToModuleFileSystemGen) value).receiver;
            } else {
                return UnnamedToModulePolyglotImpl.BRIDGE.moduleAccess.toFileSystem(value);
            }
        }

        @Override
        public Object toFileSystem(Object value) {
            return new UnnamedToModuleFileSystemGen(value);
        }

        @Override
        public Object toExecutionListenerDispatch(Object value) {
            return new UnnamedToModuleExecutionListenerDispatchGen(value);
        }

        @Override
        public Object fromExecutionListenerDispatch(Object value) {
            return ((UnnamedToModuleExecutionListenerDispatchGen) value).receiver;
        }

        @Override
        public Object toExecutionEventDispatch(Object value) {
            return new UnnamedToModuleExecutionEventDispatchGen(value);
        }

        @Override
        public Object fromExecutionEventDispatch(Object value) {
            return ((UnnamedToModuleExecutionEventDispatchGen) value).receiver;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object[] fromOptionDescriptor(Object value) {
            OptionDescriptor d = ((OptionDescriptor) value);
            OptionKey<?> key = d.getKey();
            OptionType<Object> type = (OptionType<Object>) key.getType();
            return new Object[]{
                            d.getName(),
                            d.getHelp(),
                            d.getCategory().ordinal(),
                            d.getStability().ordinal(),
                            d.isDeprecated(),
                            d.getUsageSyntax(),
                            key.getDefaultValue(),
                            type.getName(),
                            (Function<String, Object>) (o) -> type.convert(o),
                            (Consumer<Object>) (o) -> type.validate(o)
            };
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object toProcessCommand(Object[] value) {
            return ProcessHandler.ProcessCommand.create(
                            (List<String>) value[0],
                            (String) value[1],
                            (Map<String, String>) value[2],
                            (boolean) value[3],
                            deserializeRedirect(value[4]),
                            deserializeRedirect(value[5]),
                            deserializeRedirect(value[6]));
        }

        private static Redirect deserializeRedirect(Object r) {
            if (r instanceof Integer) {
                switch ((int) r) {
                    case 0:
                        return Redirect.INHERIT;
                    case 1:
                        return Redirect.PIPE;
                    default:
                        return null;
                }
            } else {
                return Redirect.createRedirectToStream((OutputStream) r);
            }
        }

        @Override
        public int[] fromMutableTargetMappingArray(Object value) {
            MutableTargetMapping[] array = (MutableTargetMapping[]) value;
            int[] ordinals = new int[array.length];
            for (int i = 0; i < array.length; i++) {
                ordinals[i] = array[i].ordinal();
            }
            return ordinals;
        }

        @Override
        public Object[] fromProcessCommand(Object value) {
            return serializeProcessCommand(value);
        }

        private static Object[] serializeProcessCommand(Object value) {
            ProcessCommand c = (ProcessCommand) value;
            return new Object[]{
                            c.getCommand(),
                            c.getDirectory(),
                            c.getEnvironment(),
                            c.isRedirectErrorStream(),
                            serializeRedirect(c.getInputRedirect()),
                            serializeRedirect(c.getOutputRedirect()),
                            serializeRedirect(c.getErrorRedirect()),
            };
        }

        private static Object serializeRedirect(Redirect r) {
            if (r.equals(Redirect.INHERIT)) {
                return 0;
            } else if (r.equals(Redirect.PIPE)) {
                return 1;
            } else {
                return r.getOutputStream();
            }
        }

        @Override
        public Object toByteSequence(Object value) {
            return new UnnamedToModuleByteSequenceGen(value);
        }

    }

    @GenerateMethodHandleBridge
    abstract static class UnnamedToModuleAccess extends ModuleAccess {
        static Lookup methodHandleLookup() {
            return UnnamedToModulePolyglotImpl.moduleLookup();
        }

    }

    @GenerateMethodHandleBridge
    @SuppressWarnings("unused")
    abstract static class UnnamedToModulePolyglotImpl extends AbstractPolyglotImpl {

        static final UnnamedToModuleBridge BRIDGE = UnnamedToModuleBridge.instance;
        static {
            UnnamedToModuleBridge.bridgeInitialized = true;
        }

        static Lookup moduleLookup() {
            if (BRIDGE == null) {
                return null;
            }
            return BRIDGE.moduleLookup;
        }

        @Override
        public final void initialize() {
            super.initialize();
            Object m = initializeModuleToUnnamedAccess(MethodHandles.lookup(), new UnnamedAccessImpl(), getAPIAccess(), getIO(), getManagement());
            BRIDGE.moduleAccess = new UnnamedToModuleAccessGen(m);
        }

        static Lookup methodHandleLookup() {
            return moduleLookup();
        }

        static ProcessHandler fromProcessHandler(Object value) {
            if (value == null) {
                return null;
            }
            return (ProcessHandler) BRIDGE.moduleAccess.fromProcessHandler(value);
        }

        static OptionDescriptors fromOptionDescriptors(Object value) {
            throw new AssertionError("Method is not expected to be called from polyglot.");
        }

        static FileSystem fromFileSystem(Object value) {
            if (value == null) {
                return null;
            }
            return new UnnamedToModuleFileSystemGen(value);
        }

        static ByteSequence fromByteSequence(Object value) {
            if (value == null) {
                return null;
            }
            return new UnnamedToModuleByteSequenceGen(value);
        }

        static ThreadScope fromThreadScope(Object value) {
            if (value == null) {
                return null;
            }
            return (ThreadScope) BRIDGE.moduleAccess.fromThreadScope(value);
        }

        static Object toFileSystem(FileSystem value) {
            if (value == null) {
                return null;
            }
            Object fs = value;
            if (fs instanceof UnnamedToModuleFileSystemGen) {
                fs = ((UnnamedToModuleFileSystemGen) fs).receiver;
            }
            return BRIDGE.moduleAccess.toFileSystem(fs);
        }

        static Object toProcessHandler(ProcessHandler value) {
            if (value == null) {
                return null;
            }
            return BRIDGE.moduleAccess.toProcessHandler(value);
        }

        static Object toMessageTransport(MessageTransport value) {
            if (value == null) {
                return null;
            }
            return BRIDGE.moduleAccess.toMessageTransport(value);
        }

        static Object toSandboxPolicy(SandboxPolicy value) {
            if (value == null) {
                return null;
            }
            return BRIDGE.moduleAccess.toSandboxPolicy(value.ordinal());
        }

        static Object toTargetMappingPrecedence(TargetMappingPrecedence value) {
            if (value == null) {
                return null;
            }
            return BRIDGE.moduleAccess.toTargetMappingPrecedence(value.ordinal());
        }

        static Object toOptionDescriptorsArray(OptionDescriptors[] optionDescriptors) {
            throw new UnsupportedOperationException("Cannot be called from the unnamed module.");
        }
    }

    @GenerateMethodHandleBridge
    abstract static class UnnamedToModuleMessageEndpoint implements MessageEndpoint {
        static Lookup methodHandleLookup() {
            return UnnamedToModulePolyglotImpl.moduleLookup();
        }
    }

    @GenerateMethodHandleBridge
    abstract static class UnnamedToModuleSourceDispatch extends AbstractSourceDispatch {
        static Lookup methodHandleLookup() {
            return UnnamedToModulePolyglotImpl.moduleLookup();
        }

        public static ByteSequence fromByteSequence(Object value) {
            return (ByteSequence) UnnamedToModulePolyglotImpl.BRIDGE.moduleAccess.fromByteSequence(value);
        }
    }

    @GenerateMethodHandleBridge
    abstract static class UnnamedToModuleSourceSectionDispatch extends AbstractSourceSectionDispatch {

        static Lookup methodHandleLookup() {
            return UnnamedToModulePolyglotImpl.moduleLookup();
        }
    }

    @GenerateMethodHandleBridge
    abstract static class UnnamedToModuleStackFrameImpl extends AbstractStackFrameImpl {
        static Lookup methodHandleLookup() {
            return UnnamedToModulePolyglotImpl.moduleLookup();
        }
    }

    @GenerateMethodHandleBridge
    abstract static class UnnamedToModuleOptionDescriptors implements OptionDescriptors {

        static Lookup methodHandleLookup() {
            return UnnamedToModulePolyglotImpl.moduleLookup();
        }

        @SuppressWarnings("unchecked")
        static OptionDescriptor fromOptionDescriptor(Object value) {
            if (value == null) {
                return null;
            }
            Object[] array = UnnamedToModulePolyglotImpl.BRIDGE.moduleAccess.fromOptionDescriptor(value);
            OptionType<Object> type = new OptionType<>((String) array[7], (Function<String, Object>) array[8], (Consumer<Object>) array[9]);
            OptionKey<Object> key = new OptionKey<>(array[6], type);
            var b = OptionDescriptor.newBuilder(key, (String) array[0]);
            b.help((String) array[1]);
            b.category(OptionCategory.values()[(int) array[2]]);
            b.stability(OptionStability.values()[(int) array[3]]);
            b.deprecated((boolean) array[4]);
            b.usageSyntax((String) array[5]);
            return b.build();
        }

    }

    @GenerateMethodHandleBridge
    abstract static class UnnamedToModuleInstrumentDispatch extends AbstractInstrumentDispatch {
        static Lookup methodHandleLookup() {
            return UnnamedToModulePolyglotImpl.moduleLookup();
        }

        static OptionDescriptors fromOptionDescriptors(Object value) {
            if (value == null) {
                return null;
            }
            return new UnnamedToModuleOptionDescriptorsGen(value);
        }

    }

    @GenerateMethodHandleBridge
    abstract static class UnnamedToModuleValueDispatch extends AbstractValueDispatch {
        static Lookup methodHandleLookup() {
            return UnnamedToModulePolyglotImpl.moduleLookup();
        }
    }

    @GenerateMethodHandleBridge
    abstract static class UnnamedToModuleLanguageDispatch extends AbstractLanguageDispatch {
        static Lookup methodHandleLookup() {
            return UnnamedToModulePolyglotImpl.moduleLookup();
        }

        static OptionDescriptors fromOptionDescriptors(Object value) {
            if (value == null) {
                return null;
            }
            return new UnnamedToModuleOptionDescriptorsGen(value);
        }
    }

    @GenerateMethodHandleBridge
    abstract static class UnnamedToModuleExceptionDispatch extends AbstractExceptionDispatch {
        static Lookup methodHandleLookup() {
            return UnnamedToModulePolyglotImpl.moduleLookup();
        }
    }

    @GenerateMethodHandleBridge
    abstract static class UnnamedToModuleFileSystem implements FileSystem {
        static Lookup methodHandleLookup() {
            return UnnamedToModulePolyglotImpl.moduleLookup();
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj);
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public String toString() {
            return super.toString();
        }
    }

    @GenerateMethodHandleBridge
    abstract static class UnnamedToModuleByteSequence implements ByteSequence {
        static Lookup methodHandleLookup() {
            return UnnamedToModulePolyglotImpl.moduleLookup();
        }

        static ByteSequence fromByteSequence(Object value) {
            return (ByteSequence) UnnamedToModulePolyglotImpl.BRIDGE.moduleAccess.fromByteSequence(value);
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj);
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public String toString() {
            return super.toString();
        }
    }

    @GenerateMethodHandleBridge
    abstract static class UnnamedToModuleEngineDispatch extends AbstractEngineDispatch {

        private static final SandboxPolicy[] SANDBOX_POLICIES = SandboxPolicy.values();

        static Lookup methodHandleLookup() {
            return UnnamedToModulePolyglotImpl.moduleLookup();
        }

        static OptionDescriptors fromOptionDescriptors(Object value) {
            if (value == null) {
                return null;
            }
            return new UnnamedToModuleOptionDescriptorsGen(value);
        }

        static SandboxPolicy fromSandboxPolicy(Object value) {
            if (value == null) {
                return null;
            }
            return SANDBOX_POLICIES[(UnnamedToModulePolyglotImpl.BRIDGE.moduleAccess.fromSandboxPolicy(value))];
        }

        static Object toSandboxPolicy(SandboxPolicy value) {
            if (value == null) {
                return null;
            }
            return UnnamedToModulePolyglotImpl.BRIDGE.moduleAccess.toSandboxPolicy(value.ordinal());
        }

        static Object toProcessHandler(ProcessHandler value) {
            if (value == null) {
                return null;
            }
            return UnnamedToModulePolyglotImpl.BRIDGE.moduleAccess.toProcessHandler(value);
        }
    }

    @GenerateMethodHandleBridge
    abstract static class UnnamedToModuleContextDispatch extends AbstractContextDispatch {
        static Lookup methodHandleLookup() {
            return UnnamedToModulePolyglotImpl.moduleLookup();
        }
    }

    @GenerateMethodHandleBridge
    abstract static class UnnamedToModuleExecutionEventDispatch extends AbstractExecutionEventDispatch {
        static Lookup methodHandleLookup() {
            return UnnamedToModulePolyglotImpl.moduleLookup();
        }
    }

    @GenerateMethodHandleBridge
    abstract static class UnnamedToModuleExecutionListenerDispatch extends AbstractExecutionListenerDispatch {
        static Lookup methodHandleLookup() {
            return UnnamedToModulePolyglotImpl.moduleLookup();
        }
    }

}
