# Eclipse

## How can I use Sulong with Eclipse?

In order to work with Eclipse, use `mx eclipseinit` to generate the
Eclipse project files. Import not only the Sulong project, but also the
Graal, Truffle, and JVMCI projects. You have the choice to either use
remote debugging and launch Sulong in mx, or launch Sulong within
Eclipse.

If you use Eclipse to launch Sulong, you have to ensure that all needed
packages are on the classpath and all necessary options set. You can
determine them by using `-v` in the mx Sulong command to make mx
output information on how it executed the command.

## How can I debug Sulong with Eclipse?

To debug the execution of a bitcode file in Eclipse, first start an mx
command with the `-d` flag, e.g.:

    $ mx -d su-run test.ll
    Listening for transport dt_socket at address: 8000

In Eclipse, set a breakpoint, navigate to
`Run->Debug->Remote Java Application` and select one
of the debug configurations, e.g.,`truffle-attach-localhost-8000`.
After clicking `Debug`, execution starts and the program should stop at
the specified breakpoint.
