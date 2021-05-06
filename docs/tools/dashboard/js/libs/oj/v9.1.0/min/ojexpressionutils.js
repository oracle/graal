/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define(["ojs/ojcore","ojs/ojconfig"],function(t,e){"use strict";function n(){}return n.getExpressionInfo=function(e){return t.__AttributeUtils.getExpressionInfo(e)},n.createGenericExpressionEvaluator=function(t){var n,r=e.getExpressionEvaluator();if(r){var o=r.createEvaluator(t).evaluate;return function(t){return o([t])}}try{n=new Function("context","with(context){return "+t+";}")}catch(e){throw new Error(e.message+' in expression "'+t+'"')}return n},n});