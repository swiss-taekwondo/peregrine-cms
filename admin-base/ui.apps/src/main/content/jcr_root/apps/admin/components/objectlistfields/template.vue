<!--
  #%L
  admin base - UI Apps
  %%
  Copyright (C) 2017 headwire inc.
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
  <materialize-modal ref="modal" modal-title="List columns">
    <div class="object-list-fields-settings">
      <p v-if="loading">{{ $i18n('Loading') }}…</p>
      <template v-else>
        <p class="settings-hint">
          Choose which object fields the object list shows for <b>{{ folderName }}</b>.
          Subfolders without their own setting use this one.
        </p>

        <p class="settings-mode">
          <input type="radio" class="with-gap" :id="`${uid}-inherit`" value="inherit" v-model="mode"/>
          <label :for="`${uid}-inherit`">
            Use the setting of the parent folder
            <span class="settings-muted">({{ inheritedSummary }})</span>
          </label>
        </p>
        <p class="settings-mode">
          <input type="radio" class="with-gap" :id="`${uid}-own`" value="own" v-model="mode"/>
          <label :for="`${uid}-own`">Choose the fields for this folder</label>
        </p>

        <div v-if="mode === 'own'" class="settings-fields">
          <p v-if="!fields.length" class="settings-muted">
            There are no objects below this folder yet, so no fields to choose from.
          </p>
          <ul v-else>
            <li v-for="field in orderedFields" :key="field.model">
              <input type="checkbox" class="filled-in" :id="`${uid}-${field.model}`"
                     :checked="isSelected(field.model)" @change="toggle(field.model)"/>
              <label :for="`${uid}-${field.model}`">
                {{ field.label }} <span class="settings-muted">{{ field.model }}</span>
              </label>
              <span v-if="isSelected(field.model)" class="settings-order">
                <button type="button" class="btn-flat" title="Move left"
                        :disabled="selected.indexOf(field.model) === 0"
                        @click="move(field.model, -1)"><i class="material-icons">arrow_upward</i></button>
                <button type="button" class="btn-flat" title="Move right"
                        :disabled="selected.indexOf(field.model) === selected.length - 1"
                        @click="move(field.model, 1)"><i class="material-icons">arrow_downward</i></button>
              </span>
            </li>
          </ul>
          <p v-if="selected.length > maxFields" class="settings-error">At most {{ maxFields }} fields.</p>
        </div>
        <p v-if="error" class="settings-error">{{ error }}</p>
      </template>
    </div>
    <template slot="footer">
      <button type="button" class="btn-flat" @click="close">{{ $i18n('cancel') }}</button>
      <button type="button" class="btn" :disabled="!canSave" @click="save">{{ $i18n('save') }}</button>
    </template>
  </materialize-modal>
</template>

