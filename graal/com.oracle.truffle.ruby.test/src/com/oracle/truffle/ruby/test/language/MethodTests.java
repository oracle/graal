/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.test.language;

import org.junit.*;

import com.oracle.truffle.ruby.runtime.control.*;
import com.oracle.truffle.ruby.test.*;

/**
 * Test method definitions and calls.
 */
public class MethodTests extends RubyTests {

    @Test
    public void testDefineCallNoArguments() {
        assertPrints("1\n", "def foo; puts 1; end; foo()");
        assertPrints("1\n", "def foo; puts 1; end; foo");
    }

    @Test
    public void testDefineCallOnePreArgument() {
        assertPrints("1\n", "def foo(a); puts a; end; foo(1)");
        assertPrints("1\n", "def foo(a); puts a; end; foo 1");
    }

    @Test
    public void testDefineCallTwoPreArguments() {
        assertPrints("1\n2\n", "def foo(a, b); puts a; puts b; end; foo(1, 2)");
        assertPrints("1\n2\n", "def foo(a, b); puts a; puts b; end; foo 1, 2");
    }

    @Test
    public void testSingleReturn() {
        assertPrints("1\n", "def foo; return 1; end; puts foo");
        assertPrints("1\n", "def foo(n); return n; end; puts foo(1)");
    }

    @Test
    public void testImplicitReturn() {
        assertPrints("1\n", "def foo; 1; end; puts foo");
        assertPrints("3\n", "def foo; 1+2; end; puts foo");
        assertPrints("14\n", "def foo; x=14; end; puts foo");
    }

    @Test
    public void testNestedCall() {
        assertPrints("1\n", "def foo(n); return n; end; def bar(n); return foo(n); end; puts bar(1)");
        assertPrints("1\n1\n1\n", "def foo(n); puts n; return n; end; def bar(n); puts n; return foo(n); end; puts bar(1)");
        assertPrints("1\n1\n", "def foo(a, b); puts a; puts b; end; def bar(n); foo(n, n); end; bar(1)");
    }

    @Test
    public void testNestedOperatorCall() {
        assertPrints("3\n", "def foo(a, b); puts a + b; end; foo(1, 2)");
    }

    /**
     * Tests that arguments are evaluated before method dispatch takes place, by putting an action
     * in one of the arguments that modifies the method that we should find in dispatch.
     */
    @Test
    public void testArgumentsExecutedBeforeDispatch() {
        /*
         * We have to use Object#send instead of calling define_method directly because that method
         * is private.
         */

        assertPrints("12\n", "def foo\n" + //
                        "  Fixnum.send(:define_method, :+) do |other|\n" + //
                        "    self - other\n" + //
                        "   end \n" + //
                        "  2\n" + //
                        "end\n" + //
                        "puts 14 + foo");
    }

    @Test(expected = RaiseException.class)
    public void testTooFewArguments1() {
        assertPrints("", "def foo(a); end; foo()");
    }

    @Test(expected = RaiseException.class)
    public void testTooFewArguments2() {
        assertPrints("", "def foo(a, b); end; foo(1)");
    }

    @Test(expected = RaiseException.class)
    public void testTooFewArguments3() {
        assertPrints("", "def foo(a, b, c); end; foo(1, 2)");
    }

    @Test(expected = RaiseException.class)
    public void testTooManyArguments1() {
        assertPrints("", "def foo(); end; foo(1)");
    }

    @Test(expected = RaiseException.class)
    public void testTooManyArguments2() {
        assertPrints("", "def foo(a); end; foo(1, 2)");
    }

    @Test(expected = RaiseException.class)
    public void testTooManyArguments3() {
        assertPrints("", "def foo(a, b); end; foo(1, 2, 3)");
    }

    @Test
    public void testPolymophicMethod() {
        assertPrints("A\nB\n", "class A\n" + //
                        "    def foo\n" + //
                        "        puts \"A\"\n" + //
                        "    end\n" + //
                        "end\n" + //
                        "\n" + //
                        "class B\n" + //
                        "    def foo\n" + //
                        "        puts \"B\"\n" + //
                        "    end\n" + //
                        "end\n" + //
                        "\n" + //
                        "def bar(x)\n" + //
                        "    x.foo\n" + //
                        "end\n" + //
                        "\n" + //
                        "a = A.new\n" + //
                        "b = B.new\n" + //
                        "\n" + //
                        "bar(a)\n" + //
                        "bar(b)\n");
    }

    @Test
    public void testOneDefaultValue() {
        assertPrints("1\n2\n", "def foo(a=1); puts a; end; foo; foo(2)");
    }

