/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define(["ojs/ojlogger"],function(e){"use strict";class t{}return t.getConverterInstance=function(t){let o="",r={},n=null;if(t&&("object"==typeof t&&(t.parse&&"function"==typeof t.parse||t.format&&"function"==typeof t.format?n=t:(o=t.type,r=t.options||{})),!n&&(o=o||t)&&"string"==typeof o)){if(oj.Validation&&oj.Validation.converterFactory)return oj.Validation.converterFactory(o).createConverter(r);e.error('oj.Validation.converterFactory is not available and it is needed to support the deprecated json format for the converters property. Please include the backward compatibility "ojvalidation-base" module.')}return n},t});