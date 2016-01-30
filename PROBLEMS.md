Known problems
==============

Wrong JDK Version
-----------------
Under some machines mx chooses the wrong JDK for executing Sulong.
If this is the case you have to tell mx explicitly which JDK to use:

    mx --jdk jvmci --vm server -J-XX:-UseJVMCIClassLoader su-run test.ll

Dragonegg Installation Failure
------------------------------
Dragonegg as used by mx requires GCC 4.6, so ensure that it is
installed. On Linux, you might also have to install gcc-4.6-plugin-dev.
