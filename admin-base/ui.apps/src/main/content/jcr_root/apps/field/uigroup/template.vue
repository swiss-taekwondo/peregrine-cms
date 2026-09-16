<template>
  <div class="ui-group">
    <button v-if="collapsible" type="button" class="ui-group-heading"
        :aria-expanded="String(expanded)" :aria-controls="contentId"
        @click="expanded = !expanded">
      <span>{{ schema.label || schema.title || 'Group' }}</span>
      <i class="material-icons ui-group-indicator" aria-hidden="true">
        {{ expanded ? 'keyboard_arrow_down' : 'keyboard_arrow_right' }}
      </i>
    </button>
    <div v-else-if="schema.label || schema.title" class="ui-group-heading">
      {{ schema.label || schema.title }}
    </div>
    <div v-show="!collapsible || expanded" :id="contentId" class="ui-group-content">
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
    collapsible() {
      return this.schema.collapsible === true || this.schema.collapsible === 'true'
    },
    contentId() {
      return `ui-group-${this._uid}`
    },
    childSchema() {
      return { fields: this.schema.fields || [] }
    }
  },
  watch: {
    model() {
      this.expanded = this.schema.expandedByDefault !== false && this.schema.expandedByDefault !== 'false'
    }
  },
  methods: {
    onModelUpdated(value, schema) {
      this.$emit('model-updated', value, schema)
    },
    onValidated(valid, errors) {
      if (!valid) this.expanded = true
      this.$emit('validated', valid, errors.map(item => item.error), this)
    },
    validate() {
      if (!this.$refs.form) return []
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
.vue-form-generator .field-uigroup > label { display: none; }
.vue-form-generator .ui-group {
  width: 100%;
  border: 1px solid #cfd8dc;
}
.vue-form-generator .ui-group > .ui-group-heading {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 0.75rem;
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
.vue-form-generator .ui-group > button.ui-group-heading { cursor: pointer; }
.vue-form-generator .ui-group > button.ui-group-heading:hover,
.vue-form-generator .ui-group > button.ui-group-heading:focus-visible {
  background-color: #b0bec5;
}
.vue-form-generator .ui-group > button.ui-group-heading:focus-visible {
  outline: 2px solid #455a64;
  outline-offset: -2px;
}
.ui-group-indicator {
  flex: 0 0 18px;
  margin-left: auto;
  font-size: 18px;
  line-height: 1;
}
.ui-group-content { padding: 0.75rem; }
</style>
