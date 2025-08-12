#
# Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
# Copyright (c) 2020, 2020, Red Hat Inc. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#
# ----------------------------------------------------------------------------------------------------
# pylint: skip-file
#
# A test script for use from gdb. It can be used to drive execution of
# a native image version of test app Hello and check that the debug
# info is valid.
#
# Assumes you have already executed
#
# $ javac hello/Hello.java
# $ mx native-image -g hello.hello
#
# Run test
#
# gdb -x gdb_utils.py -x testhello.py /path/to/hello
#
# exit status 0 means all is well 1 means test failed
#
# n.b. assumes the sourcefile cache is in local dir sources
#
# Note that the helper routines defined in gdb_utils.py are loaded
# using gdb -x rather than being imported. That avoids having to update
# PYTHON_PATH which gdb needs to use to locate any imported code.
#

import re
import sys
import os

# add test directory to path to allow import of gdb_utils.py
sys.path.insert(0, os.path.join(os.path.dirname(os.path.realpath(__file__))))
from gdb_utils import *

# Configure this gdb session

configure_gdb()


def test():
    # define some useful constants
    main_start = 237
    main_noinline = main_start + 17
    main_inlinefrom = main_start + 18
    match = match_gdb_version()
    # n.b. can only get back here with one match
    major = int(match.group(1))
    minor = int(match.group(2))
    print(f"Found gdb version {major}.{minor}")

    musl = os.environ.get('debuginfotest_musl', 'no') == 'yes'

    isolates = os.environ.get('debuginfotest_isolates', 'no') == 'yes'

    arch = os.environ.get('debuginfotest_arch', 'amd64')

    print(f"Testing with isolates {'enabled' if isolates else 'disabled'}!")

    # disable prompting to continue output
    execute("set pagination off")
    # enable pretty printing of structures
    execute("set print pretty on")
    # enable demangling of symbols in assembly code listings
    execute("set print asm-demangle on")
    # disable printing of address symbols
    execute("set print symbol off")

    exec_string = execute("ptype _objhdr")
    has_reserved_field = "reserved;" in exec_string

    # Print DefaultGreeter and check the modifiers of its methods and fields
    exec_string = execute("ptype 'hello.Hello$DefaultGreeter'")
    rexp = [r"type = class hello\.Hello\$DefaultGreeter : public hello\.Hello\$Greeter {",
            fr"{spaces_pattern}public:",
            fr"{spaces_pattern}void greet\((void)?\);",
            fr"{spaces_pattern}int hashCode\((void)?\);",
            r"}"]
    checker = Checker("ptype 'hello.Hello$DefaultGreeter'", rexp)
    checker.check(exec_string)

    # run to main so the program image is loaded
    exec_string = execute("break main")
    print(exec_string)
    exec_string = execute("run")
    print(exec_string)
    exec_string = execute("delete breakpoints")
    print(exec_string)

    # First thing to check is the arguments passed to the CEntryPoint
    # routine that enters Java
    # Find a method whose name starts with JavaMainWrapper_run_
    # It should belong to class IsolateEnterStub
    exec_string = execute("info func JavaMainWrapper_run_")
    rexp = fr"{digits_pattern}:{spaces_pattern}int com.oracle.svm.core.code.IsolateEnterStub::(JavaMainWrapper_run_{wildcard_pattern})\({wildcard_pattern}\);"
    checker = Checker('info func JavaMainWrapper_run_', rexp)
    matches = checker.check(exec_string)
    # n.b can ony get here with one match
    match = matches[0]
    method_name = match.group(1)
    print(f"method_name = {method_name}")

    ## Now find the method start addess and break it
    command = f"x/i 'com.oracle.svm.core.code.IsolateEnterStub'::{method_name}"
    exec_string = execute(command)
    rexp = fr"{wildcard_pattern}0x({hex_digits_pattern}){wildcard_pattern}com.oracle.svm.core.code.IsolateEnterStub::JavaMainWrapper_run_{wildcard_pattern}"
    checker = Checker(f'x/i IsolateEnterStub::{method_name}', rexp)
    matches = checker.check(exec_string)
    # n.b can ony get here with one match
    match = matches[0]

    bp_address = int(match.group(1), 16)
    print(f"bp = {match.group(1)} {bp_address:x}")
    exec_string = execute(f"x/i 0x{bp_address:x}")
    print(exec_string)

    # exec_string = execute("break hello.Hello::noInlineManyArgs")
    exec_string = execute(f"break *0x{bp_address:x}")
    rexp = fr"Breakpoint {digits_pattern} at {address_pattern}: file com/oracle/svm/core/code/IsolateEnterStub.java, line 1\."
    checker = Checker(fr"break *0x{bp_address:x}", rexp)
    checker.check(exec_string)

    # run to breakpoint then delete it
    execute("run")
    execute("delete breakpoints")

    # check incoming parameters are bound to sensible values
    exec_string = execute("info args")
    rexp = [fr"__int0 = {digits_pattern}",
            fr"__long1 = 0x{hex_digits_pattern}"]
    checker = Checker(f"info args : {method_name}", rexp)
    checker.check(exec_string)

    exec_string = execute("p __int0")
    rexp = [fr"\${digits_pattern} = 1"]
    checker = Checker("p __int0", rexp)
    checker.check(exec_string)

    exec_string = execute("p __long1")
    rexp = [fr"\${digits_pattern} = \(org\.graalvm\.nativeimage\.c\.type\.CCharPointerPointer\) 0x{hex_digits_pattern}"]
    checker = Checker("p __long1", rexp)
    checker.check(exec_string)

    exec_string = execute("p __long1[0]")
    rexp = [
        fr'\${digits_pattern} = \(org\.graalvm\.nativeimage\.c\.type\.CCharPointer\) 0x{hex_digits_pattern} "{wildcard_pattern}/hello_image"']
    checker = Checker("p __long1[0]", rexp)
    checker.check(exec_string)

    # set a break point at hello.Hello::main
    # expect "Breakpoint <n> at 0x[0-9a-f]+: file hello.Hello.java, line <main_start>."
    exec_string = execute("break hello.Hello::main")
    rexp = fr"Breakpoint {digits_pattern} at {address_pattern}: file hello/Hello\.java, line {main_start:d}\."
    checker = Checker('break main', rexp)
    checker.check(exec_string)

    # continue the program until the breakpoint
    execute("run")
    execute("delete breakpoints")

    # list the line at the breakpoint
    # expect "<main_start>	        Greeter greeter = Greeter.greeter(args);"
    exec_string = execute("list")
    checker = Checker(r"list bp 1", f"{main_start:d}{spaces_pattern}Greeter greeter = Greeter\.greeter\(args\);")
    checker.check(exec_string, skip_fails=False)

    # run a backtrace
    # expect "#0  hello.Hello.main(java.lang.String[] *).* at hello.Hello.java:%d"
    # expect "#1  0x[0-9a-f]+ in com.oracle.svm.core.code.IsolateEnterStub.JavaMainWrapper_run_.* at [a-z/]+/JavaMainWrapper.java:[0-9]+"
    exec_string = execute("backtrace")
    stacktrace_regex = [
        fr"#0{spaces_pattern}hello\.Hello::main{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:{main_start:d}",
        fr"#1{spaces_pattern}({address_pattern} in )?java\.lang\.invoke\.LambdaForm\$DMH/s{hex_digits_pattern}::invokeStatic(Init)?{param_types_pattern} {arg_values_pattern}( at java/lang/invoke/{package_file_pattern}:[0-9]+)?",
        fr"#2{spaces_pattern}({address_pattern} in )?com\.oracle\.svm\.core\.JavaMainWrapper::invokeMain{param_types_pattern} {arg_values_pattern} at {package_pattern}JavaMainWrapper\.java:[0-9]+",
        fr"#3{spaces_pattern}({address_pattern} in )?com\.oracle\.svm\.core\.JavaMainWrapper::runCore0{no_param_types_pattern} {no_arg_values_pattern} at {package_pattern}JavaMainWrapper\.java:[0-9]+",
        fr"#4{spaces_pattern}{address_pattern} in com\.oracle\.svm\.core\.JavaMainWrapper::runCore{no_param_types_pattern} {no_arg_values_pattern} at {package_pattern}JavaMainWrapper\.java:[0-9]+",
        fr"#5{spaces_pattern}com\.oracle\.svm\.core\.JavaMainWrapper::doRun{param_types_pattern} {arg_values_pattern} at {package_pattern}JavaMainWrapper\.java:[0-9]+",
        fr"#6{spaces_pattern}({address_pattern} in )?com\.oracle\.svm\.core\.JavaMainWrapper::run{param_types_pattern} {arg_values_pattern} at {package_pattern}JavaMainWrapper\.java:[0-9]+",
        fr"#7{spaces_pattern}({address_pattern} in )?com\.oracle\.svm\.core\.code\.IsolateEnterStub::JavaMainWrapper_run_{varname_pattern}{param_types_pattern} {arg_values_pattern}"
    ]
    if musl:
        # musl has a different entry point - drop the last two frames
        stacktrace_regex = stacktrace_regex[:-2]
    checker = Checker("backtrace hello.Hello::main", stacktrace_regex)
    checker.check(exec_string, skip_fails=False)

    # check input argument args is known
    exec_string = execute("info args")
    rexp = [fr"args = {address_pattern}"]
    checker = Checker("info args", rexp)
    checker.check(exec_string)

    # check local var greeter is not known
    exec_string = execute("info locals")
    rexp = [r"greeter = <optimized out>"]
    checker = Checker("info locals", rexp)
    checker.check(exec_string)

    # print the contents of the arguments array which will be in rdi
    exec_string = execute("print /x *args")
    rexp = [fr"{wildcard_pattern} = {{",
            fr"{spaces_pattern}<java.lang.Object> = {{",
            fr"{spaces_pattern}<_objhdr> = {{",
            fr"{spaces_pattern}hub = {address_pattern}",
            fr"{spaces_pattern}reserved = {address_pattern}" if has_reserved_field else None,
            fr"{spaces_pattern}}}, <No data fields>}}, ",
            fr"{spaces_pattern}members of java\.lang\.String\[\]:",
            fr"{spaces_pattern}len = 0x0,",
            fr"{spaces_pattern}data = {address_pattern}",
            "}"]

    checker = Checker("print String[] args", rexp)

    checker.check(exec_string, skip_fails=False)

    # print the hub of the array and check it has a name field
    exec_string = execute("print /x *args->hub")
    rexp = [fr"{wildcard_pattern} = {{",
            fr"{spaces_pattern}<java.lang.Class> = {{",
            fr"{spaces_pattern}<java.lang.Object> = {{",
            fr"{spaces_pattern}<_objhdr> = {{",
            fr"{spaces_pattern}hub = {address_pattern}",
            fr"{spaces_pattern}reserved = {address_pattern}" if has_reserved_field else None,
            fr"{spaces_pattern}}}, <No data fields>}},",
            fr"{spaces_pattern}members of java\.lang\.Class:",
            fr"{spaces_pattern}name = {address_pattern},",
            fr"{spaces_pattern}}}, <No data fields>}}"]

    checker = Checker("print String[] hub", rexp)

    checker.check(exec_string, skip_fails=True)

    # print the hub name field and check it is String[]
    # n.b. the expected String text is not necessarily null terminated
    # so we need a wild card before the final quote
    exec_string = execute("x/s args->hub->name->value->data")
    checker = Checker("print String[] hub name",
                          fr"{address_pattern}:{spaces_pattern}\"\[Ljava.lang.String;{wildcard_pattern}\"")
    checker.check(exec_string, skip_fails=False)

    # ensure we can reference static fields
    exec_string = execute("print 'java.math.BigDecimal'::BIG_TEN_POWERS_TABLE")
    rexp = fr"{wildcard_pattern} = \({compressed_pattern if isolates else ''}java.math.BigInteger\[\] \*\) {address_pattern}"

    checker = Checker("print static field value", rexp)
    checker.check(exec_string, skip_fails=False)

    # ensure we can dereference static fields
    exec_string = execute("print 'java.math.BigDecimal'::BIG_TEN_POWERS_TABLE->data[3]->mag->data[0]")
    checker = Checker("print static field value contents",
                          fr"{wildcard_pattern} = 1000")
    checker.check(exec_string, skip_fails=False)

    # ensure we can print class constants
    exec_string = execute("print /x 'hello.Hello.class'")
    rexp = [fr"{wildcard_pattern} = {{",
            fr"{spaces_pattern}<java.lang.Object> = {{",
            fr"{spaces_pattern}<_objhdr> = {{",
            fr"{spaces_pattern}hub = {address_pattern}",
            fr"{spaces_pattern}reserved = {address_pattern}" if has_reserved_field else None,
            fr"{spaces_pattern}}}, <No data fields>}},",
            fr"{spaces_pattern}members of java\.lang\.Class:",
            fr"{spaces_pattern}name = {address_pattern},",
            "}"]

    checker = Checker("print hello.Hello.class", rexp)

    checker.check(exec_string, skip_fails=True)

    # ensure we can access fields of class constants
    exec_string = execute("print 'java.lang.String[].class'.name->value->data")
    rexp = fr'{wildcard_pattern} = {address_pattern} "\[Ljava.lang.String;'

    checker = Checker("print 'java.lang.String[].class'.name->value->data", rexp)

    checker.check(exec_string)

    exec_string = execute("print 'long.class'.name->value->data")
    rexp = fr'{wildcard_pattern} = {address_pattern} "long'

    checker = Checker("print 'long.class'.name->value->data", rexp)

    checker.check(exec_string)

    exec_string = execute("print 'byte[].class'.name->value->data")
    rexp = fr'{wildcard_pattern} = {address_pattern} "\[B'

    checker = Checker("print 'byte[].class'.name->value->data", rexp)

    checker.check(exec_string)

    # look up greet methods
    # expect "All functions matching regular expression "greet":"
    # expect ""
    # expect "File hello/Hello.java:"
    # expect "      ....greeter(...);"
    # expect "      hello.Hello$NamedGreeter *hello.Hello$Greeter::greet();"
    # expect ""
    # expect "File hello/target_hello_Hello_DefaultGreeter.java:"
    # expect "      hello.Hello$DefaultGreeter *hello.Hello$Greeter::greet();"
    exec_string = execute("info func greet")
    rexp = [r'All functions matching regular expression "greet":',
            r"File hello/Hello\.java:",
            fr"72:{maybe_spaces_pattern}void hello.Hello\$NamedGreeter::greet\({wildcard_pattern}\);",
            r"File hello/Target_hello_Hello_DefaultGreeter\.java:",
            fr"48:{maybe_spaces_pattern}void hello.Hello\$DefaultGreeter::greet\({wildcard_pattern}\);"]
    checker = Checker("info func greet", rexp)
    checker.check(exec_string)

    # step into method call
    execute("step")

    # list current line
    # expect "37	            if (args.length == 0) {"
    exec_string = execute("list")
    rexp = fr"38{spaces_pattern}if \(args\.length == 0\) {{"
    checker = Checker('list hello.Hello$Greeter.greeter', rexp)
    checker.check(exec_string, skip_fails=False)

    # print details of greeter types
    exec_string = execute("ptype 'hello.Hello$NamedGreeter'")
    rexp = [r"type = class hello\.Hello\$NamedGreeter : public hello\.Hello\$Greeter {",
            fr"{spaces_pattern}private:" if major < 15 else None,
            fr"{spaces_pattern}{compressed_pattern if isolates else ''}java\.lang\.String \*name;",
            r"",
            fr"{spaces_pattern}public:",
            fr"{spaces_pattern}void NamedGreeter\(java\.lang\.String \*\);",
            fr"{spaces_pattern}void greet\(void\);",
            r"}"]

    checker = Checker('ptype NamedGreeter', rexp)
    checker.check(exec_string, skip_fails=True)

    exec_string = execute("ptype 'hello.Hello$Greeter'")
    rexp = [r"type = class hello\.Hello\$Greeter : public java\.lang\.Object {",
            fr"{spaces_pattern}public:",
            fr"{spaces_pattern}void Greeter\(void\);",
            fr"{spaces_pattern}static hello\.Hello\$Greeter \* greeter\(java\.lang\.String\[\] \*\);",
            r"}"]

    checker = Checker('ptype Greeter', rexp)
    checker.check(exec_string, skip_fails=True)

    exec_string = execute("ptype 'java.lang.Object'")
    rexp = [r"type = class java\.lang\.Object : public _objhdr {",
            fr"{spaces_pattern}public:",
            fr"{spaces_pattern}void Object\(void\);",
            fr"{spaces_pattern}boolean equals\(java\.lang\.Object \*\);",
            fr"{spaces_pattern}java\.lang\.Class \* getClass\(void\);",
            fr"{spaces_pattern}int hashCode\(void\);",
            fr"{spaces_pattern}void notify\(void\);",
            fr"{spaces_pattern}void notifyAll\(void\);",
            fr"{spaces_pattern}java\.lang\.String \* toString\(void\);",
            fr"{spaces_pattern}void wait\(void\);",
            fr"{spaces_pattern}void wait\(long\);",
            r"}"]

    checker = Checker('ptype Object', rexp)
    checker.check(exec_string, skip_fails=True)

    exec_string = execute("ptype _objhdr")
    rexp = [r"type = struct _objhdr {",
            fr"{spaces_pattern}Encoded\$Dynamic\$Hub \*hub;",
            fr"{spaces_pattern}(int|long) reserved;" if has_reserved_field else None,
            r"}"]

    checker = Checker('ptype _objhdr', rexp)
    checker.check(exec_string, skip_fails=True)

    exec_string = execute("ptype 'java.lang.String[]'")
    rexp = [r"type = class java.lang.String\[\] : public java.lang.Object {",
            fr"{spaces_pattern}int len;",
            fr"{spaces_pattern}{compressed_pattern if isolates else ''}java\.lang\.String \*data\[0\];",
            r"}"]

    checker = Checker('ptype String[]', rexp)
    checker.check(exec_string, skip_fails=True)

    # run a backtrace
    exec_string = execute("backtrace")
    stacktrace_regex = [
        fr"#0{spaces_pattern}hello\.Hello\$Greeter::greeter{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:38",
        fr"#1{spaces_pattern}{address_pattern} in hello\.Hello::main{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:{main_start:d}",
        fr"#2{spaces_pattern}({address_pattern} in )?java\.lang\.invoke\.LambdaForm\$DMH/s{hex_digits_pattern}::invokeStatic(Init)?{param_types_pattern} {arg_values_pattern}( at java/lang/invoke/{package_file_pattern}:[0-9]+)?",
        fr"#3{spaces_pattern}({address_pattern} in )?com\.oracle\.svm\.core\.JavaMainWrapper::invokeMain{param_types_pattern} {arg_values_pattern} at {package_pattern}JavaMainWrapper\.java:[0-9]+",
        fr"#4{spaces_pattern}({address_pattern} in )?com\.oracle\.svm\.core\.JavaMainWrapper::runCore0{no_param_types_pattern} {no_arg_values_pattern} at {package_pattern}JavaMainWrapper\.java:[0-9]+",
        fr"#5{spaces_pattern}{address_pattern} in com\.oracle\.svm\.core\.JavaMainWrapper::runCore{no_param_types_pattern} {no_arg_values_pattern} at {package_pattern}JavaMainWrapper\.java:[0-9]+",
        fr"#6{spaces_pattern}com\.oracle\.svm\.core\.JavaMainWrapper::doRun{param_types_pattern} {arg_values_pattern} at {package_pattern}JavaMainWrapper\.java:[0-9]+",
        fr"#7{spaces_pattern}({address_pattern} in )?com\.oracle\.svm\.core\.JavaMainWrapper::run{param_types_pattern} {arg_values_pattern} at {package_pattern}JavaMainWrapper\.java:[0-9]+",
        fr"#8{spaces_pattern}({address_pattern} in )?com\.oracle\.svm\.core\.code\.IsolateEnterStub::JavaMainWrapper_run_{varname_pattern}{param_types_pattern} {arg_values_pattern}"
    ]
    if musl:
        # musl has a different entry point - drop the last two frames
        stacktrace_regex = stacktrace_regex[:-2]
    checker = Checker("backtrace hello.Hello.Greeter::greeter", stacktrace_regex)
    checker.check(exec_string, skip_fails=False)

    # now step into inlined code
    execute("next")

    # check we are still in hello.Hello$Greeter.greeter but no longer in hello.Hello.java
    exec_string = execute("backtrace 1")
    checker = Checker("backtrace inline",
                      [
                          fr"#0{spaces_pattern}hello\.Hello\$Greeter::greeter{param_types_pattern} {arg_values_pattern} at ({package_file_pattern}):{digits_pattern}"])
    matches = checker.check(exec_string, skip_fails=False)
    # n.b. can only get back here with one match
    match = matches[0]
    if match.group(1) == "hello.Hello.java":
        line = exec_string.replace("\n", "")
        print(f'bad match for output {line:d}\n')
        print(checker)
        sys.exit(1)

    # set breakpoint at substituted method hello.Hello$DefaultGreeter::greet
    # expect "Breakpoint <n> at 0x[0-9a-f]+: file hello/Target_Hello_DefaultGreeter.java, line [0-9]+."
    exec_string = execute("break hello.Hello$DefaultGreeter::greet")
    rexp = fr"Breakpoint {digits_pattern} at {address_pattern}: file hello/Target_hello_Hello_DefaultGreeter\.java, line {digits_pattern}\."
    checker = Checker("break on substituted method", rexp)
    checker.check(exec_string, skip_fails=False)
    execute("delete breakpoints")

    # step out of the call to Greeter.greeter and then step forward
    # so the return value is assigned to local var greeter
    execute("finish")
    execute("step")

    # check argument args is not known
    exec_string = execute("info args")
    rexp = [r"args = <optimized out>"]
    checker = Checker("info args 2", rexp)
    checker.check(exec_string)

    # check local var greeter is known
    exec_string = execute("info locals")
    rexp = [fr"greeter = {address_pattern}"]
    checker = Checker("info locals 2", rexp)
    checker.check(exec_string)

    # set a break point at standard library PrintStream.println. Ideally we would like to break only at println(String)
    # however in Java 17 and GraalVM >21.3.0 this method ends up getting inlined and we can't (yet?!) set a breakpoint
    # only to a specific override of a method by specifying the parameter types when that method gets inlined.
    # As a result the breakpoint will be set at all println overrides.
    # expect "Breakpoint <n> at 0x[0-9a-f]+: java.io.PrintStream::println. ([0-9]+ locations)""
    exec_string = execute("break java.io.PrintStream::println")
    # we cannot be sure how much inlining will happen so we
    # specify a pattern for the number of locations
    rexp = fr"Breakpoint {digits_pattern} at {address_pattern}: java\.io\.PrintStream::println\. \({digits_pattern} locations\)"
    checker = Checker('break println', rexp)
    checker.check(exec_string, skip_fails=False)

    execute("continue")

    exec_string = execute("info args")
    # we cannot be sure whether "this" or argument "x" are available
    # the call to println may get inlined in which case there is no
    # guarantee that the args won't be optimized away
    rexp = [fr"this = {wildcard_pattern}",
            fr"{varname_pattern} = {wildcard_pattern}"]
    checker = Checker("info args println", rexp)
    checker.check(exec_string)

    exec_string = execute("ptype this")
    # the debugger should still know the type of "this"
    rexp = [r"type = class java\.io\.PrintStream : public java\.io\.FilterOutputStream {"]
    checker = Checker("ptype this", rexp)
    checker.check(exec_string)

    ###
    # Tests for inlined methods
    ###

    # print details of Hello type
    exec_string = execute("ptype 'hello.Hello'")
    rexp = [r"type = class hello\.Hello : public java\.lang\.Object {",
            # ptype lists inlined methods although they are not listed with info func
            fr"{spaces_pattern}private:" if major < 15 else None,
            fr"{spaces_pattern}static void inlineA\(void\);",
            fr"{spaces_pattern}static void inlineCallChain\(void\);",
            fr"{spaces_pattern}static void inlineFrom\(void\);",
            fr"{spaces_pattern}static void inlineHere\(int\);",
            fr"{spaces_pattern}static void inlineIs\(void\);",
            fr"{spaces_pattern}static void inlineMee\(void\);",
            fr"{spaces_pattern}static void inlineMixTo\(int\);",
            fr"{spaces_pattern}static void inlineMoo\(void\);",
            fr"{spaces_pattern}static void inlineReceiveConstants\(byte, int, long, java\.lang\.String \*, float, double\);",
            fr"{spaces_pattern}static void inlineTailRecursion\(int\);",
            fr"{spaces_pattern}static void inlineTo\(int\);",
            fr"{spaces_pattern}static java\.lang\.String \* lambda\$(static\$)?{digits_pattern}\(void\);",
            fr"{spaces_pattern}public:",
            fr"{spaces_pattern}static void main\(java\.lang\.String\[\] \*\);",
            fr"{spaces_pattern}private:",
            fr"{spaces_pattern}static void noInlineFoo\(void\);",
            fr"{spaces_pattern}static void noInlineHere\(int\);",
            fr"{spaces_pattern}static void noInlineManyArgs\(int, byte, short, char, boolean, int, int, long, int, long, float, float, float, float, double, float, float, float, float, double, boolean, float\);",
            fr"{spaces_pattern}static void noInlinePassConstants\(void\);",
            fr"{spaces_pattern}static void noInlineTest\(void\);",
            fr"{spaces_pattern}static void noInlineThis\(void\);",
            r"}"]
    checker = Checker('ptype hello.Hello', rexp)
    checker.check(exec_string, skip_fails=True)

    # list methods matching regular expression "nline", inline methods are not listed because they lack a definition
    # (this is true for C/C++ as well)
    exec_string = execute("info func nline")
    rexp = [r"All functions matching regular expression \"nline\":",
            r"File hello/Hello\.java:",
            fr"{line_number_prefix_pattern}void hello\.Hello::noInlineFoo\(\);",
            fr"{line_number_prefix_pattern}void hello\.Hello::noInlineHere\(int\);",
            fr"{line_number_prefix_pattern}void hello\.Hello::noInlineTest\(\);",
            fr"{line_number_prefix_pattern}void hello\.Hello::noInlineThis\(\);"]
    checker = Checker('info func nline', rexp)
    checker.check(exec_string)

    # list inlineIs and inlineA and check that the listing maps to the inlined code instead of the actual code,
    # although not ideal this is how GDB treats inlined code in C/C++ as well
    rexp = [fr"{digits_pattern + spaces_pattern}inlineA\(\);"]
    checker = Checker('list inlineIs', rexp)
    checker.check(execute("list inlineIs"))
    # List inlineA may actually return more locations dependent on inlining decisions, but noInlineTest
    # always needs to be listed
    rexp = [fr"{digits_pattern + spaces_pattern}noInlineTest\(\);"]
    checker = Checker('list inlineA', rexp)
    checker.check(execute("list inlineA"))

    execute("delete breakpoints")
    # Set breakpoint at inlined method and step through its nested inline methods
    exec_string = execute("break hello.Hello::inlineIs")
    # Dependent on inlining decisions, there are either two or one locations
    rexp = fr"Breakpoint {digits_pattern} at {address_pattern}: (hello\.Hello::inlineIs\. \(2 locations\)|file hello/Hello\.java, line {digits_pattern}\.)"
    checker = Checker('break inlineIs', rexp)
    checker.check(exec_string, skip_fails=False)

    execute("continue")
    exec_string = execute("list")
    rexp = [fr"{digits_pattern + spaces_pattern}inlineA\(\);"]
    checker = Checker('hit break at inlineIs', rexp)
    checker.check(exec_string, skip_fails=False)
    execute("step")
    exec_string = execute("list")
    rexp = [fr"{digits_pattern + spaces_pattern}noInlineTest\(\);"]
    checker = Checker('step in inlineA', rexp)
    checker.check(exec_string, skip_fails=False)
    exec_string = execute("backtrace 4")
    rexp = [
        fr"#0{spaces_pattern}hello\.Hello::inlineA{no_param_types_pattern} {no_arg_values_pattern} at hello/Hello\.java:120",
        fr"#1{spaces_pattern}hello\.Hello::inlineIs{no_param_types_pattern} {no_arg_values_pattern} at hello/Hello\.java:115",
        fr"#2{spaces_pattern}hello\.Hello::noInlineThis{no_param_types_pattern} {no_arg_values_pattern} at hello/Hello\.java:110",
        fr"#3{spaces_pattern}{address_pattern} in hello\.Hello::main{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:{main_noinline:d}"]
    checker = Checker('backtrace inlineMee', rexp)
    checker.check(exec_string, skip_fails=False)

    execute("delete breakpoints")
    exec_string = execute("break hello.Hello::noInlineTest")
    rexp = fr"Breakpoint {digits_pattern} at {address_pattern}: file hello/Hello\.java, line {digits_pattern}\."
    checker = Checker('break noInlineTest', rexp)
    checker.check(exec_string, skip_fails=False)

    execute("continue")
    exec_string = execute("list")
    rexp = fr"{digits_pattern + spaces_pattern}System.out.println\(\"This is a test\"\);"
    checker = Checker('hit breakpoint in noInlineTest', rexp)
    checker.check(exec_string, skip_fails=False)
    exec_string = execute("backtrace 5")
    rexp = [
        fr"#0{spaces_pattern}hello\.Hello::noInlineTest{no_param_types_pattern} {no_arg_values_pattern} at hello/Hello\.java:125",
        fr"#1{spaces_pattern}{address_pattern} in hello\.Hello::inlineA{no_param_types_pattern} {no_arg_values_pattern} at hello/Hello\.java:120",
        fr"#2{spaces_pattern}hello\.Hello::inlineIs{no_param_types_pattern} {no_arg_values_pattern} at hello/Hello\.java:115",
        fr"#3{spaces_pattern}hello\.Hello::noInlineThis{no_param_types_pattern} {no_arg_values_pattern} at hello/Hello\.java:110",
        fr"#4{spaces_pattern}{address_pattern} in hello\.Hello::main{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:{main_noinline:d}"]
    checker = Checker('backtrace in inlineMethod', rexp)
    checker.check(exec_string, skip_fails=False)

    execute("delete breakpoints")
    # Set breakpoint at method with inline and not-inlined invocation in same line
    exec_string = execute("break hello.Hello::inlineFrom")
    rexp = fr"Breakpoint {digits_pattern} at {address_pattern}: file hello/Hello\.java, line {digits_pattern}."
    checker = Checker('break inlineFrom', rexp)
    checker.check(exec_string, skip_fails=False)

    exec_string = execute("info break")
    rexp = [
        fr"{digits_pattern}{spaces_pattern}breakpoint{spaces_pattern}keep{spaces_pattern}y{spaces_pattern}{address_pattern} in hello\.Hello::inlineFrom\(\) at hello/Hello\.java:131"]
    checker = Checker('info break inlineFrom', rexp)
    checker.check(exec_string)

    execute("delete breakpoints")
    exec_string = execute("break Hello.java:147")
    rexp = fr"Breakpoint {digits_pattern} at {address_pattern}: (Hello\.java:147\. \(2 locations\)|file hello/Hello\.java, line {digits_pattern}\.)"
    checker = Checker('break Hello.java:147', rexp)
    checker.check(exec_string)

    execute("continue 5")
    exec_string = execute("backtrace 14")
    rexp = [
        fr"#0{spaces_pattern}hello\.Hello::inlineMixTo{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:147",
        fr"#1{spaces_pattern}hello\.Hello::noInlineHere{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:139",
        fr"#2{spaces_pattern}({address_pattern} in)? hello\.Hello::inlineMixTo{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:145",
        fr"#3{spaces_pattern}hello\.Hello::noInlineHere{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:139",
        fr"#4{spaces_pattern}({address_pattern} in)? hello\.Hello::inlineMixTo{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:145",
        fr"#5{spaces_pattern}hello\.Hello::noInlineHere{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:139",
        fr"#6{spaces_pattern}({address_pattern} in)? hello\.Hello::inlineMixTo{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:145",
        fr"#7{spaces_pattern}hello\.Hello::noInlineHere{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:139",
        fr"#8{spaces_pattern}({address_pattern} in)? hello\.Hello::inlineMixTo{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:145",
        fr"#9{spaces_pattern}hello\.Hello::noInlineHere{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:139",
        fr"#10{spaces_pattern}({address_pattern} in)? hello\.Hello::inlineMixTo{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:145",
        fr"#11{spaces_pattern}hello\.Hello::noInlineHere{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:139",
        fr"#12{spaces_pattern}({address_pattern} in)? hello\.Hello::inlineFrom{no_param_types_pattern} {no_arg_values_pattern} at hello/Hello\.java:131",
        fr"#13{spaces_pattern}hello\.Hello::main{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:{main_inlinefrom:d}"]
    checker = Checker('backtrace in recursive inlineMixTo', rexp)
    checker.check(exec_string, skip_fails=False)

    execute("delete breakpoints")
    exec_string = execute("break Hello.java:160")
    # we cannot be sure how much inlining will happen so we
    # specify a pattern for the number of locations
    rexp = fr"Breakpoint {digits_pattern} at {address_pattern}: Hello\.java:160\. \({digits_pattern} locations\)"
    checker = Checker('break Hello.java:160', rexp)
    checker.check(exec_string)

    execute("continue")
    exec_string = execute("backtrace 14")
    # we cannot be sure exactly how much inlining happens
    # which means the format of the frame display may vary from
    # one build to the next. so we use a generic match after the
    # first pair.
    rexp = [
        fr"#0{spaces_pattern}hello\.Hello::inlineTo{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:160",
        fr"#1{spaces_pattern}({address_pattern} in)? hello\.Hello::inlineHere{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:152",
        fr"#2{wildcard_pattern}hello\.Hello::inlineTo{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:158",
        fr"#3{wildcard_pattern}hello\.Hello::inlineHere{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:152",
        fr"#4{wildcard_pattern}hello\.Hello::inlineTo{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:158",
        fr"#5{wildcard_pattern}hello\.Hello::inlineHere{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:152",
        fr"#6{wildcard_pattern}hello\.Hello::inlineTo{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:158",
        fr"#7{wildcard_pattern}hello\.Hello::inlineHere{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:152",
        fr"#8{wildcard_pattern}hello\.Hello::inlineTo{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:158",
        fr"#9{wildcard_pattern}hello\.Hello::inlineHere{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:152",
        fr"#10{wildcard_pattern}hello\.Hello::inlineTo{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:158",
        fr"#11{wildcard_pattern}hello\.Hello::inlineHere{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:152",
        fr"#12{spaces_pattern}hello\.Hello::inlineFrom{no_param_types_pattern} {no_arg_values_pattern} at hello/Hello\.java:133",
        fr"#13{spaces_pattern}hello\.Hello::main{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:{main_inlinefrom:d}"]
    checker = Checker('backtrace in recursive inlineTo', rexp)
    checker.check(exec_string, skip_fails=False)

    execute("delete breakpoints")
    exec_string = execute("break Hello.java:166")
    # we cannot be sure how much inlining will happen so we
    # specify a pattern for the number of locations
    rexp = fr"Breakpoint {digits_pattern} at {address_pattern}: Hello\.java:166\. \({digits_pattern} locations\)"
    checker = Checker('break Hello.java:166', rexp)
    checker.check(exec_string)

    execute("continue 5")
    exec_string = execute("backtrace 8")
    # we cannot be sure exactly how much inlining happens
    # which means the format of the frame display may vary from
    # one build to the next. so we use a generic match after the
    # first one.
    rexp = [
        fr"#0{spaces_pattern}hello\.Hello::inlineTailRecursion{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:166",
        fr"#1{wildcard_pattern}hello\.Hello::inlineTailRecursion{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:169",
        fr"#2{wildcard_pattern}hello\.Hello::inlineTailRecursion{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:169",
        fr"#3{wildcard_pattern}hello\.Hello::inlineTailRecursion{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:169",
        fr"#4{wildcard_pattern}hello\.Hello::inlineTailRecursion{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:169",
        fr"#5{wildcard_pattern}hello\.Hello::inlineTailRecursion{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:169",
        fr"#6{spaces_pattern}hello\.Hello::inlineFrom{no_param_types_pattern} {no_arg_values_pattern} at hello/Hello\.java:134",
        fr"#7{spaces_pattern}hello\.Hello::main{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:{main_inlinefrom:d}"]
    checker = Checker('backtrace in recursive inlineTailRecursion', rexp)
    checker.check(exec_string, skip_fails=False)

    # on aarch64 the initial break occurs at the stack push
    # but we need to check the args before and after the stack push
    # so we need to use the examine command to identify the start
    # address of the method and place an instruction break at that
    # address to ensure we have the very first instruction
    exec_string = execute("x/i 'hello.Hello'::noInlineManyArgs")
    rexp = fr"{spaces_pattern}0x({hex_digits_pattern}){wildcard_pattern}hello.Hello::noInlineManyArgs{wildcard_pattern}"
    checker = Checker('x/i hello.Hello::noInlineManyArgs', rexp)
    matches = checker.check(exec_string)
    # n.b can ony get here with one match
    match = matches[0]
    bp_address = int(match.group(1), 16)
    print(f"bp = {match.group(1)} {bp_address:x}")

    # exec_string = execute("break hello.Hello::noInlineManyArgs")
    exec_string = execute(f"break *0x{bp_address:x}")
    rexp = fr"Breakpoint {digits_pattern} at {address_pattern}: file hello/Hello\.java, line {digits_pattern}\."
    checker = Checker(fr"break *0x{bp_address:x}", rexp)
    checker.check(exec_string)
    # rexp = r"Breakpoint %s at %s: file hello/Hello\.java, line 163\."%(digits_pattern, address_pattern)
    # checker = Checker('break hello.Hello::noInlineManyArgs', rexp)
    # checker.check(exec_string)
    execute("continue")
    exec_string = execute("info args")
    rexp = [r"i0 = 0",
            r"b1 = 1 '\\001'",
            r"s2 = 2",
            r"c3 = 51",
            r"b4 = true",
            r"i5 = 5",
            r"i6 = 6",
            r"l7 = 7",
            r"i8 = 8",
            r"l9 = 9",
            r"f0 = 0",
            r"f1 = 1.125",
            r"f2 = 2.25",
            r"f3 = 3.375",
            r"d4 = 4.5",
            r"f5 = 5.625",
            r"f6 = 6.75",
            r"f7 = 7.875",
            r"f8 = 9",
            r"d9 = 10.125",
            r"b10 = false",
            r"f11 = 12.375"]
    checker = Checker('info args', rexp)
    checker.check(exec_string)

    # proceed to the next infopoint, which is at the end of the method prologue.
    # since the compiler may emit different instruction sequences, we step
    # through the instructions until we find and execute the first instruction
    # that adjusts the stack pointer register, which concludes the prologue.
    if arch == "aarch64":
        # on aarch64 the stack build sequence requires both a stack extend and
        # then a save of sp to fp. therefore, match the second instruction,
        # which is expected to be a store pair (stp).
        instruction_adjusts_sp_register_pattern = fr"{wildcard_pattern}stp{spaces_pattern}x{digits_pattern}, x{digits_pattern}, \[sp,"
    else:
        instruction_adjusts_sp_register_pattern = fr"{wildcard_pattern},%rsp"
    instruction_adjusts_sp_register_regex = re.compile(instruction_adjusts_sp_register_pattern)
    max_num_stepis = 6
    num_stepis = 0
    while True:
        exec_string = execute("x/i $pc")
        print(exec_string)
        execute("stepi")
        num_stepis += 1
        if instruction_adjusts_sp_register_regex.match(exec_string):
            print("reached end of method prologue successfully")
            break
        if num_stepis >= max_num_stepis:
            print(f"method prologue is unexpectedly long, did not reach end after {num_stepis} stepis")
            sys.exit(1)

    exec_string = execute("info args")
    rexp = [r"i0 = 0",
            r"b1 = 1 '\\001'",
            r"s2 = 2",
            r"c3 = 51",
            r"b4 = true",
            r"i5 = 5",
            r"i6 = 6",
            r"l7 = 7",
            r"i8 = 8",
            r"l9 = 9",
            r"f0 = 0",
            r"f1 = 1.125",
            r"f2 = 2.25",
            r"f3 = 3.375",
            r"d4 = 4.5",
            r"f5 = 5.625",
            r"f6 = 6.75",
            r"f7 = 7.875",
            r"f8 = 9",
            r"d9 = 10.125",
            r"b10 = false",
            r"f11 = 12.375"]
    checker = Checker('info args 2', rexp)
    checker.check(exec_string)

    execute("delete breakpoints")

    exec_string = execute("break hello.Hello::inlineReceiveConstants")
    # we cannot be sure how much inlining will happen so we
    # specify a pattern for the number of locations
    rexp = fr"Breakpoint {digits_pattern} at {address_pattern}: hello\.Hello::inlineReceiveConstants\. \({digits_pattern} locations\)"
    checker = Checker('break hello.Hello::inlineReceiveConstants', rexp)
    checker.check(exec_string)

    execute("continue")

    exec_string = execute("info args")
    rexp = [
        r"b = 1 '\\001'",
        r"i = 2",
        r"l = 3",
        fr"s = {address_pattern}",
        r"f = 4",
        r"d = 5"]
    checker = Checker('info args 3', rexp)
    checker.check(exec_string)

    execute("set print elements 10")
    exec_string = execute("x/s s->value->data")
    execute("set print elements unlimited")
    rexp = [fr'{address_pattern}:{spaces_pattern}"stringtext"']
    checker = Checker('x/s s->value->data', rexp)
    checker.check(exec_string, skip_fails=True)

    execute("next 3")
    exec_string = execute("info locals")
    rexp = [r"n = 6",
            r"q = 20",
            fr"t = {address_pattern}"]
    checker = Checker('info locals 3', rexp)
    checker.check(exec_string)

    execute("set print elements 11")
    exec_string = execute("x/s t->value->data")
    execute("set print elements unlimited")
    rexp = [fr'{address_pattern}:{spaces_pattern}"stringtext!"']
    checker = Checker('x/s t->value->data', rexp)
    checker.check(exec_string, skip_fails=True)

    # look up lambda method
    exec_string = execute("info func hello.Hello::lambda")
    rexp = [r'All functions matching regular expression "hello\.Hello::lambda":',
            r"File hello/Hello\.java:",
            fr"{line_number_prefix_pattern}java\.lang\.String \*(hello\.Hello::lambda\$(static\$)?{digits_pattern}){no_param_types_pattern};"]
    checker = Checker("info func hello.Hello::lambda", rexp)
    matches = checker.check(exec_string)
    # lambda's name depends on the underlying JDK, so we get it from gdb's output instead of hardcoding it
    lambda_name = matches[2].group(1)

    execute("delete breakpoints")

    exec_string = execute("break " + lambda_name)
    rexp = fr"Breakpoint {digits_pattern} at {address_pattern}: (file hello/Hello.java, line 221|hello.Hello::lambda($static)?${digits_pattern}. \({digits_pattern} locations\))"
    checker = Checker('break ' + lambda_name, rexp)
    checker.check(exec_string)

    execute("continue")
    exec_string = execute("list")
    rexp = fr"{digits_pattern + spaces_pattern}StringBuilder sb = new StringBuilder\(\"lambda\"\);"
    checker = Checker('hit breakpoint in lambda', rexp)
    checker.check(exec_string, skip_fails=False)

    execute("delete breakpoints")

    exec_string = execute("break Hello.java:222")
    rexp = fr"Breakpoint {digits_pattern} at {address_pattern}: file hello/Hello.java, line 222"
    checker = Checker('break Hello.java:222', rexp)
    checker.check(exec_string)

    execute("continue")
    exec_string = execute("list")
    rexp = fr"{digits_pattern + spaces_pattern}sb\.append\(System\.getProperty\(\"never_optimize_away\", \"Text\"\)\);"
    checker = Checker('hit breakpoint 2 in lambda', rexp)
    checker.check(exec_string, skip_fails=False)
    exec_string = execute("backtrace 3")
    rexp = [
        fr"#0{spaces_pattern}hello\.Hello::lambda\$(static\$)?0{no_param_types_pattern} {no_arg_values_pattern} at hello/Hello\.java:222",
        fr"#1{spaces_pattern}{address_pattern} in hello\.Hello\$\$Lambda((\${digits_pattern}/0x)|(\$)|(\.0x|/0x))?{hex_digits_pattern}::get{wildcard_pattern} at hello/Hello\.java:259",
        fr"#2{spaces_pattern}hello\.Hello::main{param_types_pattern} {arg_values_pattern} at hello/Hello\.java:259"]
    checker = Checker('backtrace in lambda', rexp)
    checker.check(exec_string, skip_fails=False)

    execute("delete breakpoints")

    # check if java.lang.Class and the hub field is resolved correctly
    exec_string = execute(f"break hello.Hello::checkClassType")
    rexp = fr"Breakpoint {digits_pattern} at {address_pattern}: file hello/Hello\.java, line {digits_pattern}\."
    checker = Checker(fr"break hello.Hello::checkClassType", rexp)
    checker.check(exec_string)

    execute("continue")
    exec_string = execute("print *clazz.name.value")
    rexp = [fr"{wildcard_pattern} = {{",
            fr"{spaces_pattern}<java\.lang\.Object> = {{",
            fr"{spaces_pattern}<_objhdr> = {{",
            fr"{spaces_pattern}hub = {address_pattern}",
            fr"{spaces_pattern}}}, <No data fields>}},",
            fr"{spaces_pattern}members of byte \[\]",
            fr"{spaces_pattern}len = 16,",
            fr"{spaces_pattern}data = {address_pattern}"]
    checker = Checker('print *clazz.name.value', rexp)
    checker.check(exec_string)

    exec_string = execute("ptype clazz")
    rexp = [r"type = class java\.lang\.Class : public java\.lang\.Object {"]
    checker = Checker('ptype clazz', rexp)
    checker.check(exec_string)

    exec_string = execute("print *clazz.hub.name.value")
    rexp = [fr"{wildcard_pattern} = {{",
            fr"{spaces_pattern}<java\.lang\.Object> = {{",
            fr"{spaces_pattern}<_objhdr> = {{",
            fr"{spaces_pattern}hub = {address_pattern}",
            fr"{spaces_pattern}}}, <No data fields>}},",
            fr"{spaces_pattern}members of byte \[\]",
            fr"{spaces_pattern}len = 15,",
            fr"{spaces_pattern}data = {address_pattern}"]
    checker = Checker('print *clazz.hub.name.value', rexp)
    checker.check(exec_string)

    exec_string = execute("ptype clazz.hub")
    rexp = [r"type = class Encoded\$Dynamic\$Hub : public java\.lang\.Class {"]
    checker = Checker('ptype clazz.hub', rexp)
    checker.check(exec_string)

    # check object on heap and static object (must be the same as the field 'clazz')
    for obj in ['dyn.c', "'hello.Hello::staticHolder'.c",
                f"(('{'_z_.' if isolates else ''}java.lang.Class' *)dyn.o)",
                f"(('{'_z_.' if isolates else ''}java.lang.Class' *)'hello.Hello::staticHolder'.o)"]:
        command = f"print *{obj}.name.value"
        exec_string = execute(command)
        rexp = [fr"{wildcard_pattern} = {{",
                fr"{spaces_pattern}<java\.lang\.Object> = {{",
                fr"{spaces_pattern}<_objhdr> = {{",
                fr"{spaces_pattern}hub = {address_pattern}",
                fr"{spaces_pattern}}}, <No data fields>}},",
                fr"{spaces_pattern}members of byte \[\]",
                fr"{spaces_pattern}len = 16,",
                fr"{spaces_pattern}data = {address_pattern}"]
        checker = Checker(command, rexp)
        checker.check(exec_string)

        execute(f"print *{obj}")
        exec_string = execute("ptype $")
        rexp = [r"type = class _z_\.java\.lang\.Class : public java\.lang\.Class {" if isolates else r"type = class java\.lang\.Class : public java\.lang\.Object {"]
        checker = Checker(f'ptype {obj}', rexp)
        checker.check(exec_string)

        command = f"print *{obj}.hub.name.value"
        exec_string = execute(command)
        rexp = [fr"{wildcard_pattern} = {{",
                fr"{spaces_pattern}<java\.lang\.Object> = {{",
                fr"{spaces_pattern}<_objhdr> = {{",
                fr"{spaces_pattern}hub = {address_pattern}",
                fr"{spaces_pattern}}}, <No data fields>}},",
                fr"{spaces_pattern}members of byte \[\]",
                fr"{spaces_pattern}len = 15,",
                fr"{spaces_pattern}data = {address_pattern}"]
        checker = Checker(command, rexp)
        checker.check(exec_string)

        execute(f"print {obj}.hub")
        exec_string = execute("ptype $")
        rexp = [r"type = class Encoded\$Dynamic\$Hub : public java\.lang\.Class {"]
        checker = Checker(f'ptype {obj}.hub', rexp)
        checker.check(exec_string)

    execute("delete breakpoints")

    ### Now check foreign debug type info

    # check type information is reported correctly

    exec_string = execute("info types com.oracle.svm.test.debug.CStructTests\$")
    rexp = [
        fr"{spaces_pattern}typedef composite_struct \* com\.oracle\.svm\.test\.debug\.CStructTests\$CompositeStruct;",
        fr"{spaces_pattern}typedef int \* com\.oracle\.svm\.test\.debug\.CStructTests\$MyCIntPointer;",
        fr"{spaces_pattern}typedef simple_struct \* com\.oracle\.svm\.test\.debug\.CStructTests\$SimpleStruct;",
        fr"{spaces_pattern}typedef simple_struct2 \* com\.oracle\.svm\.test\.debug\.CStructTests\$SimpleStruct2;",
        fr"{spaces_pattern}typedef weird \* com\.oracle\.svm\.test\.debug\.CStructTests\$Weird;"]
    checker = Checker("info types com.oracle.svm.test.debug.CStructTests\\$", rexp)
    checker.check(exec_string)

    # Print various foreign struct types and check they have the correct layout
    exec_string = execute("ptype /o 'com.oracle.svm.test.debug.CStructTests$CompositeStruct'")
    rexp = [r"type = struct composite_struct {",
            fr"/\*{spaces_pattern}0{spaces_pattern}\|{spaces_pattern}1{spaces_pattern}\*/{spaces_pattern}byte c1;",
            fr"/\*{spaces_pattern}XXX{spaces_pattern}3-byte hole{spaces_pattern}\*/",
            fr"/\*{spaces_pattern}4{spaces_pattern}\|{spaces_pattern}8{spaces_pattern}\*/{spaces_pattern}struct simple_struct {{",
            fr"/\*{spaces_pattern}4{spaces_pattern}\|{spaces_pattern}4{spaces_pattern}\*/{spaces_pattern}int first;",
            fr"/\*{spaces_pattern}8{spaces_pattern}\|{spaces_pattern}4{spaces_pattern}\*/{spaces_pattern}int second;",
            fr"{spaces_pattern}/\* total size \(bytes\):{spaces_pattern}8 \*/",
            fr"{spaces_pattern}}} c2;",
            fr"/\*{spaces_pattern}12{spaces_pattern}\|{spaces_pattern}4{spaces_pattern}\*/{spaces_pattern}int c3;",
            fr"/\*{spaces_pattern}16{spaces_pattern}\|{spaces_pattern}16{spaces_pattern}\*/{spaces_pattern}struct simple_struct2 {{",
            fr"/\*{spaces_pattern}16{spaces_pattern}\|{spaces_pattern}1{spaces_pattern}\*/{spaces_pattern}byte alpha;",
            fr"/\*{spaces_pattern}XXX{spaces_pattern}7-byte hole{spaces_pattern}\*/",
            fr"/\*{spaces_pattern}24{spaces_pattern}\|{spaces_pattern}8{spaces_pattern}\*/{spaces_pattern}long beta;",
            fr"{spaces_pattern}/\* total size \(bytes\):{spaces_pattern}16 \*/",
            fr"{spaces_pattern}}} c4;",
            fr"/\*{spaces_pattern}32{spaces_pattern}\|{spaces_pattern}2{spaces_pattern}\*/{spaces_pattern}short c5;",
            fr"/\*{spaces_pattern}XXX{spaces_pattern}6-byte padding{spaces_pattern}\*/",
            fr"{spaces_pattern}/\* total size \(bytes\):{spaces_pattern}40 \*/",
            fr"{spaces_pattern}}} \*"]
    checker = Checker("ptype 'com.oracle.svm.test.debug.CStructTests$CompositeStruct'", rexp)
    checker.check(exec_string)

    exec_string = execute("ptype /o 'com.oracle.svm.test.debug.CStructTests$Weird'")
    rexp = [r"type = struct weird {",
            fr"/\*{spaces_pattern}0{spaces_pattern}\|{spaces_pattern}2{spaces_pattern}\*/{spaces_pattern}short f_short;",
            fr"/\*{spaces_pattern}XXX{spaces_pattern}6-byte hole{spaces_pattern}\*/",
            fr"/\*{spaces_pattern}8{spaces_pattern}\|{spaces_pattern}4{spaces_pattern}\*/{spaces_pattern}int f_int;",
            fr"/\*{spaces_pattern}XXX{spaces_pattern}4-byte hole{spaces_pattern}\*/",
            fr"/\*{spaces_pattern}16{spaces_pattern}\|{spaces_pattern}8{spaces_pattern}\*/{spaces_pattern}long f_long;",
            fr"/\*{spaces_pattern}24{spaces_pattern}\|{spaces_pattern}4{spaces_pattern}\*/{spaces_pattern}float f_float;",
            fr"/\*{spaces_pattern}XXX{spaces_pattern}4-byte hole{spaces_pattern}\*/",
            fr"/\*{spaces_pattern}32{spaces_pattern}\|{spaces_pattern}8{spaces_pattern}\*/{spaces_pattern}double f_double;",
            fr"/\*{spaces_pattern}40{spaces_pattern}\|{spaces_pattern}32{spaces_pattern}\*/{spaces_pattern}int a_int\[8\];",
            fr"/\*{spaces_pattern}72{spaces_pattern}\|{spaces_pattern}12{spaces_pattern}\*/{spaces_pattern}char a_char\[12\];",
            fr"/\*{spaces_pattern}XXX{spaces_pattern}4-byte padding{spaces_pattern}\*/",
            fr"{spaces_pattern}/\* total size \(bytes\):{spaces_pattern}88 \*/",
            fr"{spaces_pattern}}} \*"]
    checker = Checker("ptype 'com.oracle.svm.test.debug.CStructTests$Weird'", rexp)
    checker.check(exec_string)

    # check foreign data is printed correctly if we can

    # set a break point at com.oracle.svm.test.debug.CStructTests::free
    exec_string = execute("break com.oracle.svm.test.debug.CStructTests::free")
    rexp = fr"Breakpoint {digits_pattern} at {address_pattern}: file com/oracle/svm/test/debug/CStructTests\.java, line {digits_pattern}\."
    checker = Checker('break free', rexp)
    checker.check(exec_string)

    # continue the program to the breakpoint
    execute("continue")

    # check the argument
    exec_string = execute("print *('com.oracle.svm.test.debug.CStructTests$CompositeStruct')ptr")
    rexp = [fr"{wildcard_pattern} = {{",
            r"  c1 = 7 '\\a',",
            r"  c2 = {",
            r"    first = 17,",
            r"    second = 19",
            r"  },",
            r"  c3 = 13",
            r"  c4 = {",
            r"    alpha = 3 '\\003',",
            r"    beta = 9223372036854775807",
            r"  },",
            r"  c5 = 32000",
            r"}"]
    checker = Checker('print CompositeStruct', rexp)
    checker.check(exec_string)

    # run the program till the breakpoint
    execute("continue")

    # check the argument again
    exec_string = execute("print *('com.oracle.svm.test.debug.CStructTests$Weird')ptr")
    rexp = [r"  f_short = 42,",
            r"  f_int = 43",
            r"  f_long = 44",
            r"  f_float = 4.5",
            r"  f_double = 4.5999999999999996",
            r"  a_int = {0, 1, 2, 3, 4, 5, 6, 7},",
            r'  a_char = "0123456789AB"']
    checker = Checker('print Weird', rexp)
    checker.check(exec_string)

    execute("delete breakpoints")

    # place a break point at the first instruction of method
    # CStructTests::testMixedArguments
    exec_string = execute("x/i 'com.oracle.svm.test.debug.CStructTests'::testMixedArguments")
    rexp = fr"{spaces_pattern}0x({hex_digits_pattern}){wildcard_pattern}com.oracle.svm.test.debug.CStructTests::testMixedArguments{wildcard_pattern}"
    checker = Checker('x/i CStructTests::testMixedArguments', rexp)
    matches = checker.check(exec_string)
    # n.b can ony get here with one match
    match = matches[0]
    bp_address = int(match.group(1), 16)
    print(f"bp = {match.group(1)} {bp_address:x}")

    exec_string = execute(f"break *0x{bp_address:x}")
    rexp = fr"Breakpoint {digits_pattern} at {address_pattern}: file com/oracle/svm/test/debug/CStructTests\.java, line {digits_pattern}\."
    checker = Checker(fr"break *0x{bp_address:x}", rexp)
    checker.check(exec_string)

    # continue the program to the breakpoint
    execute("continue")

    exec_string = execute("info args")
    rexp = [fr"m1 = 0x{hex_digits_pattern}",
            r"s = 1",
            fr"ss1 = 0x{hex_digits_pattern}",
            r"l = 123456789",
            fr"m2 = 0x{hex_digits_pattern}",
            fr"ss2 = 0x{hex_digits_pattern}",
            fr"m3 = 0x{hex_digits_pattern}"]
    checker = Checker('info args CStructTests::testMixedArguments', rexp)
    checker.check(exec_string)

    exec_string = execute("p m1->value->data")
    rexp = [fr'\${digits_pattern} = 0x{hex_digits_pattern} "a message in a bottle"']
    checker = Checker('p *m1->value->data', rexp)
    checker.check(exec_string)

    exec_string = execute("p m2->value->data")
    rexp = [fr'\${digits_pattern} = 0x{hex_digits_pattern} "a ship in a bottle"']
    checker = Checker('p *m1->value->data', rexp)
    checker.check(exec_string)

    exec_string = execute("p m3->value->data")
    rexp = [fr'\${digits_pattern} = 0x{hex_digits_pattern} "courage in a bottle"']
    checker = Checker('p *m1->value->data', rexp)
    checker.check(exec_string)

    exec_string = execute("p *ss1")
    rexp = [fr"\${digits_pattern} = {{",
            fr"{spaces_pattern}first = 1,",
            fr"{spaces_pattern}second = 2",
            r"}"]
    checker = Checker('p *ss1', rexp)
    checker.check(exec_string)

    exec_string = execute("p *ss2")
    rexp = [fr"\${digits_pattern} = {{",
            fr"{spaces_pattern}alpha = 99 'c',",
            fr"{spaces_pattern}beta = 100",
            r"}"]
    checker = Checker('p *ss1', rexp)
    checker.check(exec_string)

    print(execute("quit 0"))


test()
