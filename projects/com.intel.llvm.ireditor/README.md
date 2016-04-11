Including this project into the mx workflow is a rather tedious task.
A previous attempt to use the xtext infrastructure in a standalone way
failed, because some code fragments did not work with absolute paths.

At the moment, the project is thus included in the suite with only a
dummy source directory. Since findbug fails on the gate if no bin
directory exists, we still have to have some Java file to compile,
which is in the mentioned dummy source directory. However, mx does not
compile projects with a plugin.xml file. To circumvent this issue, the
plugin.xml is not checked into the repository.

In order to still be able to work on the project, it is recommended to
use Eclipse. After import of the project, the folders with Java sources
have to be added as source folders. Then, the project has to be
converted to a plugin project and xtext nature added to it. Afterwards
it is possible to generate the parser.
