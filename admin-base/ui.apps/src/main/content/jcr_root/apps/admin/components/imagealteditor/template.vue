<template>
  <span>
    <button
        v-if="showEditButton"
        class="waves-effect waves-green btn-flat page-check-edit"
        type="button"
        v-on:click="$emit('edit')">
      <i class="material-icons">edit</i>
      Edit
    </button>
    <div v-if="showEditor" class="page-check-image-editor">
      <label>
        Alt text
        <input type="text" :value="value" @input="$emit('update:value', $event.target.value)"/>
      </label>
      <div class="page-check-image-actions">
        <button class="waves-effect waves-green btn-flat" type="button" v-on:click="$emit('cancel')" :disabled="saving">
          Cancel
        </button>
        <button class="waves-effect waves-green btn-flat" type="button" v-on:click="$emit('save')" :disabled="saving">
          Save
        </button>
      </div>
    </div>
  </span>
</template>

<script>
export default {
  props: {
    editingId: String,
    itemId: String,
    hasAltKey: Boolean,
    value: String,
    saving: Boolean,
    isTemplate: Boolean
  },
  computed: {
    showEditButton() {
      return !this.isTemplate && this.editingId !== this.itemId && this.hasAltKey;
    },
    showEditor() {
      return !this.isTemplate && this.editingId === this.itemId && this.hasAltKey;
    }
  }
}
</script>
