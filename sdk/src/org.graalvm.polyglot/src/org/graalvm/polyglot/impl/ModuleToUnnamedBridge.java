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

import java.lang.invoke.MethodHandles.Lookup;
import java.util.function.Consumer;
import java.util.function.Function;

import org.graalvm.nativeimage.LogHandler;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionType;
import org.graalvm.polyglot.HostAccess.MutableTargetMapping;
import org.graalvm.polyglot.HostAccess.TargetMappingPrecedence;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.APIAccess;
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
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.IOAccessor;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.ManagementAccess;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.MessageEndpoint;
import org.graalvm.polyglot.io.MessageTransport;
import org.graalvm.polyglot.io.ProcessHandler;

public final class ModuleToUnnamedBridge {
    /*
     * This field is initialized once when the first module bridge instance is created.
     */
    private static volatile boolean bridgeInitialized;
    private static volatile ModuleToUnnamedBridge instance;

    private final Lookup unnamedLookup;
    private UnnamedAccess unnamed;
    private ModuleToUnnamedAPIAccessGen api;
    private ModuleToUnnamedIOAccessorGen io;
    private ModuleToUnnamedManagementAccessGen management;

    private ModuleAccessImpl moduleAccess;

    public APIAccess getAPIAccess() {
        return api;
    }

    public IOAccessor getIOAccess() {
        return io;
    }

    public ManagementAccess getManagementAccess() {
        return management;
    }

    public Object getModuleAccess() {
        return moduleAccess;
    }

    ModuleToUnnamedBridge(Lookup unnamedLookup) {
        this.unnamedLookup = unnamedLookup;
    }

    public static ModuleToUnnamedBridge create(Lookup lookup, Object unnamedAccess, Object unnamedAPIAccess, Object unnamedIOAccess, Object unnamedManagementAccess) {
        assert !lookup.lookupClass().getModule().isNamed() : "unnamed lookup is not actually unnamed";
        ModuleToUnnamedBridge bridge = instance;
        if (bridge == null) {
            synchronized (ModuleToUnnamedBridge.class) {
                bridge = instance;
                if (bridge == null) {
                    if (bridgeInitialized) {
                        throw new InternalError("Classes used for the module bridge are already initialized.");
                    }
                    bridge = instance = new ModuleToUnnamedBridge(lookup);
                    bridge.unnamed = new ModuleToUnnamedAccessGen(unnamedAccess);
                    bridge.api = new ModuleToUnnamedAPIAccessGen(unnamedAPIAccess);
                    bridge.io = new ModuleToUnnamedIOAccessorGen(unnamedIOAccess);
                    bridge.management = new ModuleToUnnamedManagementAccessGen(unnamedManagementAccess);
                    bridge.moduleAccess = new ModuleAccessImpl();
                }
            }
        }
        if (bridge.unnamedLookup.equals(lookup)) {
            return bridge;
        } else {
            throw new InternalError("Polyglot bridge was already initialized with a different lookup.");
        }
    }

    private static class ModuleAccessImpl extends ModuleAccess {

        @Override
        public Object toMessageTransport(Object value) {
            return new ModuleToUnnamedMessageTransportGen(value);
        }

        @Override
        public Object fromMessageTransport(Object value) {
            return ((ModuleToUnnamedMessageTransportGen) value).receiver;
        }

        @Override
        public Object toLogHandler(Object value) {
            return new ModuleToUnnamedLogHandlerGen(value);
        }

        @Override
        public Object fromMessageEndpoint(Object value) {
            return ((ModuleToUnnamedMessageEndpointGen) value).receiver;
        }

        @Override
        public Object toMessageEndpoint(Object value) {
            return new ModuleToUnnamedMessageEndpointGen(value);
        }

        @Override
        public Object fromByteSequence(Object value) {
            // byte sequences support being created both ways
            if (value instanceof ModuleToUnnamedByteSequenceGen) {
                return ((ModuleToUnnamedByteSequenceGen) value).receiver;
            } else {
                return ModuleToUnnamedAPIAccess.BRIDGE.unnamed.toByteSequence(value);
            }
        }

        @Override
        public int fromSandboxPolicy(Object value) {
            return ((SandboxPolicy) value).ordinal();
        }

