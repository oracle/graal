---
layout: docs
toc_group: contributing
link_title: Code Style
permalink: /reference-manual/native-image/contributing/CodeStyle/
redirect_from: /reference-manual/native-image/CodeStyle/
---

# Native Image Code Style

To ensure the quality of code and that all contributors follow the same standards, we established a ruleset that grew over time and proved to be useful. Note that the rules are open for discussion!

## Source Code Formatting

The IDE projects generated with `mx ideinit` are configured with strict formatting rules.
In Eclipse, when a file is saved, it is automatically formatted according to these rules.
The configuration includes special comments which can be used to relax checks in particular regions of code.

Source code formatting can be disabled with special comments:
```java
//@formatter:off

//@formatter:on
```
Comment reformatting can be disabled like this:
```java
/*-
 *
 */
```

## Checks with Checkstyle

Checkstyle is used to verify adherence to the style rules.
It can be run manually with `mx checkstyle`.

The default Checkstyle rules are defined in `src/com.oracle.svm.core/.checkstyle_checks.xml` and define various special comments, including
```java
//Checkstyle: stop method name check

//Checkstyle: resume method name check
```
and similar commands for other checks that can be disabled (including general `stop` and `resume` commands).
Of course, ensuring a reasonable use of these comments is a matter for code review.

If a project requires a different set of Checkstyle rules, this can be specified in `mx.substratevm/suite.py` by changing the value of the project's `checkstyle` attribute (which, by default, references `com.oracle.svm.core`).
Specific code files can be excluded from checkstyle on a directory granularity with a file `src/<project name>/.checkstyle.exclude`.
Such an exclusion file must contain one directory per line, with paths relative to the project root.
The file must be explicitly added with `git add` because git will ignore it by default.

When pulling a changeset which adds or removes Checkstyle XML files, the IDE might show inappropriate style warnings or errors.
This is resolved by running `mx ideinit` and cleaning the affected projects.

## IDE Integration

IDE plugins can be helpful in adhering to style rules.
Some examples are:

* [Eclipse Checkstyle Plugin](https://checkstyle.github.io/eclipse-cs/): reports Checkstyle violations in Eclipse, making it unnecessary to run `mx checkstyle` manually.
* [IntelliJ Eclipse Code Formatter](https://github.com/krasa/EclipseCodeFormatter): formats source files in IntelliJ according to Eclipse IntelliJ rules. This plugin is automatically configured by `mx ideinit`.
* [IntelliJ Save Actions](https://github.com/dubreuia/intellij-plugin-save-actions) to automatically format files before saving them.

See the [documentation on IDE integration](https://github.com/graalvm/mx/blob/master/docs/IDE.md) for further suggestions.