/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tck.instrumentation;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.debug.DebugScope;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendAnchor;
import com.oracle.truffle.api.debug.SuspendedCallback;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.debug.SuspensionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.source.SourceSection;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

/**
 * A total debugging instrument that steps through the whole guest language execution and asks for
 * all debugger-related information. Debugging information is printed to the environment output.
 */
@Registration(name = "Debug a lot", id = DebugALot.ID)
public class DebugALot extends TruffleInstrument implements SuspendedCallback {

    static final String ID = "debugalot";

    private boolean failFast;
    private PrintWriter logger;
    private boolean doEval;
    private volatile boolean hasFailed;
    private Throwable error;

    @Option(name = "", help = "Start debugging logger.", category = OptionCategory.EXPERT) //
    static final OptionKey<Boolean> DebugALot = new OptionKey<>(true);

    @Option(name = "Eval", help = "Whether to test evaluations. (default:false)", category = OptionCategory.EXPERT) //
    static final OptionKey<Boolean> Eval = new OptionKey<>(false);

    @Option(name = "FailFast", help = "Fail fast, give up after the first error. (default:false)", category = OptionCategory.EXPERT) //
    static final OptionKey<Boolean> FailFast = new OptionKey<>(false);

    @Option(name = "LogFile", help = "File to print the debugger log into. (default:standard output)", category = OptionCategory.EXPERT) //
    static final OptionKey<String> LogFile = new OptionKey<>("");

    @Override
    protected void onCreate(Env env) {
        Boolean debugALot = env.getOptions().get(DebugALot);
        failFast = env.getOptions().get(FailFast);
        doEval = env.getOptions().get(Eval);
        boolean isLogFile = env.getOptions().hasBeenSet(LogFile);
        if (!(Boolean.TRUE.equals(debugALot) || failFast || doEval || isLogFile)) {
            return;
        }
        if (isLogFile) {
            String logFilePath = env.getOptions().get(LogFile);
            try {
                logger = new PrintWriter(new FileWriter(logFilePath));
            } catch (IOException ioex) {
                logger = new PrintWriter(env.out());
                logger.print(ioex.getLocalizedMessage());
            }
        } else {
            logger = new PrintWriter(env.out());
        }
        Debugger debugger = env.lookup(env.getInstruments().get("debugger"), Debugger.class);
        DebuggerSession debuggerSession = debugger.startSession(this);
        debuggerSession.suspendNextExecution();
        debuggerSession.setSteppingFilter(SuspensionFilter.newBuilder().ignoreLanguageContextInitialization(true).build());
    }

    @Override
    protected void onDispose(Env env) {
        logger.print("Executed successfully: ");
        logger.print(Boolean.toString(!hasFailed).toUpperCase());
        logger.flush();
        super.onDispose(env);
        if (error != null) {
            throw new AssertionError("Failure", error);
        }
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new DebugALotOptionDescriptors();
    }

    @Override
    public void onSuspend(SuspendedEvent event) {
        try {
            logSuspendLocation(event.isLanguageContextInitialized(), event.getSuspendAnchor(), event.getSourceSection());
            logFrames(event.getStackFrames());
        } catch (Throwable t) {
            hasFailed = true;
            try {
                logThrowable(t);
            } catch (Throwable lt) {
                lt.printStackTrace(logger);
                if (lt instanceof ThreadDeath) {
                    throw lt;
                }
            }
            if (t instanceof ThreadDeath) {
                throw t;
            }
            if (failFast) {
                error = t;
            }
        }
        logger.flush();
        if (failFast && hasFailed) {
            event.prepareContinue();
        } else {
            event.prepareStepInto(1);
        }
    }

    private void logSuspendLocation(boolean initialized, SuspendAnchor suspendAnchor, SourceSection sourceSection) {
        if (!initialized) {
            logger.print("Uninitialized: ");
        }
        logger.print(suspendAnchor);
        if (sourceSection == null) {
            throw new NullPointerException("No source section is available at suspend location.");
        }
        logSourceSection(sourceSection);
    }

