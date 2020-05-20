# pylint: skip-file
#
# A test script for use from gdb. It can be sued to drive execution of
# a native image version of test app Hello and check that the debug
# info is valid.
#
# Assumes you have already executed
#
# $ javac hello/Hello.java
# $ mx native-image -H:GenerateDebugInfo=1 hello.hello
#
# Run test
#
# gdb -d /path/to/sources/src -d /path/to/sources/graal -d /path/to/sources/jdk -x testhello.py /path/to/hello
#
# exit status 0 means all is well 1 means test failed
#
# n.b. assumes the sourcefile cache is in local dir sources

import re
import sys

# A helper class which checks that a sequence of lines of output
# from a gdb command matches a sequence of per-line regular
# expressions

class Checker:
    # Create a checker to check gdb command output text.
    # name - string to help identify the check if we have a failure.
    # regexps - a list of regular expressions which must match.
    # successive lines of checked
    def __init__(self, name, regexps):
        self.name = name
        compiled = []
        if not isinstance(regexps, list):
            regexps = [regexps]
        for regexp in regexps:
            compiled.append(re.compile(regexp))
        self.rexps = compiled

    # Check that successive lines of a gdb command's output text
    # match the corresponding regexp patterns provided when this
    # Checker was created.
    # text - the full output of a gdb comand run by calling
    # gdb.execute and passing to_string = True.
    # Exits with status 1 if there are less lines in the text
    # than regexp patterns or if any line fails to match the
    # corresponding pattern otherwise prints the text and returns
    # the set of matches.
    def check(self, text, skip_fails=True):
        lines = text.split('\n')
        rexps = self.rexps
        num_lines = len(lines)
        num_rexps = len(rexps)
        line_idx = 0
        matches = []
        for i in range(0, (num_rexps)):
            rexp = rexps[i]
            match = None
            while line_idx < num_lines and match is None:
                line = lines[line_idx]
                match = rexp.match(line)
                if  match is None:
                    if not skip_fails:
                        print('Checker %s: match %d failed at line %d %s\n'%(self.name, i, line_idx, line))
                        print(self)
                        print(text)
                        sys.exit(1)
                else:
                    matches.append(match)
                line_idx += 1
        if len(matches) < num_rexps:
            print('Checker %s: insufficient matching lines %d for regular expressions %d'%(self.name, len(matches), num_rexps))
            print(self)
            print(text)
            sys.exit(1)
        print(text)
        return matches

    # Format a Checker as a string
    def __str__(self):
        rexps = self.rexps
        result = 'Checker %s '%(self.name)
        result += '{\n'
        for rexp in rexps:
            result += '  %s\n'%(rexp)
        result += '}\n'
        return result

def execute(command):
    print('(gdb) %s'%(command))
    return gdb.execute(command, to_string=True)

# Configure this gdb session

# ensure file listings show only the current line
execute("set listsize 1")

# Start of actual test code
#

def test():

    # disable prompting to continue output
    execute("set pagination off")
    # set a break point at hello.Hello::main
    # expect "Breakpoint 1 at 0x[0-9a-f]+: file hello.Hello.java, line 64."
    exec_string = execute("break hello.Hello::main")
    rexp = "Breakpoint 1 at 0x([0-9a-f]+): file hello/Hello\\.java, line 64\\."
    checker = Checker('break main', rexp)
    checker.check(exec_string)

    # run the program
    execute("run")

    # list the line at the breakpoint
    # expect "64	        Greeter greeter = Greeter.greeter(args);"
    exec_string = execute("list")
    checker = Checker("list bp 1", "64[ \t]+Greeter greeter = Greeter\\.greeter\\(args\\);")
    checker.check(exec_string, skip_fails=False)

    # run a backtrace
    # expect "#0  hello.Hello::main(java.lang.String[]).* at hello.Hello.java:64"
    # expect "#1  0x[0-9a-f]+ in com.oracle.svm.core.code.IsolateEnterStub::JavaMainWrapper_run_.* at [a-z/]+/JavaMainWrapper.java:[0-9]+"
    exec_string = execute("backtrace")
    checker = Checker("backtrace hello.Hello::main",
                      ["#0[ \t]+hello\\.Hello::main\\(java\\.lang\\.String\\[\\]\\).* at hello/Hello\\.java:64",
                       "#1[ \t]+0x[0-9a-f]+ in com\\.oracle\\.svm\\.core\\.code\\.IsolateEnterStub::JavaMainWrapper_run_.* at [a-z/]+/JavaMainWrapper\\.java:[0-9]+"
                      ])
    checker.check(exec_string, skip_fails=False)

    # look up PrintStream::println methods
    # expect "All functions matching regular expression "java.io.PrintStream::println":"
    # expect ""
    # expect "File java.base/java/io/PrintStream.java:"
    # expect "      void java.io.PrintStream::println(java.lang.Object)(void);"
    # expect "      void java.io.PrintStream::println(java.lang.String)(void);"
    exec_string = execute("info func java.io.PrintStream::println")
