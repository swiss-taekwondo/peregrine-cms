<!--
  #%L
  admin base - UI Apps
  %%
  Copyright (C) 2020 The Regents of the University of Michigan
  %%
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
  #L%
  -->


<template>
    <div class="publishinginfo">
      <table>
        <tbody>
          <tr>
            <th>Status</th>
            <td>{{printStatus(node)}}</td>
          </tr>
          <tr>
            <th>Last Published</th>
            <td>{{publishActionDate}}</td>
          </tr>
          <tr>
            <th>Last Published by</th>
            <td>{{node.ReplicatedBy}}</td>
          </tr>

          <tr>
            <th>Last Modified</th>
            <td>{{modificationDate}}</td>
          </tr>
          <tr>
            <th>Last Modified by</th>
            <td>{{node.lastModifiedBy}}</td>
          </tr>

          <tr v-if="translationDate">
            <th>Last Translated</th>
            <td>{{translationDate}}</td>
          </tr>
          <tr v-if="node.lastTranslatedBy">
            <th>Last Translated by</th>
            <td>{{node.lastTranslatedBy}}</td>
          </tr>
        </tbody>
      </table>
    </div>
</template>

<script>
import ReferenceUtil from '../../../../../../js/mixins/ReferenceUtil'

export default {
    props: [
        'isOpen',
        'node',
        'modalTitle',
    ],
    data(){
        return {

        }
    },
    mixins: [ReferenceUtil],
    computed: {
        publishActionDate(){
            if (this.node.Replicated) {
                return new Date(this.node.Replicated).toLocaleString();
            }
            return '';
        },
        modificationDate(){
            if (this.node.lastModified) {
                return new Date(this.node.lastModified).toLocaleString();
            }
            return '';
        },
        translationDate(){
            if (this.node.lastTranslated) {
                return new Date(this.node.lastTranslated).toLocaleString();
            }
            return '';
        },

    },
}
</script>

<style>
.publishinginfo {
    padding: 0 0.75rem;
    border-bottom: 1px solid rgba(0, 0, 0, 0.1);
}
th {
    color: #607d8b;
    font-size: 1rem;
}
</style>