<script>
  import MaterializeModal from '../materializemodal/template.vue'

  /** Fields that make no sense as a list column */
  const SKIPPED_TYPES = ['texteditor', 'material-multiselect', 'collection', 'fileupload', 'iconbrowser']

  export default {
    name: 'ObjectListFields',
    components: { MaterializeModal },
    data() {
      return {
        path: null,
        loading: false,
        saving: false,
        error: null,
        mode: 'inherit',
        selected: [],
        fields: [],
        settings: null,
        maxFields: 12
      }
    },
    computed: {
      uid() {
        return `olf-${this._uid}`
      },
      folderName() {
        return this.path ? this.path.split('/').pop() : ''
      },
      inheritedSummary() {
        const settings = this.settings
        if (!settings) return ''
        const parentFields = settings.inherited ? settings.effectiveListFields : null
        if (parentFields && settings.inheritedFrom) {
          const names = parentFields.map(this.labelOf).join(', ') || 'no fields'
          return `${names}, from ${settings.inheritedFrom.split('/').pop()}`
        }
        return settings.inherited ? 'no fields' : 'looked up after saving'
      },
      orderedFields() {
        const byModel = {}
        this.fields.forEach(field => { byModel[field.model] = field })
        const chosen = this.selected.map(model => byModel[model] || { model, label: model })
        return [...chosen, ...this.fields.filter(field => !this.isSelected(field.model))]
      },
      canSave() {
        return !this.loading && !this.saving && (this.mode === 'inherit' || this.selected.length <= this.maxFields)
      }
    },
    methods: {
      open(path) {
        this.path = path
        this.error = null
        this.loading = true
        this.$refs.modal.open()
        const api = $perAdminApp.getApi()
        api.getObjectFolderSettings(path)
          .then(settings => {
            this.settings = settings
            this.mode = settings.inherited ? 'inherit' : 'own'
            this.selected = [...(settings.inherited ? settings.effectiveListFields : settings.listFields) || []]
            return Promise.all((settings.objectDefinitions || []).map(definition =>
              api.fetchComponentDefinition(definition).catch(() => null)))
          })
          .then(definitions => { this.fields = this.collectFields(definitions) })
          .catch(error => { this.error = `Could not load the folder settings (${error})` })
          .then(() => { this.loading = false })
      },
      close() {
        this.$refs.modal.close()
      },
      collectFields(definitions) {
        const fields = []
        const seen = {}
        const add = list => (list || []).forEach(field => {
          if (field.fields) add(field.fields)
          if (!field.model || field.model.indexOf(':') >= 0 || seen[field.model]) return
          if (SKIPPED_TYPES.includes(field.type) || field.multifield) return
          seen[field.model] = true
          fields.push({ model: field.model, label: field.label || field.model })
        })
        definitions.forEach(definition => {
          const model = definition && definition.model
          if (!model) return
          add(model.fields)
          ;(model.groups || []).forEach(group => add(group.fields))
        })
        return fields
      },
      labelOf(model) {
        const field = this.fields.find(candidate => candidate.model === model)
        return field ? field.label : model
      },
      isSelected(model) {
        return this.selected.includes(model)
      },
      toggle(model) {
        this.selected = this.isSelected(model)
          ? this.selected.filter(item => item !== model)
          : [...this.selected, model]
      },
      move(model, step) {
        const index = this.selected.indexOf(model)
        const target = index + step
        if (index < 0 || target < 0 || target >= this.selected.length) return
        const selected = [...this.selected]
        selected.splice(index, 1)
        selected.splice(target, 0, model)
        this.selected = selected
      },
      save() {
        this.saving = true
        this.error = null
        const listFields = this.mode === 'inherit' ? null : this.selected
        $perAdminApp.getApi().setObjectFolderListFields(this.path, listFields)
          .then(() => {
            this.$emit('saved', this.path)
            this.close()
          })
          .catch(error => {
            const message = error && error.response && error.response.data && error.response.data.message
            this.error = `Could not save: ${message || error}`
          })
          .then(() => { this.saving = false })
      }
    }
  }
</script>

<style>
  .object-list-fields-settings {
    padding-top: 1.5rem;
  }
  .object-list-fields-settings .settings-hint {
    margin-top: 0;
  }
  .object-list-fields-settings .settings-mode {
    margin: 0.5rem 0;
  }
  .object-list-fields-settings .settings-muted {
    color: #9e9e9e;
  }
  .object-list-fields-settings .settings-fields {
    margin: 0.5rem 0 0 2rem;
  }
  .object-list-fields-settings .settings-fields ul {
    margin: 0;
  }
  .object-list-fields-settings .settings-fields li {
    display: flex;
    align-items: center;
    min-height: 2.25rem;
  }
  .object-list-fields-settings .settings-order {
    margin-left: auto;
    display: flex;
  }
  .object-list-fields-settings .settings-order .btn-flat {
    padding: 0 0.25rem;
    height: 2rem;
    line-height: 2rem;
  }
  .object-list-fields-settings .settings-error {
    color: #c62828;
  }
</style>
