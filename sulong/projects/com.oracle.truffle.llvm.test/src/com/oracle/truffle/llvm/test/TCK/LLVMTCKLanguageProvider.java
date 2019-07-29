/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.test.TCK;

import static org.graalvm.polyglot.tck.TypeDescriptor.array;
import static org.graalvm.polyglot.tck.TypeDescriptor.intersection;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.tck.InlineSnippet;
import org.graalvm.polyglot.tck.LanguageProvider;
import org.graalvm.polyglot.tck.ResultVerifier;
import org.graalvm.polyglot.tck.Snippet;
import org.graalvm.polyglot.tck.TypeDescriptor;

public final class LLVMTCKLanguageProvider implements LanguageProvider {

    private static final String ID = "Sulong";

    private static final String PATTERN_VALUE_FNC = "function () {\n" +
            "%s\n" +
            "}";
    private static final String PATTERN_BIN_OP_FNC = "function(a,b) {\n" +
            "a %s b\n" +
            "}";
    private static final String PATTERN_PREFIX_OP_FNC = "function(a) {\n" +
            "%s a\n" +
            "}";

    public LLVMTCKLanguageProvider(){
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public Value createIdentityFunction(Context context) {
        return eval(context, "void* identity(void* x){\n" +
                                       "return x;\n" +
                                       "}");
    }

    public Snippet createIdentityFunctionSnippet(Context context) {
        Value value = createIdentityFunction(context);
        return (Snippet.newBuilder("identity", value, TypeDescriptor.ANY).parameterTypes(TypeDescriptor.ANY).resultVerifier(new IdentityFunctionResultVerifier()).build());
    }

    @Override
    public Collection<? extends Snippet> createValueConstructors(Context context) {
        List<Snippet> vals = new ArrayList<>();
        vals.add(createValueConstructor(context, "1", intersection(TypeDescriptor.NUMBER, array(TypeDescriptor.NUMBER))));
        vals.add(createValueConstructor(context, "1.42", intersection(TypeDescriptor.NUMBER, array(TypeDescriptor.NUMBER))));
        vals.add(createValueConstructor(context, "false", intersection(TypeDescriptor.BOOLEAN, array(TypeDescriptor.BOOLEAN))));
        vals.add(createValueConstructor(context, "'test'", intersection(TypeDescriptor.STRING, array(TypeDescriptor.STRING))));
        vals.add(createValueConstructor(context, "null", TypeDescriptor.NULL));
        vals.add(createValueConstructor(context, "{ 3, 3, 3, 3, 3}", TypeDescriptor.array(TypeDescriptor.NUMBER)));
        vals.add(createValueConstructor(context, "{TRUE, FALSE}", TypeDescriptor.array(TypeDescriptor.BOOLEAN)));
        vals.add(createValueConstructor(context, "{1, 'STRING'}", TypeDescriptor.array(TypeDescriptor.STRING)));

        return Collections.unmodifiableList(vals);
    }

    private static Snippet createValueConstructor(
            Context context,
            String value,
            TypeDescriptor type) {
        return Snippet.newBuilder(value, eval(context, String.format(PATTERN_VALUE_FNC, value)), type).build();
    }

    @Override
    public Collection<? extends Snippet> createExpressions(Context context) {
        List<Snippet> ops = new ArrayList<>();

        TypeDescriptor numOrBool = TypeDescriptor.union(TypeDescriptor.NUMBER, TypeDescriptor.BOOLEAN);
        TypeDescriptor numOrBoolOrNull = TypeDescriptor.union(numOrBool, TypeDescriptor.NULL);
        TypeDescriptor numOrBoolOrArray = TypeDescriptor.union(numOrBool, TypeDescriptor.ARRAY);
        TypeDescriptor numOrBoolOrArrayPrNull = TypeDescriptor.union(numOrBoolOrArray, TypeDescriptor.NULL);
        TypeDescriptor arrNumBool = TypeDescriptor.array(numOrBool);
        TypeDescriptor numOrBoolOrArrNumBool = TypeDescriptor.union(numOrBool, arrNumBool);
        TypeDescriptor numOrBoolOrNullOrArrNumBool = TypeDescriptor.union(numOrBoolOrNull, arrNumBool);
        TypeDescriptor boolOrArrBool = TypeDescriptor.union(TypeDescriptor.BOOLEAN, TypeDescriptor.array(TypeDescriptor.BOOLEAN));

        TypeDescriptor[] acceptedParameterTypes = new TypeDescriptor[]{numOrBoolOrNullOrArrNumBool, numOrBoolOrNullOrArrNumBool};
        TypeDescriptor[] declaredParameterTypes = new TypeDescriptor[]{numOrBoolOrArrayPrNull, numOrBoolOrArrayPrNull};

        // +
        ops.add(createBinaryOperator(context, "+", numOrBoolOrArrNumBool, numOrBoolOrArrayPrNull, numOrBoolOrArrayPrNull,
                RResultVerifier.newBuilder(acceptedParameterTypes, declaredParameterTypes).emptyArrayCheck().build()));
        // -
        ops.add(createBinaryOperator(context, "-", numOrBoolOrArrNumBool, numOrBoolOrArrayPrNull, numOrBoolOrArrayPrNull,
                RResultVerifier.newBuilder(acceptedParameterTypes, declaredParameterTypes).emptyArrayCheck().build()));
        // *
        ops.add(createBinaryOperator(context, "*", numOrBoolOrArrNumBool, numOrBoolOrArrayPrNull, numOrBoolOrArrayPrNull,
                RResultVerifier.newBuilder(acceptedParameterTypes, declaredParameterTypes).emptyArrayCheck().build()));
        // /
        ops.add(createBinaryOperator(context, "/", numOrBoolOrArrNumBool, numOrBoolOrArrayPrNull, numOrBoolOrArrayPrNull,
                RResultVerifier.newBuilder(acceptedParameterTypes, declaredParameterTypes).emptyArrayCheck().build()));



        return ops;
    }

    private static Snippet createBinaryOperator(
            Context context,
            String operator,
            TypeDescriptor type,
            TypeDescriptor ltype,
            TypeDescriptor rtype,
            ResultVerifier verifier) {
        Value fnc = eval(context, String.format(PATTERN_BIN_OP_FNC, operator));
        Snippet.Builder opb = Snippet.newBuilder(operator, fnc, type).parameterTypes(ltype, rtype).resultVerifier(verifier);
        return opb.build();
    }

    private static Snippet createPrefixOperator(
            Context context,
            String operator,
            TypeDescriptor type,
            TypeDescriptor rtype,
            ResultVerifier verifier) {
        Value fnc = eval(context, String.format(PATTERN_PREFIX_OP_FNC, operator));
        Snippet.Builder opb = Snippet.newBuilder(operator, fnc, type).parameterTypes(rtype).resultVerifier(verifier);
        return opb.build();
    }

    @Override
    public Collection<? extends Snippet> createStatements(Context context) {
        List<Snippet> statements = new ArrayList<>();
        Collections.unmodifiableList(statements);


        return statements;
    }

    public Collection<? extends Snippet> createScripts(Context context) {
        List<Snippet> res = new ArrayList<>();


        return res;
    }

    @Override
    public Collection<? extends Source> createInvalidSyntaxScripts(Context ctx) {
        List<Source> res = new ArrayList<>();


        return res;
    }

    @Override
    public Collection<? extends InlineSnippet> createInlineScripts(Context context) {
        return null;
    }

    private static Snippet loadScript(
            Context context,
            String resourceName,
            TypeDescriptor type,
            ResultVerifier verifier) {
        try {
            Source src = createSource(resourceName);
            return Snippet.newBuilder(src.getName(), context.eval(src), type).resultVerifier(verifier).build();
        } catch (IOException ioe) {
            throw new AssertionError("IOException while creating a test script.", ioe);
        }
    }

    private static Source createSource(String resourceName) throws IOException {
        int slashIndex = resourceName.lastIndexOf('/');
        String scriptName = slashIndex >= 0 ? resourceName.substring(slashIndex + 1) : resourceName;
        Reader in = new InputStreamReader(LLVMTCKLanguageProvider.class.getResourceAsStream(resourceName), "UTF-8");
        return Source.newBuilder(ID, in, scriptName).build();
    }

    private static Value eval(Context context, String statement) {
        return context.eval(ID, statement);
    }


    final class IdentityFunctionResultVerifier implements ResultVerifier {
        ResultVerifier delegate = ResultVerifier.getIdentityFunctionDefaultResultVerifier();

        private IdentityFunctionResultVerifier() {
        }

        @Override
        public void accept(SnippetRun snippetRun) throws PolyglotException {
            for (Value p : snippetRun.getParameters()) {
                if (p.isNull()) {
                    return;
                }
            }
            delegate.accept(snippetRun);
        }
    }

    private static final class RResultVerifier implements ResultVerifier {
        /**
         * Declared is a superset of accepted; If a parameter is an object array, we declare it as
         * such, but a conversion to a fastr vector accepts it only of it contains homogenous values
         * of some specific type - e.g. new Object[] {Integer, Integer}
         *
         */
        private TypeDescriptor[] declaredParameterTypes;
        private TypeDescriptor[] acceptedParameterTypes;
        BiFunction<Boolean, SnippetRun, Void> next;

        private RResultVerifier(
                TypeDescriptor[] acceptedParameterTypes,
                TypeDescriptor[] declaredParameterTypes,
                BiFunction<Boolean, SnippetRun, Void> next) {
            this.acceptedParameterTypes = Objects.requireNonNull(acceptedParameterTypes, "The acceptedParameterTypes cannot be null.");
            this.declaredParameterTypes = declaredParameterTypes;
            this.next = Objects.requireNonNull(next, "The verifier chain cannot be null.");
        }

        @Override
        public void accept(SnippetRun snippetRun) throws PolyglotException {
            boolean hasValidArgumentTypes = isAssignable(acceptedParameterTypes, snippetRun.getParameters());
            List<? extends Value> args = snippetRun.getParameters();

            boolean hasValidDeclaredTypes = isAssignable(declaredParameterTypes, args);
            if (hasValidDeclaredTypes) {
                if (hasValidArgumentTypes) {
                    next.apply(hasValidArgumentTypes, snippetRun);
                } else {
                    return;
                }
            } else {
                next.apply(hasValidArgumentTypes, snippetRun);
            }
        }

        private static boolean isAssignable(TypeDescriptor[] types, List<? extends Value> args) {
            if (types == null) {
                return false;
            }
            for (int i = 0; i < types.length; i++) {
                if (!types[i].isAssignable(TypeDescriptor.forValue(args.get(i)))) {
                    return false;
                }
            }
            return true;
        }

        static Builder newBuilder(TypeDescriptor[] acceptedParameterTypes) {
            return new Builder(acceptedParameterTypes, null);
        }

        static Builder newBuilder(TypeDescriptor[] acceptedParameterTypes, TypeDescriptor[] declaredParameterTypes) {
            return new Builder(acceptedParameterTypes, declaredParameterTypes);
        }

        static final class Builder {
            private final TypeDescriptor[] acceptedParameterTypes;
            private final TypeDescriptor[] declaredParameterTypes;
            private BiFunction<Boolean, SnippetRun, Void> chain;

            private Builder(TypeDescriptor[] acceptedParameterTypes, TypeDescriptor[] declaredParameterTypes) {
                this.acceptedParameterTypes = acceptedParameterTypes;
                this.declaredParameterTypes = declaredParameterTypes;
                chain = (valid, snippetRun) -> {
                    ResultVerifier.getDefaultResultVerifier().accept(snippetRun);
                    return null;
                };
            }

            /**
             * Enables result verifier to handle empty arrays. Use this for R expressions,
             * statements which accept array but not an empty array
             *
             * @return the Builder
             */
            Builder emptyArrayCheck() {
                chain = new BiFunction<Boolean, SnippetRun, Void>() {
                    private final BiFunction<Boolean, SnippetRun, Void> next = chain;

                    @Override
                    public Void apply(Boolean valid, SnippetRun sr) {
                        if (valid && sr.getException() != null && hasEmptyArrayArg(sr.getParameters())) {
                            return null;
                        }
                        return next.apply(valid, sr);
                    }

                    private boolean hasEmptyArrayArg(List<? extends Value> args) {
                        for (Value arg : args) {
                            if (arg.hasArrayElements() && arg.getArraySize() == 0) {
                                return true;
                            }
                        }
                        return false;
                    }
                };
                return this;
            }

            // - not empty homogenous number, boolean or string arrays -> vector
            // - any other array -> list
            //
            // comparing:
            // - null with anything does not fail - logical(0)
            // - empty list or vector with anything does not fail - logical(0)
            // - atomic vectors does not fail
            // - a list with an atomic vector does not fail
            // - a list with another list FAILS
            // - other kind of object with anything but null or empty list or vector FAILS
            Builder compareParametersCheck() {
                chain = new BiFunction<Boolean, SnippetRun, Void>() {
                    private final BiFunction<Boolean, SnippetRun, Void> next = chain;

                    @Override
                    public Void apply(Boolean valid, SnippetRun sr) {
                        if (valid && sr.getException() != null && expectsException(sr.getParameters())) {
                            return null;
                        }
                        return next.apply(valid, sr);
                    }

                    private boolean expectsException(List<? extends Value> args) {
                        boolean parametersValid = false;
                        int mixed = 0;
                        for (Value arg : args) {
                            parametersValid = false;
                            if (arg.isNull()) {
                                // one of the given parameters is NULL
                                // this is never expected to fail
                                return false;
                            }
                            if (arg.isNumber() || arg.isString() || arg.isBoolean()) {
                                parametersValid = true;
                            } else if (arg.hasArrayElements()) {
                                if (arg.getArraySize() == 0) {
                                    // one of the given parameters is an emtpy list or vector,
                                    // this is never expected to fail
                                    return false;
                                } else {
                                    boolean str = false;
                                    boolean num = false;
                                    boolean other = false;
                                    for (int i = 0; i < arg.getArraySize(); i++) {
                                        TypeDescriptor td = TypeDescriptor.forValue(arg.getArrayElement(i));
                                        if (TypeDescriptor.STRING.isAssignable(td)) {
                                            str = true;
                                        } else if (TypeDescriptor.NUMBER.isAssignable(td) || TypeDescriptor.BOOLEAN.isAssignable(td)) {
                                            num = true;
                                        } else {
                                            other = true;
                                        }
                                    }
                                    parametersValid = !other;
                                    if (str && num) {
                                        mixed++;
                                    }
                                }
                            }
                            if (!parametersValid) {
                                break;
                            }
                        }
                        return !(parametersValid && mixed < args.size());
                    }
                };
                return this;
            }

            RResultVerifier build() {
                return new RResultVerifier(acceptedParameterTypes, declaredParameterTypes, chain);
            }
        }
    }


}
