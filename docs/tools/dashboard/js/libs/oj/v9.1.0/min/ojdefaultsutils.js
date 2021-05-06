/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define(["ojs/ojmetadatautils"],function(t){"use strict";var e;return function(e){function a(e,a,i){var n;let u=e._staticDefaults;if(void 0===u){if(u=null,a){const e=a.properties,l=null===(n=a.extension)||void 0===n?void 0:n._DEFAULTS;if(l){var f=new l;u=Object.create(f)}else e&&(u=Object.create(t.getDefaultValues(e,i)))}e._staticDefaults=u}return u}function i(t,e){if(t.getDynamicDefaults){const a=t.getDynamicDefaults();if(a)for(let t in a)void 0===e[t]&&(e[t]=a[t])}}e.getFrozenDefault=function(a,i,n){var u=e.getDefaults(i,n,!0);return t.deepFreeze(u[a])},e.getDefaults=function(t,e,n){let u=t._defaults;if(void 0===u){const f=a(t,e,n);i(t,u=Object.create(f)),t._defaults=u}return u},e.getStaticDefaults=a,e.applyDynamicDefaults=i}(e||(e={})),{DefaultsUtils:e}});