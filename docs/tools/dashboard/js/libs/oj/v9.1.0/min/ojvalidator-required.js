/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define(["ojs/ojcore","ojs/ojtranslation","ojs/ojvalidator","ojs/ojvalidation-error"],function(t,e,i){"use strict";var o=function(t){this.Init(t)};return t.Object.createSubclass(o,i,"oj.RequiredValidator"),o._BUNDLE_KEY_DETAIL="oj-validator.required.detail",o._BUNDLE_KEY_SUMMARY="oj-validator.required.summary",o.prototype.Init=function(t){o.superclass.Init.call(this),this._options=t},o.prototype.validate=function(i){var o,r,n,a,s,l="";if(null==i||("string"==typeof i||i instanceof Array)&&0===i.length)throw this._options&&(o=this._options.messageDetail||this._options.message||null,a=this._options.messageSummary||null,l=this._options.label||""),s={label:l},n=a?e.applyParameters(a,s):e.getTranslatedString(this._getSummaryKey(),s),r=o?e.applyParameters(o,s):e.getTranslatedString(this._getDetailKey(),s),new t.ValidatorError(n,r)},o.prototype.getHint=function(){var t="";return this._options&&this._options.hint&&(t=e.getTranslatedString(this._options.hint)),t},o.prototype._getSummaryKey=function(){return o._BUNDLE_KEY_SUMMARY},o.prototype._getDetailKey=function(){return o._BUNDLE_KEY_DETAIL},o});