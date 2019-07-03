![TruffleRuby logo](logo/png/truffleruby_logo_horizontal_medium.png)

A high performance implementation of the Ruby programming language. Built on
[GraalVM](http://graalvm.org/) by [Oracle Labs](https://labs.oracle.com).

## Getting started

There are three ways to install TruffleRuby:

* Via [GraalVM](doc/user/installing-graalvm.md), which includes support for
  other languages such as JavaScript, R and Python and supports both the
  [*native* and *JVM* configurations](#truffleruby-configurations).
  Inside GraalVM will then be a `bin/ruby` command that runs TruffleRuby.
  We recommend that you use a [Ruby manager](doc/user/ruby-managers.md#configuring-ruby-managers-for-the-full-graalvm-distribution)
  to use TruffleRuby inside GraalVM.

* Via your [Ruby manager/installer](doc/user/ruby-managers.md) (RVM, rbenv,
  chruby, ruby-build, ruby-install). This contains only TruffleRuby, in the
  [*native* configuration](#truffleruby-configurations), making it a smaller
  download. It is meant for users just wanting a Ruby implementation and already
  using a Ruby manager.

* Using the [standalone distribution](doc/user/standalone-distribution.md)
  as a simple binary tarball. This distribution is also useful for
  [testing TruffleRuby in CI](doc/user/standalone-distribution.md).
  On [TravisCI](https://docs.travis-ci.com/user/languages/ruby#truffleruby), you can simply use:
  ```yaml
  language: ruby
  rvm:
    - truffleruby
  ```

You can use `gem` to install Gems as normal.

You can also build TruffleRuby from source, see the
[building instructions](doc/contributor/workflow.md), and using
[Docker](doc/contributor/docker.md).

Please report any issue you might find on [GitHub](https://github.com/oracle/truffleruby/issues).

## Aim

TruffleRuby aims to:

* Run idiomatic Ruby code faster
* Run Ruby code in parallel
* Boot Ruby applications in less time
* Execute C extensions in a managed environment
* Add fast and low-overhead interoperability with languages like Java, JavaScript, Python and R
* Provide new tooling such as debuggers and monitoring
* All while maintaining very high compatibility with the standard implementation of Ruby

## TruffleRuby configurations

There are two main configurations of TruffleRuby: *native* and *JVM*. It's
important to understand the different configurations of TruffleRuby, as each has
different capabilities and performance characteristics. You should pick the
execution mode that is appropriate for your application.

TruffleRuby by default runs in the *native*
configuration. In this configuration, TruffleRuby is ahead-of-time compiled to a
standalone native executable. This means that you don't need a JVM installed on
your system to use it. The advantage of the native configuration is that it
[starts about as fast as MRI](doc/contributor/svm.md), it may use less memory,
and it becomes fast in less time. The disadvantage of the native configuration
is that you can't use Java tools like VisualVM, you can't use Java
interoperability, and *peak performance may be lower than on the JVM*. The
native configuration is used by default, but you can also request it using
`--native`. To use polyglot programming with the *native* configuration, you
need to use the `--polyglot` flag. To check you are using the *native*
configuration, `ruby --version` should mention `Native`.

TruffleRuby can also be used in the *JVM* configuration, where it runs as a
normal Java application on the JVM, as any other Java application would. The
advantage of the JVM configuration is that you can use Java interoperability,
and *peak performance may be higher than the native configuration*. The
disadvantage of the JVM configuration is that it takes much longer to start and
to get fast, and may use more memory. The JVM configuration is requested using
`--jvm`. To check you are using the *JVM* configuration, `ruby --version` should
not mention `Native`.

If you are running a short-running program you probably want the default,
*native*, configuration. If you are running a long-running program and want the
highest possible performance you probably want the *JVM* configuration, by using
`--jvm`.

At runtime you can tell if you are using the native configuration using
`TruffleRuby.native?`.

You won't encounter it when using TruffleRuby from the GraalVM, but there is
also another configuration which is TruffleRuby running on the JVM but with the
Graal compiler not available. This configuration will have much lower
performance and should normally only be used for development. `ruby --version`
will mention `Interpreter` for this configuration.

## System compatibility

TruffleRuby is actively tested on these systems:

* Oracle Linux 7
* Ubuntu 18.04 LTS
* Ubuntu 16.04 LTS
* Fedora 28
* macOS 10.14 (Mojave)
* macOS 10.13 (High Sierra)

You may find that TruffleRuby will not work if you severely restrict the
environment, for example by unmounting system filesystems such as `/dev/shm`.

## Dependencies

* [LLVM](doc/user/installing-llvm.md) to build and run C extensions
* [libssl](doc/user/installing-libssl.md) for the `openssl` C extension
* [zlib](doc/user/installing-zlib.md) for the `zlib` C extension

Without these dependencies, many libraries including RubyGems will not work.
TruffleRuby will try to print a nice error message if a dependency is missing,
but this can only be done on a best effort basis.

You may also need to set up a [UTF-8 locale](doc/user/utf8-locale.md).

## Current status

We recommend that people trying TruffleRuby on their gems and applications
[get in touch with us](#contact) for help.

TruffleRuby is progressing fast but is currently probably not ready for you to
try running your full Ruby application on. However it is ready for
experimentation and curious end-users to try on their gems and smaller
applications.

TruffleRuby runs Rails, and passes the majority of the Rails test suite. But it
is missing support for Nokogiri and ActiveRecord database drivers which makes it
not practical to run real Rails applications at the moment.

You will find that many C extensions will not work without modification.

## Migration from MRI

TruffleRuby should in most cases work as a drop-in replacement for MRI, but you
should read about our [compatibility](doc/user/compatibility.md).

## Migration from JRuby

For many use cases TruffleRuby should work as a drop-in replacement for JRuby.
However, our approach to integration with Java is different to JRuby so you
should read our [migration guide](doc/user/jruby-migration.md).

## Documentation

Extensive documentation is available in [`doc`](doc).
[`doc/user`](doc/user) documents how to use TruffleRuby and
[`doc/contributor`](doc/contributor) documents how to develop TruffleRuby.

## Contact

The best way to get in touch with us is to join us in
https://gitter.im/graalvm/truffleruby, but you can also Tweet to
[@TruffleRuby](https://twitter.com/truffleruby), or email
chris.seaton@oracle.com.

## Mailing list

Announcements about GraalVM, including TruffleRuby, are made on the
[graal-dev](http://mail.openjdk.java.net/mailman/listinfo/graal-dev) mailing list.

## Authors

The main authors of TruffleRuby in order of joining the project are:

* Chris Seaton
* Benoit Daloze
* Kevin Menard
* Petr Chalupa
* Brandon Fish
* Duncan MacGregor
* Christian Wirth

Additionally:

* Thomas Würthinger
* Matthias Grimmer
* Josef Haider
* Fabio Niephaus
* Matthias Springer
* Lucas Allan Amorim
* Aditya Bhardwaj

Collaborations with:

* [Institut für Systemsoftware at Johannes Kepler University
   Linz](http://ssw.jku.at)

And others.

## Security

See the [security documentation](doc/user/security.md).

## Licence

TruffleRuby is copyright (c) 2013-2019 Oracle and/or its affiliates, and is made
available to you under the terms of any one of the following three licenses:

* Eclipse Public License version 1.0, or
* GNU General Public License version 2, or
* GNU Lesser General Public License version 2.1.

TruffleRuby contains additional code not always covered by these licences, and
with copyright owned by other people. See
[doc/legal/legal.md](doc/legal/legal.md) for full documentation.

## Attribution

TruffleRuby is a fork of [JRuby](https://github.com/jruby/jruby), combining it
with code from the [Rubinius](https://github.com/rubinius/rubinius) project, and
also containing code from the standard implementation of Ruby,
[MRI](https://github.com/ruby/ruby).
