<template>
  <div class="confirm-dialog">
    <button class="modal-action waves-effect waves-light btn-flat" v-on:click="cancelEdit">
      {{ $i18n(cancelText) }}
    </button>

    <div v-if="submitOptions && submitOptions.length" class="publish-dropdown-wrapper">
      <button
        class="modal-action waves-effect waves-light btn-flat dropdown-trigger"
        @click.stop="toggleDropdown">
        {{ $i18n(submitText || "submit") }}
        <i class="material-icons right">arrow_drop_down</i>
      </button>

      <ul v-if="dropdownOpen" class="publish-dropdown-menu">
        <li v-for="option in submitOptions"
            :key="option.action"
            @click="confirmEdit(option.action)">
          <span class="action-title">{{ $i18n(option.text) }}</span>
          <span v-if="option.desc" class="action-desc">{{ $i18n(option.desc) }}</span>
        </li>
      </ul>
    </div>

    <button class="modal-action waves-effect waves-light btn-flat"
            v-else-if="submitText"
            v-on:click="confirmEdit('confirm')">
      {{ $i18n(submitText) }}
    </button>

    <div v-if="dropdownOpen" class="dropdown-backdrop" @click.stop="dropdownOpen = false"></div>
  </div>
</template>

<script>
export default {
  name: "ConfirmDialog",
  props: {
    cancelText: { type: String, default: "cancel" },
    submitText: String,
    submitOptions: Array // Expected format: [{ text: '...', action: '...', desc: '...' }]
  },
  data() {
    return {
      dropdownOpen: false
    };
  },
  methods: {
    cancelEdit() {
      this.$emit("confirm-dialog", "cancel");
    },
    toggleDropdown() {
      this.dropdownOpen = !this.dropdownOpen;
    },
    confirmEdit(action = "confirm") {
      this.dropdownOpen = false;
      this.$emit("confirm-dialog", action);
    }
  }
};
</script>

<style scoped>
.confirm-dialog {
  display: flex;
  justify-content: flex-end;
  align-items: center;
}

/* Dropdown Styling */
.publish-dropdown-wrapper {
  position: relative;
  display: inline-block;
}

.dropdown-trigger {
  display: flex;
  align-items: center;
  gap: 5px;
}

.publish-dropdown-menu {
  position: absolute;
  bottom: 100%; /* Opens upwards, typical for modal footers */
  right: 0;
  z-index: 1000;
  min-width: 220px;
  background-color: white;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.15);
  border-radius: 4px;
  margin: 0 0 5px 0;
  padding: 5px 0;
  list-style: none;
  text-align: left;
}

.publish-dropdown-menu li {
  padding: 10px 15px;
  cursor: pointer;
  transition: background 0.2s;
  border-bottom: 1px solid #cfd8dc;
  line-height: 1.2;
}

.publish-dropdown-menu li:last-child {
  border-bottom: none;
}

.publish-dropdown-menu li:hover {
  background-color: #f5f5f5;
}

.action-title {
  display: block;
  font-weight: 600;
  font-size: 14px;
  color: #333;
}

.action-desc {
  display: block;
  font-size: 12px;
  color: #757575;
  margin-top: 4px;
}

/* Backdrop */
.dropdown-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 999;
  background: transparent;
}
</style>