        static final SandboxPolicy[] SANDBOX_POLICIES = SandboxPolicy.values();
        static final TargetMappingPrecedence[] TARGET_MAPPING_PRECEDENCE = TargetMappingPrecedence.values();

        @Override
        public Object toSandboxPolicy(int ordinal) {
            return SANDBOX_POLICIES[ordinal];
        }

        @Override
        public Object toTargetMappingPrecedence(int ordinal) {
            return TARGET_MAPPING_PRECEDENCE[ordinal];
        }

        @Override
        public Object toProcessHandler(Object value) {
            return new ModuleToUnnamedProcessHandlerGen(value);
        }

        @Override
        public Object fromOptionDescriptors(Object value) {
            return ((ModuleToUnnamedOptionDescriptorsGen) value).receiver;
        }

        @Override
        public Object toFileSystem(Object value) {
            if (value instanceof FileSystem) {
                return value;
            }
            return new ModuleToUnnamedFileSystemGen(value);
        }

        @Override
        public Object fromLogHandler(Object value) {
            return ((ModuleToUnnamedLogHandlerGen) value).receiver;
        }

        @Override
        public Object fromThreadScope(Object value) {
            return ((ModuleToUnnamedThreadScopeGen) value).receiver;
        }

        @Override
        public Object fromFileSystem(Object value) {
            if (value instanceof FileSystem) {
                return ModuleToUnnamedAPIAccess.BRIDGE.unnamed.toFileSystem(value);
            }
            return ((ModuleToUnnamedFileSystemGen) value).receiver;
        }

        @Override
        public Object fromProcessHandler(Object value) {
            return ((ModuleToUnnamedProcessHandlerGen) value).receiver;
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

    }

    @GenerateMethodHandleBridge
    @SuppressWarnings("unused")
    abstract static class ModuleToUnnamedAPIAccess extends APIAccess {
        static final ModuleToUnnamedBridge BRIDGE = ModuleToUnnamedBridge.instance;
        static {
            ModuleToUnnamedBridge.bridgeInitialized = true;
        }

        static Lookup unnamedLookup() {
            if (BRIDGE == null) {
                return null;
            }
            return BRIDGE.unnamedLookup;
        }

        static Lookup methodHandleLookup() {
            return unnamedLookup();
        }

        static ByteSequence fromByteSequence(Object value) {
            if (value == null) {
                return null;
            }
            return new ModuleToUnnamedByteSequenceGen(value);
        }

        static Object toAbstractSourceDispatch(AbstractSourceDispatch dispatch) {
            if (dispatch == null) {
                return null;
            }
            return BRIDGE.unnamed.toSourceDispatch(dispatch);
        }

        static Object toAbstractSourceSectionDispatch(AbstractSourceSectionDispatch dispatch) {
            if (dispatch == null) {
                return null;
            }
            return BRIDGE.unnamed.toSourceSectionDispatch(dispatch);
        }

        static Object toAbstractStackFrameImpl(AbstractStackFrameImpl dispatch) {
            if (dispatch == null) {
                return null;
            }
            return BRIDGE.unnamed.toStackFrameImpl(dispatch);
        }

        static Object toAbstractInstrumentDispatch(AbstractInstrumentDispatch dispatch) {
            if (dispatch == null) {
                return null;
            }
            return BRIDGE.unnamed.toInstrumentDispatch(dispatch);
        }

        static Object toAbstractValueDispatch(AbstractValueDispatch dispatch) {
            if (dispatch == null) {
                return null;
            }
            return BRIDGE.unnamed.toValueDispatch(dispatch);
        }

        static Object toAbstractLanguageDispatch(AbstractLanguageDispatch dispatch) {
            if (dispatch == null) {
                return null;
            }
            return BRIDGE.unnamed.toLanguageDispatch(dispatch);
        }

        static Object toAbstractExceptionDispatch(AbstractExceptionDispatch dispatch) {
            if (dispatch == null) {
                return null;
            }
            return BRIDGE.unnamed.toExceptionDispatch(dispatch);
        }

        static Object toAbstractEngineDispatch(AbstractEngineDispatch dispatch) {
            if (dispatch == null) {
                return null;
            }
            return BRIDGE.unnamed.toEngineDispatch(dispatch);
        }

