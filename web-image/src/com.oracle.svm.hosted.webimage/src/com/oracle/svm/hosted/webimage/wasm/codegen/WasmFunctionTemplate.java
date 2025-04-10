/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.svm.hosted.webimage.wasm.codegen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.oracle.svm.hosted.webimage.wasm.ast.Function;
import com.oracle.svm.hosted.webimage.wasm.ast.TypeUse;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmIdFactory;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WebImageWasmIds;

import jdk.graal.compiler.debug.GraalError;

/**
 * Base class to define parameterized, handwritten Wasm functions.
 * <p>
 * These templates are a good way to outline often-used code patterns during codegen. Instead of
 * emitting some code pattern in multiple places, a template can be written that produces a Wasm
 * {@link Function} containing that code pattern. The code generation would then only request a
 * function id using {@link #requestFunctionId(Object)} and emit a Wasm call instruction that
 * targets that id.
 * <p>
 * The actual functions have to be created using {@link #createFunction(Context)} and added to the
 * Wasm module, otherwise assembling the final binary will fail due to function ids that can't be
 * found.
 * <p>
 * The generated code may need to be slightly different depending on some constant parameter (e.g.
 * the struct type the templated code operates on). In that case, multiple functions can be
 * generated using different values of that parameter.
 * <p>
 * See also {@link Singleton} for a base class without parameterization
 * <p>
 * See also {@link com.oracle.svm.hosted.webimage.wasmgc.codegen.WasmGCFunctionTemplates} For
 * implementations of these templates.
 *
 * @param <T> Templates are specialized using a parameter of this type. For each unique instance of
 *            this type, a specialized variant of the template can be requested and generated.
 */
public abstract class WasmFunctionTemplate<T> {

    /**
     * Function creation context that is passed to {@link #createFunction(Context)}.
     * <p>
     * Holds all information necessary to create a template function. Call
     * {@link #createFunction(TypeUse, Object)} to create the Wasm function skeleton.
     * <p>
     * Late templates ({@link #isLate}) will not have access to {@link WasmCodeGenTool}, only to a
     * {@link WebImageWasmProviders} instance.
     */
    protected final class Context {
        private final WasmCodeGenTool codeGenTool;
        private final WebImageWasmProviders providers;
        private final T param;

        /**
         * Never use this constructor directly. Use {@link #Context(WasmCodeGenTool, Object)} or
         * {@link #Context(WebImageWasmProviders, Object)}.
         */
        private Context(WasmCodeGenTool codeGenTool, WebImageWasmProviders providers, T param) {
            this.codeGenTool = codeGenTool;
            this.providers = providers;
            this.param = param;
        }

        /**
         * Constructor for regular template generation.
         */
        public Context(WasmCodeGenTool codeGenTool, T param) {
            this(codeGenTool, codeGenTool.getWasmProviders(), param);
            assert !isLateTemplate() : "Created regular context for late template";
        }

        /**
         * Constructor context used in late-generating templates.
         */
        public Context(WebImageWasmProviders providers, T param) {
            this(null, providers, param);
            assert isLateTemplate() : "Created late context for regular template";
        }

        public WasmCodeGenTool getCodeGenTool() {
            if (isLateTemplate()) {
                throw GraalError.shouldNotReachHere("This is a late-generating template, it does not have access to " + WasmCodeGenTool.class + ": " + WasmFunctionTemplate.this);
            }
            return codeGenTool;
        }

        public WebImageWasmProviders getProviders() {
            return providers;
        }

        public T getParameter() {
            if (WasmFunctionTemplate.this instanceof Singleton) {
                throw GraalError.shouldNotReachHere("This is a singleton template, it does not have a parameter: " + WasmFunctionTemplate.this);
            }
            return param;
        }

        /**
         * Helper method to create a Wasm function for this template.
         */
        public Function createFunction(TypeUse typeUse, Object comment) {
            return Function.createSimple(idFactory, getId(), typeUse, comment);
        }

