/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define(["ojs/ojcore","ojs/ojmessaging"],function(r,t){"use strict";r.ConverterError=function(r,e){var o={summary:r,detail:e,severity:t.SEVERITY_LEVEL.ERROR};this.Init(o)},r.ConverterError.prototype=new Error,r.ConverterError.prototype.Init=function(r){var t=r.detail,e=r.summary;this._message=r,this.name="Converter Error",this.message=t||e},r.ConverterError.prototype.getMessage=function(){return this._message},r.ValidatorError=function(r,e){var o={summary:r,detail:e,severity:t.SEVERITY_LEVEL.ERROR};this.Init(o)},r.ValidatorError.prototype=new Error,r.ValidatorError.prototype.Init=function(r){var t=r.detail,e=r.summary;this._message=r,this.name="Validator Error",this.message=t||e},r.ValidatorError.prototype.getMessage=function(){return this._message}});