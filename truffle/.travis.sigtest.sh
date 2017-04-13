#!/bin/bash
wget http://hudson.apidesign.org/job/sigtest-truffle/lastSuccessfulBuild/artifact/truffle-signature.tgz
tar fxvz truffle-signature.tgz
MX=mx
if [ -e mx/mx ]; then
  MX=mx/mx
fi
$MX build
$MX sigtest --check all || echo There are changes!
