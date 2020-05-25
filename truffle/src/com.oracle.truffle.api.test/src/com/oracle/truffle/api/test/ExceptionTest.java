/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.truffle.api.test;

import com.oracle.truffle.api.AbstractTruffleException;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Test;

public class ExceptionTest {

    @Test
    public void test() {
        try (Context ctx = Context.create()) {
            Source src = Source.newBuilder(ExceptionTestLanguage.ID, "", "test.txt").buildLiteral();
            ctx.eval(src);
        }
    }


    @TruffleLanguage.Registration(id = ExceptionTestLanguage.ID, name=ExceptionTestLanguage.ID, characterMimeTypes = ExceptionTestLanguage.MIME)
    public static final class ExceptionTestLanguage extends TruffleLanguage<TruffleLanguage.Env> {
        public static final String ID = "ExceptionTestLanguage";
        public static final String MIME = "x-text/exceptiontestlanguage";

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return Truffle.getRuntime().createCallTarget(new MainRoot(this, request.getSource().createSection(1)));
        }
    }

    private static final class MainRoot extends RootNode {

        private final SourceSection sourceSection;
        private final DirectCallNode callNode;

        MainRoot(TruffleLanguage<?> language, SourceSection sourceSection) {
            super(language);
            this.sourceSection = sourceSection;
            this.callNode = Truffle.getRuntime().createDirectCallNode(Truffle.getRuntime().createCallTarget(new FncRoot(language, sourceSection)));
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return callNode.call();
        }

        @Override
        public SourceSection getSourceSection() {
            return sourceSection;
        }

        @Override
        public String getName() {
            return "main";
        }
    }

    private static final class FncRoot extends RootNode {

        private final SourceSection sourceSection;
        private final ThrowNode throwNode;

        FncRoot(TruffleLanguage<?> language, SourceSection sourceSection) {
            super(language);
            this.sourceSection = sourceSection;
            this.throwNode = new ThrowNode(sourceSection);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return throwNode.execute(frame);
        }

        @Override
        public SourceSection getSourceSection() {
            return sourceSection;
        }

        @Override
        public String getName() {
            return "fnc";
        }
    }

    private static final class ThrowNode extends Node {

        private final SourceSection sourceSection;

        ThrowNode(SourceSection sourceSection) {
            this.sourceSection = sourceSection;
        }


        Object execute(VirtualFrame frame) {
            throw createException();
        }

        private RuntimeException createException() {
            return new TestException(this);
        }

        @Override
        public SourceSection getSourceSection() {
            return sourceSection;
        }
    }

    @SuppressWarnings("serial")
    private static final class TestException extends AbstractTruffleException {

        private final Node source;

        public TestException(Node source) {
            this.source = source;
        }

        @Override
        public Node getLocation() {
            return source;
        }
    }
}