/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define(["ojs/ojcore","knockout","ojs/ojkeyset"],function(e,t){"use strict";var r=function(o){var a=o||new e.ExpandedKeySet,n=t.observable(a);return Object.setPrototypeOf(n,r.proto),n};r.proto=Object.create(t.observable.fn),t.utils.arrayForEach(["add","addAll","clear","delete"],function(e){r.proto[e]=function(){var t=this.peek();return this(t[e].apply(t,arguments)),this}});var o=function(r){var a=r||new e.KeySetImpl,n=t.observable(a);return Object.setPrototypeOf(n,o.proto),n};return o.proto=Object.create(t.observable.fn),t.utils.arrayForEach(["add","addAll","clear","delete"],function(e){o.proto[e]=function(){var t=this.peek();return this(t[e].apply(t,arguments)),this}}),{ObservableExpandedKeySet:r,ObservableKeySet:o}});