    private void logSourceSection(SourceSection sourceSection) {
        if (sourceSection == null) {
            logger.println(" <NONE>");
            return;
        }
        logger.print(" [");
        logger.print(sourceSection.getStartLine());
        logger.print(':');
        logger.print(sourceSection.getStartColumn());
        logger.print('-');
        logger.print(sourceSection.getEndLine());
        logger.print(':');
        logger.print(sourceSection.getEndColumn());
        logger.print("] in ");
        logger.println(sourceSection.getSource().getURI());
    }

    private void logSourceSection(org.graalvm.polyglot.SourceSection sourceSection) {
        if (sourceSection == null) {
            logger.println(" <NONE>");
            return;
        }
        logger.print(" [");
        logger.print(sourceSection.getStartLine());
        logger.print(':');
        logger.print(sourceSection.getStartColumn());
        logger.print('-');
        logger.print(sourceSection.getEndLine());
        logger.print(':');
        logger.print(sourceSection.getEndColumn());
        logger.print("] in ");
        logger.println(sourceSection.getSource().getURI());
    }

    private void logFrames(Iterable<DebugStackFrame> stackFrames) {
        logger.print("Stack: ");
        List<DebugStackFrame> frames = new ArrayList<>();
        for (DebugStackFrame frame : stackFrames) {
            frames.add(frame);
        }
        logger.print(frames.size());
        logger.println((frames.size() == 1) ? " frame" : " frames");
        for (int i = 0; i < frames.size(); i++) {
            logger.print(i + 1);
            logger.print(". ");
            int offset = Integer.toString(i + 1).length() + 2;
            String framePrefix = getPrefix(offset);
            logFrame(framePrefix, frames.get(i));
        }
    }

    private void logFrame(String prefix, DebugStackFrame frame) {
        logger.print(frame.getName());
        if (frame.isInternal()) {
            logger.print(" [Internal]");
        }
        logSourceSection(frame.getSourceSection());
        List<DebugScope> scopes = new ArrayList<>();
        for (DebugScope scope = frame.getScope(); scope != null; scope = scope.getParent()) {
            scopes.add(scope);
        }
        logger.print(prefix);
        logger.print("Scopes: ");
        logger.println(scopes.size());
        for (int i = 0; i < scopes.size(); i++) {
            logger.print(prefix);
            logger.print(i + 1);
            logger.print(". ");
            int offset = prefix.length() + Integer.toString(i + 1).length() + 2;
            String scopePrefix = getPrefix(offset);
            logScope(scopePrefix, scopes.get(i), (i == 0) ? frame : null);
        }
    }

    private void logScope(String prefix, DebugScope scope, DebugStackFrame frameForEval) {
        logger.print(scope.getName());
        if (scope.isFunctionScope()) {
            logger.println(" [Function]");
        } else {
            logger.println();
        }
        Iterable<DebugValue> arguments = scope.getArguments();
        List<DebugValue> values;
        if (arguments != null) {
            logger.print(prefix);
            logger.print("Arguments: ");
            values = new ArrayList<>();
            for (DebugValue v : arguments) {
                values.add(v);
            }
            logger.println(values.size());
            logValues(prefix, values);
        }
        Iterable<DebugValue> variables = scope.getDeclaredValues();
        logger.print(prefix);
        logger.print("Variables: ");
        values = new ArrayList<>();
        for (DebugValue v : variables) {
            values.add(v);
        }
        logger.println(values.size());
        logValues(prefix, values);

        if (frameForEval != null && doEval) {
            testEval(prefix, frameForEval, values);
        }
    }

    private void logValues(String prefix, List<DebugValue> values) {
        for (int i = 0; i < values.size(); i++) {
            logger.print(prefix);
            logger.print(i + 1);
            logger.print(". ");
            DebugValue v = values.get(i);
            logger.print(v.getName());
            logger.print(" = ");
            logger.println(v.toDisplayString(false));
            int offset = prefix.length() + Integer.toString(i + 1).length() + 2;
            String valuePrefix = getPrefix(offset);
            logValue(valuePrefix, v);
        }
    }

