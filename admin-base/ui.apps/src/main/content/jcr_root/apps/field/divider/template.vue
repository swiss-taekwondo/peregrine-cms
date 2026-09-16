<template>
  <hr class="ui-divider" aria-hidden="true">
</template>

<script>
export default {
  mixins: [VueFormGenerator.abstractField],
  methods: {
    validate() { return [] },
    clearValidationErrors() {}
  }
}
</script>

<style>
.vue-form-generator .field-divider > label { display: none; }
.vue-form-generator .ui-divider {
  width: 100%;
  margin: 0.75rem 0;
  border: 0;
  border-top: 1px solid #cfd8dc;
}
</style>
