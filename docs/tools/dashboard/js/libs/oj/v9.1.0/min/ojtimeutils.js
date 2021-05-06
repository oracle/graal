/**
 * @license
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates.
 * Licensed under The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define(["ojs/ojcore","jquery","ojs/ojcomponentcore","ojs/ojdvt-base"],function(e,t,i,n){"use strict";e.TimeUtils={};e.TimeUtils;return e.TimeUtils.getPosition=function(e,t,i,n){var o=new Date(e).getTime(),m=new Date(t).getTime(),s=(o-m)*n,T=new Date(i).getTime()-m;return 0===s||0===T?0:s/T},e.TimeUtils.getLength=function(t,i,n,o,m){var s=new Date(t).getTime(),T=new Date(i).getTime(),g=new Date(n).getTime(),r=new Date(o).getTime(),a=e.TimeUtils.getPosition(s,g,r,m);return e.TimeUtils.getPosition(T,g,r,m)-a},e.TimeUtils.getDate=function(e,t,i,n){var o=new Date(t).getTime(),m=e*(new Date(i).getTime()-o);return 0===m||0===n?o:m/n+o},e.TimeUtils});