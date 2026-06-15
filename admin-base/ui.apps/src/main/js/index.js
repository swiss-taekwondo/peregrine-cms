/*-
 * #%L
 * admin base - UI Apps
 * %%
 * Copyright (C) 2017 headwire inc.
 * %%
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * #L%
 */
import PerAdminApp from './perAdminApp'
import PerAdminImpl from './apiImpl'

function initAnalytics() {
  axios.get('/apps/admin/analytics.json')
    .then((analytics) => {
      if (location.origin === analytics.data.domain && !window.Cypress && analytics.data.script) {
        const sentry = document.createElement('script');
        sentry.crossOrigin = 'anonymous';
        sentry.src = analytics.data.script;
        sentry.dataset.lazy = 'no';
        document.body.append(sentry);
      }
    })
    .catch(() => null)
}

function initHotReload() {
    if (!window.EventSource) {
        return
    }

    const hostname = window.location.hostname
    if (hostname !== 'localhost' && hostname !== '127.0.0.1') {
        return
    }

    const source = new window.EventSource(`http://${hostname}:35729/events`)
    source.addEventListener('reload', () => {
        window.location.reload()
    })
}

initAnalytics()
initHotReload()

var $pappView = {}
var $papp = new PerAdminApp($pappView)
var $perApi = new PerAdminImpl($papp)
$papp.setApi($perApi)
var $logger = $papp.getLogger('index.js')
$papp.getApi().populateTools().then(() => { $logger.info(JSON.stringify($pappView, true, 2)) } ).catch( (error) => $logger.info('failed test', error) )
