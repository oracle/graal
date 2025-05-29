/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
 
function colorize(property, regexp, color) {
    var f = new ColorFilter("");
    f.addRule(new ColorRule(new MatcherSelector(new RegexpPropertyMatcher(property, regexp)), color));
    f.apply(graph); 
}

function remove(property, regexp) {
    var f = new RemoveFilter("");
    f.addRule(new RemoveRule(new MatcherSelector(new RegexpPropertyMatcher(property, regexp))));
    f.apply(graph);
}

function removeIncludingOrphans(property, regexp) {
    var f = new RemoveFilter("");
    f.addRule(new RemoveRule(new MatcherSelector(new RegexpPropertyMatcher(property, regexp)), true));
    f.apply(graph);
}

function classSimpleName(simpleRegexp) {
    return "(.*)\\.?" + simpleRegexp + "$";
}

function split(property, regexp, propertyName) {
    if (propertyName == undefined) {
        propertyName = graph.getNodeText();
    }
    var f = new SplitFilter("", new MatcherSelector(new RegexpPropertyMatcher(property, regexp)), propertyName);
    f.apply(graph);
}

function removeInputs(property, regexp, from, to) {
    var f = new RemoveInputsFilter("");
    if(from == undefined && to == undefined) {
        f.addRule(new RemoveInputsRule(new MatcherSelector(new RegexpPropertyMatcher(property, regexp))));
    } else if(to == undefined) {
        f.addRule(new RemoveInputsRule(new MatcherSelector(new RegexpPropertyMatcher(property, regexp)), from));
    } else {
        f.addRule(new RemoveInputsRule(new MatcherSelector(new RegexpPropertyMatcher(property, regexp)), from, to));
    }
    f.apply(graph);
}

function removeUnconnectedSlots(inputs, outputs) {
    var f = new UnconnectedSlotFilter(inputs, outputs);
    f.apply(graph);
}

function colorizeGradient(property, min, max) {
    var f = new GradientColorFilter();
    f.setPropertyName(property);
    f.setMinValue(min);
    f.setMaxValue(max);
    f.apply(graph);
}

function colorizeGradientWithMode(property, min, max, mode) {
    var f = new GradientColorFilter();
    f.setPropertyName(property);
    f.setMinValue(min);
    f.setMaxValue(max);
    f.setMode(mode);
    f.apply(graph);
}

function colorizeGradientCustom(property, min, max, mode, colors, fractions, nshades) {
    var f = new GradientColorFilter();
    f.setPropertyName(property);
    f.setMinValue(min);
    f.setMaxValue(max);
    f.setMode(mode);
    f.setColors(colors);
    f.setFractions(fractions);
    f.setShadeCount(nshades);
    f.apply(graph);
}

var black = Color.static.black;
var blue = Color.static.blue;
var cyan = Color.static.cyan;
var darkGray = Color.static.darkGray;
var gray = Color.static.gray;
var green = Color.static.green;
var lightGray = Color.static.lightGray;
var magenta = Color.static.magenta;
var orange = Color.static.orange;
var pink = Color.static.pink
var red = Color.static.red;
var yellow = Color.static.yellow;
var white = Color.static.white;
