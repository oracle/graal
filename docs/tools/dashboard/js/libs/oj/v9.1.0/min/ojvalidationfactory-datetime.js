/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define(["ojs/ojcore","ojs/ojvalidationfactory-base","ojs/ojconverter-datetime","ojs/ojvalidator-datetimerange","ojs/ojvalidator-daterestriction"],function(t,r,e,a,o){"use strict";var n=function(){return{createConverter:function(t){return function(t){return new e.IntlDateTimeConverter(t)}(t)}}}();r.Validation.__registerDefaultConverterFactory(r.ConverterFactory.CONVERTER_TYPE_DATETIME,n);var i=function(){return{createValidator:function(t){return function(t){return new a(t)}(t)}}}();r.Validation.__registerDefaultValidatorFactory(r.ValidatorFactory.VALIDATOR_TYPE_DATETIMERANGE,i);var c=function(){return{createValidator:function(t){return function(t){return new o(t)}(t)}}}();r.Validation.__registerDefaultValidatorFactory(r.ValidatorFactory.VALIDATOR_TYPE_DATERESTRICTION,c);var u={};return u.DateTimeConverterFactory=n,u.DateTimeRangeValidatorFactory=i,u.DateRestrictionValidatorFactory=c,u});