    private void logValue(String prefix, DebugValue v) {
        LanguageInfo language = v.getOriginalLanguage();
        if (language != null) {
            logger.print(prefix);
            logger.print("From: ");
            logger.println(language.getId());
        }
        DebugValue metaObject = v.getMetaObject();
        if (metaObject != null) {
            logger.print(prefix);
            logger.print("Type: ");
            logger.println(metaObject.toDisplayString(false));
        }
        SourceSection sourceLocation = v.getSourceLocation();
        if (sourceLocation != null) {
            logger.print(prefix);
            logger.print("SourceSection: ");
            logSourceSection(sourceLocation);
        }
        if (v.isArray()) {
            List<DebugValue> array = v.getArray();
            int length = array.size();
            logger.print(prefix);
            logger.print("Array of length: ");
            logger.println(Integer.toString(length));
            for (int i = 0; i < length && i < 10; i++) {
                logger.print(prefix);
                logger.print("  element #");
                logger.print(Integer.toString(i));
                logger.print(" : ");
                logger.println(array.get(i).toDisplayString(false));
            }
        }
        Collection<DebugValue> properties = v.getProperties();
        logger.print(prefix);
        if (properties == null || properties.isEmpty()) {
            logger.println("Properties: none");
        } else {
            logger.print("Properties: ");
            logger.println(Integer.toString(properties.size()));
        }
        logger.print(prefix);
        logger.print("Internal: ");
        logger.println(v.isInternal());
        logger.print(prefix);
        logger.print("Readable: ");
        logger.println(v.isReadable());
        logger.print(prefix);
        logger.print("Writable: ");
        logger.println(v.isWritable());
    }

    private void testEval(String prefix, DebugStackFrame frame, List<DebugValue> values) {
        for (DebugValue v : values) {
            DebugValue ev = frame.eval(v.getName());
            String value = v.toDisplayString(false);
            String evalue = ev.toDisplayString(false);
            if (!value.equals(evalue)) {
                hasFailed = true;
                logger.print(prefix);
                logger.print("ERROR: local value '");
                logger.print(v.getName());
                logger.print("' has value '");
                logger.print(v.toDisplayString(false));
                logger.print("' but evaluated to '");
                logger.print(ev.toDisplayString(false));
                logger.println("'");
            }
        }
    }

    private void logThrowable(Throwable t) {
        logger.print("\nERROR: Thrown: '");
        logger.print(t.getLocalizedMessage());
        logger.print("', throwable class = ");
        logger.println(t.getClass());
        if (t instanceof PolyglotException) {
            PolyglotException pe = (PolyglotException) t;
            logger.print("  Polyglot Message: '");
            logger.print(pe.getMessage());
            logger.println("'");
            logger.print("  canceled = ");
            logger.print(pe.isCancelled());
            logger.print(", exited = ");
            logger.print(pe.isExit());
            logger.print(", guest ex. = ");
            logger.print(pe.isGuestException());
            logger.print(", host ex. = ");
            logger.print(pe.isHostException());
            logger.print(", incompl. source = ");
            logger.print(pe.isIncompleteSource());
            logger.print(", internal = ");
            logger.print(pe.isInternalError());
            logger.print(", syntax error = ");
            logger.println(pe.isSyntaxError());
            logger.print("  Source Section: ");
            logSourceSection(pe.getSourceLocation());
            if (pe.isExit()) {
                logger.print("  Exit Status = ");
                logger.println(pe.getExitStatus());
            }
            if (pe.isGuestException()) {
                Value guestObject = pe.getGuestObject();
                logger.print("  Guest Object = ");
                logger.println(guestObject.toString());
            }
            if (pe.isHostException()) {
                logger.println("  Host Exception:");
                pe.asHostException().printStackTrace(logger);
            }
            logger.println("  Polyglot Stack Trace:");
            for (PolyglotException.StackFrame sf : pe.getPolyglotStackTrace()) {
                logger.print("    Language ID: ");
                logger.println(sf.getLanguage().getId());
                logger.print("    Root Name: ");
                logger.println(sf.getRootName());
                logger.print("    Source Location: ");
                logSourceSection(sf.getSourceLocation());
                logger.print("    Guest Frame: ");
                logger.println(sf.isGuestFrame());
                logger.print("    Host Frame: ");
                if (sf.isHostFrame()) {
                    logger.println(sf.toHostFrame());
                } else {
                    logger.println(false);
                }
            }
        } else {
            t.printStackTrace(logger);
        }
    }

    private static String getPrefix(int length) {
        char[] prefixChars = new char[length];
        Arrays.fill(prefixChars, ' ');
        return new String(prefixChars);
    }

}
