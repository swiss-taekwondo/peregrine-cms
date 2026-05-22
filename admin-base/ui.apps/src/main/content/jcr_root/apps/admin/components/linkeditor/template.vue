<template>
  <span>
    <button
        v-if="showEditButton"
        class="waves-effect waves-green btn-flat page-check-edit"
        type="button"
        v-on:click="$emit('edit', item)">
      <i class="material-icons">edit</i>
      Edit
    </button>
    <div v-if="showEditor" class="page-check-property-editor">
      <label>
        Link URL
        <input type="text" :value="value" @input="$emit('update:value', $event.target.value)"/>
      </label>
      <div class="page-check-image-actions">
        <button class="waves-effect waves-green btn-flat" type="button" v-on:click="$emit('browse', item)" :disabled="saving">
          <i class="material-icons">folder_open</i>
          Browse
        </button>
        <button class="waves-effect waves-green btn-flat page-check-remove-link" type="button" v-on:click="$emit('remove', item)" :disabled="saving">
          <i class="fa fa-chain-broken"></i>
          Remove
        </button>
        <button class="waves-effect waves-green btn-flat" type="button" v-on:click="$emit('cancel')" :disabled="saving">
          Cancel
        </button>
        <button class="waves-effect waves-green btn-flat" type="button" v-on:click="$emit('save', item)" :disabled="saving">
          Save
        </button>
      </div>
    </div>
  </span>
</template>

<script>
export default {
  props: {
    item: Object,
    editingId: String,
    value: String,
    saving: Boolean
  },
  computed: {
    showEditButton() {
      return this.item && this.item.owner && this.item.propertyKey
          && this.editingId !== this.item.id;
    },
    showEditor() {
      return this.item && this.editingId === this.item.id
          && this.item.owner && this.item.propertyKey;
    }
  }
}
</script>
