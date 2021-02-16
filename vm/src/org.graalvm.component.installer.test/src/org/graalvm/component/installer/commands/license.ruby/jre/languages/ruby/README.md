![TruffleRuby logo](logo/png/truffleruby_logo_horizontal_medium.png)

A high performance implementation of the Ruby programming language. Built on the
GraalVM by [Oracle Labs](https://labs.oracle.com).

## Authors

The main authors of TruffleRuby in order of joining the project are:

* Chris Seaton
* Benoit Daloze
* Kevin Menard
* Petr Chalupa
* Brandon Fish
* Duncan MacGregor

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

The best way to get in touch with us is to join us in
https://gitter.im/graalvm/truffleruby, but you can also Tweet to @chrisgseaton,
or email chris.seaton@oracle.com.

## Mailing list

Announcements about GraalVM, including TruffleRuby, are made on the
[graalvm-dev](https://oss.oracle.com/mailman/listinfo/graalvm-dev) mailing list.

## System Compatibility

TruffleRuby is actively tested on these systems:

* Ubuntu 16.04 LTS
* Fedora 26
* macOS 10.12

You need to [install LLVM](doc/user/installing-llvm.md) to build and run C
extensions and [`libssl`](doc/user/installing-libssl.md) to use `openssl`. You
may also need to set up a [UTF-8 locale](doc/user/utf8-locale.md)

Oracle Linux is not currently supported due to not having a good way to install
a recent LLVM.

## Current Status

TruffleRuby is progressing fast but is currently probably not ready for you to
try running your full Ruby application on. Support for critical C extensions
such as OpenSSL and Nokogiri is missing.

TruffleRuby is ready for experimentation and curious end-users to try on their
gems and smaller applications.

### Common questions about the status of TruffleRuby

#### Do you run Rails?

We do run Rails, and pass the majority of the Rails test suite. But we are
missing support for OpenSSL, Nokogiri, and ActiveRecord database drivers
which makes it not practical to run real Rails applications at the moment.

#### What is happening with AOT, startup time, and the SubstrateVM?

You don't need a JVM to run TruffleRuby. With the
[SubstrateVM](doc/user/svm.md)
it is possible to produce a single, statically linked native binary executable
version of TruffleRuby, which doesn't need any JVM to run.

This SubstrateVM version of TruffleRuby has startup performance and memory
footprint more similar to MRI than TruffleRuby on the JVM or JRuby. There are
[instructions](doc/user/svm.md)
for using it as part of GraalVM.

#### Can TruffleRuby run on a standard JVM?

It is possible to [run on an unmodified JDK 9](doc/user/using-java9.md) but you
will have to build Graal yourself and we recommend using GraalVM instead.

#### How do I install gems?

TruffleRuby cannot install gems out of the box yet, however there are 
[temporary workarounds](doc/user/installing-gems.md) 
which can be used to get it working. 

## Getting Started

The best way to get started with TruffleRuby is via the GraalVM, which includes
compatible versions of everything you need as well as TruffleRuby.

http://www.oracle.com/technetwork/oracle-labs/program-languages/

Inside the GraalVM is a `bin/ruby` command that runs TruffleRuby.
See [Using TruffleRuby with GraalVM](doc/user/using-graalvm.md)
instructions.

You can also build TruffleRuby from source, see the
[Building Instructions](doc/contributor/workflow.md).

## Documentation

Documentation is in [`doc`](doc).

## Licence

TruffleRuby is copyright (c) 2013-2017 Oracle and/or its
affiliates, and is made available to you under the terms of three licenses:

* Eclipse Public License version 1.0
* GNU General Public License version 2
* GNU Lesser General Public License version 2.1

TruffleRuby contains additional code not always covered by these licences, and
with copyright owned by other people. See
[doc/legal/legal.md](doc/legal/legal.md) for full documentation.

## Attribution

TruffleRuby is a fork of [JRuby](https://github.com/jruby/jruby), combining it
with code from the [Rubinius](https://github.com/rubinius/rubinius) project, and
also containing code from the standard implementation of Ruby,
[MRI](https://github.com/ruby/ruby).
