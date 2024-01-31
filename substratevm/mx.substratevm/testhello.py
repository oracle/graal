#
# Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

# Configure this gdb session

configure_gdb()

def test():

    # define some useful constants
    main_start = 216
    main_noinline = main_start+17
    main_inlinefrom = main_start+18
    match = match_gdb_version()
    # n.b. can only get back here with one match
    major = int(match.group(1))
    minor = int(match.group(2))
    print("Found gdb version %s.%s"%(major, minor))
    # check if we can print object data
    can_print_data = check_print_data(major, minor)

    musl = os.environ.get('debuginfotest_musl', 'no') == 'yes'

    isolates = os.environ.get('debuginfotest_isolates', 'no') == 'yes'

    arch = os.environ.get('debuginfotest_arch', 'amd64')

    if isolates:
        print("Testing with isolates enabled!")
    else:
        print("Testing with isolates disabled!")

    if not can_print_data:
        print("Warning: cannot test printing of objects!")

    # disable prompting to continue output
    execute("set pagination off")
    # enable pretty printing of structures
    execute("set print pretty on")
    # enable demangling of symbols in assembly code listings
    execute("set print asm-demangle on")
    # disable printing of address symbols
    execute("set print symbol off")

    exec_string = execute("ptype _objhdr")
    fixed_idhash_field = "int idHash;" in exec_string

    # Print DefaultGreeter and check the modifiers of its methods and fields
    exec_string = execute("ptype 'hello.Hello$DefaultGreeter'")
    rexp = [r"type = class hello\.Hello\$DefaultGreeter : public hello\.Hello\$Greeter {",
            r"%spublic:"%spaces_pattern,
            r"%svoid greet\(\);"%spaces_pattern,
            r"%sint hashCode\(\);"%spaces_pattern,
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
    rexp = r"%s:%sint com.oracle.svm.core.code.IsolateEnterStub::(JavaMainWrapper_run_%s)\(%s\);"%(digits_pattern, spaces_pattern, wildcard_pattern, wildcard_pattern)
    checker = Checker('info func JavaMainWrapper_run_', rexp)
    matches = checker.check(exec_string)
    # n.b can ony get here with one match
    match = matches[0]
    method_name = match.group(1)
    print("method_name = %s"%(method_name))

    ## Now find the method start addess and break it
    command = "x/i 'com.oracle.svm.core.code.IsolateEnterStub'::%s"%(method_name)
    exec_string = execute(command)
    rexp = r"%s0x(%s)%scom.oracle.svm.core.code.IsolateEnterStub::JavaMainWrapper_run_%s"%(wildcard_pattern, hex_digits_pattern, wildcard_pattern, wildcard_pattern)
    checker = Checker('x/i IsolateEnterStub::%s'%(method_name), rexp)
    matches = checker.check(exec_string)
    # n.b can ony get here with one match
    match = matches[0]
    
    bp_address = int(match.group(1), 16)
    print("bp = %s %x"%(match.group(1), bp_address))
    exec_string = execute("x/i 0x%x"%bp_address)
    print(exec_string)

    # exec_string = execute("break hello.Hello::noInlineManyArgs")
    exec_string = execute("break *0x%x"%bp_address)
    rexp = r"Breakpoint %s at %s: file com/oracle/svm/core/code/IsolateEnterStub.java, line 1\."%(digits_pattern, address_pattern)
    checker = Checker(r"break *0x%x"%bp_address, rexp)
    checker.check(exec_string)

    # run to breakpoint then delete it
    execute("run")
    execute("delete breakpoints")

    # check incoming parameters are bound to sensible values
    exec_string = execute("info args")
    rexp = [r"__0 = %s"%(digits_pattern),
            r"__1 = 0x%s"%(hex_digits_pattern)]
    checker = Checker("info args : %s"%(method_name), rexp)
    checker.check(exec_string)

    exec_string = execute("p __0")
    rexp = [r"\$%s = 1"%(digits_pattern)]
    checker = Checker("p __0", rexp)
    checker.check(exec_string)

    
    exec_string = execute("p __1")
    rexp = [r"\$%s = \(org\.graalvm\.nativeimage\.c\.type\.CCharPointerPointer\) 0x%s"%(digits_pattern, hex_digits_pattern)]
    checker = Checker("p __1", rexp)
    checker.check(exec_string)

    exec_string = execute("p __1[0]")
    rexp = [r'\$%s = \(org\.graalvm\.nativeimage\.c\.type\.CCharPointer\) 0x%s "%s/hello_image"'%(digits_pattern, hex_digits_pattern, wildcard_pattern)]
    checker = Checker("p __1[0]", rexp)
    checker.check(exec_string)

    
    # set a break point at hello.Hello::main
    # expect "Breakpoint <n> at 0x[0-9a-f]+: file hello.Hello.java, line %d."
    exec_string = execute("break hello.Hello::main")
    rexp = r"Breakpoint %s at %s: file hello/Hello\.java, line %d\."%(digits_pattern, address_pattern, main_start)
    checker = Checker('break main', rexp)
    checker.check(exec_string)

    # continue the program until the breakpoint
    execute("run")
    execute("delete breakpoints")

    # list the line at the breakpoint
    # expect "%d	        Greeter greeter = Greeter.greeter(args);"
    exec_string = execute("list")
    checker = Checker(r"list bp 1", "%d%sGreeter greeter = Greeter\.greeter\(args\);"%(main_start, spaces_pattern))
    checker.check(exec_string, skip_fails=False)

    # run a backtrace
    # expect "#0  hello.Hello.main(java.lang.String[] *).* at hello.Hello.java:%d"
    # expect "#1  0x[0-9a-f]+ in com.oracle.svm.core.code.IsolateEnterStub.JavaMainWrapper_run_.* at [a-z/]+/JavaMainWrapper.java:[0-9]+"
    exec_string = execute("backtrace")
    stacktraceRegex = [r"#0%shello\.Hello::main%s %s at hello/Hello\.java:%d"%(spaces_pattern, param_types_pattern, arg_values_pattern, main_start),
                       r"#1%s(%s in )?java\.lang\.invoke\.LambdaForm\$DMH/s%s::invokeStatic(Init)?%s %s( at java/lang/invoke/%s:[0-9]+)?"%(spaces_pattern, address_pattern, hex_digits_pattern, param_types_pattern, arg_values_pattern, package_file_pattern),
                       r"#2%s(%s in )?com\.oracle\.svm\.core\.JavaMainWrapper::invokeMain%s %s at %sJavaMainWrapper\.java:[0-9]+"%(spaces_pattern, address_pattern, param_types_pattern, arg_values_pattern, package_pattern),
                       r"#3%s(%s in )?com\.oracle\.svm\.core\.JavaMainWrapper::runCore0%s %s at %sJavaMainWrapper\.java:[0-9]+"%(spaces_pattern, address_pattern, no_param_types_pattern, no_arg_values_pattern, package_pattern),
                       r"#4%s%s in com\.oracle\.svm\.core\.JavaMainWrapper::runCore%s %s at %sJavaMainWrapper\.java:[0-9]+"%(spaces_pattern, address_pattern, no_param_types_pattern, no_arg_values_pattern, package_pattern),
                       r"#5%scom\.oracle\.svm\.core\.JavaMainWrapper::doRun%s %s at %sJavaMainWrapper\.java:[0-9]+"%(spaces_pattern, param_types_pattern, arg_values_pattern, package_pattern),
                       r"#6%s(%s in )?com\.oracle\.svm\.core\.JavaMainWrapper::run%s %s at %sJavaMainWrapper\.java:[0-9]+"%(spaces_pattern, address_pattern, param_types_pattern, arg_values_pattern, package_pattern),
                       r"#7%scom\.oracle\.svm\.core\.code\.IsolateEnterStub::JavaMainWrapper_run_%s%s %s"%(spaces_pattern, hex_digits_pattern, param_types_pattern, arg_values_pattern)
                      ]
    if musl:
        # musl has a different entry point - drop the last two frames
        stacktraceRegex = stacktraceRegex[:-2]
    checker = Checker("backtrace hello.Hello::main", stacktraceRegex)
    checker.check(exec_string, skip_fails=False)

    # check input argument args is known
    exec_string = execute("info args")
    rexp = [r"args = %s"%(address_pattern)]
    checker = Checker("info args", rexp)

    # check local var greeter is not known
    exec_string = execute("info locals")
    rexp = [r"greeter = <optimized out>"]
    checker = Checker("info locals", rexp)

    if can_print_data:
        # print the contents of the arguments array which will be in rdi
        exec_string = execute("print /x *args")
        rexp = [r"%s = {"%(wildcard_pattern),
                r"%s<java.lang.Object> = {"%(spaces_pattern),
                r"%s<_objhdr> = {"%(spaces_pattern),
                r"%shub = %s"%(spaces_pattern, address_pattern),
                r"%sidHash = %s"%(spaces_pattern, address_pattern) if fixed_idhash_field else None,
                r"%s}, <No data fields>}, "%(spaces_pattern),
                r"%smembers of java\.lang\.String\[\]:"%(spaces_pattern),
                r"%slen = 0x0,"%(spaces_pattern),
                r"%sdata = %s"%(spaces_pattern, address_pattern),
                "}"]

        checker = Checker("print String[] args", rexp)

        checker.check(exec_string, skip_fails=False)

        # print the hub of the array and check it has a name field
        exec_string = execute("print /x *args->hub")
        if isolates:
            rexp = [r"%s = {"%(wildcard_pattern),
                    r"%s<java.lang.Class> = {"%(spaces_pattern),
                    r"%s<java.lang.Object> = {"%(spaces_pattern),
                    r"%s<_objhdr> = {"%(spaces_pattern),
                    r"%shub = %s"%(spaces_pattern, address_pattern),
                    r"%sidHash = %s"%(spaces_pattern, address_pattern) if fixed_idhash_field else None,
                    r"%s}, <No data fields>},"%(spaces_pattern),
                    r"%smembers of java\.lang\.Class:"%(spaces_pattern),
                    r"%sname = %s,"%(spaces_pattern, address_pattern),
                    r"%s}, <No data fields>}"%spaces_pattern]
        else:
            rexp = [r"%s = {"%(wildcard_pattern),
                    r"%s<java.lang.Object> = {"%(spaces_pattern),
                    r"%s<_objhdr> = {"%(spaces_pattern),
                    r"%shub = %s"%(spaces_pattern, address_pattern),
                    r"%sidHash = %s"%(spaces_pattern, address_pattern) if fixed_idhash_field else None,
                    r"%s}, <No data fields>},"%(spaces_pattern),
                    r"%smembers of java\.lang\.Class:"%(spaces_pattern),
                    r"%sname = %s,"%(spaces_pattern, address_pattern),
                    "}"]

        checker = Checker("print String[] hub", rexp)

        checker.check(exec_string, skip_fails=True)

        # print the hub name field and check it is String[]
        # n.b. the expected String text is not necessarily null terminated
        # so we need a wild card before the final quote
        exec_string = execute("x/s args->hub->name->value->data")
        checker = Checker("print String[] hub name",
                          r"%s:%s\"\[Ljava.lang.String;%s\""%(address_pattern, spaces_pattern, wildcard_pattern))
        checker.check(exec_string, skip_fails=False)

        # ensure we can reference static fields
        exec_string = execute("print 'java.math.BigDecimal'::BIG_TEN_POWERS_TABLE")
        if isolates:
            rexp = r"%s = \(_z_\.java.math.BigInteger\[\] \*\) %s"%(wildcard_pattern, address_pattern)
        else:
            rexp = r"%s = \(java.math.BigInteger\[\] \*\) %s"%(wildcard_pattern, address_pattern)

        checker = Checker("print static field value",rexp)
        checker.check(exec_string, skip_fails=False)

        # ensure we can dereference static fields
        exec_string = execute("print 'java.math.BigDecimal'::BIG_TEN_POWERS_TABLE->data[3]->mag->data[0]")
        checker = Checker("print static field value contents",
                          r"%s = 1000"%(wildcard_pattern))
        checker.check(exec_string, skip_fails=False)

        # ensure we can print class constants
        exec_string = execute("print /x 'hello.Hello.class'")
        rexp = [r"%s = {"%(wildcard_pattern),
                r"%s<java.lang.Object> = {"%(spaces_pattern),
                r"%s<_objhdr> = {"%(spaces_pattern),
                r"%shub = %s"%(spaces_pattern, address_pattern),
                r"%sidHash = %s"%(spaces_pattern, address_pattern) if fixed_idhash_field else None,
                r"%s}, <No data fields>},"%(spaces_pattern),
                r"%smembers of java\.lang\.Class:"%(spaces_pattern),
                r"%sname = %s,"%(spaces_pattern, address_pattern),
                "}"]

        checker = Checker("print hello.Hello.class", rexp)

        checker.check(exec_string, skip_fails=True)

        # ensure we can access fields of class constants
        exec_string = execute("print 'java.lang.String[].class'.name->value->data")
        rexp = r'%s = %s "\[Ljava.lang.String;'%(wildcard_pattern, address_pattern)

        checker = Checker("print 'java.lang.String[].class'.name->value->data", rexp)

        checker.check(exec_string)

        exec_string = execute("print 'long.class'.name->value->data")
        rexp = r'%s = %s "long'%(wildcard_pattern, address_pattern)

        checker = Checker("print 'long.class'.name->value->data", rexp)

        checker.check(exec_string)

        exec_string = execute("print 'byte[].class'.name->value->data")
        rexp = r'%s = %s "\[B'%(wildcard_pattern, address_pattern)

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
            r"72:%svoid hello.Hello\$NamedGreeter::greet\(%s\);"%(maybe_spaces_pattern,wildcard_pattern),
            r"File hello/Target_hello_Hello_DefaultGreeter\.java:",
            r"48:%svoid hello.Hello\$DefaultGreeter::greet\(%s\);"%(maybe_spaces_pattern,wildcard_pattern)]
    checker = Checker("info func greet", rexp)
    checker.check(exec_string)

    # step into method call
    execute("step")

    # list current line
    # expect "37	            if (args.length == 0) {"
    exec_string = execute("list")
    rexp = r"38%sif \(args\.length == 0\) {"%spaces_pattern
    checker = Checker('list hello.Hello$Greeter.greeter', rexp)
    checker.check(exec_string, skip_fails=False)

    # print details of greeter types
    exec_string = execute("ptype 'hello.Hello$NamedGreeter'")
    if isolates:
        rexp = [r"type = class hello\.Hello\$NamedGreeter : public hello\.Hello\$Greeter {",
                r"%sprivate:"%(spaces_pattern),
                r"%s_z_\.java\.lang\.String \*name;"%(spaces_pattern),
                r"",
                r"%spublic:"%(spaces_pattern),
                r"%svoid NamedGreeter\(java\.lang\.String \*\);"%(spaces_pattern),
                r"%svoid greet\(void\);"%(spaces_pattern),
                r"}"]
    else:
        rexp = [r"type = class hello\.Hello\$NamedGreeter : public hello\.Hello\$Greeter {",
                r"%sprivate:"%(spaces_pattern),
                r"%sjava\.lang\.String \*name;"%(spaces_pattern),
                r"",
                r"%spublic:"%(spaces_pattern),
                r"%svoid NamedGreeter\(java\.lang\.String \*\);"%(spaces_pattern),
                r"%svoid greet\(void\);"%(spaces_pattern),
                r"}"]

    checker = Checker('ptype NamedGreeter', rexp)
    checker.check(exec_string, skip_fails=False)

    exec_string = execute("ptype 'hello.Hello$Greeter'")
    rexp = [r"type = class hello\.Hello\$Greeter : public java\.lang\.Object {",
            r"%spublic:"%(spaces_pattern),
            r"%svoid Greeter\(void\);"%(spaces_pattern),
            r"%sstatic hello\.Hello\$Greeter \* greeter\(java\.lang\.String\[\] \*\);"%(spaces_pattern),
            r"}"]

    checker = Checker('ptype Greeter', rexp)
    checker.check(exec_string, skip_fails=False)

    exec_string = execute("ptype 'java.lang.Object'")
    rexp = [r"type = class java\.lang\.Object : public _objhdr {",
            r"%spublic:"%(spaces_pattern),
            r"%svoid Object\(void\);"%(spaces_pattern),
            r"%sboolean equals\(java\.lang\.Object \*\);"%(spaces_pattern),
            r"%sjava\.lang\.Class \* getClass\(void\);"%(spaces_pattern),
            r"%sint hashCode\(void\);"%(spaces_pattern),
            r"%svoid notify\(void\);"%(spaces_pattern),
            r"%svoid notifyAll\(void\);"%(spaces_pattern),
            r"%sjava\.lang\.String \* toString\(void\);"%(spaces_pattern),
            r"%svoid wait\(void\);"%(spaces_pattern),
            r"%svoid wait\(long\);"%(spaces_pattern),
            r"}"]

    checker = Checker('ptype Object', rexp)
    checker.check(exec_string, skip_fails=True)

    exec_string = execute("ptype _objhdr")
    if isolates:
        rexp = [r"type = struct _objhdr {",
                r"%s_z_\.java\.lang\.Class \*hub;"%(spaces_pattern),
                r"%sint idHash;"%(spaces_pattern) if fixed_idhash_field else None,
                r"}"]
    else:
        rexp = [r"type = struct _objhdr {",
                r"%sjava\.lang\.Class \*hub;"%(spaces_pattern),
                r"%sint idHash;"%(spaces_pattern) if fixed_idhash_field else None,
                r"}"]

    checker = Checker('ptype _objhdr', rexp)
    checker.check(exec_string, skip_fails=True)

    exec_string = execute("ptype 'java.lang.String[]'")
    if isolates:
        rexp = [r"type = class java.lang.String\[\] : public java.lang.Object {",
                r"%sint len;"%(spaces_pattern),
                r"%s_z_\.java\.lang\.String \*data\[0\];"%(spaces_pattern),
                r"}"]
    else:
        rexp = [r"type = class java.lang.String\[\] : public java.lang.Object {",
                r"%sint len;"%(spaces_pattern),
                r"%sjava\.lang\.String \*data\[0\];"%(spaces_pattern),
                r"}"]

    checker = Checker('ptype String[]', rexp)
    checker.check(exec_string, skip_fails=True)

    # run a backtrace
    exec_string = execute("backtrace")
    stacktraceRegex = [r"#0%shello\.Hello\$Greeter::greeter%s %s at hello/Hello\.java:38"%(spaces_pattern, param_types_pattern, arg_values_pattern),
                       r"#1%s%s in hello\.Hello::main%s %s at hello/Hello\.java:%d"%(spaces_pattern, address_pattern, param_types_pattern, arg_values_pattern, main_start),
                       r"#2%s(%s in )?java\.lang\.invoke\.LambdaForm\$DMH/s%s::invokeStatic(Init)?%s %s( at java/lang/invoke/%s:[0-9]+)?"%(spaces_pattern, address_pattern, hex_digits_pattern, param_types_pattern, arg_values_pattern, package_file_pattern),
                       r"#3%s(%s in )?com\.oracle\.svm\.core\.JavaMainWrapper::invokeMain%s %s at %sJavaMainWrapper\.java:[0-9]+"%(spaces_pattern, address_pattern, param_types_pattern, arg_values_pattern, package_pattern),
                       r"#4%s(%s in )?com\.oracle\.svm\.core\.JavaMainWrapper::runCore0%s %s at %sJavaMainWrapper\.java:[0-9]+"%(spaces_pattern, address_pattern, no_param_types_pattern, no_arg_values_pattern, package_pattern),
                       r"#5%s%s in com\.oracle\.svm\.core\.JavaMainWrapper::runCore%s %s at %sJavaMainWrapper\.java:[0-9]+"%(spaces_pattern, address_pattern, no_param_types_pattern, no_arg_values_pattern, package_pattern),
                       r"#6%scom\.oracle\.svm\.core\.JavaMainWrapper::doRun%s %s at %sJavaMainWrapper\.java:[0-9]+"%(spaces_pattern, param_types_pattern, arg_values_pattern, package_pattern),
                       r"#7%s(%s in )?com\.oracle\.svm\.core\.JavaMainWrapper::run%s %s at %sJavaMainWrapper\.java:[0-9]+"%(spaces_pattern, address_pattern, param_types_pattern, arg_values_pattern, package_pattern),
                       r"#8%scom\.oracle\.svm\.core\.code\.IsolateEnterStub::JavaMainWrapper_run_%s%s %s"%(spaces_pattern, hex_digits_pattern, param_types_pattern, arg_values_pattern)
                      ]
    if musl:
        # musl has a different entry point - drop the last two frames
        stacktraceRegex = stacktraceRegex[:-2]
    checker = Checker("backtrace hello.Hello.Greeter::greeter", stacktraceRegex)
    checker.check(exec_string, skip_fails=False)

    # now step into inlined code
    execute("next")

    # check we are still in hello.Hello$Greeter.greeter but no longer in hello.Hello.java
    exec_string = execute("backtrace 1")
    checker = Checker("backtrace inline",
                      [r"#0%shello\.Hello\$Greeter::greeter%s %s at (%s):%s"%(spaces_pattern, param_types_pattern, arg_values_pattern, package_file_pattern, digits_pattern)])
    matches = checker.check(exec_string, skip_fails=False)
    # n.b. can only get back here with one match
    match = matches[0]
    if match.group(1) == "hello.Hello.java":
        line = exec_string.replace("\n", "")
        print('bad match for output %d\n'%(line))
        print(checker)
        sys.exit(1)

    # set breakpoint at substituted method hello.Hello$DefaultGreeter::greet
    # expect "Breakpoint <n> at 0x[0-9a-f]+: file hello/Target_Hello_DefaultGreeter.java, line [0-9]+."
    exec_string = execute("break hello.Hello$DefaultGreeter::greet")
    rexp = r"Breakpoint %s at %s: file hello/Target_hello_Hello_DefaultGreeter\.java, line %s\."%(digits_pattern, address_pattern, digits_pattern)
    checker = Checker("break on substituted method", rexp)
    checker.check(exec_string, skip_fails=False)
    execute("delete breakpoints")

    # step out of the call to Greeter.greeter and then step forward
    # so the return value is assigned to local var greeter
    exec_string = execute("finish");
    exec_string = execute("step");

    # check argument args is not known
    exec_string = execute("info args")
    rexp = [r"args = <optimized out>"]
    checker = Checker("info args 2", rexp)

    # check local var greeter is known
    exec_string = execute("info locals")
    rexp = [r"greeter = %s"%(address_pattern)]
    checker = Checker("info locals 2", rexp)

    # set a break point at standard library PrintStream.println. Ideally we would like to break only at println(String)
    # however in Java 17 and GraalVM >21.3.0 this method ends up getting inlined and we can't (yet?!) set a breakpoint
    # only to a specific override of a method by specifying the parameter types when that method gets inlined.
    # As a result the breakpoint will be set at all println overrides.
    # expect "Breakpoint <n> at 0x[0-9a-f]+: java.io.PrintStream::println. ([0-9]+ locations)""
    exec_string = execute("break java.io.PrintStream::println")
    # we cannot be sure how much inlining will happen so we
    # specify a pattern for the number of locations
    rexp = r"Breakpoint %s at %s: java\.io\.PrintStream::println\. \(%s locations\)"%(digits_pattern, address_pattern, digits_pattern)
    checker = Checker('break println', rexp)
    checker.check(exec_string, skip_fails=False)

    execute("continue")

    exec_string = execute("info args")
    # we cannot be sure whether "this" or argument "x" are available
    # the call to println may get inlined in which case there is no
    # guarantee that the args won't be optimized away
    rexp = [r"this = %s"%(wildcard_pattern),
            r"%s = %s"%(varname_pattern, wildcard_pattern)]
    checker = Checker("info args println", rexp)
    checker.check(exec_string)

    exec_string = execute("ptype this");
    # the debugger shoudl still know the type of "this"
    rexp = [r"type = class java\.io\.PrintStream : public java\.io\.FilterOutputStream {"]
    checker = Checker("ptype this", rexp)
    checker.check(exec_string);

    ###
    # Tests for inlined methods
    ###

    # print details of Hello type
    exec_string = execute("ptype 'hello.Hello'")
    rexp = [r"type = class hello\.Hello : public java\.lang\.Object {",
            # ptype lists inlined methods although they are not listed with info func
            r"%sprivate:"%spaces_pattern,
            r"%sstatic void inlineA\(void\);"%spaces_pattern,
            r"%sstatic void inlineCallChain\(void\);"%spaces_pattern,
            r"%sstatic void inlineFrom\(void\);"%spaces_pattern,
            r"%sstatic void inlineHere\(int\);"%spaces_pattern,
            r"%sstatic void inlineIs\(void\);"%spaces_pattern,
            r"%sstatic void inlineMee\(void\);"%spaces_pattern,
            r"%sstatic void inlineMixTo\(int\);"%spaces_pattern,
            r"%sstatic void inlineMoo\(void\);"%spaces_pattern,
            r"%sstatic void inlineReceiveConstants\(byte, int, long, java\.lang\.String \*, float, double\);"%spaces_pattern,
            r"%sstatic void inlineTailRecursion\(int\);"%spaces_pattern,
            r"%sstatic void inlineTo\(int\);"%spaces_pattern,
            r"%sstatic java\.lang\.String \* lambda\$(static\$)?%s\(void\);"%(spaces_pattern, digits_pattern),
            r"%spublic:"%spaces_pattern,
            r"%sstatic void main\(java\.lang\.String\[\] \*\);"%spaces_pattern,
            r"%sprivate:"%spaces_pattern,
            r"%sstatic void noInlineFoo\(void\);"%spaces_pattern,
            r"%sstatic void noInlineHere\(int\);"%spaces_pattern,
            r"%sstatic void noInlineManyArgs\(int, byte, short, char, boolean, int, int, long, int, long, float, float, float, float, double, float, float, float, float, double, boolean, float\);"%spaces_pattern,
            r"%sstatic void noInlinePassConstants\(void\);"%spaces_pattern,
            r"%sstatic void noInlineTest\(void\);"%spaces_pattern,
            r"%sstatic void noInlineThis\(void\);"%spaces_pattern,
            r"}"]
    checker = Checker('ptype hello.Hello', rexp)
    checker.check(exec_string, skip_fails=False)

    # list methods matching regural expression "nline", inline methods are not listed because they lack a definition
    # (this is true for C/C++ as well)
    exec_string = execute("info func nline")
    rexp = [r"All functions matching regular expression \"nline\":",
            r"File hello/Hello\.java:",
            r"%svoid hello\.Hello::noInlineFoo\(\);"%line_number_prefix_pattern,
            r"%svoid hello\.Hello::noInlineHere\(int\);"%line_number_prefix_pattern,
            r"%svoid hello\.Hello::noInlineTest\(\);"%line_number_prefix_pattern,
            r"%svoid hello\.Hello::noInlineThis\(\);"%line_number_prefix_pattern]
    checker = Checker('info func nline', rexp)
    checker.check(exec_string)

    # list inlineIs and inlineA and check that the listing maps to the inlined code instead of the actual code,
    # although not ideal this is how GDB treats inlined code in C/C++ as well
    rexp = [r"103%sinlineA\(\);"%spaces_pattern]
    checker = Checker('list inlineIs', rexp)
    checker.check(execute("list inlineIs"))
    # List inlineA may actually return more locations dependening on inlining decisions, but noInlineTest
    # always needs to be listed
    rexp = [r"108%snoInlineTest\(\);"%spaces_pattern]
    checker = Checker('list inlineA', rexp)
    checker.check(execute("list inlineA"))

    execute("delete breakpoints")
    # Set breakpoint at inlined method and step through its nested inline methods
    exec_string = execute("break hello.Hello::inlineIs")
    # Dependening on inlining decisions, there are either two or one locations
    rexp = r"Breakpoint %s at %s: (hello\.Hello::inlineIs\. \(2 locations\)|file hello/Hello\.java, line 103\.)"%(digits_pattern, address_pattern)
    checker = Checker('break inlineIs', rexp)
    checker.check(exec_string, skip_fails=False)

    execute("continue")
    exec_string = execute("list")
    rexp = [r"103%sinlineA\(\);"%spaces_pattern]
    checker = Checker('hit break at inlineIs', rexp)
    checker.check(exec_string, skip_fails=False)
    execute("step")
    exec_string = execute("list")
    rexp = [r"108%snoInlineTest\(\);"%spaces_pattern]
    checker = Checker('step in inlineA', rexp)
    checker.check(exec_string, skip_fails=False)
    exec_string = execute("backtrace 4")
    rexp = [r"#0%shello\.Hello::inlineA%s %s at hello/Hello\.java:108"%(spaces_pattern, no_param_types_pattern, no_arg_values_pattern),
            r"#1%shello\.Hello::inlineIs%s %s at hello/Hello\.java:103"%(spaces_pattern, no_param_types_pattern, no_arg_values_pattern),
            r"#2%shello\.Hello::noInlineThis%s %s at hello/Hello\.java:98"%(spaces_pattern, no_param_types_pattern, no_arg_values_pattern),
            r"#3%s%s in hello\.Hello::main%s %s at hello/Hello\.java:%d"%(spaces_pattern, address_pattern, param_types_pattern, arg_values_pattern, main_noinline)]
    checker = Checker('backtrace inlineMee', rexp)
    checker.check(exec_string, skip_fails=False)

    execute("delete breakpoints")
    exec_string = execute("break hello.Hello::noInlineTest")
    rexp = r"Breakpoint %s at %s: file hello/Hello\.java, line 113\."%(digits_pattern, address_pattern)
    checker = Checker('break noInlineTest', rexp)
    checker.check(exec_string, skip_fails=False)

    execute("continue")
    exec_string = execute("list")
    rexp = r"113%sSystem.out.println\(\"This is a test\"\);"%spaces_pattern
    checker = Checker('hit breakpoint in noInlineTest', rexp)
    checker.check(exec_string, skip_fails=False)
    exec_string = execute("backtrace 5")
    rexp = [r"#0%shello\.Hello::noInlineTest%s %s at hello/Hello\.java:113"%(spaces_pattern, no_param_types_pattern, no_arg_values_pattern),
            r"#1%s%s in hello\.Hello::inlineA%s %s at hello/Hello\.java:108"%(spaces_pattern, address_pattern, no_param_types_pattern, no_arg_values_pattern),
            r"#2%shello\.Hello::inlineIs%s %s at hello/Hello\.java:103"%(spaces_pattern, no_param_types_pattern, no_arg_values_pattern),
            r"#3%shello\.Hello::noInlineThis%s %s at hello/Hello\.java:98"%(spaces_pattern, no_param_types_pattern, no_arg_values_pattern),
            r"#4%s%s in hello\.Hello::main%s %s at hello/Hello\.java:%d"%(spaces_pattern, address_pattern, param_types_pattern, arg_values_pattern, main_noinline)]
    checker = Checker('backtrace in inlineMethod', rexp)
    checker.check(exec_string, skip_fails=False)

    execute("delete breakpoints")
    # Set breakpoint at method with inline and not-inlined invocation in same line
    exec_string = execute("break hello.Hello::inlineFrom")
    rexp = r"Breakpoint %s at %s: file hello/Hello\.java, line 119."%(digits_pattern, address_pattern)
    checker = Checker('break inlineFrom', rexp)
    checker.check(exec_string, skip_fails=False)

    exec_string = execute("info break")
    rexp = [r"%s%sbreakpoint%skeep%sy%s%s in hello\.Hello::inlineFrom\(\) at hello/Hello\.java:119"%(digits_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, address_pattern)]
    checker = Checker('info break inlineFrom', rexp)
    checker.check(exec_string)

    execute("delete breakpoints")
    exec_string = execute("break Hello.java:135")
    rexp = r"Breakpoint %s at %s: (Hello\.java:135\. \(2 locations\)|file hello/Hello\.java, line 135\.)"%(digits_pattern, address_pattern)
    checker = Checker('break Hello.java:135', rexp)
    checker.check(exec_string)

    execute("continue 5")
    exec_string = execute("backtrace 14")
    rexp = [r"#0%shello\.Hello::inlineMixTo%s %s at hello/Hello\.java:135"%(spaces_pattern, param_types_pattern, arg_values_pattern),
            r"#1%shello\.Hello::noInlineHere%s %s at hello/Hello\.java:127"%(spaces_pattern, param_types_pattern, arg_values_pattern),
            r"#2%s(%s in)? hello\.Hello::inlineMixTo%s %s at hello/Hello\.java:133"%(spaces_pattern, address_pattern, param_types_pattern, arg_values_pattern),
            r"#3%shello\.Hello::noInlineHere%s %s at hello/Hello\.java:127"%(spaces_pattern, param_types_pattern, arg_values_pattern),
            r"#4%s(%s in)? hello\.Hello::inlineMixTo%s %s at hello/Hello\.java:133"%(spaces_pattern, address_pattern, param_types_pattern, arg_values_pattern),
            r"#5%shello\.Hello::noInlineHere%s %s at hello/Hello\.java:127"%(spaces_pattern, param_types_pattern, arg_values_pattern),
            r"#6%s(%s in)? hello\.Hello::inlineMixTo%s %s at hello/Hello\.java:133"%(spaces_pattern, address_pattern, param_types_pattern, arg_values_pattern),
            r"#7%shello\.Hello::noInlineHere%s %s at hello/Hello\.java:127"%(spaces_pattern, param_types_pattern, arg_values_pattern),
            r"#8%s(%s in)? hello\.Hello::inlineMixTo%s %s at hello/Hello\.java:133"%(spaces_pattern, address_pattern, param_types_pattern, arg_values_pattern),
            r"#9%shello\.Hello::noInlineHere%s %s at hello/Hello\.java:127"%(spaces_pattern, param_types_pattern, arg_values_pattern),
            r"#10%s(%s in)? hello\.Hello::inlineMixTo%s %s at hello/Hello\.java:133"%(spaces_pattern, address_pattern, param_types_pattern, arg_values_pattern),
            r"#11%shello\.Hello::noInlineHere%s %s at hello/Hello\.java:127"%(spaces_pattern, param_types_pattern, arg_values_pattern),
            r"#12%s(%s in)? hello\.Hello::inlineFrom%s %s at hello/Hello\.java:119"%(spaces_pattern, address_pattern, no_param_types_pattern, no_arg_values_pattern),
            r"#13%shello\.Hello::main%s %s at hello/Hello\.java:%d"%(spaces_pattern, param_types_pattern, arg_values_pattern, main_inlinefrom)]
    checker = Checker('backtrace in recursive inlineMixTo', rexp)
    checker.check(exec_string, skip_fails=False)

    execute("delete breakpoints")
    exec_string = execute("break Hello.java:148")
    # we cannot be sure how much inlining will happen so we
    # specify a pattern for the number of locations
    rexp = r"Breakpoint %s at %s: Hello\.java:148\. \(%s locations\)"%(digits_pattern, address_pattern, digits_pattern)
    checker = Checker('break Hello.java:148', rexp)
    checker.check(exec_string)

    execute("continue")
    exec_string = execute("backtrace 14")
    # we cannot be sure exactly how much inlining happens
    # which means the format of the frame display may vary from
    # one build to the next. so we use a generic match after the
    # first pair.
    rexp = [r"#0%shello\.Hello::inlineTo%s %s at hello/Hello\.java:148"%(spaces_pattern, param_types_pattern, arg_values_pattern),
            r"#1%s(%s in)? hello\.Hello::inlineHere%s %s at hello/Hello\.java:140"%(spaces_pattern, address_pattern, param_types_pattern, arg_values_pattern),
            r"#2%shello\.Hello::inlineTo%s %s at hello/Hello\.java:146"%(wildcard_pattern, param_types_pattern, arg_values_pattern),
            r"#3%shello\.Hello::inlineHere%s %s at hello/Hello\.java:140"%(wildcard_pattern, param_types_pattern, arg_values_pattern),
            r"#4%shello\.Hello::inlineTo%s %s at hello/Hello\.java:146"%(wildcard_pattern, param_types_pattern, arg_values_pattern),
            r"#5%shello\.Hello::inlineHere%s %s at hello/Hello\.java:140"%(wildcard_pattern, param_types_pattern, arg_values_pattern),
            r"#6%shello\.Hello::inlineTo%s %s at hello/Hello\.java:146"%(wildcard_pattern, param_types_pattern, arg_values_pattern),
            r"#7%shello\.Hello::inlineHere%s %s at hello/Hello\.java:140"%(wildcard_pattern, param_types_pattern, arg_values_pattern),
            r"#8%shello\.Hello::inlineTo%s %s at hello/Hello\.java:146"%(wildcard_pattern, param_types_pattern, arg_values_pattern),
            r"#9%shello\.Hello::inlineHere%s %s at hello/Hello\.java:140"%(wildcard_pattern, param_types_pattern, arg_values_pattern),
            r"#10%shello\.Hello::inlineTo%s %s at hello/Hello\.java:146"%(wildcard_pattern, param_types_pattern, arg_values_pattern),
            r"#11%shello\.Hello::inlineHere%s %s at hello/Hello\.java:140"%(wildcard_pattern, param_types_pattern, arg_values_pattern),
            r"#12%shello\.Hello::inlineFrom%s %s at hello/Hello\.java:121"%(spaces_pattern, no_param_types_pattern,no_arg_values_pattern),
            r"#13%shello\.Hello::main%s %s at hello/Hello\.java:%d"%(spaces_pattern, param_types_pattern, arg_values_pattern, main_inlinefrom)]
    checker = Checker('backtrace in recursive inlineTo', rexp)
    checker.check(exec_string, skip_fails=False)

    execute("delete breakpoints")
    exec_string = execute("break Hello.java:154")
    # we cannot be sure how much inlining will happen so we
    # specify a pattern for the number of locations
    rexp = r"Breakpoint %s at %s: Hello\.java:154\. \(%s locations\)"%(digits_pattern, address_pattern, digits_pattern)
    checker = Checker('break Hello.java:154', rexp)
    checker.check(exec_string)

    execute("continue 5")
    exec_string = execute("backtrace 8")
    # we cannot be sure exactly how much inlining happens
    # which means the format of the frame display may vary from
    # one build to the next. so we use a generic match after the
    # first one.
    rexp = [r"#0%shello\.Hello::inlineTailRecursion%s %s at hello/Hello\.java:154"%(spaces_pattern, param_types_pattern, arg_values_pattern),
            r"#1%shello\.Hello::inlineTailRecursion%s %s at hello/Hello\.java:157"%(wildcard_pattern, param_types_pattern, arg_values_pattern),
            r"#2%shello\.Hello::inlineTailRecursion%s %s at hello/Hello\.java:157"%(wildcard_pattern, param_types_pattern, arg_values_pattern),
            r"#3%shello\.Hello::inlineTailRecursion%s %s at hello/Hello\.java:157"%(wildcard_pattern, param_types_pattern, arg_values_pattern),
            r"#4%shello\.Hello::inlineTailRecursion%s %s at hello/Hello\.java:157"%(wildcard_pattern, param_types_pattern, arg_values_pattern),
            r"#5%shello\.Hello::inlineTailRecursion%s %s at hello/Hello\.java:157"%(wildcard_pattern, param_types_pattern, arg_values_pattern),
            r"#6%shello\.Hello::inlineFrom%s %s at hello/Hello\.java:122"%(spaces_pattern, no_param_types_pattern, no_arg_values_pattern),
            r"#7%shello\.Hello::main%s %s at hello/Hello\.java:%d"%(spaces_pattern, param_types_pattern, arg_values_pattern, main_inlinefrom)]
    checker = Checker('backtrace in recursive inlineTailRecursion', rexp)
    checker.check(exec_string, skip_fails=False)

    # on aarch64 the initial break occurs at the stack push
    # but we need to check the args before and after the stack push
    # so we need to use the examine command to identify the start
    # address of the method and place an instruction break at that
    # address to ensure we have the very first instruction
    exec_string = execute("x/i 'hello.Hello'::noInlineManyArgs")
    rexp = r"%s0x(%s)%shello.Hello::noInlineManyArgs%s"%(spaces_pattern, hex_digits_pattern, wildcard_pattern, wildcard_pattern)
    checker = Checker('x/i hello.Hello::noInlineManyArgs', rexp)
    matches = checker.check(exec_string)
    # n.b can ony get here with one match
    match = matches[0]
    bp_address = int(match.group(1), 16)
    print("bp = %s %x"%(match.group(1), bp_address))

    # exec_string = execute("break hello.Hello::noInlineManyArgs")
    exec_string = execute("break *0x%x"%bp_address)
    rexp = r"Breakpoint %s at %s: file hello/Hello\.java, line 163\."%(digits_pattern, address_pattern)
    checker = Checker(r"break *0x%x"%bp_address, rexp)
    checker.check(exec_string)
    #rexp = r"Breakpoint %s at %s: file hello/Hello\.java, line 163\."%(digits_pattern, address_pattern)
    #checker = Checker('break hello.Hello::noInlineManyArgs', rexp)
    #checker.check(exec_string)
    execute("continue")
    exec_string = execute("info args")
    rexp =[r"i0 = 0",
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
        instruction_adjusts_sp_register_pattern = r"%sstp%sx%s, x%s, \[sp,"%(wildcard_pattern, spaces_pattern, digits_pattern, digits_pattern)
    else:
        instruction_adjusts_sp_register_pattern = r"%s,%%rsp"%(wildcard_pattern)
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
            print("method prologue is unexpectedly long, did not reach end after %s stepis" % num_stepis)
            sys.exit(1)

    exec_string = execute("info args")
    rexp =[r"i0 = 0",
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

    execute("delete breakpoints");

    exec_string = execute("break hello.Hello::inlineReceiveConstants")
    # we cannot be sure how much inlining will happen so we
    # specify a pattern for the number of locations
    rexp = r"Breakpoint %s at %s: hello\.Hello::inlineReceiveConstants\. \(%s locations\)"%(digits_pattern, address_pattern, digits_pattern)
    checker = Checker('break hello.Hello::inlineReceiveConstants', rexp)
    checker.check(exec_string)

    execute("continue")

    exec_string = execute("info args")
    rexp =[r"b = 1 '\\001'",
           r"i = 2",
           r"l = 3",
           r"s = %s"%(address_pattern),
           r"f = 4",
           r"d = 5"]
    checker = Checker('info args 3', rexp)
    checker.check(exec_string)

    execute("set print elements 10")
    exec_string = execute("x/s s->value->data");
    execute("set print elements unlimited")
    rexp=[r'%s:%s"stringtext"'%(address_pattern, spaces_pattern)];
    checker = Checker('x/s s->value->data', rexp)
    checker.check(exec_string, skip_fails=True)

    exec_string = execute("next 3")
    exec_string = execute("info locals")
    rexp =[r"n = 6",
           r"q = 20",
           r"t = %s"%(address_pattern)]
    checker = Checker('info locals 3', rexp)
    checker.check(exec_string)

    execute("set print elements 11")
    exec_string = execute("x/s t->value->data");
    execute("set print elements unlimited")
    rexp=[r'%s:%s"stringtext!"'%(address_pattern, spaces_pattern)];
    checker = Checker('x/s t->value->data', rexp)
    checker.check(exec_string, skip_fails=True)

    # look up lambda method
    exec_string = execute("info func hello.Hello::lambda")
    rexp = [r'All functions matching regular expression "hello\.Hello::lambda":',
            r"File hello/Hello\.java:",
            r"%sjava\.lang\.String \*(hello\.Hello::lambda\$(static\$)?%s)%s;"%(line_number_prefix_pattern, digits_pattern, no_param_types_pattern)]
    checker = Checker("info func hello.Hello::lambda", rexp)
    matches = checker.check(exec_string)
    # lambda's name depends on the underlying JDK, so we get it from gdb's output instead of hardcoding it
    lambdaName = matches[2].group(1)

    execute("delete breakpoints");

    exec_string = execute("break " + lambdaName)
    rexp = r"Breakpoint %s at %s: (file hello/Hello.java, line 209|hello.Hello::lambda($static)?$%s. \(%s locations\))"%(digits_pattern, address_pattern, digits_pattern, digits_pattern)
    checker = Checker('break ' + lambdaName, rexp)
    checker.check(exec_string)

    execute("continue");
    exec_string = execute("list")
    rexp = r"209%sStringBuilder sb = new StringBuilder\(\"lambda\"\);"%spaces_pattern
    checker = Checker('hit breakpoint in lambda', rexp)
    checker.check(exec_string, skip_fails=False)

    execute("delete breakpoints");

    exec_string = execute("break Hello.java:210")
    rexp = r"Breakpoint %s at %s: file hello/Hello.java, line 210"%(digits_pattern, address_pattern)
    checker = Checker('break Hello.java:210', rexp)
    checker.check(exec_string)

    execute("continue");
    exec_string = execute("list")
    rexp = r"210%ssb\.append\(System\.getProperty\(\"never_optimize_away\", \"Text\"\)\);"%spaces_pattern
    checker = Checker('hit breakpoint 2 in lambda', rexp)
    checker.check(exec_string, skip_fails=False)
    exec_string = execute("backtrace 3")
    rexp = [r"#0%shello\.Hello::lambda\$(static\$)?0%s %s at hello/Hello\.java:210"%(spaces_pattern, no_param_types_pattern, no_arg_values_pattern),
            r"#1%s%s in hello\.Hello\$\$Lambda((\$%s/0x)|(\$)|(\.0x|/0x))?%s::get%s at hello/Hello\.java:238"%(spaces_pattern, address_pattern, digits_pattern, hex_digits_pattern, wildcard_pattern),
            r"#2%shello\.Hello::main%s %s at hello/Hello\.java:238"%(spaces_pattern, param_types_pattern, arg_values_pattern)]
    checker = Checker('backtrace in lambda', rexp)
    checker.check(exec_string, skip_fails=False)

    execute("delete breakpoints");

    ### Now check foreign debug type info

    # check type information is reported correctly

    exec_string=execute("info types com.oracle.svm.test.debug.CStructTests\$")
    rexp = [r"%stypedef composite_struct \* com\.oracle\.svm\.test\.debug\.CStructTests\$CompositeStruct;"%spaces_pattern,
            r"%stypedef int32_t \* com\.oracle\.svm\.test\.debug\.CStructTests\$MyCIntPointer;"%spaces_pattern,
            r"%stypedef simple_struct \* com\.oracle\.svm\.test\.debug\.CStructTests\$SimpleStruct;"%spaces_pattern,
            r"%stypedef simple_struct2 \* com\.oracle\.svm\.test\.debug\.CStructTests\$SimpleStruct2;"%spaces_pattern,
            r"%stypedef weird \* com\.oracle\.svm\.test\.debug\.CStructTests\$Weird;"%spaces_pattern]
    checker = Checker("info types com.oracle.svm.test.debug.CStructTests\$", rexp)
    checker.check(exec_string)

    # Print various foreign struct types and check they have the correct layout
    exec_string = execute("ptype /o 'com.oracle.svm.test.debug.CStructTests$CompositeStruct'")
    rexp = [r"type = struct composite_struct {",
            r"/\*%s0%s\|%s1%s\*/%sbyte c1;"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%sXXX%s3-byte hole%s\*/"%(spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%s4%s\|%s8%s\*/%sstruct simple_struct {"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%s4%s\|%s4%s\*/%sint first;"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%s8%s\|%s4%s\*/%sint second;"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"%s/\* total size \(bytes\):%s8 \*/"%(spaces_pattern, spaces_pattern),
            r"%s} c2;"%(spaces_pattern),
            r"/\*%s12%s\|%s4%s\*/%sint c3;"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%s16%s\|%s16%s\*/%sstruct simple_struct2 {"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%s16%s\|%s1%s\*/%sbyte alpha;"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%sXXX%s7-byte hole%s\*/"%(spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%s24%s\|%s8%s\*/%slong beta;"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"%s/\* total size \(bytes\):%s16 \*/"%(spaces_pattern, spaces_pattern),
            r"%s} c4;"%(spaces_pattern),
            r"/\*%s32%s\|%s2%s\*/%sshort c5;"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%sXXX%s6-byte padding%s\*/"%(spaces_pattern, spaces_pattern, spaces_pattern),
            r"%s/\* total size \(bytes\):%s40 \*/"%(spaces_pattern, spaces_pattern),
            r"%s} \*"%(spaces_pattern)]
    checker = Checker("ptype 'com.oracle.svm.test.debug.CStructTests$CompositeStruct'", rexp)
    checker.check(exec_string)

    exec_string = execute("ptype /o 'com.oracle.svm.test.debug.CStructTests$Weird'")
    rexp = [r"type = struct weird {",
            r"/\*%s0%s\|%s2%s\*/%sshort f_short;"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%sXXX%s6-byte hole%s\*/"%(spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%s8%s\|%s4%s\*/%sint f_int;"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%sXXX%s4-byte hole%s\*/"%(spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%s16%s\|%s8%s\*/%slong f_long;"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%s24%s\|%s4%s\*/%sfloat f_float;"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%sXXX%s4-byte hole%s\*/"%(spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%s32%s\|%s8%s\*/%sdouble f_double;"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%s40%s\|%s32%s\*/%sint32_t a_int\[8\];"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%s72%s\|%s12%s\*/%s(u)?int8_t a_char\[12\];"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%sXXX%s4-byte padding%s\*/"%(spaces_pattern, spaces_pattern, spaces_pattern),
            r"%s/\* total size \(bytes\):%s88 \*/"%(spaces_pattern, spaces_pattern),
            r"%s} \*"%(spaces_pattern)]
    checker = Checker("ptype 'com.oracle.svm.test.debug.CStructTests$Weird'", rexp)
    checker.check(exec_string)


    # check foreign data is printed correctly if we can

    if can_print_data:
        # set a break point at com.oracle.svm.test.debug.CStructTests::free
        exec_string = execute("break com.oracle.svm.test.debug.CStructTests::free")
        rexp = r"Breakpoint %s at %s: file com/oracle/svm/test/debug/CStructTests\.java, line %s\."%(digits_pattern, address_pattern, digits_pattern)
        checker = Checker('break free', rexp)
        checker.check(exec_string)

        # continue the program to the breakpoint
        execute("continue")

        # check the argument
        exec_string = execute("print *('com.oracle.svm.test.debug.CStructTests$CompositeStruct')ptr")
        rexp = [r"%s = {"%wildcard_pattern,
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

    exec_string = execute("delete breakpoints")

    # place a break point at the first instruction of method
    # CStructTests::testMixedArguments
    exec_string = execute("x/i 'com.oracle.svm.test.debug.CStructTests'::testMixedArguments")
    rexp = r"%s0x(%s)%scom.oracle.svm.test.debug.CStructTests::testMixedArguments%s"%(spaces_pattern, hex_digits_pattern, wildcard_pattern, wildcard_pattern)
    checker = Checker('x/i CStructTests::testMixedArguments', rexp)
    matches = checker.check(exec_string)
    # n.b can ony get here with one match
    match = matches[0]
    bp_address = int(match.group(1), 16)
    print("bp = %s %x"%(match.group(1), bp_address))

    exec_string = execute("break *0x%x"%bp_address)
    rexp = r"Breakpoint %s at %s: file com/oracle/svm/test/debug/CStructTests\.java, line %s\."%(digits_pattern, address_pattern, digits_pattern)
    checker = Checker(r"break *0x%x"%bp_address, rexp)
    checker.check(exec_string)

    # continue the program to the breakpoint
    execute("continue")

    exec_string = execute("info args")
    rexp = [r"m1 = 0x%s"%(hex_digits_pattern), 
            r"s = 1",
            r"ss1 = 0x%s"%(hex_digits_pattern),
            r"l = 123456789",
            r"m2 = 0x%s"%(hex_digits_pattern),
            r"ss2 = 0x%s"%(hex_digits_pattern),
            r"m3 = 0x%s"%(hex_digits_pattern)]
    checker = Checker('info args CStructTests::testMixedArguments', rexp)
    checker.check(exec_string)

    exec_string = execute("p m1->value->data")
    rexp = [r'\$%s = 0x%s "a message in a bottle"'%(digits_pattern, hex_digits_pattern)]
    checker = Checker('p *m1->value->data', rexp)
    checker.check(exec_string)

    exec_string = execute("p m2->value->data")
    rexp = [r'\$%s = 0x%s "a ship in a bottle"'%(digits_pattern, hex_digits_pattern)]
    checker = Checker('p *m1->value->data', rexp)
    checker.check(exec_string)

    exec_string = execute("p m3->value->data")
    rexp = [r'\$%s = 0x%s "courage in a bottle"'%(digits_pattern, hex_digits_pattern)]
    checker = Checker('p *m1->value->data', rexp)
    checker.check(exec_string)

    exec_string = execute("p *ss1")
    rexp = [r"\$%s = {"%(digits_pattern),
            r"%sfirst = 1,"%(spaces_pattern),
            r"%ssecond = 2"%(spaces_pattern),
            r"}"]
    checker = Checker('p *ss1', rexp)
    checker.check(exec_string)

    exec_string = execute("p *ss2")
    rexp = [r"\$%s = {"%(digits_pattern),
            r"%salpha = 99 'c',"%(spaces_pattern),
            r"%sbeta = 100"%(spaces_pattern),
            r"}"]
    checker = Checker('p *ss1', rexp)
    checker.check(exec_string)

    print(execute("quit 0"))

test()
