/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.common;

import static org.junit.Assert.assertEquals;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.Proxy;
import org.graalvm.polyglot.proxy.ProxyArray;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.TestAPIAccessor;
import com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage.ExecutableContext;

public abstract class AbstractExecutableTestLanguage extends TruffleLanguage<ExecutableContext> {

    private static final String ARGUMENTS = "_$arguments";

    public static Value execute(Context context, Class<? extends AbstractExecutableTestLanguage> clazz, Object... contextArgs) {
        String languageId = TestUtils.getDefaultLanguageId(clazz);
        return evalTestLanguage(context, clazz, Source.create(languageId, ""), contextArgs);
    }

    public static Value evalTestLanguage(Context context, Class<? extends AbstractExecutableTestLanguage> clazz, Source source, Object... contextArgs) {
        String languageId = TestUtils.getDefaultLanguageId(clazz);
        assertEquals(languageId, source.getLanguage());
        if (contextArgs.length > 0) {
            context.getBindings(languageId).putMember(ARGUMENTS, createHostToGuestArguments(context, contextArgs));
        } else {
            if (context.getBindings(languageId).getMemberKeys().contains(ARGUMENTS)) {
                context.getBindings(languageId).removeMember(ARGUMENTS);
            }
        }
        return context.eval(source);
    }

    public static Value evalTestLanguage(Context context, Class<? extends AbstractExecutableTestLanguage> clazz, CharSequence source, Object... contextArgs) {
        String languageId = TestUtils.getDefaultLanguageId(clazz);
        return evalTestLanguage(context, clazz, Source.create(languageId, source), contextArgs);
    }

    private static Object createHostToGuestArguments(Context context, Object[] args) {
        for (Object object : args) {
            if (object != null && !InteropLibrary.isValidValue(object) && !(object instanceof Proxy)) {
                /*
                 * The following check is not precise, because a host object can have no member keys
                 * even if host access is enabled, but as there is no direct way to determine if
                 * host access is enabled, this is the best test we came up with.
                 */
                Value value = context.asValue(object);
                if (value.isHostObject() && value.getMemberKeys().isEmpty()) {
                    throw new AssertionError("Value " + object + " is of type " + object.getClass().getName() + " which is not a valid interop primitive but host access is not allowed. " +
                                    "Please allow host access or use primitive args only. Add builder.hostAccess(HostAccess.ALL) to enable host access.");
                }
            }
        }
        return ProxyArray.fromArray(args);
    }

    public static Value parseTestLanguage(Context context, Class<? extends AbstractExecutableTestLanguage> clazz, CharSequence source, Object... contextArgs) {
        String languageId = TestUtils.getDefaultLanguageId(clazz);
        return parseTestLanguage(context, clazz, Source.create(languageId, source), contextArgs);
    }

    public static Value parseTestLanguage(Context context, Class<? extends AbstractExecutableTestLanguage> clazz, Source source, Object... contextArgs) {
        String languageId = TestUtils.getDefaultLanguageId(clazz);
        assertEquals(languageId, source.getLanguage());
        context.getBindings(languageId).putMember(ARGUMENTS, createHostToGuestArguments(context, contextArgs));
        return context.parse(source);
    }

    protected final InteropLibrary interop = InteropLibrary.getFactory().createDispatched(3);

    @Override
    protected final ExecutableContext createContext(Env env) {
        return new ExecutableContext(getClass(), env);
    }

    @Override
    @SuppressWarnings("unused")
    protected final CallTarget parse(ParsingRequest request) throws Exception {
        ExecutableContext executableContext = TestAPIAccessor.engineAccess().getCurrentContext(getClass());
        Object[] contextArgs = createArgumentsArray(executableContext);
        onParse(request, executableContext.env, contextArgs);
        com.oracle.truffle.api.source.Source source = request.getSource();
        String rootNodeName = getRootName(request, executableContext.env, contextArgs);
        SourceSection srcSec = source.hasCharacters() ? source.createSection(0, request.getSource().getLength()) : null;
        return new RootNode(this, null) {
            @Child InteropLibrary interopLibrary = interop;
            private final SourceSection sourceSection = srcSec;
            @CompilationFinal(dimensions = 1) private final Object[] contextArguments = contextArgs;

            @Override
            public Object execute(VirtualFrame frame) {
                ExecutableContext execCtx = TestAPIAccessor.engineAccess().getCurrentContext(AbstractExecutableTestLanguage.this.getClass());
                try {
                    Object returnValue = AbstractExecutableTestLanguage.this.execute(this, execCtx.env, contextArguments, frame.getArguments());

                    if (returnValue == null) {
                        return NullObject.SINGLETON;
                    }
                    return returnValue;
                } catch (AbstractTruffleException e) {
                    throw e;
                } catch (Exception e) {
                    throw throwAssertionError(e);
                }
            }

            @TruffleBoundary
            private AssertionError throwAssertionError(Exception e) {
                throw new AssertionError(e);
            }

            @Override
            public String getName() {
                return rootNodeName;
            }

            @Override
            public SourceSection getSourceSection() {
                return sourceSection;
            }
        }.getCallTarget();
    }

    private static Object[] createArgumentsArray(ExecutableContext c) {
        try {
            Object arguments = c.scope.get(ARGUMENTS);
            if (arguments == null) {
                return new Object[0];
            }
            InteropLibrary interop = InteropLibrary.getUncached(arguments);
            int size = (int) interop.getArraySize(arguments);
            Object[] values = new Object[size];
            for (int i = 0; i < size; i++) {
                Object value = interop.readArrayElement(arguments, i);
                if (InteropLibrary.getUncached().isNull(value)) {
                    value = null;
                }
                values[i] = value;
            }
            return values;
        } catch (InteropException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    public static final class ExecutableContext {

        public final Env env;
        final InteropMapObject scope;

        ExecutableContext(Class<? extends AbstractExecutableTestLanguage> clazz, Env env) {
            this.env = env;
            this.scope = new InteropMapObject(clazz) {
                @Override
                @TruffleBoundary
                Object toDisplayString(boolean allowSideEffects) {
                    return clazz.getSimpleName() + ".scope";
                }
            };
        }

    }

    @Override
    protected final Object getScope(ExecutableContext context) {
        return context.scope;
    }

    /**
     * Subclasses implement this method to define a particular behavior of the language.
     *
     * @param node root node of the main call target.
     * @param env {@link TruffleLanguage.Env}.
     * @param contextArguments context arguments that can be set only once for a particular source
     *            and context. The arguments are specified in a call to
     *            {@link #evalTestLanguage(Context, Class, CharSequence, Object...)},
     *            {@link #parseTestLanguage(Context, Class, CharSequence, Object...)}, or
     *            {@link #parseTestLanguage(Context, Class, Source, Object...)}. If different
     *            arguments for different executions are required, use one of the parse methods and
     *            then the execute method on the resulting {@link Value polyglot Value} to pass
     *            arguments. Those will be then passed to this execute method as frameArguments.
     * @param frameArguments call specific arguments as opposed to contextArguments that can be
     *            specified only once as described above.
     * @return
     * @throws Exception exceptions of type AbstractTruffleException are rethrown, other exceptions
     *             are wrapped in AssertionError.
     */
    protected abstract Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception;

    @SuppressWarnings("unused")
    protected void onParse(ParsingRequest request, Env env, Object[] contextArguments) throws Exception {
    }

    @SuppressWarnings("unused")
    protected String getRootName(ParsingRequest request, Env env, Object[] contextArguments) throws Exception {
        return getClass().getName() + ".execute";
    }
}
