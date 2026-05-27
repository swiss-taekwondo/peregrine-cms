<template>
  <span>
    <button
        v-if="showGoToComponentButton"
        class="waves-effect waves-green btn-flat page-check-edit"
        type="button"
        v-on:click="$emit('go-to-component', item)">
      <i class="material-icons">open_in_new</i>
      {{ isTemplate ? 'Go to Template' : 'Go to Component' }}
    </button>
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
        <span v-if="item.propertyLabel">{{ item.propertyLabel }}</span>
        <span v-else>Link URL</span>
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
    saving: Boolean,
    isVariant: Boolean,
    isTemplate: Boolean
  },
  computed: {
    showGoToComponentButton() {
      return this.isVariant;
    },
    showEditButton() {
      return !this.isVariant && this.isEditable() && this.editingId !== this.item.id;
    },
    showEditor() {
      return !this.isVariant && this.editingId === this.item.id && this.isEditable();
    }
  },
  methods: {
    isEditable() {
      const item = this.item;
      if (!item || !item.owner || !item.propertyKey) return false;
      if (item.type === 'link-field' || item.type === 'html-link') return true;
      if (item.type === 'broken-link' || item.type === 'verified-link') {
        return item.linkType === 'link-field' || item.linkType === 'html-link';
      }
      if (item.linkType === 'link-field' || item.linkType === 'html-link') return true;
      return false;
    }
  }
}
</script>
