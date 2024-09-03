---
layout: docs
link_title: Implement Your Tool
permalink: /graalvm-as-a-platform/implement-instrument/
redirect_from: /docs/graalvm-as-a-platform/implement-instrument/
toc_group: graalvm-as-a-platform
---

# Getting Started with Instruments in GraalVM

Tools are sometimes referred to as _Instruments_ within the GraalVM platform.
The [Instrument API](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/package-summary.html) is used to implement such instruments.
Instruments can track very fine-grained, VM-level runtime events to profile, inspect, and analyze the runtime behavior of applications running on GraalVM.

## Simple Tool

To provide an easier starting point for tool developers we have created a [Simple Tool](https://github.com/graalvm/simpletool) example project.
This is a javadoc-rich Maven project which implements a simple code coverage tool.

We recommend cloning the repository and exploring the source code as a starting point for tool development.
The following sections will provide a guided tour of the steps needed to build and run a GraalVM tool, using Simple Tool source code as the running example.
These sections do not cover all of the features of the [Instrument API](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/package-summary.html) so we encourage you to check the javadoc for more details.

### Requirements

As mentioned before, Simple Tool is a code coverage tool.
Ultimately, it should provide the developer with information on what percentage of source code lines was executed, as well as exactly which lines were executed.
With that in mind, we can define some high-level requirements from our tool:

1. The tool keeps track of loaded source code.
2. The tool keeps track of executed source code.
3. On application exit, the tool calculates and prints per-line coverage information.

### Instrument API

The main starting point for tools is subclassing the [TruffleInstrument](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.html) class.
Unsurprisingly, the simple tool code base does exactly this, creating the [SimpleCoverageInstrument](https://github.com/graalvm/simpletool/blob/master/src/main/java/com/oracle/truffle/st/SimpleCoverageInstrument.java#L84) class.

The [Registration](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.Registration.html) annotation on the class ensures that the newly created instrument is registered with the Instrument API, in other words, that it will be automatically discovered by the framework.
It also provides some metadata about the instrument: ID, name, version, which services the instrument provides, and whether the instrument is internal or not.
In order for this annotation to be effective the DSL processor needs to process this class.
This is, in the case of Simple Tool, done automatically by having the DSL processor as a dependency in the [Maven configuration](https://github.com/graalvm/simpletool/blob/master/pom.xml#L83).

Now we will look back at the implementation of the `SimpleCoverageInstrument` class, namely which methods from `TruffleInstrument` it overrides.
These are [onCreate](https://github.com/graalvm/simpletool/blob/master/src/main/java/com/oracle/truffle/st/SimpleCoverageInstrument.java#L130), [onDispose](https://github.com/graalvm/simpletool/blob/master/src/main/java/com/oracle/truffle/st/SimpleCoverageInstrument.java#L182), and [getOptionDescriptors](https://github.com/graalvm/simpletool/blob/master/src/main/java/com/oracle/truffle/st/SimpleCoverageInstrument.java#L245).
The `onCreate` and `onDispose` methods are self-explanatory: they are called by the framework when the instrument is created and disposed.
We will discuss their implementations later, but first let us discuss the remaining one: `getOptionDescriptors`.

The [Truffle language implementation framework](../../truffle/docs/README.md) comes with its own system for specifying command-line options.
These options allow tool users to control the tool either from the command line or when creating [polyglot contexts](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/Context.html).
It is annotation-based, and examples for such options are the [ENABLED](https://github.com/graalvm/simpletool/blob/master/src/main/java/com/oracle/truffle/st/SimpleCoverageInstrument.java#L91) and [PRINT_COVERAGE](https://github.com/graalvm/simpletool/blob/master/src/main/java/com/oracle/truffle/st/SimpleCoverageInstrument.java#L97) fields of `SimpleCoverageInstrument`.
Both of these are static final fields of the type [OptionKey](https://www.graalvm.org/truffle/javadoc/org/graalvm/options/OptionKey.html) annotated with [Option](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/Option.html) which, similar to the `Registration` annotation, provides some metadata for the option.
Again, as with the `Registration` annotation, for the `Option` annotation to be effective the DSL processor is needed, which generates a subclass of [OptionDescriptors](https://www.graalvm.org/truffle/javadoc/org/graalvm/options/OptionDescriptors.html) (in our case named `SimpleCoverageInstrumentOptionDescriptors`).
An instance of this class should be returned from the `getOptionDescriptors` method to let the framework know which options the instrument provides.

Returning to the `onCreate` method, as an argument, we receive an instance of the [Env](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.Env.html) class.
This object gives a lot of useful information, but for the `onCreate` method we are primarily interested in the [getOptions](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.Env.html#getOptions--) method, which can be used to read which options are passed to the tool.
We use this to check whether the `ENABLED` option has been set and if so we enable our tool by calling the [enable](https://github.com/graalvm/simpletool/blob/master/src/main/java/com/oracle/truffle/st/SimpleCoverageInstrument.java#L169) method.
Similarly, in the `onDispose` method we check the options for the state of the `PRINT_COVERAGE` option, and if it is enabled we call the [printResults](https://github.com/graalvm/simpletool/blob/master/src/main/java/com/oracle/truffle/st/SimpleCoverageInstrument.java#L184) method which will print our results.

What does it mean "to enable a tool?"
In general, it means that we tell the framework about the events we are interested in and how we want to react to them. Looking at our `enable` method, it does the following:

- First, it defines [SourceSectionFilter](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/SourceSectionFilter.html).
This filter is a declarative definition of the parts of the source code we are interested in.
In our example, we care about all nodes that are considered expressions, and we do not care about internal language parts.
- Second, we obtain an instance of an
[Instrumenter](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/Instrumenter.html)
class which is an object allowing us to specify which parts of the system we wish to instrument.
- Finally, using the `Instrumenter` class, we specify a Source Section Listener and an Execution Event Factory which are both described in the next two sections.

### Source Section Listener

The Language API provides the notion of a [Source](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/Source.html) which is the source code unit, and a [SourceSection](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/SourceSection.html) which is one continuous part of a `Source`, e.g., one method, one statement, one expression, and so on. More details can be found in the respective javadoc.

The first requirement for Simple Tool is to keep track of loaded source code.
The Instrument API provides the [LoadSourceSectionListener](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/LoadSourceSectionListener.html) which, when subclassed and registered with the instrumenter, allows users to react to the runtime loading source sections.
This is exactly what we do with the [GatherSourceSectionsListener](https://github.com/graalvm/simpletool/blob/master/src/main/java/com/oracle/truffle/st/GatherSourceSectionsListener.java#L56), which is registered in the [enable](https://github.com/graalvm/simpletool/blob/master/src/main/java/com/oracle/truffle/st/SimpleCoverageInstrument.java#L172) method of the instrument.
The implementation of `GatherSourceSectionsListener` is quite simple: we override the [onLoad](https://github.com/graalvm/simpletool/blob/master/src/main/java/com/oracle/truffle/st/GatherSourceSectionsListener.java#L71) method to notify the instrument of each loaded source section.
The instrument keeps a mapping from each `Source` to a [Coverage](https://github.com/graalvm/simpletool/blob/master/src/main/java/com/oracle/truffle/st/Coverage.java) object which keeps a set of loaded source sections for each source.

### Execution Event Node

Guest languages are implemented as Abstract Syntax Tree (AST) interpreters.
The language implementers annotate certain nodes with tags, which allows us to select which nodes we are interested in, by using the aforementioned `SourceSectionFilter`, in a language-agnostic manner.

The main power of the Instrument API lies in its ability to insert specialized nodes in the AST which "wrap" the nodes of interest.
These nodes are built using the same infrastructure that the language developers use, and are, from the perspective of the runtime, indistinguishable from the language nodes.
This means that all of the techniques used to optimize guest languages into such high performing language implementations are available to the tool developers as well.

More information about these techniques is available in the [language implementation documentation](https://github.com/oracle/graal/tree/master/truffle/docs).
Suffice it to say that for Simple Tool to meet its second requirement, we need to instrument all expressions with our own node that will notify us when that expression is executed.

For this task we use the [CoverageNode](https://github.com/graalvm/simpletool/blob/master/src/main/java/com/oracle/truffle/st/CoverageNode.java).
It is a subclass of [ExecutionEventNode](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/ExecutionEventNode.html) which, as the name implies, is used to instrument events during execution.
The `ExecutionEventNode` offers many methods to override, but we are only interested in [onReturnValue](https://github.com/graalvm/simpletool/blob/master/src/main/java/com/oracle/truffle/st/CoverageNode.java#L106).
This method is invoked when the "wrapped" node returns a value, that is, it is successfully executed.
The implementation is rather simple. We just notify the instrument that the node with this particular `SourceSection` has been executed, and the instrument updates the `Coverage` object in its coverage map.

The instrument is notified only once per node, as the logic is guarded by the [flag](https://github.com/graalvm/simpletool/blob/master/src/main/java/com/oracle/truffle/st/CoverageNode.java#L60).
The fact that this flag is annotated with [CompilationFinal](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/CompilerDirectives.CompilationFinal.html) and that the call to the instrument is preceded by a call to [transferToInterpreterAndInvalidate()](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/CompilerDirectives.html#transferToInterpreterAndInvalidate--) is a standard technique in Truffle, which ensures that once this instrumentation is no longer needed (a node has been executed), the instrumentation is removed from further compilations, along with any performance overhead.

In order for the framework to know how to instantiate the `CoverageNode` when it is needed, we need to provide a factory for it.
The factory is the [CoverageEventFactory](https://github.com/graalvm/simpletool/blob/master/src/main/java/com/oracle/truffle/st/CoverageEventFactory.java), a subclass of [ExecutionEventNodeFactory](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/ExecutionEventNodeFactory.html).
This class just ensures that each `CoverageNode` knows the `SourceSection` it is instrumenting by looking it up in the provided [EventContext](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/EventContext.html).

Finally, when we are [enabling the instrument](https://github.com/graalvm/simpletool/blob/master/src/main/java/com/oracle/truffle/st/SimpleCoverageInstrument.java#L173), we tell the instrumenter to use our factory to "wrap" the nodes selected by our filter.

### Interaction Between Users and Instruments

The third and final requirement Simple Tool has is to actually interact with its user by printing line coverage to standard output.
The instrument overriders the [onDispose](https://github.com/graalvm/simpletool/blob/master/src/main/java/com/oracle/truffle/st/SimpleCoverageInstrument.java#L182) method which is unsurprisingly called when the instrument is being disposed of.
In this method we check that the proper option has been set and, if so, calculate and print the coverage as recorded by our map of `Coverage` objects.

This is a simple way of providing useful information to a user, but it is definitely not the only one.
A tool could dump its data directly to a file, or run a web endpoint which shows the information, etc.
One of the mechanisms that the Instrument API provides users with is registering instruments as services to be looked up by other instruments.
If we look at the [Registration](https://github.com/graalvm/simpletool/blob/master/src/main/java/com/oracle/truffle/st/SimpleCoverageInstrument.java#L83) annotation of our instrument we can see that it provides a `services` field where we can specify which services the instrument provides to other instruments.
These services need to be explicitly [registered](https://github.com/graalvm/simpletool/blob/master/src/main/java/com/oracle/truffle/st/SimpleCoverageInstrument.java#L134).
This allows a nicer separation of concerns among instruments so that, for example, we could have a "real time coverage" instrument which would use our `SimpleCoverageInstrument` to provide on-demand coverage information to a user through a REST API, and an "aborts on low coverage" instrument which stops the execution if coverage drops below a threshold, both using the `SimpleCoverageInstrument` as a service.

Note: For reasons of isolation, instrument services are not available to application code, and instrument services can only be used from other instruments or guest languages.

### Installing a Tool into GraalVM

So far, Simple Tool seems to meet all requirements but the question remains: how do we use it?
As mentioned before, Simple Tool is a Maven project.
Setting `JAVA_HOME` to a GraalVM installation and running `mvn package` produces a `target/simpletool-<version>.jar`.
This is the Simple Tool distribution form.

The [Truffle framework](../../truffle/docs/README.md) offers a clear separation between the language/tooling code and the application code.
For this reason, putting the JAR file on the class path will not result in the framework realizing a new tool is needed.
To achieve this we use `--vm.Dtruffle.class.path.append=/path/to/simpletool-<version>.jar` as is illustrated in a [launcher script for our simple tool](https://github.com/graalvm/simpletool/blob/master/simpletool).
This script also shows we can [set the CLI options](https://github.com/graalvm/simpletool/blob/master/simpletool#L19) we specified for Simple Tool.
This means that if we execute `./simpletool js example.js`, we will launch the `js` launcher of GraalVM, add the tool to the framework class path, and run the included [example.js](https://github.com/graalvm/simpletool/blob/master/example.js) file with Simple Tool enabled, resulting in the following output:

```
==
Coverage of /path/to/simpletool/example.js is 59.42%
+ var N = 2000;
+ var EXPECTED = 17393;

  function Natural() {
+     x = 2;
+     return {
+         'next' : function() { return x++; }
+     };
  }

  function Filter(number, filter) {
+     var self = this;
+     this.number = number;
+     this.filter = filter;
+     this.accept = function(n) {
+       var filter = self;
+       for (;;) {
+           if (n % filter.number === 0) {
+               return false;
+           }
+           filter = filter.filter;
+           if (filter === null) {
+               break;
+           }
+       }
+       return true;
+     };
+     return this;
  }

  function Primes(natural) {
+     var self = this;
+     this.natural = natural;
+     this.filter = null;
+     this.next = function() {
+         for (;;) {
+             var n = self.natural.next();
+             if (self.filter === null || self.filter.accept(n)) {
+                 self.filter = new Filter(n, self.filter);
+                 return n;
+             }
+         }
+     };
  }

+ var holdsAFunctionThatIsNeverCalled = function(natural) {
-     var self = this;
-     this.natural = natural;
-     this.filter = null;
-     this.next = function() {
-         for (;;) {
-             var n = self.natural.next();
-             if (self.filter === null || self.filter.accept(n)) {
-                 self.filter = new Filter(n, self.filter);
-                 return n;
-             }
-         }
-     };
+ }

- var holdsAFunctionThatIsNeverCalledOneLine = function() {return null;}

  function primesMain() {
+     var primes = new Primes(Natural());
+     var primArray = [];
+     for (var i=0;i<=N;i++) { primArray.push(primes.next()); }
-     if (primArray[N] != EXPECTED) { throw new Error('wrong prime found: ' + primArray[N]); }
  }
+ primesMain();
```

## Other Examples

The following examples are intended to show common use-cases that can be solved with the [Instrument API](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/package-summary.html).

 - [Coverage Instrument](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.instrumentation.test/src/com/oracle/truffle/api/instrumentation/test/examples/CoverageExample.java): a coverage tool example which was used to build up Simple Tool. It is used as the running example in further text where appropriate.
 - [Debugger Instrument](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.instrumentation.test/src/com/oracle/truffle/api/instrumentation/test/examples/DebuggerExample.java):
 a sketch on how a debugger can be implemented. Note that the Instrument API already provides a [Debugger Instrument](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/debug/package-summary.html) that can be used directly.
 - [Statement Profiler](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.instrumentation.test/src/com/oracle/truffle/api/instrumentation/test/examples/StatementProfilerExample.java): a profiler that is able to profile the execution of statements.

### Instrumentation Event Listeners

The Instrument API is defined in the `com.oracle.truffle.api.instrumentation` package.
Instrumentation agents can be developed by extending the `TruffleInstrument` class, and can be attached to a running GraalVM instance using the `Instrumenter` class.
Once attached to a running language runtime, instrumentation agents remain usable as long as the language runtime is not disposed.
Instrumentation agents on GraalVM can monitor a variety of VM-level runtime events, including any of the following:

1. _Source code-related events_: The agent can be notified every time a new [Source](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/Source.html) or [SourceSection](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/SourceSection.html) element is loaded by the monitored language runtime.
2. _Allocation events_: The agent can be notified every time a new object is allocated in the memory space of the monitored language runtime.
3. _Language runtime and thread creation events_: The agent can be notified as soon as a new [execution context](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html) or a new thread for a monitored language runtime is created.
4. _Application execution events_: The agent gets notified every time a monitored application executes a specific set of language operations. Examples of such operations include language statements and expressions, thus allowing an instrumentation agent to inspect running applications with very high precision.

For each execution event, instrumentation agents can define _filtering_ criteria that will be used by the GraalVM instrumentation runtime to monitor only the relevant execution events.
Currently, GraalVM instruments accept one of the following two filter types:

1. `AllocationEventFilter` to filter allocation events by allocation type.
2. `SourceSectionFilter` to filter source code locations in an application.

Filters can be created using the provided builder object. For example, the following builder creates a `SourceSectionFilter`:

```java
SourceSectionFilter.newBuilder()
                   .tagIs(StandardTag.StatementTag)
                   .mimeTypeIs("x-application/js")
                   .build()
```

The filter in the example can be used to monitor the execution of all JavaScript statements in a given application.
Other filtering options such as line numbers or file extensions can also be provided.

Source section filters like the one in the example can use _Tags_ to specify a set of execution events to be monitored. Language-agnostic tags such as statements and expressions are defined in the [`com.oracle.truffle.api.instrumentation.Tag`](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/Tag.html) class, and are supported by all GraalVM languages.
In addition to standard tags, GraalVM languages may provide other, language-specific, tags to enable fine-grained profiling of language-specific events.
(As an example, the GraalVM JavaScript engine provides JavaScript-specific tags to track the usages of ECMA builtin objects such as `Array`, `Map`, or `Math`.)

### Monitoring Execution Events

Application execution events enable very precise and detailed monitoring. GraalVM supports two different types of instrumentation agents to profile such events, namely:

1. _Execution listener_: an instrumentation agent that can be notified every time a given runtime event happens. Listeners implement the [`ExecutionEventListener`](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/ExecutionEventListener.html) interface, and cannot associate any _state_ with source code locations.
2. _Execution event node_: an instrumentation agent that can be expressed using Truffle Framework AST nodes. Such agents extend the [`ExecutionEventNode`](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/ExecutionEventNode.html) class and have the same capabilities of an execution listener, but can associate state with source code locations.

### Simple Instrumentation Agent

A simple example of a custom instrumentation agent used to perform runtime code coverage can be found in the `CoverageExample` class.
What follows is an overview of the agent, its design, and its capabilities.

All instruments extend the `TruffleInstrument` abstract class and are registered in the GraalVM runtime through the `@Registration` annotation:

```java
@Registration(id = CoverageExample.ID, services = Object.class)
public final class CoverageExample extends TruffleInstrument {

  @Override
  protected void onCreate(final Env env) {
  }

  /* Other methods omitted... */
}
```

Instruments override the `onCreate(Env env)` method to perform custom operations at instrument loading time.
Typically, an instrument would use this method to register itself in the existing GraalVM execution environment.
As an example, an instrument using AST nodes can be registered in the following way:

```java
@Override
protected void onCreate(final Env env) {
  SourceSectionFilter.Builder builder = SourceSectionFilter.newBuilder();
  SourceSectionFilter filter = builder.tagIs(EXPRESSION).build();
  Instrumenter instrumenter = env.getInstrumenter();
  instrumenter.attachExecutionEventFactory(filter, new CoverageEventFactory(env));
}
```

The instrument connects itself to the running GraalVM using the `attachExecutionEventFactory` method, providing the following two arguments:
1. `SourceSectionFilter`: a source section filter used to inform the GraalVM about specific code sections to be tracked.
2. `ExecutionEventNodeFactory`: the Truffle AST factory that provides instrumentation AST nodes to be executed by the agent every time a runtime event (as specified by the source filter) is executed.

A basic `ExecutionEventNodeFactory` that instruments the AST nodes of an application can be implemented in the following way:

```java
public ExecutionEventNode create(final EventContext ec) {
  return new ExecutionEventNode() {
    @Override
    public void onReturnValue(VirtualFrame vFrame, Object result) {
      /*
       * Code to be executed every time a filtered source code
       * element is evaluated by the guest language.
       */
    }
  };
}
```

Execution event nodes can implement certain callback methods to intercept runtime execution events. Examples include:

1. `onEnter`: executed before an AST node corresponding to a filtered source code element (for example, a language statement or an expression) is evaluated.
2. `onReturnValue`: executed after a source code element returns a value.
3. `onReturnExceptional`: executed in case the filtered source code element throws an exception.

Execution event nodes are created on a _per code location_ basis.
Therefore, they can be used to store data specific to a given source code location in the instrumented application.
As an example, an instrumentation node can simply keep track of all code locations that have already been visited using a node-local flag.
Such a node-local `boolean` flag can be used to track the execution of AST nodes in the following way:

```java
// To keep track of all source code locations executed
private final Set<SourceSection> coverage = new HashSet<>();

public ExecutionEventNode create(final EventContext ec) {
  return new ExecutionEventNode() {
    // Per-node flag to keep track of execution for this node
    @CompilationFinal private boolean visited = false;

    @Override
    public void onReturnValue(VirtualFrame vFrame, Object result) {
      if (!visited) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        visited = true;
        SourceSection src = ec.getInstrumentedSourceSection();
        coverage.add(src);
      }
    }
  };
}
```

As the above code shows, an `ExecutionEventNode` is a valid AST node.
This implies that the instrumentation code will be optimized by the GraalVM runtime together with the instrumented application, resulting in minimal instrumentation overhead. Furthermore, this allows instrument developers to use the [Truffle framework compiler directives](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/CompilerDirectives.html) directly from instrumentation nodes.
In the example, compiler directives are used to inform the Graal compiler that `visited` can be considered compilation-final.

Each instrumentation node is bound to a specific code location.
Such locations can be accessed by the agent using the provided [`EventContext`](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/EventContext.html) object. The context object gives instrumentation nodes access to a variety of information about the current AST nodes being executed.
Examples of query APIs available to instrumentation agents through `EventContext` include:

1. `hasTag`: to query an instrumented node for a certain node `Tag` (for example, to check if a statement node is also a conditional node).
2. `getInstrumentedSourceSection`: to access the [`SourceSection`](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/SourceSection.html) associated with the current node.
3. `getInstrumentedNode`: to access the [`Node`](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/Node.html) corresponding to the current instrumentation event.

### Fine-grained Expression Profiling

Instrumentation agents can profile even fractional events such as language expressions. To this end, an agent needs to be initialized providing two source section filters:

```java
// What source sections are we interested in?
SourceSectionFilter sourceSectionFilter = SourceSectionFilter.newBuilder()
  .tagIs(JSTags.BinaryOperation.class)
  .build();

// What generates input data to track?
SourceSectionFilter inputGeneratingLocations = SourceSectionFilter.newBuilder()
  .tagIs(StandardTags.ExpressionTag.class)
  .build();

instrumenter.attachExecutionEventFactory(sourceSectionFilter, inputGeneratingLocations, factory);
```

The first source section filter (`sourceSectionFilter`, in the example) is a normal filter equivalent to other filters described before, and is used to identify the source code locations to be monitored.
The second section filter, `inputGeneratingLocations`, is used by the agent to specify the _intermediate values_ that should be monitored for a certain source section.
Intermediate values correspond to _all_ observable values that are involved in the execution of a monitored code element, and are reported to the instrumentation agent by means of the `onInputValue` callback.
As an example, let us assume an agent needs to profile all _operand_ values provided to sum operations (`+`) in JavaScript:

```javascript
var a = 3;
var b = 4;
// the '+' expression is profiled
var c = a + b;
```

By filtering on JavaScript binary expressions, an instrumentation agent would be able to detect the following runtime events for the above code snippet:
1. `onEnter()`: for the binary expression at line 3.
2. `onInputValue()`: for the first operand of the binary operation at line 3. The value reported by the callback will be `3`, that is, the value of the `a` local variable.
3. `onInputValue()`: for the second operand of the binary operation. The value reported by the callback will be `4`, that is, the value of the `b` local variable.
2. `onReturnValue()`: for the binary expression. The value provided to the callback will be the value returned by the expression after it has completed its evaluation, that is, the value `7`.

By extending the source section filters to _all_ possible events, an instrumentation agent will observe something equivalent to the following execution trace (in pseudocode):
```shell
// First variable declaration
onEnter - VariableWrite
    onEnter - NumericLiteral
    onReturnValue - NumericLiteral
  onInputValue - (3)
onReturnValue - VariableWrite

// Second variable declaration
onEnter - VariableWrite
    onEnter - NumericLiteral
    onReturnValue - NumericLiteral
  onInputValue - (4)
onReturnValue - VariableWrite

// Third variable declaration
onEnter - VariableWrite
    onEnter - BinaryOperation
        onEnter - VariableRead
        onReturnValue - VariableRead
      onInputValue - (3)
        onEnter - VariableRead
        onReturnValue - VariableRead
      onInputValue - (4)
    onReturnValue - BinaryOperation
  onInputValue - (7)
onReturnValue - VariableWrite
```

The `onInputValue` method can be used in combination with source section filters to intercept very fine-grained execution events such as intermediate values used by language expressions.
The intermediate values that are accessible to the Instrumentation framework greatly depend on the instrumentation support provided by each language.
Moreover, languages may provide additional metadata associated with language-specific `Tag` classes.

### Altering the Execution Flow of an Application

The instrumentation capabilities that we have presented so far enable users to _observe_ certain aspects of a running application.
In addition to passive monitoring of an application's behavior, the Instrument API features support for actively _altering_ the behavior of an application at runtime.
Such capabilities can be used to write complex instrumentation agents that affect the behavior of a running application to achieve specific runtime semantics.
For example, one could alter the semantics of a running application to ensure that certain methods or functions are never executed (for example, by throwing an exception when they are called).

Instrumentation agents with such capabilities can be implemented by leveraging the `onUnwind` callback in execution event listeners and factories.
As an example, let's consider the following JavaScript code:

```javascript
function inc(x) {
  return x + 1
}

var a = 10
var b = a;
// Let's call inc() with normal semantics
while (a == b && a < 100000) {
  a = inc(a);
  b = b + 1;
}
c = a;
// Run inc() and alter it's return type using the instrument
return inc(c)
```

An instrumentation agent that modifies the return value of `inc` to always be `42` can be implemented using an `ExecutionEventListener`, in the following way:

```java
ExecutionEventListener myListener = new ExecutionEventListener() {

  @Override
  public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
    String callSrc = context.getInstrumentedSourceSection().getCharacters();
    // is this the function call that we want to modify?
    if ("inc(c)".equals(callSrc)) {
      CompilerDirectives.transferToInterpreter();
      // notify the runtime that we will change the current execution flow
      throw context.createUnwind(null);
    }
  }

  @Override
  public Object onUnwind(EventContext context, VirtualFrame frame, Object info) {
    // just return 42 as the return value for this node
    return 42;
  }
}
```

The event listener can be executed intercepting all function calls, for example using the following instrument:

```java
@TruffleInstrument.Registration(id = "UniversalAnswer", services = UniversalAnswerInstrument.class)
public static class UniversalAnswerInstrument extends TruffleInstrument {

  @Override
  protected void onCreate(Env env) {
    env.registerService(this);
    env.getInstrumenter().attachListener(SourceSectionFilter.newBuilder().tagIs(CallTag.class).build(), myListener);
  }
}
```

When enabled, the instrument will execute its `onReturnValue` callback each time a function call returns.
The callback reads the associated source section (using `getInstrumentedSourceSection`) and looks for a specific source code pattern (the function call `inc(c)`, in this case).
As soon as such code pattern is found, the instrument throws a special runtime exception, called `UnwindException`, that instructs the Instrumentation framework about a change in the current application's execution flow.
The exception is intercepted by the `onUnwind` callback of the instrumentation agent, which can be used to return _any_ arbitrary value to the original instrumented application.

In the example, all calls to `inc(c)` will return `42` regardless of any application-specific data.
A more realistic instrument might access and monitor several aspects of an application, and might not rely on source code locations, but rather on object instances or other application-specific data.
