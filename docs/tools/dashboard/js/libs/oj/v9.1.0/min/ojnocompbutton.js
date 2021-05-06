/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define(["ojs/ojcore","jquery","ojs/ojcomponentcore"],function(e,t){"use strict";var n={setup:function(n){e.DomUtils.makeFocusable({element:t(n),applyHighlight:!0}),n.addEventListener("keydown",function(e){13!==e.keyCode&&"Enter"!==e.key||n.classList.add("oj-active")}),n.addEventListener("keyup",function(e){13!==e.keyCode&&"Enter"!==e.key||(n.classList.remove("oj-active"),n.click())})}};return n});