        public Function createFunction(WebImageWasmIds.DescriptorFuncType funcType, Object comment) {
            return Function.create(idFactory, getId(), funcType, funcType.getTypeUse(), comment);
        }

        public WasmId.Func getId() {
            synchronized (lock) {
                assert wasRequested(param);
                return functionIds.get(param);
            }
        }
    }

    protected final WasmIdFactory idFactory;

    /**
     * Whether this is a regular ({@code false}) or late ({@code true}) template.
     * <p>
     * Regular templates are generated at the end of the compile queue and so their function ids can
     * only be requested during compilation, after that, the templates are frozen. If any new
     * template functions need to be requested after compilation (e.g. for initializing the image
     * heap), those templates have to be declared as late. Late templates are generated and frozen
     * as late as possible, right before the Wasm module is finished.
     * <p>
     * Due to not being part of the compile queue, late templates will not have access to a
     * {@link WasmCodeGenTool} and calling {@link Context#getCodeGenTool()} will fails.
     */
    protected final boolean isLate;

    private final AtomicBoolean frozen = new AtomicBoolean(false);

    protected WasmFunctionTemplate(WasmIdFactory idFactory) {
        this(idFactory, false);
    }

    protected WasmFunctionTemplate(WasmIdFactory idFactory, boolean isLate) {
        this.idFactory = idFactory;
        this.isLate = isLate;
    }

    /**
     * Class that can be synchronized on.
     */
    private static final class Lock {
    }

    private final Lock lock = new Lock();

    /**
     * Holds requested function ids.
     * <p>
     * The map keys are the parameter instances passed to {@link #requestFunctionId(Object)} and
     * their corresponding values are the function ids generated in that method.
     * <p>
     * Accesses to this map may be concurrent (e.g. during method compilation) and have to be
     * synchronized on {@link #lock}.
     */
    private final Map<T, WasmId.Func> functionIds = new HashMap<>();

    /**
     * Holds all requested function ids for which {@link #createFunction(Context)} was not called
     * yet.
     * <p>
     * Also serves as a reverse map of {@link #functionIds}.
     * <p>
     * Accesses to this map may be concurrent (e.g. during method compilation) and have to be
     * synchronized on {@link #lock}.
     */
    private final Map<WasmId.Func, T> notYetGenerated = new HashMap<>();

    /**
     * Whether this template instance is frozen and no more <b>new</b> function ids can be
     * requested.
     *
     * @see #freeze()
     */
    public boolean isFrozen() {
        return frozen.get();
    }

    private boolean assertNotFrozen() {
        assert !isFrozen() : "Attempt to modify frozen function template: " + this;
        return true;
    }

    /**
     * Freeze this template, preventing any new function ids being requested from it.
     * <p>
     * Calls to {@link #requestFunctionId(Object)} will still succeed as long as the requested id
     * has been requested before.
     * <p>
     * This method must only be called on a template that's not frozen and has no functions left to
     * generate.
     */
    public void freeze() {
        assert !isFrozen() : "Function template is already frozen: " + this;
        assert allFunctionsGenerated() : "Attempt to freeze function template before all functions were generated: " + this;
        frozen.set(true);
    }

    /**
     * Overwrite this method if your template cannot support arbitrary parameters of the templated
     * type.
     *
     * @param parameter This method checks whether this instance of {@code T} is valid.
     * @return Whether this template supports the given parameter instance.
     */
    protected boolean isValidParameter(@SuppressWarnings("unused") T parameter) {
        // By default, all instances of the given type are valid parameters.
        return true;
    }

    protected final boolean isLateTemplate() {
        return isLate;
    }

    /**
     * Ensures that template instances are only requested with valid parameters.
     */
    protected final void validateParameter(T parameter) {
        GraalError.guarantee(isValidParameter(parameter), "Invalid parameter detected in %s: %s", this.getClass(), parameter);
    }

