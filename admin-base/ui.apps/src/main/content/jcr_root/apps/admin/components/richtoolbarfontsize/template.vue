<template>
  <div class="font-size-wrapper btn-group">
    <button class="subtract btn" @click="onSubtract" title="Decrease font size" :disabled="toolbarDisabled">
      <icon icon="remove" :lib="iconLib" />
    </button>

    <div class="inputWrapper" @focusin="onFocusIn" @focusout="onFocusOut">
      <input
        v-bind:value="inputValue"
        @input="onInput"
        @keyup="onInputKeyUp"
        ref="inputRef"
        type="number"
        max="250"
        min="1"
        :disabled="toolbarDisabled"
      />

      <ul>
        <template v-for="sizePreset in fontSizeSelections">
          <li :key="sizePreset" @click="onListSelect(sizePreset)">
            {{ sizePreset }}
          </li>
        </template>
      </ul>
    </div>

    <button class="add btn" @click="onAdd" title="Increase font size" :disabled="toolbarDisabled">
      <icon icon="add" :lib="iconLib" />
    </button>
  </div>
</template>

<script>
import { IconLib } from "../../../../../../js/constants";
import Icon from "../icon/template.vue";

export default {
  name: "FontSizeSelector",
  components: { Icon },
  props: {
    currentFontSize: {
      type: Number,
      default: null,
    },
    lastInRteSelection: {
      default: null,
    },
    iconLib: {
      type: String,
      default: IconLib.MATERIAL_ICONS
    },
    toolbarDisabled: {
      type: Boolean,
      default: false
    },
  },

    data() {
      return {
      fontSizeSelections: [ 8, 9, 10, 11, 12, 14, 18, 24, 30, 36, 48, 60, 72, 96 ],
      selectionRange: null,
      inputValue: this.currentFontSize,
      inlineListenerInterval: null,
      presetSelectionActive: false,
    };
  },

  computed: {
    forInline() {
      if (this.$refs.inputRef.closest('.richtoolbar.on-sub-nav')) {
        return true
      }
      return false
    }
  },

  mounted() {
  },
  onBeforeUnmount() {
  },
  watch: {
    currentFontSize(newValue) {
      this.inputValue = newValue;
    }
  },

  methods: {
    applyFontSize() {
      if (!this.inputValue) {
        console.warn("tried to set falsy fontsize");
        return;
      }
      // this.exec("updateFontSize", this.inputValue);
      window.dispatchEvent(new CustomEvent('richtoolbar:cmd', { detail: { cmd: "updateFontSize", value: this.inputValue  } }))
    },
    onAdd() {
      let value = this.inputValue;
      value = Number(value);
      if (!value || isNaN(value)) return;
      value += 1;
      this.inputValue = value;
      this.applyFontSize();
    },
    onSubtract() {
      let value = this.inputValue;
      value = Number(value);
      if (!value || isNaN(value)) return;
      value -= 1;
      this.inputValue = value;
      this.applyFontSize();
    },

    onInput(e) {
      let value = e.target.value;
      value = value.replace(/[^\d]/g, "");
      value = Number(value);
      if (!value || isNaN(value)) return;
      this.inputValue = value;
    },

    onInputKeyUp(e) {
      if (e.key === "Enter" || e.key === "Tab") {
        this.$refs.inputRef.parentElement.blur();
        this.$refs.inputRef.blur();
      }
    },

    onListSelect(sizePreset) {
      this.inputValue = sizePreset;
      this.applyFontSize();
    },

    // save/load selection between manual inputs
    onFocusOut(e) {
    },
    onFocusIn(e) {
    }
  }
};
</script>

<style scoped>
.font-size-wrapper {
  display: flex;
}

.inputWrapper {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  background-color: white;
  color: black;
  border-bottom: 1px solid var(--pcms-blue-grey);
}

.inputWrapper input {
  padding: 0;
  margin: 0;
  border: none;
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  text-align: center;
}

/* Hide the browser's number-input spinner arrows visually
   while keeping keyboard arrow-key increment/decrement intact */
.inputWrapper input::-webkit-outer-spin-button,
.inputWrapper input::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}

.inputWrapper input[type="number"] {
  -moz-appearance: textfield;
}

.inputWrapper ul {
  position: absolute;
  display: flex;
  flex-direction: column;
  z-index: 1;
  top: 100%;
  left: 0;
  width: 100%;
  visibility: collapse;
  background-color: white;
  border: 1px solid var(--pcms-gray);

  transition: visibility 0.3s;
}

.inputWrapper ul,
.inputWrapper ul li {
  padding: 2px;
  margin: 0;
  list-style: none;
}
.inputWrapper ul li {
  cursor: pointer;
}

.inputWrapper:focus-within ul {
  visibility: visible;
}
</style>