        static Object toAbstractContextDispatch(AbstractContextDispatch value) {
            if (value == null) {
                return null;
            }
            return BRIDGE.unnamed.toContextDispatch(value);
        }

        static AbstractContextDispatch fromAbstractContextDispatch(Object value) {
            if (value == null) {
                return null;
            }
            return (AbstractContextDispatch) BRIDGE.unnamed.fromContextDispatch(value);
        }

        static AbstractValueDispatch fromAbstractValueDispatch(Object value) {
            if (value == null) {
                return null;
            }
            return (AbstractValueDispatch) BRIDGE.unnamed.fromValueDispatch(value);
        }

        static AbstractLanguageDispatch fromAbstractLanguageDispatch(Object value) {
            if (value == null) {
                return null;
            }
            return (AbstractLanguageDispatch) BRIDGE.unnamed.fromLanguageDispatch(value);
        }

        static AbstractSourceSectionDispatch fromAbstractSourceSectionDispatch(Object value) {
            if (value == null) {
                return null;
            }
            return (AbstractSourceSectionDispatch) BRIDGE.unnamed.fromSourceSectionDispatch(value);
        }

        static AbstractInstrumentDispatch fromAbstractInstrumentDispatch(Object value) {
            if (value == null) {
                return null;
            }
            return (AbstractInstrumentDispatch) BRIDGE.unnamed.fromInstrumentDispatch(value);
        }

        static AbstractEngineDispatch fromAbstractEngineDispatch(Object value) {
            if (value == null) {
                return null;
            }
            return (AbstractEngineDispatch) BRIDGE.unnamed.fromEngineDispatch(value);
        }

        static AbstractStackFrameImpl fromAbstractStackFrameImpl(Object value) {
            if (value == null) {
                return null;
            }
            return (AbstractStackFrameImpl) BRIDGE.unnamed.fromStackFrameImpl(value);
        }

        static AbstractSourceDispatch fromAbstractSourceDispatch(Object value) {
            if (value == null) {
                return null;
            }
            return (AbstractSourceDispatch) BRIDGE.unnamed.fromSourceDispatch(value);
        }

        private static final MutableTargetMapping[] VALUES = MutableTargetMapping.values();

        static MutableTargetMapping[] fromMutableTargetMappingArray(Object value) {
            if (value == null) {
                return null;
            }
            int[] array = BRIDGE.unnamed.fromMutableTargetMappingArray(value);
            MutableTargetMapping[] target = new MutableTargetMapping[array.length];
            if (target.length == 0) {
                return target;
            }
            for (int i = 0; i < array.length; i++) {
                target[i] = VALUES[array[i]];
            }
            return target;
        }

    }

    @GenerateMethodHandleBridge
    abstract static class ModuleToUnnamedThreadScope extends AbstractPolyglotImpl.ThreadScope {

        protected ModuleToUnnamedThreadScope(AbstractPolyglotImpl engineImpl) {
            super(engineImpl);
        }

        static Lookup methodHandleLookup() {
            return ModuleToUnnamedAPIAccess.unnamedLookup();
        }

    }

    @GenerateMethodHandleBridge
    abstract static class ModuleToUnnamedFileSystem implements FileSystem {