#    checker = Checker("info func java.io.PrintStream::println",
#                      ["All functions matching regular expression \"java\\.io\\.PrintStream::println\":",
#                       "",
#                       "File .*java/io/PrintStream.java:",
#                       "[ \t]*void java.io.PrintStream::println\\(java\\.lang\\.Object\\)\\(void\\);",
#                       "[ \t]*void java.io.PrintStream::println\\(java\\.lang\\.String\\)\\(void\\);",
#                      ])
    checker = Checker("info func java.io.PrintStream::println",
                      "[ \t]*void java.io.PrintStream::println\\(java\\.lang\\.String\\)")
    checker.check(exec_string)

    # set a break point at PrintStream::println(String)
    # expect "Breakpoint 2 at 0x[0-9a-f]+: java.base/java/io/PrintStream.java, line [0-9]+."
    exec_string = execute("break java.io.PrintStream::println(java.lang.String)")
    rexp = "Breakpoint 2 at 0x([0-9a-f]+): file .*java/io/PrintStream\\.java, line [0-9]+\\."
    checker = Checker('break println', rexp)
    checker.check(exec_string, skip_fails=False)

    # step into method call
    execute("step")

    # list current line
    # expect "31	            if (args.length == 0) {"
    exec_string = execute("list")
    rexp = "31[ \t]+if \\(args\\.length == 0\\) {"
    checker = Checker('list hello.Hello.Greeter::greeter', rexp)
    checker.check(exec_string, skip_fails=False)

    # run a backtrace
    # expect "#0  hello.Hello.greeter::greeter(java.lang.String[]).* at hello.Hello.java:31"
    # expect "#1  0x[0-9a-f]+ in hello.Hello::main(java.lang.String[]).* at hello.Hello.java:64"
    # expect "#2  0x[0-9a-f]+ in com.oracle.svm.core.code.IsolateEnterStub::JavaMainWrapper_run_.* at [a-z/]+/JavaMainWrapper.java:[0-9]+"
    exec_string = execute("backtrace")
    checker = Checker("backtrace hello.Hello.Greeter::greeter",
                      ["#0[ \t]+hello\\.Hello\\.Greeter::greeter\\(java\\.lang\\.String\\[\\]\\).* at hello/Hello\\.java:31",
                       "#1[ \t]+0x[0-9a-f]+ in hello\\.Hello::main\\(java\\.lang\\.String\\[\\]\\).* at hello/Hello\\.java:64",
                       "#2[ \t]+0x[0-9a-f]+ in com\\.oracle\\.svm\\.core\\.code\\.IsolateEnterStub::JavaMainWrapper_run_.* at [a-z/]+/JavaMainWrapper\\.java:[0-9]+"
                      ])
    checker.check(exec_string, skip_fails=False)

    # now step into inlined code
    execute("next")

    # check we are still in hello.Hello.Greeter::greeter but no longer in hello.Hello.java
    exec_string = execute("backtrace 1")
    checker = Checker("backtrace inline",
                      ["#0[ \t]+hello\\.Hello\\.Greeter::greeter\\(java\\.lang\\.String\\[\\]\\).* at ([a-zA-Z0-9/]+\\.java):[0-9]+"])
    matches = checker.check(exec_string, skip_fails=False)
    # n.b. can only get back here with one match
    match = matches[0]
    if match.group(1) == "hello.Hello.java":
        line = exec_string.replace("\n", "")
        print('bad match for output %d\n'%(line))
        print(checker)
        sys.exit(1)

    # continue to next breakpoint
    execute("continue")

    # run backtrace to check we are in java.io.PrintStream::println(java.lang.String)
    # expect "#0  java.io.PrintStream::println(java.lang.String).* at java.base/java/io/PrintStream.java:[0-9]+"
    exec_string = execute("backtrace 1")
    checker = Checker("backtrace 1 PrintStream::println",
                      ["#0[ \t]+java\\.io\\.PrintStream::println\\(java\\.lang\\.String\\).* at .*java/io/PrintStream.java:[0-9]+"])
    checker.check(exec_string, skip_fails=False)

    # list current line
    # expect "[0-9]+        synchronized (this) {"
    exec_string = execute("list")
    rexp = "([0-9]+)[ \t]+synchronized \\(this\\) {"
    checker = Checker('list println 1', rexp)
    matches = checker.check(exec_string, skip_fails=False)

    # n.b. can only get back here with one match
    match = matches[0]
    prev_line_num = int(match.group(1)) - 1

    # check the previous line is the declaration for println(String)
    # list {prev_line_num}
    # expect "{prev_line_num}        public void println(String [a-zA-Z0-9_]+) {"
    exec_string = execute("list %d"%prev_line_num)
    rexp = "%d[ \t]+public void println\\(String [a-zA-Z0-9_]+\\) {"%prev_line_num
    checker = Checker('list println 2', rexp)
    checker.check(exec_string, skip_fails=False)

    # continue to next breakpoint
    print(execute("quit 0"))

test()
