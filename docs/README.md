# How to Contribute to GraalVM Documentation

The GraalVM documentation is open source and anyone can contribute to make it perfect and comprehensive.

The GraalVM documentation is presented in the form of:
* reference documentation which includes getting started and user guides, reference manuals, examples, security guidelines
* Javadoc APIs documentation for application developers, or those who write Java compatibility tests or seek to re-implement the GraalVM platform

Here you will find most of the GraalVM documentation sources, in the same hierarchy as displayed on the [GraalVM website](https://www.graalvm.org/docs/introduction/).
The Truffle framework documentation can be found in the [graal/truffle/docs](https://github.com/oracle/graal/tree/master/truffle/docs) folder.
GraalVM languages implementations are being developed and tested in separate from the GraalVM core repositories, so their user documentation can be found in:

* [GraalJS](https://github.com/oracle/graaljs/tree/master/docs/user) - JavaScript and Node.js
* [FastR](https://github.com/oracle/fastr/tree/master/documentation/user) - R
* [GraalPy](https://github.com/oracle/graalpython/tree/master/docs/user) - Python
* [TruffleRuby](https://github.com/oracle/truffleruby/tree/master/doc/user) - Ruby

To update the documentation:

1. Create a GitHub account or sign in to your existing account
2. Navigate to the source file you are intended to update
3. Click the "edit" button at the top of the section.
   > Note: GitHub introduced a new feature: online web editor, which allows to edit multiple files from a browser. To enable it, press `.` on any GitHub repo. For example, go to [https://github.com/oracle/graal](https://github.com/oracle/graal) and hit `.`. You will be immediately redirected to [https://github.dev/oracle/graal](https://github.dev/oracle/graal).
   ![](/img/github-web-editor.png)
 
4. Create a Pull Request (PR)
5. Sign the [Oracle Contributor Agreement](https://oca.opensource.oracle.com/)
6. Watch your PR for pipeline results

A member from the GraalVM project team will review your PR and merge as appropriate.
There is a CI pipeline which will pick up your change once merged to the master branch, and publish on the website.

The GraalVM core and its projects are hosted in the Oracle organization on GitHub, so we except a contributor to abide by the [Contributor Covenant Code of Conduct](https://www.graalvm.org/community/conduct/).
For more specific guidelines see [Contribute to GraalVM](https://www.graalvm.org/community/contributors/).

Do not hesitate to push fixes to broken links or typos, update an outdated section, propose information if you find it missing, or propose a new feature documentation.
Help us make GraalVM better by improving the existing and contributing new documentation!
