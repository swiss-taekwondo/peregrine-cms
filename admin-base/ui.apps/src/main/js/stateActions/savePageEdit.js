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
import { LoggerFactory } from '../logger'
let log = LoggerFactory.logger('savePageEdit').setLevelDebug()

import { set } from '../utils'

export default function(me, target) {

    log.fine(target)

    let view = me.getView()

    return new Promise( (resolve, reject) => {
        Promise.resolve().then(async () => {
            // DownloadList also saves through the navigation confirmation flow, which
            // passes the live model and must wait for its pending asset-folder refresh.
            // Allow the form watcher to start, then await the latest folder request.
            await me.getApp().$nextTick()
            while (target.data._assetSectionsPending) await target.data._assetSectionsPending
            if (target.data._assetSectionsError) throw new Error('Sections could not be loaded. Select the asset folder again before saving.')
            return me.getApi().savePageEdit(view.pageView.path, target.data)
        }).then(() => {
            if (target.data.component === 'stkdtheme-components-downloadlist') {
                // DownloadList's files are computed by Java; fetch them after saving
                // so the preview reflects the saved folder without a browser reload.
                return me.getApi().populatePageView(view.pageView.path)
            }
        }).then( () => {
            delete view.state.editor;
            set(view, '/state/editorVisible', false)
            $perAdminApp.clearBeforeStateActions();
            $perAdminApp.clearBeforeUnloadHandler();
            if(view.pageView.page.serverSide) {
                me.action(me.getApp().$children[0], 'refreshEditor', view.pageView.page)
                resolve()
            } else {
                resolve()
            }
        }).catch( error => {
           reject(error)
         })
    })
}