    @Test
    public void testTwoDefaultValues() {
        assertPrints("1\n2\n3\n2\n3\n4\n", "def foo(a=1,b=2); puts a; puts b; end; foo; foo(3); foo(3, 4)");
    }

    @Test
    public void testOneDefaultValueAfterNonDefault() {
        assertPrints("2\n1\n2\n3\n", "def foo(a, b=1); puts a; puts b; end; foo(2); foo(2, 3)");
    }

    @Test
    public void testOneDefaultValueBeforeNonDefault() {
        assertPrints("1\n2\n2\n3\n", "def foo(a=1, b); puts a; puts b; end; foo(2); foo(2, 3)");
    }

    @Test
    public void testBlockArgument() {
        assertPrints("1\n2\n3\n", "def foo(&block); block.call(14); end; puts 1; foo { |n| puts 2 }; puts 3");
    }

    @Test
    public void testBlockArgumentWithOthers() {
        assertPrints("1\n2\n3\n4\n5\n", "def foo(a, b, &block); puts a; block.call(14); puts b; end; puts 1; foo(2, 4) { |n| puts 3 }; puts 5");
    }

    @Test
    public void testBlockPass() {
        assertPrints("1\n2\n3\n", "def bar; yield; end; def foo(&block); bar(&block); end; puts 1; foo { puts 2 }; puts 3");
    }

    @Test
    public void testBlockPassWithOthers() {
        assertPrints("1\n2\n3\n4\n5\n", "def bar(a, b); puts a; yield; puts b; end; def foo(a, b, &block); bar(a, b, &block); end; puts 1; foo(2, 4) { puts 3 }; puts 5");
    }

    @Test
    public void testSplatWhole() {
        assertPrints("1\n2\n3\n", "def foo(a, b, c); puts a; puts b; puts c; end; d = [1, 2, 3]; foo(*d)");
    }

    @Test
    public void testSplatSome() {
        assertPrints("1\n2\n3\n", "def foo(a, b, c); puts a; puts b; puts c; end; d = [2, 3]; foo(1, *d)");
    }

    @Test
    public void testSplatParam() {
        assertPrints("[1, 2, 3]\n", "def foo(*bar); puts bar.to_s; end; foo(1, 2, 3)");
    }

    @Test
    public void testSplatParamWithOther() {
        assertPrints("1\n[2]\n", "def foo(a, *b); puts a.to_s; puts b.to_s; end; foo(1, 2)");
    }

    @Test
    public void testSplatParamWithBlockPass() {
        assertPrints("[1, 2, 3]\n", "def foo(*bar, &block); block.call(bar); end; foo(1, 2, 3) { |x| puts x.to_s }");
    }

    @Test
    public void testSingletonMethod() {
        assertPrints("1\n", "foo = Object.new; def foo.bar; puts 1; end; foo.bar");
    }

    @Test
    public void testAlias() {
        assertPrints("1\n", "def foo; puts 1; end; alias bar foo; bar");
        assertPrints("1\n", "class Foo; def foo; puts 1; end; alias bar foo; end; Foo.new.bar");
    }

    @Test
    public void testAliasMethod() {
        assertPrints("1\n", "class Foo; def foo; puts 1; end; alias_method :bar, :foo; end; Foo.new.bar");
    }

    @Test
    public void testSymbolAsBlock() {
        assertPrints("6\n", "puts [1, 2, 3].inject(&:+)");
    }

    @Test
    public void testSuper() {
        assertPrints("1\n2\n3\n", "class Foo; def foo; puts 2; end; end; class Bar < Foo; def foo; puts 1; super; puts 3; end; end; Bar.new.foo");
    }

    @Test
    public void testSuperWithArgs() {
        assertPrints("1\n2\n3\n", "class Foo; def foo(n); puts n; end; end; class Bar < Foo; def foo(n); puts 1; super(n); puts 3; end; end; Bar.new.foo(2)");
    }

    @Test
    public void testPushSplat() {
        assertPrints("[1, 0, 4]\n", "a = [1, 2, 3, 4]; b = [1, 2]; a[*b] = 0; puts a.to_s");
    }

    @Test
    public void testBlocksPassedIntoBlocks() {
        assertPrints("14\n", "def foo; 1.times do; yield; end; end; foo do; puts 14; end");
    }

    @Test
    public void testBlocksNotPassedIntoFullMethods() {
        assertPrints("no block\n", "def foo(&block); if block; puts 'block'; else; puts 'no block'; end; end; def bar; foo; end; bar do; end");
    }

}
