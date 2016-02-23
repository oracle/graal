#!/bin/bash
wget http://hudson.apidesign.org/job/sigtest-truffle/lastSuccessfulBuild/artifact/truffle-signature.tgz
tar fxvz truffle-signature.tgz
mx/mx build
mx/mx sigtest --check all || echo There are changes!