        static Lookup methodHandleLookup() {
            return ModuleToUnnamedAPIAccess.unnamedLookup();
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
    abstract static class ModuleToUnnamedOptionDescriptors implements OptionDescriptors {

        static Lookup methodHandleLookup() {
            return ModuleToUnnamedAPIAccess.unnamedLookup();
        }

        @SuppressWarnings("unchecked")
        static OptionDescriptor fromOptionDescriptor(Object value) {
            if (value == null) {
                return null;
            }
            Object[] array = ModuleToUnnamedAPIAccess.BRIDGE.unnamed.fromOptionDescriptor(value);
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
    abstract static class ModuleToUnnamedProcessHandler implements ProcessHandler {

        static Lookup methodHandleLookup() {
            return ModuleToUnnamedAPIAccess.unnamedLookup();
        }

        static Object toProcessCommand(ProcessCommand value) {
            if (value == null) {
                return null;
            }
            return ModuleToUnnamedAPIAccess.BRIDGE.unnamed.toProcessCommand(serializeProcessCommand(value));
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

    }

    @GenerateMethodHandleBridge
    abstract static class ModuleToUnnamedLogHandler implements LogHandler {

        static Lookup methodHandleLookup() {
            return ModuleToUnnamedAPIAccess.unnamedLookup();
        }

    }

    @GenerateMethodHandleBridge
    abstract static class ModuleToUnnamedMessageEndpoint implements MessageEndpoint {

        static Lookup methodHandleLookup() {
            return ModuleToUnnamedAPIAccess.unnamedLookup();
        }

    }

    @GenerateMethodHandleBridge
    abstract static class ModuleToUnnamedMessageTransport implements MessageTransport {
        static Lookup methodHandleLookup() {
            return ModuleToUnnamedAPIAccess.unnamedLookup();
        }

        static MessageEndpoint fromMessageEndpoint(Object value) {
            if (value == null) {
                return null;
            }
            return (MessageEndpoint) ModuleToUnnamedAPIAccess.BRIDGE.unnamed.fromMessageEndpoint(value);
        }

        static Object toMessageEndpoint(MessageEndpoint value) {
            if (value == null) {
                return null;
            }
            return ModuleToUnnamedAPIAccess.BRIDGE.unnamed.toMessageEndpoint(value);
        }

        @Override
        public String toString() {
            return super.toString();
        }

    }

    @GenerateMethodHandleBridge
    abstract static class ModuleToUnnamedIOAccessor extends IOAccessor {

        static Lookup methodHandleLookup() {
            return ModuleToUnnamedAPIAccess.unnamedLookup();
        }

        static Object toFileSystem(FileSystem value) {
            if (value == null) {
                return null;
            }
            return ModuleToUnnamedAPIAccess.BRIDGE.unnamed.toFileSystem(value);
        }

        public static FileSystem fromFileSystem(Object value) {
            if (value == null) {
                return null;
            }
            return (FileSystem) ModuleToUnnamedAPIAccess.BRIDGE.unnamed.fromFileSystem(value);
        }

    }

    @GenerateMethodHandleBridge
    abstract static class ModuleToUnnamedManagementAccess extends ManagementAccess {

        static Lookup methodHandleLookup() {
            return ModuleToUnnamedAPIAccess.unnamedLookup();
        }

        static AbstractExecutionEventDispatch fromAbstractExecutionEventDispatch(Object value) {
            if (value == null) {
                return null;
            }
            return (AbstractExecutionEventDispatch) ModuleToUnnamedAPIAccess.BRIDGE.unnamed.fromExecutionEventDispatch(value);
        }

        static AbstractExecutionListenerDispatch fromAbstractExecutionListenerDispatch(Object value) {
            if (value == null) {
                return null;
            }
            return (AbstractExecutionListenerDispatch) ModuleToUnnamedAPIAccess.BRIDGE.unnamed.fromExecutionListenerDispatch(value);
        }

        static Object toAbstractExecutionEventDispatch(AbstractExecutionEventDispatch value) {
            if (value == null) {
                return null;
            }
            return ModuleToUnnamedAPIAccess.BRIDGE.unnamed.toExecutionEventDispatch(value);
        }

        static Object toAbstractExecutionListenerDispatch(AbstractExecutionListenerDispatch value) {
            if (value == null) {
                return null;
            }
            return ModuleToUnnamedAPIAccess.BRIDGE.unnamed.toExecutionListenerDispatch(value);
        }

    }

    @GenerateMethodHandleBridge
    abstract static class ModuleToUnnamedAccess extends UnnamedAccess {

        static Lookup methodHandleLookup() {
            return ModuleToUnnamedAPIAccess.unnamedLookup();
        }

    }

    @GenerateMethodHandleBridge
    abstract static class ModuleToUnnamedByteSequence implements ByteSequence {

        static Lookup methodHandleLookup() {
            return ModuleToUnnamedAPIAccess.unnamedLookup();
        }

        static ByteSequence fromByteSequence(Object value) {
            if (value == null) {
                return null;
            }
            return (ByteSequence) ModuleToUnnamedAPIAccess.BRIDGE.unnamed.fromByteSequence(value);
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

}
