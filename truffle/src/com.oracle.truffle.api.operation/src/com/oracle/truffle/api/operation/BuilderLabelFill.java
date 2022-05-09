package com.oracle.truffle.api.operation;

class BuilderLabelFill {
    int locationBci;
    BuilderOperationLabel label;

    BuilderLabelFill(int locationBci, BuilderOperationLabel label) {
        this.locationBci = locationBci;
        this.label = label;
    }

    BuilderLabelFill offset(int offset) {
        return new BuilderLabelFill(offset + locationBci, label);
    }
}