    /**
     * Request an id for a function specialized using the given parameter.
     * <p>
     * Each distinct parameter instance will generate a unique function id.
     * <p>
     * If this template is frozen, this must only be called with parameters that have been used
     * before since no new function ids can be generated.
     */
    public final WasmId.Func requestFunctionId(T parameter) {
        synchronized (lock) {
            validateParameter(parameter);
            return functionIds.computeIfAbsent(parameter, k -> {
                assert assertNotFrozen();
                WasmId.Func functionId = idFactory.newInternalFunction(getFunctionName(k));
                notYetGenerated.put(functionId, k);
                return functionId;
            });
        }
    }

    private boolean wasRequested(T parameter) {
        synchronized (lock) {
            return functionIds.containsKey(parameter);
        }
    }

    /**
     * Whether this template has functions left to generate.
     * <p>
     * If this returns {@code false} and more function ids are requested later, this may return
     * {@code true} again.
     */
    public boolean allFunctionsGenerated() {
        synchronized (lock) {
            return notYetGenerated.isEmpty();
        }
    }

    /**
     * Generates function for the given requested function ids.
     * <p>
     * The function id must have been returned by this template instance.
     * <p>
     * This method can be called at most once for each function id.
     */
    public Function createFunctionForId(WasmCodeGenTool codeGenTool, WasmId.Func func) {
        assert !isLateTemplate() : "This is a late template and can't be generated regularly: " + this;
        return createFunctionForId(func, p -> new Context(codeGenTool, p));
    }

    /**
     * Just like {@link #createFunctionForId(WasmCodeGenTool, WasmId.Func)} but for late-generating
     * templates. In this variant, no {@link WasmCodeGenTool} has to be supplied, which means it can
     * be called after the compile queue.
     */
    public Function createFunctionForIdLate(WebImageWasmProviders providers, WasmId.Func func) {
        assert isLateTemplate() : "This is a regular template and can't be generated late: " + this;
        return createFunctionForId(func, p -> new Context(providers, p));
    }

    private Function createFunctionForId(WasmId.Func func, java.util.function.Function<T, Context> contextCreator) {
        synchronized (lock) {
            assert functionIds.containsValue(func) : "This function ID was not produced by this template: " + func;
            T param = notYetGenerated.remove(func);
            assert param != null : "A function for this ID has already been generated: " + func;
            return createFunction(contextCreator.apply(param));
        }
    }

    public List<WasmId.Func> getNotYetGenerated() {
        synchronized (lock) {
            return new ArrayList<>(notYetGenerated.keySet());
        }
    }

    /**
     * Human-readable name for this function.
     * <p>
     * This is only for debugging as the Wasm binary format does not have function names.
     */
    protected abstract String getFunctionName(T parameter);

    /**
     * Builds the Wasm function for this template.
     * <p>
     * Templates need to implement this and create and return a new {@link Function} instance using
     * {@link Context#createFunction(TypeUse, Object)}.
     */
    protected abstract Function createFunction(Context context);

    /**
     * Specialization of the enclosing class allowing for a single function.
     * <p>
     * Use this to define non-parameterized templates.
     */
    public abstract static class Singleton extends WasmFunctionTemplate<Singleton.Param> {

        /**
         * Pseudo parameter type with only a single instance for singleton templates.
         */
        protected static final class Param {
            static final Param INSTANCE = new Param();

            private Param() {
            }
        }

        protected Singleton(WasmIdFactory idFactory) {
            super(idFactory);
        }

        protected Singleton(WasmIdFactory idFactory, boolean isLate) {
            super(idFactory, isLate);
        }

        @Override
        protected final boolean isValidParameter(Param parameter) {
            return parameter == Param.INSTANCE;
        }

        public WasmId.Func requestFunctionId() {
            return requestFunctionId(Param.INSTANCE);
        }

        @Override
        protected final String getFunctionName(Param parameter) {
            return getFunctionName();
        }

        /**
         * Non-parameterized variant of {@link #getFunctionName(Param)}.
         */
        protected abstract String getFunctionName();
    }
}
