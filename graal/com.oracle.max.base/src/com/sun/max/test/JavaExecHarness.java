/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.sun.max.test;

import java.io.*;
import java.lang.reflect.*;
import java.text.*;
import java.util.*;

import com.sun.max.lang.*;
import com.sun.max.program.*;

public class JavaExecHarness implements TestHarness<JavaExecHarness.JavaTestCase> {

    private static final char SQUOTE = '\'';
    private static final char BACKSLASH = '\\';
    private static final char QUOTE = '"';
    private static final String ESCAPED_QUOTE = "\\\"";

    private final Executor executor;

    public class CodeLiteral {
        public String codeLiteral;
        CodeLiteral(String codeLiteral) {
            this.codeLiteral = codeLiteral;
        }
        @Override
        public String toString() {
            return codeLiteral;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof CodeLiteral)) {
                return resolve().equals(obj);
            }
            return super.equals(obj);
        }

        public Object resolve() {
            String s = codeLiteral;
            String className = s.substring(0, s.lastIndexOf('.'));
            String fieldName = s.substring(s.lastIndexOf('.') + 1);
            Class klass;
            try {
                klass = Class.forName(className);
                return klass.getField(fieldName).get(null);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException(e);
            } catch (SecurityException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public class MethodCall extends CodeLiteral {
        MethodCall(String codeCall) {
            super(codeCall);
        }
    }

    public interface Executor {
        void initialize(JavaTestCase testCase, boolean loadingPackages);

        Object execute(JavaTestCase c, Object[] vals) throws InvocationTargetException;
    }

    public JavaExecHarness(Executor exec) {
        executor = exec;
    }

    public static class Run {
        public final Object[] input;
        public final Object expectedValue;
        public final Class<? extends Throwable> expectedException;

        Object returnVal;
        Throwable returnExc;
        Throwable thrown;

        Run(Object[] input, Object expected, Class<? extends Throwable> expectedException) {
            this.input = input;
            expectedValue = expected;
            this.expectedException = expectedException;
        }
    }

    public class JavaTestCase extends TestCase {
        public final Class clazz;
        public final List<Run> runs;
        public final Executor exec;
        public final String execName;

        public Object slot1;
        public Object slot2;

        protected JavaTestCase(String execName, Executor executor, File file, Class testClass, List<Run> runs, boolean loadingPackages) {
            super(JavaExecHarness.this, file, null);
            this.execName = execName;
            exec = executor;
            this.runs = runs;
            clazz = testClass;
            executor.initialize(this, loadingPackages);
        }

        @Override
        public void run() throws Throwable {
            for (Run run : runs) {
                try {
                    run.returnVal = exec.execute(this, run.input);
                } catch (InvocationTargetException t) {
                    run.returnExc = t.getTargetException();
                } catch (Throwable t) {
                    run.thrown = t;
                }
            }
        }
    }

    public static class ExecFailure extends TestResult.Failure {
        protected final Run run;
        protected final String result;
        protected final String expect;
        protected ExecFailure(Run run, String result) {
            this.run = run;
            this.expect = resultToString(run.expectedValue, run.expectedException);
            this.result = result;
        }
        @Override
        public String failureMessage(TestCase testCase) {
            final JavaTestCase javaTestCase = (JavaTestCase) testCase;
            return inputToString(javaTestCase.clazz, run, false) + " failed with " + result + " (expected " + expect + ")";
        }

    }

    public TestResult evaluateTest(TestEngine engine, JavaTestCase testCase) {
        if (testCase.thrown != null) {
            return new TestResult.UnexpectedException(testCase.thrown);
        }
        for (Run run : testCase.runs) {
            if (run.thrown != null) {
                return new ExecFailure(run, "unexpected " + run.thrown.getClass().getName() + " (\"" + run.thrown.getMessage() + "\")");
            }
            final String result = valueToString(run.returnVal, run.returnExc);
            if (run.expectedException != null) {
                if (run.returnExc == null || run.returnExc.getClass() != run.expectedException) {
                    return new ExecFailure(run, result);
                }
            } else if (run.returnExc != null) {
                return new ExecFailure(run, result);
            } else if (run.expectedValue == null) {
                if (run.returnVal != null) {
                    return new ExecFailure(run, result);
                }
            } else if (!run.expectedValue.equals(run.returnVal)) {
                return new ExecFailure(run, result);
            }
        }
        return TestResult.SUCCESS;
    }

    public void parseTests(TestEngine engine, File file, Properties props) {
        try {
            // 1. find the class
            final Class testClass = findClass(file, props);
            // 2. parse the runs
            final List<Run> runs = parseRuns(testClass, file, props);
            if (runs != null) {
                // 3. add a test case to the engine
                engine.addTest(new JavaTestCase("exec", executor, file, testClass, runs, engine.loadingPackages()));
            } else {
                engine.skipFile(file);
            }
        } catch (Exception e1) {
            throw ProgramError.unexpected(e1);
        }
    }

    private Class findClass(File file, Properties props) throws Exception {
        final BufferedReader r = new BufferedReader(new FileReader(file));

        // search for the package statement in the file.
        String line = r.readLine();
        for (; line != null; line = r.readLine()) {
            line = line.trim();
            if (line.startsWith("package")) {
                // this is probably a java file
                r.close();
                int indx = line.indexOf(' ');
                while (line.charAt(indx) == ' ') {
                    indx++;
                }
                final String packageName = line.substring(indx, line.indexOf(';'));
                String className = file.getName();
                if (className.endsWith(".java")) {
                    className = className.substring(0, className.length() - ".java".length());
                }
                // use the package name plus the name of the file to load the class.
                return Class.forName(packageName + "." + className);
            }
            if (line.startsWith(".class")) {
                // this is probably a jasm file
                String[] tokens = line.split(" ");
                String className = null;
                for (String s : tokens) {
                    if (!".class".equals(s) && !"public".equals(s) && !"abstract".equals(s)) {
                        className = s;
                        break;
                    }
                }
                return Class.forName(className.replace('/', '.'));
            }
        }
        r.close();
        throw ProgramError.unexpected("could not find package statement in " + file);
    }

    private List<Run> parseRuns(Class testClass, File file, Properties props) {
        final String rstr = props.getProperty("Runs");
        if (rstr == null) {
            return null;
        }
        final List<Run> runs = new LinkedList<Run>();
        final CharacterIterator i = new StringCharacterIterator(rstr);
        while (i.getIndex() < i.getEndIndex()) {
            runs.add(parseRun(i));
            if (!skipPeekAndEat(i, ';')) {
                break;
            }
        }
        return runs;
    }

    private Run parseRun(CharacterIterator iterator) {
        // parses strings of the form:
        // ()=value
        // (value,...)=result
        // value=result
        Object[] vals = new Object[1];
        if (skipPeekAndEat(iterator, '(')) {
            final List<Object> inputValues = new LinkedList<Object>();
            if (!skipPeekAndEat(iterator, ')')) {
                while (true) {
                    inputValues.add(parseValue(iterator));
                    if (!skipPeekAndEat(iterator, ',')) {
                        break;
                    }
                }
                skipPeekAndEat(iterator, ')');
            }
            vals = inputValues.toArray(vals);
        } else {
            vals[0] = parseValue(iterator);
        }
        skipPeekAndEat(iterator, '=');
        if (skipPeekAndEat(iterator, '!')) {
            return new Run(vals, null, parseException(iterator));
        }
        return new Run(vals, parseValue(iterator), null);
    }

    private Object parseValue(CharacterIterator iterator) {
        // parses strings of the form:
        // <integer> | <long> | <string> | true | false | null
        skipWhitespace(iterator);
        if (iterator.current() == '-' || Character.isDigit(iterator.current())) {
            // parse a number.
            return parseNumber(iterator);
        } else if (iterator.current() ==  QUOTE) {
            // a string constant.
            return parseStringLiteral(iterator);
        } else if (peekAndEat(iterator, "true")) {
            // the boolean value (true)
            return Boolean.TRUE;
        } else if (peekAndEat(iterator, "false")) {
            // the boolean value (false)
            return Boolean.FALSE;
        } else if (peekAndEat(iterator, "null")) {
            // the null value (null)
            return null;
        } else if (iterator.current() == '`') {
            expectChar(iterator, '`');
            return new CodeLiteral(parseCodeLiteral(iterator));
        } else if (iterator.current() == '(') {
            expectChar(iterator, '(');
            expectChar(iterator, ')');
            return new MethodCall(parseCodeLiteral(iterator));
        }
        throw ProgramError.unexpected("invalid value at " + iterator.getIndex());
    }

    private ProgramError raiseParseErrorAt(String message, CharacterIterator iterator) {
        final int errorIndex = iterator.getIndex();
        final StringBuilder sb = new StringBuilder(message).append(String.format(":%n"));
        iterator.setIndex(iterator.getBeginIndex());
        for (char ch = iterator.current(); ch != CharacterIterator.DONE; ch = iterator.next()) {
            sb.append(ch);
        }
        sb.append(String.format("%n"));
        for (int i = 0; i < errorIndex; ++i) {
            sb.append(' ');
        }
        sb.append('^');
        throw ProgramError.unexpected(sb.toString());
    }

    private Object parseNumber(CharacterIterator iterator) {
        // an integer.
        final StringBuilder buf = new StringBuilder();

        if (iterator.current() == '-') {
            buf.append('-');
            iterator.next();
        }

        int radix = 10;
        if (iterator.current() == '0') {
            radix = 8;
            iterator.next();
            if (iterator.current() == 'x' || iterator.current() == 'X') {
                radix = 16;
                iterator.next();
            } else if (Character.digit(iterator.current(), 8) == -1) {
                radix = 10;
                buf.append('0');
            }
        }
        appendDigits(buf, iterator, radix);

        if (peekAndEat(iterator, '.')) {
            if (radix != 10) {
                raiseParseErrorAt("Cannot have decimal point in number with radix " + radix, iterator);
            }
            // parse the fractional suffix of a float or double
            buf.append('.');
            appendDigits(buf, iterator, radix);
            if (peekAndEat(iterator, 'f') || peekAndEat(iterator, "F")) {
                return Float.valueOf(buf.toString());
            }
            if (peekAndEat(iterator, 'd') || peekAndEat(iterator, "D")) {
                return Double.valueOf(buf.toString());
            }
            return Float.valueOf(buf.toString());
        }
        if (radix == 10) {
            if (peekAndEat(iterator, 'f') || peekAndEat(iterator, "F")) {
                return Float.valueOf(buf.toString());
            }
            if (peekAndEat(iterator, 'd') || peekAndEat(iterator, "D")) {
                return Double.valueOf(buf.toString());
            }
            if (peekAndEat(iterator, 's') || peekAndEat(iterator, "S")) {
                return Short.valueOf(buf.toString());
            }
            if (peekAndEat(iterator, 'b') || peekAndEat(iterator, "B")) {
                return Byte.valueOf(buf.toString());
            }
            if (peekAndEat(iterator, 'c') || peekAndEat(iterator, "C")) {
                return Character.valueOf((char) Integer.valueOf(buf.toString()).intValue());
            }
        }
        if (peekAndEat(iterator, 'l') || peekAndEat(iterator, "L")) {
            return Long.valueOf(buf.toString(), radix);
        }
        return Integer.valueOf(buf.toString(), radix);
    }

    private void appendDigits(final StringBuilder buf, CharacterIterator iterator, int radix) {
        while (Character.digit(iterator.current(), radix) != -1) {
            buf.append(iterator.current());
            iterator.next();
        }
    }

    private Class<? extends Throwable> parseException(CharacterIterator iterator) {
        final String exceptionName = parseCodeLiteral(iterator);
        try {
            return Class.forName(exceptionName).asSubclass(Throwable.class);
        } catch (ClassNotFoundException e) {
            throw raiseParseErrorAt("Unknown exception type", iterator);
        }
    }

    private String parseCodeLiteral(CharacterIterator iterator) {
        final StringBuilder buf = new StringBuilder();
        while (true) {
            final char ch = iterator.current();
            if (Character.isJavaIdentifierPart(ch) || ch == '.') {
                buf.append(ch);
                iterator.next();
            } else {
                break;
            }
        }
        return buf.toString();
    }

    private boolean skipPeekAndEat(CharacterIterator iterator, char c) {
        skipWhitespace(iterator);
        return peekAndEat(iterator, c);
    }

    private boolean peekAndEat(CharacterIterator iterator, char c) {
        if (iterator.current() == c) {
            iterator.next();
            return true;
        }
        return false;
    }

    private boolean peekAndEat(CharacterIterator iterator, String string) {
        final int indx = iterator.getIndex();
        for (int j = 0; j < string.length(); j++) {
            if (iterator.current() != string.charAt(j)) {
                iterator.setIndex(indx);
                return false;
            }
            iterator.next();
        }
        return true;
    }

    private void skipWhitespace(CharacterIterator iterator) {
        while (true) {
            if (!Character.isWhitespace(iterator.current())) {
                break;
            }
            iterator.next();
        }
    }

    private void expectChar(CharacterIterator i, char c) {
        final char r = i.current();
        i.next();
        if (r != c) {
            throw ProgramError.unexpected("parse error at " + i.getIndex() + ", expected character '" + c + "'");
        }
    }

    private char parseCharLiteral(CharacterIterator i) throws Exception {

        expectChar(i, SQUOTE);

        char ch;
        if (peekAndEat(i, BACKSLASH)) {
            ch = parseEscapeChar(i);
        } else {
            ch = i.current();
            i.next();
        }

        expectChar(i, SQUOTE);

        return ch;
    }

    private char parseEscapeChar(CharacterIterator i) {
        final char c = i.current();
        switch (c) {
            case 'f':
                i.next();
                return '\f';
            case 'b':
                i.next();
                return '\b';
            case 'n':
                i.next();
                return '\n';
            case 'r':
                i.next();
                return '\r';
            case BACKSLASH:
                i.next();
                return BACKSLASH;
            case SQUOTE:
                i.next();
                return SQUOTE;
            case QUOTE:
                i.next();
                return QUOTE;
            case 't':
                i.next();
                return '\t';
            case 'x':
                return (char) readHexValue(i, 4);
            case '0': // fall through
            case '1': // fall through
            case '2': // fall through
            case '3': // fall through
            case '4': // fall through
            case '5': // fall through
            case '6': // fall through
            case '7':
                return (char) readOctalValue(i, 3);

        }
        return c;
    }

    private String parseStringLiteral(CharacterIterator i) {
        final StringBuilder buffer = new StringBuilder(i.getEndIndex() - i.getBeginIndex() + 1);

        expectChar(i, QUOTE);
        while (true) {
            if (peekAndEat(i, QUOTE)) {
                break;
            }
            char c = i.current();
            i.next();

            if (c == CharacterIterator.DONE) {
                break;
            }
            if (c == BACKSLASH) {
                c = parseEscapeChar(i);
            }

            buffer.append(c);
        }

        return buffer.toString();
    }

    public static int readHexValue(CharacterIterator i, int maxchars) {
        int accumul = 0;

        for (int cntr = 0; cntr < maxchars; cntr++) {
            final char c = i.current();

            if (c == CharacterIterator.DONE || !Chars.isHexDigit(c)) {
                break;
            }

            accumul = (accumul << 4) | Character.digit(c, 16);
            i.next();
        }

        return accumul;
    }

    public static int readOctalValue(CharacterIterator i, int maxchars) {
        int accumul = 0;

        for (int cntr = 0; cntr < maxchars; cntr++) {
            final char c = i.current();

            if (!Chars.isOctalDigit(c)) {
                break;
            }

            accumul = (accumul << 3) | Character.digit(c, 8);
            i.next();
        }

        return accumul;
    }

    public static String inputToString(Class testClass, Run run, boolean asJavaString) {
        final StringBuilder buffer = new StringBuilder();
        if (asJavaString) {
            buffer.append(QUOTE);
        }
        buffer.append("(");
        for (int i = 0; i < run.input.length; i++) {
            if (i > 0) {
                buffer.append(',');
            }
            final Object val = run.input[i];
            if (val instanceof Character) {
                buffer.append(Chars.toJavaLiteral((Character) val));
            } else if (val instanceof String) {
                buffer.append(asJavaString ? ESCAPED_QUOTE : QUOTE);
                buffer.append(val);
                buffer.append(asJavaString ? ESCAPED_QUOTE : QUOTE);
            } else {
                buffer.append(String.valueOf(val));
            }
        }
        buffer.append(')');
        if (asJavaString) {
            buffer.append(QUOTE);
        }
        return buffer.toString();
    }

    public static String resultToString(Object val, Class<? extends Throwable> throwable) {
        if (throwable != null) {
            return "!" + throwable.getName();
        }
        if (val instanceof Character) {
            return Chars.toJavaLiteral((Character) val);
        }
        if (val instanceof String) {
            return "\"" + val + "\"";
        }
        return String.valueOf(val);
    }

    public static String valueToString(Object val, Throwable thrown) {
        if (thrown == null) {
            return resultToString(val, null);
        }
        return resultToString(val, thrown.getClass()) + "(" + thrown.getMessage() + ")";
    }
}
