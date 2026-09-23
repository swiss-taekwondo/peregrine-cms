<template>
  <div class="ui-group ui-group-switch" :class="{ 'ui-group-disabled': !enabled, 'ui-group-collapsible': collapsible }">
    <div class="ui-group-heading" :role="collapsible ? 'button' : null" :tabindex="collapsible ? 0 : null"
        :aria-expanded="collapsible ? String(enabled && expanded) : null" :aria-controls="contentId"
        :aria-disabled="collapsible ? String(!enabled) : null" @click="toggleExpanded"
        @keydown.enter.prevent="toggleExpanded" @keydown.space.prevent="toggleExpanded">
      <span>{{ schema.label || schema.title || 'Group' }}</span>
      <span v-if="!schema.preview" class="switch ui-group-toggle" @click.stop>
        <label :for="getFieldID(schema)">
          <input type="checkbox" v-model="value" :autocomplete="schema.autocomplete"
              :disabled="disabled" :name="schema.inputName" :id="getFieldID(schema)">
          <span class="lever" :title="value ? schema.textOn : schema.textOff"></span>
        </label>
      </span>
      <span v-else class="ui-group-preview">{{ value }}</span>
      <i v-if="collapsible" class="material-icons ui-group-indicator" aria-hidden="true">
        {{ enabled && expanded ? 'keyboard_arrow_down' : 'keyboard_arrow_right' }}
      </i>
    </div>
    <div v-show="enabled && (!collapsible || expanded)" :id="contentId" class="ui-group-content">
      <vue-form-generator ref="form" :schema="childSchema" :model="model"
          :options="formOptions" tag="div"
          @model-updated="onModelUpdated" @validated="onValidated" />
    </div>
  </div>
</template>

<script>
export default {
  mixins: [VueFormGenerator.abstractField],
  data() {
    return { expanded: this.schema.expandedByDefault !== false && this.schema.expandedByDefault !== 'false' }
  },
  computed: {
    enabled() {
      return this.value === true || this.value === this.schema.valueOn
    },
    collapsible() {
      return this.schema.collapsible === true || this.schema.collapsible === 'true'
    },
    contentId() {
      return `ui-group-switch-${this._uid}`
    },
    childSchema() {
      return { fields: this.schema.fields || [] }
    }
  },
  watch: {
    enabled(value) {
      if (!value) {
        this.expanded = false
      } else if (this.schema.expandedByDefault !== false && this.schema.expandedByDefault !== 'false') {
        this.expanded = true
      }
    },
    model() {
      this.expanded = this.enabled && this.schema.expandedByDefault !== false && this.schema.expandedByDefault !== 'false'
    }
  },
  methods: {
    formatValueToField(value) {
      if (value != null && this.schema.valueOn) return value == this.schema.valueOn
      return value
    },
    formatValueToModel(value) {
      if (value != null && this.schema.valueOn) return value ? this.schema.valueOn : this.schema.valueOff
      return value
    },
    toggleExpanded() {
      if (this.enabled && this.collapsible) this.expanded = !this.expanded
    },
    onModelUpdated(value, schema) {
      this.$emit('model-updated', value, schema)
    },
    onValidated(valid, errors) {
      if (!valid && this.enabled && this.collapsible) this.expanded = true
      this.$emit('validated', valid, errors.map(item => item.error), this)
    },
    validate() {
      if (!this.enabled || !this.$refs.form) return []
      if (this.formOptions && this.formOptions.validateAsync) {
        return this.$refs.form.validate(true).then(errors => errors.map(item => item.error))
      }
      this.$refs.form.validate(false)
      return this.$refs.form.errors.map(item => item.error)
    },
    clearValidationErrors() {
      if (this.$refs.form) this.$refs.form.clearValidationErrors()
    }
  }
}
</script>

<style>
.vue-form-generator .field-uigroupswitch > label { display: none; }
.vue-form-generator .ui-group-switch {
  width: 100%;
  border: 1px solid #cfd8dc;
}

.vue-form-generator .ui-group-switch > .ui-group-heading {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 0.45rem 0.55rem;
  border: 0;
  border-radius: 0;
  background-color: #cfd8dc;
  color: #455a64;
  font: inherit;
  text-align: left;
  text-transform: capitalize;
  user-select: none;
  transition: background-color 0.35s ease-out;
}

.vue-form-generator .ui-group-switch.ui-group-collapsible > .ui-group-heading {
  cursor: pointer;
}

.vue-form-generator .ui-group-switch.ui-group-collapsible > .ui-group-heading:hover,
.vue-form-generator .ui-group-switch.ui-group-collapsible > .ui-group-heading:focus-visible {
  background-color: #b0bec5;
}

.vue-form-generator .ui-group-switch.ui-group-collapsible > .ui-group-heading[aria-disabled="true"] {
  cursor: default;
}

.vue-form-generator .ui-group-switch.ui-group-collapsible > .ui-group-heading:focus-visible {
  outline: 2px solid #455a64;
  outline-offset: -2px;
}

.ui-group-toggle {
  margin-left: auto;
  text-transform: none;
  line-height: 1;
}

.ui-group-toggle label {
  color: #455a64;
  display: block;
  height: 18px;
}

.ui-group-toggle.switch label .lever {
  width: 30px;
  height: 14px;
  margin: 2px 0 0;
}

.ui-group-toggle.switch label .lever:after {
  width: 18px;
  height: 18px;
  left: -2px;
  top: -2px;
}

.ui-group-toggle.switch label input[type=checkbox]:checked + .lever:after {
  left: 14px;
}

.ui-group-preview {
  margin-left: auto;
}

.ui-group-switch .ui-group-indicator {
  flex: 0 0 16px;
  margin-left: 6px;
  font-size: 16px;
  line-height: 1;
}

.ui-group-switch .ui-group-content {
  padding: 0.55rem;
}

.ui-group.ui-group-switch .switch label input[type=checkbox]:checked + .lever:before {
  left: 15px !important;
}
</style>
