---
layout: docs
toc_group: examples
link_title: Polyglot Node.js Example
permalink: /examples/polyglot-javascript-java-r/
---

# Polyglot JavaScript, Java, R Example Application

This page describes an example of a polyglot application you can run with GraalVM.

### Preparation

1&#46; Download or clone the repository and navigate into the `polyglot-javascript-java-r` directory:
  ```bash
  git clone https://github.com/graalvm/graalvm-demos
  cd graalvm-demos/polyglot-javascript-java-r
  ```

2&#46; [Download GraalVM](https://www.graalvm.org/downloads/), unzip the archive, export the GraalVM home directory as the `$JAVA_HOME` and add `$JAVA_HOME/bin` to the `PATH` environment variable:
  On Linux:
  ```bash
  export JAVA_HOME=/home/${current_user}/path/to/graalvm
  export PATH=$JAVA_HOME/bin:$PATH
  ```
  On macOS:
  ```bash
  export JAVA_HOME=/Users/${current_user}/path/to/graalvm/Contents/Home
  export PATH=$JAVA_HOME/bin:$PATH
  ```
  On Windows:
  ```bash
  setx /M JAVA_HOME "C:\Progra~1\Java\<graalvm>"
  setx /M PATH "C:\Progra~1\Java\<graalvm>\bin;%PATH%"
  ```
  Note that your paths are likely to be different depending on the download location.

3&#46; To run the demo, you need to enable Node.js support in GraalVM:
  ```bash
  gu install nodejs
  ```

4&#46; This application contains R code. The R language support is not enabled by default in GraalVM and you should add it too:
  ```bash
  gu install R
  ```

5&#46; Build the benchmark. You can manually execute `npm install`, but there is also a `build.sh` script included for your convenience:
  ```bash
  ./build.sh
  ```
Now you are all set to run the polyglot JavaScript, Java, R application.

### Running the Application

To run the application, you need to execute the `server.js` file.
You can run it with the following command (or run the `run.sh` script):
```bash
$JAVA_HOME/bin/node --polyglot --jvm server.js
```

If you would like to run the benchmark on a different instance of Node, you can run it with whatever `node` you have. However, presumably, the polyglot capability will not be supported.

Open [localhost:3000](http://localhost:3000) and see the output of the polyglot app.
Play with the source code and restart the application to see what else you can do with the mix of JavaScript, Java, and R.

### Debugging Polyglot Applications

GraalVM also supports debugging of polyglot applications and provides a built-in implementation of the [Chrome DevTools Protocol](../tools/chrome-debugger.md).
Add the `--inspect` parameter to the command line, open the URL the application prints at the startup in the Chrome browser, and start debugging: set breakpoints, evaluate expressions of this app in JavaScript and R code alike, and so on.

### Note about the Application

For brevity, this sample application contains large snippets of code inside the strings.
This is not the best approach for structuring polyglot applications, but it is the easiest way to demonstrate polyglot capabilities in a single file.
