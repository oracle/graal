#!/bin/bash

shopt -s extglob
rm !(*.sh)
mx build
mx igv