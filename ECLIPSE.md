# Developing Sulong with Eclipse

In order to work with Eclipse, use `mx eclipseinit` to generate the
Eclipse project files. Import not only the Sulong project, but also the
Graal, Truffle, and JVMCI projects. You have the choice to either use
remote debugging and launch Sulong in mx, or launch Sulong within
Eclipse.

If you use Eclipse to launch Sulong, you have to ensure that all needed
packages are on the classpath and all necessary options set. You can
determine them by using `-v` in the mx Sulong command to make mx
output information on how it executed the command.

## Useful Plugins:

* Use [checkstyle](http://checkstyle.sourceforge.net/) to highlight
style errors during development. Alternatively you can also use
mx checkstyle to run checkstyle from mx.

## Useful Configs:

* See
[http://stackoverflow.com/questions/2604424/how-can-i-add-a-
    default-header-to-my-source-files-automatically-in-eclipse](http://stackoverflow.com/questions/2604424/how-can-i-add-a-
    default-header-to-my-source-files-automatically-in-eclipse)
for alternatives regarding adding the copyright header automatically
for new filse.
