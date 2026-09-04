/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot.isolate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolates;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.RuntimeOptions;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

@TruffleLanguage.Registration(id = PolyglotIsolateTestLanguage.ID, name = PolyglotIsolateTestLanguage.ID, //
                defaultMimeType = PolyglotIsolateTestLanguage.MIME_TYPE, //
                characterMimeTypes = PolyglotIsolateTestLanguage.MIME_TYPE, //
                contextPolicy = ContextPolicy.SHARED, fileTypeDetectors = FileTypeDetector.class, sandbox = SandboxPolicy.UNTRUSTED)
class PolyglotIsolateTestLanguage extends TruffleLanguage<PolyglotIsolateTestLanguage.Context> {
    public static final String ID = "triste";
    public static final String MIME_TYPE = "text/x-TruffleIsolateTestLanguage";

    private static final TruffleLogger LOGGER = TruffleLogger.getLogger(ID);
    private static final ContextReference<Context> REFERENCE = ContextReference.create(PolyglotIsolateTestLanguage.class);

    @Override
    protected Context createContext(Env env) {
        return new Context(env);
    }

    @Override
    protected Object getScope(Context context) {
        return context.bindings;
    }

    @Override
    protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
        return true;
    }

    protected ContextReference<Context> getContextReference() {
        return REFERENCE;
    }

    private static final String HOST_OBJECT_CALL = "hostObjectCall";
    private static final String SPAWN_HOST_OBJECT_CALL = "spawnHostObjectCall";
    private static final String SPAWN_HOST_OBJECT_CALL_NO_WAIT = "spawnHostObjectCallNoWait";
    private static final String SPAWN_HOST_OBJECT_CALL_VIRTUAL = "spawnHostObjectCallVirtual";
    private static final String THROW = "guestToHostThrow";
    private static final String CATCH = "hostToGuestCatch";
    private static final String READ = "read";
    private static final String WRITE = "write";
    private static final String EVAL = "eval";
    private static final String ALLOC = "alloc";
    private static final String ACCESS = "access";
    private static final String ACCESS_BINDINGS = "bindings";
    private static final String RETURN_WEAKLY_REACHABLE_GUEST_OBJECT = "returnWeaklyReachableGuestObject";
    private static final String EXIT = "exit";
    private static final String TEST = "test";
    private static final String LOG = "log";
    private static final String INTERPRETERRECURSION = "interpreterRecursion";
    private static final String KILL_ACTIVE_CHILD_PROCESS = "killActiveChildProcess";
    private static final String SPAWN_SUBPROCESS = "spawnSubProcess";
    private static final String LOOP_HOST_CALL = "loopHostCall";
    private static final String ALLOCATE_GUEST_OBJECT = "allocateGuestObject";
    private static final String TEST_SYMBOL_CACHE = "testSymbolCache";
    private static final Pattern CALL_PATTERN = Pattern.compile(
                    "^(" + HOST_OBJECT_CALL + "|" + SPAWN_HOST_OBJECT_CALL + "|" + SPAWN_HOST_OBJECT_CALL_NO_WAIT + "|" + SPAWN_HOST_OBJECT_CALL_VIRTUAL +
                                    "|" + THROW + "|" + CATCH + "|" + READ + "|" + WRITE + "|" + EVAL + "|" + ALLOC + "|" + ACCESS + "|" + ACCESS_BINDINGS +
                                    "|" + RETURN_WEAKLY_REACHABLE_GUEST_OBJECT + "|" + EXIT + "|" + TEST + "|" + LOG + "|" + INTERPRETERRECURSION + "|" + KILL_ACTIVE_CHILD_PROCESS +
                                    "|" + SPAWN_SUBPROCESS + "|" + LOOP_HOST_CALL + "|" + ALLOCATE_GUEST_OBJECT + "|" + TEST_SYMBOL_CACHE +
                                    ")\\((\\w+)\\((.*)\\),(\\d+)\\)$");

    @Override
    protected CallTarget parse(ParsingRequest request) {
        Source source = request.getSource();
        CharSequence sourceText = source.getCharacters();
        Matcher matcher = CALL_PATTERN.matcher(sourceText);
        if (matcher.find()) {
            String hostObjectCallType = matcher.group(1);
            Command command = Command.forName(hostObjectCallType);
            if (command == null) {
                throw new ParseException("Unknown command " + hostObjectCallType, source, 1);
            }
            String hostMethodToCall = matcher.group(2);
            String argument = matcher.group(3);
            String repeatCountString = matcher.group(4);
            int repeatCount = Integer.parseInt(repeatCountString);
            RootNode main = new MainNode(this, source, command, hostMethodToCall, argument, repeatCount, getContextReference());
            return main.getCallTarget();
        } else {
            throw new ParseException("Failed to parse: '" + sourceText + "'", source, 1);
        }

    }

    @Override
    protected void finalizeContext(Context context) {
        joinSpawnedThreads(context);
    }

    private static void joinSpawnedThreads(Context context) {
        List<Thread> threads;
        do {
            threads = new ArrayList<>();
            synchronized (context.spawnedThreads) {
                for (Thread t : context.spawnedThreads.keySet()) {
                    if (t.isAlive()) {
                        threads.add(t);
                    }
                }
            }
            for (Thread t : threads) {
                try {
                    t.join();
                } catch (InterruptedException ie) {
                    throw CompilerDirectives.shouldNotReachHere(ie);
                }
            }
        } while (!threads.isEmpty());
    }

    @Override
    protected boolean patchContext(Context context, Env newEnv) {
        context.env = newEnv;
        return true;
    }

    private enum Command {
        ACCESS(PolyglotIsolateTestLanguage.ACCESS),
        ALLOC(PolyglotIsolateTestLanguage.ALLOC),
        BINDINGS(PolyglotIsolateTestLanguage.ACCESS_BINDINGS),
        CALL(PolyglotIsolateTestLanguage.HOST_OBJECT_CALL),
        CATCH(PolyglotIsolateTestLanguage.CATCH),
        EVAL(PolyglotIsolateTestLanguage.EVAL),
        EXIT(PolyglotIsolateTestLanguage.EXIT),
        LOG(PolyglotIsolateTestLanguage.LOG),
        TEST(PolyglotIsolateTestLanguage.TEST),
        READ(PolyglotIsolateTestLanguage.READ),
        RETURN(PolyglotIsolateTestLanguage.RETURN_WEAKLY_REACHABLE_GUEST_OBJECT),
        SPAWN(PolyglotIsolateTestLanguage.SPAWN_HOST_OBJECT_CALL),
        SPAWNNOWAIT(PolyglotIsolateTestLanguage.SPAWN_HOST_OBJECT_CALL_NO_WAIT),
        SPAWNVIRTUAL(PolyglotIsolateTestLanguage.SPAWN_HOST_OBJECT_CALL_VIRTUAL),
        THROW(PolyglotIsolateTestLanguage.THROW),
        WRITE(PolyglotIsolateTestLanguage.WRITE),
        INTERPRETERRECURSION(PolyglotIsolateTestLanguage.INTERPRETERRECURSION),
        KILL(PolyglotIsolateTestLanguage.KILL_ACTIVE_CHILD_PROCESS),
        SPAWNSUBPROCESS(PolyglotIsolateTestLanguage.SPAWN_SUBPROCESS),
        LOOP_HOST_CALL(PolyglotIsolateTestLanguage.LOOP_HOST_CALL),
        ALLOCATE_GUEST_OBJECT(PolyglotIsolateTestLanguage.ALLOCATE_GUEST_OBJECT),
        TEST_SYMBOL_CACHE(PolyglotIsolateTestLanguage.TEST_SYMBOL_CACHE);

        private static final Map<String, Command> COMMAND_BY_NAME;
        static {
            Map<String, Command> m = new HashMap<>();
            for (Command command : values()) {
                m.put(command.name, command);
            }
            COMMAND_BY_NAME = Collections.unmodifiableMap(m);
        }

        private final String name;

        Command(String name) {
            this.name = name;
        }

        static Command forName(String name) {
            return COMMAND_BY_NAME.get(name);
        }
    }

    private static final class MainNode extends RootNode {

        private final Source source;
        private final Command command;
        private final String hostMethodToCall;
        private final String argument;
        private final int repeatCount;
        private final ContextReference<Context> contextReference;
        @Child private DirectCallNode callNode;

        MainNode(PolyglotIsolateTestLanguage language, Source source, Command command, String hostMethodToCall,
                        String argument, int repeatCount, ContextReference<Context> contextReference) {
            super(language);
            this.source = source;
            this.command = command;
            this.hostMethodToCall = hostMethodToCall;
            this.argument = argument;
            this.repeatCount = repeatCount;
            this.contextReference = contextReference;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Request: {0}, method: {1}, arg: {2}, repeat: {3}",
                                new Object[]{command.name, hostMethodToCall, argument, repeatCount});
            }
            switch (command) {
                case ACCESS:
                    return accessMemory();
                case ALLOC:
                    return allocateMemory();
                case BINDINGS:
                    return executeAccessBindings();
                case CALL:
                    return callHostObject();
                case CATCH:
                    return catchFromHost();
                case EVAL:
                    return evalSnippet();
                case EXIT:
                    return execExit();
                case LOG:
                    return log();
                case TEST:
                    return execTest();
                case READ:
                    return readStreamToHostObject();
                case RETURN:
                    return returnWeaklyReachableGuestObject();
                case SPAWN:
                    return spawnCall(true, false);
                case SPAWNNOWAIT:
                    return spawnCall(false, false);
                case SPAWNVIRTUAL:
                    return spawnCall(true, true);
                case THROW:
                    return throwToHost();
                case WRITE:
                    return writeHostObjectToStream();
                case INTERPRETERRECURSION:
                    return interpreterRecursion();
                case KILL:
                    return killActiveChildProcess();
                case SPAWNSUBPROCESS:
                    return spawnSubProcess();
                case LOOP_HOST_CALL:
                    return loopHostCall();
                case ALLOCATE_GUEST_OBJECT:
                    return allocateGuestObject();
                case TEST_SYMBOL_CACHE:
                    return testSymbolCache();
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalArgumentException("Unknown command " + command);
            }
        }

        @TruffleBoundary
        private Object callHostObject() {
            Context ctx = contextReference.get(this);
            TruffleObject hostObject = (TruffleObject) ctx.bindings.readMember("hostObject");
            try {
                InteropLibrary interop = InteropLibrary.getUncached();
                if (argument.isEmpty()) {
                    for (int i = 0; i < repeatCount; i++) {
                        interop.invokeMember(hostObject, hostMethodToCall);
                        TruffleSafepoint.poll(this);
                    }
                } else {
                    for (int i = 0; i < repeatCount; i++) {
                        interop.invokeMember(hostObject, hostMethodToCall, argument);
                        TruffleSafepoint.poll(this);
                    }
                }
                return NullObject.SINGLETON;
            } catch (UnsupportedMessageException | ArityException | UnknownIdentifierException | UnsupportedTypeException e) {
                throw new RuntimeException(e);
            }
        }

        @TruffleBoundary
        private Object throwToHost() {
            Context ctx = contextReference.get(this);
            TruffleObject hostObject = (TruffleObject) ctx.bindings.readMember("hostObject");
            try {
                InteropLibrary interop = InteropLibrary.getUncached();
                ThrowObject throwObject = new ThrowObject(source, argument);
                for (int i = 0; i < repeatCount; i++) {
                    interop.invokeMember(hostObject, hostMethodToCall, throwObject);
                    TruffleSafepoint.poll(this);
                }
                return NullObject.SINGLETON;
            } catch (UnsupportedMessageException | ArityException | UnknownIdentifierException | UnsupportedTypeException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @TruffleBoundary
        private Object catchFromHost() {
            Context ctx = contextReference.get(this);
            TruffleObject hostObject = (TruffleObject) ctx.bindings.readMember("hostObject");
            InteropLibrary interop = InteropLibrary.getUncached();
            for (int i = 0; i < repeatCount; i++) {
                try {
                    interop.invokeMember(hostObject, hostMethodToCall, argument);
                    throw CompilerDirectives.shouldNotReachHere("Expected exception.");
                } catch (Throwable t) {
                    switch (argument) {
                        case "UnsupportedTypeException":
                        case "ArityException":
                            if (!argument.equals(t.getClass().getSimpleName())) {
                                throw new AssertionError("Expected " + argument + " got " + t);
                            }
                            break;
                        case "CancelExecution":
                        case "ExitException":
                            if (!(t instanceof ThreadDeath)) {
                                throw new AssertionError("Expected " + argument + " got " + t);
                            }
                            break;
                        case "TruffleException":
                            if (!InteropLibrary.getUncached().isException(t)) {
                                throw new AssertionError("Expected Truffle exception got " + t);
                            }
                            break;
                        default:
                            throw CompilerDirectives.shouldNotReachHere(t);
                    }
                }
                TruffleSafepoint.poll(this);
            }
            return NullObject.SINGLETON;
        }

        @TruffleBoundary
        private Object spawnCall(boolean joinThread, boolean virtual) {
            RootNode spawnRootNode = new RootNode(getLanguage(PolyglotIsolateTestLanguage.class)) {
                @Override
                public Object execute(VirtualFrame frame) {
                    callHostObject();
                    return NullObject.SINGLETON;
                }
            };
            CallTarget target = spawnRootNode.getCallTarget();
            callNode = insert(Truffle.getRuntime().createDirectCallNode(target));
            Context ctx = contextReference.get(this);
            Throwable[] error = new Throwable[1];
            Thread thread = ctx.env.newTruffleThreadBuilder(() -> {
                try {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.log(Level.FINE, "Executing in thread {0}", Thread.currentThread().getName());
                    }
                    callNode.call();
                } catch (Throwable t) {
                    if (joinThread) {
                        throw t;
                    } else {
                        error[0] = t;
                    }
                }
            }).virtual(virtual).build();
            if (joinThread) {
                thread.setUncaughtExceptionHandler((th, e) -> error[0] = e);
            }
            thread.start();
            synchronized (ctx.spawnedThreads) {
                ctx.spawnedThreads.put(thread, null);
            }
            if (joinThread) {
                try {
                    thread.join();
                } catch (InterruptedException ie) {
                    TruffleSafepoint.poll(this);
                    throw new ExecutionInterruptedException();
                }

                if (error[0] instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                } else if (error[0] != null) {
                    throw new RuntimeException(error[0]);
                }
            }
            return NullObject.SINGLETON;
        }

        @TruffleBoundary
        private Object readStreamToHostObject() {
            if (repeatCount != 1) {
                throw CompilerDirectives.shouldNotReachHere("Repeat count must be 1");
            }
            if (argument.isEmpty() || "stdin".equals(argument)) {
                Context ctx = contextReference.get(this);
                TruffleLanguage.Env env = ctx.env;
                InputStream in = env.in();
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                try (OutputStream out = bout) {
                    copy(out, in);
                } catch (IOException ioe) {
                    throw CompilerDirectives.shouldNotReachHere(ioe);
                }
                TruffleObject hostObject = (TruffleObject) ctx.bindings.readMember("hostObject");
                InteropLibrary interop = InteropLibrary.getUncached();
                try {
                    interop.invokeMember(hostObject, hostMethodToCall, bout.toString());
                } catch (InteropException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            } else {
                throw CompilerDirectives.shouldNotReachHere("Unsupported argument " + argument);
            }
            return NullObject.SINGLETON;
        }

        @TruffleBoundary
        private Object writeHostObjectToStream() {
            if (repeatCount != 1) {
                throw CompilerDirectives.shouldNotReachHere("Repeat count must be 1");
            }
            Context ctx = contextReference.get(this);
            TruffleLanguage.Env env = ctx.env;
            OutputStream target = null;
            switch (argument) {
                case "":
                case "stdout":
                    target = env.out();
                    break;
                case "stderr":
                    target = env.err();
                    break;
            }
            if (target == null) {
                throw CompilerDirectives.shouldNotReachHere("Unsupported argument " + argument);
            }
            TruffleObject hostObject = (TruffleObject) ctx.bindings.readMember("hostObject");
            InteropLibrary interop = InteropLibrary.getUncached();
            try {
                Object result = interop.invokeMember(hostObject, hostMethodToCall);
                ByteArrayInputStream in = new ByteArrayInputStream(interop.asString(result).getBytes());
                copy(target, in);
            } catch (InteropException | IOException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
            return NullObject.SINGLETON;
        }

        static class RecursionRootNode extends RootNode {
            @Child private DirectCallNode callNode;

            RecursionRootNode(PolyglotIsolateTestLanguage language) {
                super(language);
            }

            @Override
            public Object execute(VirtualFrame frame) {
                int n = (Integer) frame.getArguments()[0];
                if (n == 1) {
                    return 1;
                } else {
                    return (Integer) callNode.call(n - 1) + 1;
                }
            }

            @Override
            public String getName() {
                return "recFunction";
            }
        }

        @TruffleBoundary
        private Object interpreterRecursion() {
            int recursionDepth = Integer.parseInt(argument);
            RecursionRootNode prevRecRoot = new RecursionRootNode(getLanguage(PolyglotIsolateTestLanguage.class));
            CallTarget callTarget = prevRecRoot.getCallTarget();
            callNode = insert(Truffle.getRuntime().createDirectCallNode(callTarget));
            for (int i = 2; i <= recursionDepth; i++) {
                RecursionRootNode recRoot = new RecursionRootNode(getLanguage(PolyglotIsolateTestLanguage.class));
                callTarget = recRoot.getCallTarget();
                prevRecRoot.callNode = prevRecRoot.insert(Truffle.getRuntime().createDirectCallNode(callTarget));
                prevRecRoot = recRoot;
            }
            return callNode.call(recursionDepth);
        }

        @TruffleBoundary
        private Object killActiveChildProcess() {
            Context ctx = contextReference.get(this);
            TruffleObject hostObject = (TruffleObject) ctx.bindings.readMember("hostObject");
            try {
                InteropLibrary interop = InteropLibrary.getUncached();
                Integer waitAfter = argument.isEmpty() ? null : Integer.parseInt(argument);
                interop.invokeMember(hostObject, hostMethodToCall, 1, new KillActiveChildProcess(ctx.env, hostObject, hostMethodToCall, waitAfter));
                return NullObject.SINGLETON;
            } catch (UnsupportedMessageException | ArityException | UnknownIdentifierException | UnsupportedTypeException e) {
                throw new RuntimeException(e);
            }
        }

        @TruffleBoundary
        private Object spawnSubProcess() {
            try {
                Context ctx = contextReference.get(this);
                ctx.env.newProcessBuilder(argument).start();
                return NullObject.SINGLETON;
            } catch (IOException ioException) {
                // Throw as IOException to make it an internal error with merged stack trace.
                throw sthrow(RuntimeException.class, ioException);
            }
        }

        @TruffleBoundary
        private Object loopHostCall() {
            Context ctx = contextReference.get(this);
            TruffleObject hostObject = (TruffleObject) ctx.bindings.readMember("hostObject");
            InteropLibrary interop = InteropLibrary.getUncached();
            try {
                for (int i = 0; i < repeatCount; i++) {
                    String result = interop.asString(interop.invokeMember(hostObject, hostMethodToCall));
                    if (!argument.equals(result)) {
                        throw CompilerDirectives.shouldNotReachHere("Expected " + argument + ", got " + result);
                    }
                }
                return repeatCount;
            } catch (InteropException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @TruffleBoundary
        private Object allocateGuestObject() {
            return new GuestArray(new byte[Integer.parseInt(argument)]);
        }

        @TruffleBoundary
        private Object testSymbolCache() {
            Map<String, Object> members = new HashMap<>();
            int count = repeatCount;
            String prefix = argument.isEmpty() ? "" : argument;
            if ("method".equals(hostMethodToCall)) {
                for (int i = 1; i <= count; i++) {
                    members.put(prefix + i, new GuestFunction(i));
                }
            } else if ("field".equals(hostMethodToCall)) {
                for (int i = 1; i <= count; i++) {
                    members.put(prefix + i, i);
                }
            } else {
                throw CompilerDirectives.shouldNotReachHere("Unknown command " + hostMethodToCall);
            }
            return new GuestObject(members);
        }

        @SuppressWarnings({"unchecked", "unused"})
        private static <T extends Throwable> T sthrow(Class<T> type, Throwable t) throws T {
            throw (T) t;
        }

        private static void copy(OutputStream out, InputStream in) throws IOException {
            byte[] data = new byte[4];
            while (true) { // TERMINATION ARGUMENT: terminate when no more input available
                int len = in.read(data);
                if (len <= 0) {
                    break;
                }
                out.write(data, 0, len);
            }
        }

        @TruffleBoundary
        private Object accessMemory() {
            if (!ImageInfo.inImageRuntimeCode()) {
                throw new RuntimeException("ACCESS can only be used on SVM");
            }
            long pointer = Long.parseLong(argument);
            Pointer p = WordFactory.pointer(pointer);
            byte x = p.readByte(0);
            x++;
            p.writeByte(0, x);
            return NullObject.SINGLETON;
        }

        @TruffleBoundary
        private static Object allocateMemory() {
            if (!ImageInfo.inImageRuntimeCode()) {
                throw new RuntimeException("ALLOCATE can only be used on SVM");
            }
            PinnedObject po = PinnedObject.create(new Object());
            PointerBase addr = po.addressOfObject();
            Pointer p = WordFactory.pointer(addr.rawValue());
            return new PointerObject(p);
        }

        @TruffleBoundary
        private Object evalSnippet() {
            Context ctx = contextReference.get(this);
            TruffleLanguage.Env env = ctx.env;
            CallTarget callTarget = env.parsePublic(Source.newBuilder(hostMethodToCall, argument, "snippet").build());
            callNode = insert(Truffle.getRuntime().createDirectCallNode(callTarget));
            for (int i = 0; i < repeatCount; i++) {
                callNode.call();
            }
            return NullObject.SINGLETON;
        }

        @TruffleBoundary
        private boolean execExit() {
            Context ctx = contextReference.get(this);
            TruffleLanguage.Env env = ctx.env;
            env.getContext().closeExited(this, Integer.parseInt(argument));
            return true;
        }

        @TruffleBoundary
        private Object log() {
            TruffleLogger logger = contextReference.get(this).env.getLogger(hostMethodToCall);
            logger.log(Level.INFO, argument);
            return NullObject.SINGLETON;
        }

        @TruffleBoundary
        private boolean execTest() {
            switch (hostMethodToCall) {
                case "isIsolated":
                    return ImageInfo.inImageRuntimeCode();
                case "hasCompilerMitigation":
                    Boolean blindConstantsValue = RuntimeOptions.get("BlindConstants");
                    if (Platform.includedIn(Platform.AMD64.class)) {
                        Boolean memoryMaskingAndFencing = RuntimeOptions.get("MemoryMaskingAndFencing");
                        return Boolean.TRUE.equals(blindConstantsValue) && Boolean.TRUE.equals(memoryMaskingAndFencing);
                    } else {
                        Object spectreBarriersValue = RuntimeOptions.get("SpectrePHTBarriers");
                        try {
                            Object guardTargets = Class.forName("jdk.graal.compiler.core.common.SpectrePHTMitigations").getEnumConstants()[2];
                            return Boolean.TRUE.equals(blindConstantsValue) && spectreBarriersValue.equals(guardTargets);
                        } catch (ClassNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                    }
                case "isMemoryProtected":
                    return ImageInfo.inImageRuntimeCode() && isMemoryProtected();
                default:
                    throw new ExecuteException("Unknown request " + hostMethodToCall);
            }
        }

        private static boolean isMemoryProtected() {
            // We cannot depend on SVM core from truffle. We rather use reflection than add
            // a new project with a feature used for TruffleIsolateTestLanguage.
            try {
                Class<?> memoryProtectionDomainClz = Class.forName("com.oracle.svm.core.os.MemoryProtectionProvider");
                if (ImageSingletons.contains(memoryProtectionDomainClz)) {
                    Method getProtectionDomain = memoryProtectionDomainClz.getDeclaredMethod("getProtectionDomain");
                    getProtectionDomain.setAccessible(true);
                    Isolates.ProtectionDomain pd = (Isolates.ProtectionDomain) getProtectionDomain.invoke(ImageSingletons.lookup(memoryProtectionDomainClz));
                    if (!Isolates.ProtectionDomain.NO_DOMAIN.equals(pd)) {
                        return true;
                    }
                }
                return false;
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e.getMessage());
            }
        }

        @TruffleBoundary
        private Object executeAccessBindings() {
            Context ctx = contextReference.get(this);
            TruffleLanguage.Env env = ctx.env;
            env.getPolyglotBindings();
            return NullObject.SINGLETON;
        }

        @TruffleBoundary
        private TruffleObject returnWeaklyReachableGuestObject() {
            Context ctx = contextReference.get(this);
            CallbackObject callbackObject = null;
            if ("create".equals(argument)) {
                callbackObject = new CallbackObject(ctx);
                ctx.weaklyReachableGuestObjects.put(hostMethodToCall, new WeakReference<>(callbackObject));
            } else {
                WeakReference<CallbackObject> callbackObjectRef = ctx.weaklyReachableGuestObjects.get(hostMethodToCall);
                if (callbackObjectRef != null) {
                    callbackObject = callbackObjectRef.get();
                }
                if (callbackObject == null) {
                    return NullObject.SINGLETON;
                }
            }
            return callbackObject;
        }

        @Override
        public SourceSection getSourceSection() {
            return source.createSection(1);
        }
    }

    static final class Context {
        private final Map<Thread, Void> spawnedThreads = new WeakHashMap<>();
        private TruffleLanguage.Env env;
        final AssociativeArray bindings;
        private final Map<String, WeakReference<CallbackObject>> weaklyReachableGuestObjects = new ConcurrentHashMap<>();

        Context(Env env) {
            this.env = env;
            bindings = new AssociativeArray("triste bindings");
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    static final class NullObject implements TruffleObject {
        static final NullObject SINGLETON = new NullObject();

        @ExportMessage
        boolean isNull() {
            return true;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    static final class PointerObject implements TruffleObject {

        private final Pointer value;

        PointerObject(Pointer value) {
            this.value = value;
        }

        @ExportMessage
        boolean isPointer() {
            return true;
        }

        @ExportMessage
        long asPointer() {
            return this.value.rawValue();
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class ThrowObject implements TruffleObject {

        private final SourceSection sourceSection;
        private final String exceptionType;

        ThrowObject(Source source, String exceptionType) {
            this.sourceSection = source == null ? null : source.createSection(1);
            this.exceptionType = exceptionType;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        Object execute(Object... arguments) throws UnsupportedTypeException, ArityException {
            switch (exceptionType) {
                case "UnsupportedTypeException":
                    throw UnsupportedTypeException.create(arguments);
                case "ArityException":
                    throw ArityException.create(0, 10, 42);
                case "TruffleException":
                    throw new ExecuteException();
                default:
                    throw CompilerDirectives.shouldNotReachHere(exceptionType);
            }
        }

        @ExportMessage
        boolean hasSourceLocation() {
            return sourceSection != null;
        }

        @ExportMessage(name = "getSourceLocation")
        SourceSection getSourceSection() throws UnsupportedMessageException {
            if (sourceSection == null) {
                throw UnsupportedMessageException.create();
            }
            return sourceSection;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class KillActiveChildProcess implements TruffleObject {

        private final Env env;
        private final TruffleObject target;
        private final String hostMethodName;
        private final Integer waitAfter;
        private final InteropLibrary interop;

        KillActiveChildProcess(Env env, TruffleObject target, String hostMethodName, Integer waitAfter) {
            this.env = env;
            this.target = target;
            this.hostMethodName = hostMethodName;
            this.waitAfter = waitAfter;
            interop = InteropLibrary.getUncached();
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        Object execute(Object... arguments) throws ArityException, UnsupportedMessageException, UnsupportedTypeException {
            int depth = (int) arguments[0];
            if (waitAfter != null && waitAfter < depth) {
                Thread polyglotThread = env.newTruffleThreadBuilder(() -> {
                    try {
                        interop.invokeMember(target, "notifyBlocked");
                    } catch (InteropException ui) {
                        // Ignore host does not support notifyBlocked.
                    }
                }).build();
                polyglotThread.setName("NotifyBlocked Thread");
                polyglotThread.start();
                // Blocks until this process has terminated
                while (true) {
                    TruffleSafepoint.poll(null);
                }
            }
            // Host increments the depth parameter.
            try {
                return interop.invokeMember(target, hostMethodName, depth, this);
            } catch (UnknownIdentifierException uid) {
                throw CompilerDirectives.shouldNotReachHere("Invalid hostMethodName " + hostMethodName);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class GuestArray implements TruffleObject {

        final byte[] data;

        GuestArray(byte[] data) {
            this.data = data;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return data.length;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < data.length;
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (index < 0 || index >= data.length) {
                throw InvalidArrayIndexException.create(index);
            }
            return data[(int) index];
        }
    }

    @SuppressWarnings({"unused", "static-method"})
    @ExportLibrary(InteropLibrary.class)
    static final class GuestObject implements TruffleObject {

        private final Map<String, Object> members;

        GuestObject(Map<String, Object> members) {
            this.members = Objects.requireNonNull(members);
        }

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        Object getMembers(boolean includeInternal) {
            return new GuestList(members.keySet().toArray());
        }

        @ExportMessage
        @TruffleBoundary
        boolean isMemberReadable(String member) {
            return members.containsKey(member);
        }

        @ExportMessage
        boolean isMemberModifiable(String member) {
            return false;
        }

        @ExportMessage
        boolean isMemberInsertable(String member) {
            return false;
        }

        @ExportMessage
        boolean isMemberRemovable(String member) {
            return false;
        }

        @ExportMessage
        @TruffleBoundary
        boolean isMemberInvocable(String member) {
            Object value = members.get(member);
            if (value instanceof TruffleObject) {
                return InteropLibrary.getUncached(value).isExecutable(value);
            }
            return false;
        }

        @ExportMessage
        @TruffleBoundary
        Object readMember(String memberName) throws UnknownIdentifierException {
            Object value = members.get(memberName);
            if (value == null) {
                throw UnknownIdentifierException.create(memberName);
            }
            return value;
        }

        @ExportMessage
        @TruffleBoundary
        Object invokeMember(String member, Object[] arguments) throws UnsupportedMessageException, ArityException, UnknownIdentifierException, UnsupportedTypeException {
            Object value = members.get(member);
            if (value == null) {
                throw UnknownIdentifierException.create(member);
            }
            InteropLibrary interop = InteropLibrary.getUncached();
            if (value instanceof TruffleObject && interop.isExecutable(value)) {
                return interop.execute(value, arguments);
            }
            throw UnsupportedMessageException.create();
        }

        @ExportMessage
        void writeMember(String member, Object value) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @ExportMessage
        void removeMember(String member) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    @SuppressWarnings({"unused", "static-method"})
    @ExportLibrary(InteropLibrary.class)
    static final class GuestList implements TruffleObject {

        private final Object[] items;

        GuestList(Object... items) {
            this.items = items;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return items.length;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < items.length;
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (index < 0 || index >= items.length) {
                throw InvalidArrayIndexException.create(index);
            }
            return items[(int) index];
        }
    }

    @SuppressWarnings({"unused", "static-method"})
    @ExportLibrary(InteropLibrary.class)
    static final class GuestFunction implements TruffleObject {

        private final Object result;

        GuestFunction(Object result) {
            this.result = Objects.requireNonNull(result);
        }

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object... arguments) {
            return result;
        }
    }
}
