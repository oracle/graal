/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define(["ojs/ojcore","ojs/ojvalidationfactory-base","ojs/ojconverter-number","ojs/ojvalidator-numberrange"],function(r,t,e,n){"use strict";var o=function(){return{createConverter:function(r){return function(r){return new e.IntlNumberConverter(r)}(r)}}}();t.Validation.__registerDefaultConverterFactory(t.ConverterFactory.CONVERTER_TYPE_NUMBER,o);var a=function(){return{createValidator:function(r){return function(r){return new n(r)}(r)}}}();t.Validation.__registerDefaultValidatorFactory(t.ValidatorFactory.VALIDATOR_TYPE_NUMBERRANGE,a);var i={};return i.NumberConverterFactory=o,i.NumberRangeValidatorFactory=a,i});