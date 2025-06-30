<template>
  <div class="font-size-wrapper" >
    <button class="add btn" @click="onAdd">
      <icon icon="add" :lib="iconLib" />
    </button>

    <div class="inputWrapper" @focusin="onFocusIn" @focusout="onFocusOut">
      <input v-model="inputValue" @input="onInput" @keyup="onInputKeyUp" ref="inputRef" type="number" max="250" min="1" />

      <ul>
        <template v-for="sizePreset in fontSizeSelections">
          <li :key="sizePreset" @click="onListSelect(sizePreset)">{{ sizePreset }}</li>
        </template>
      </ul>
    </div>

    <button class="subtract btn" @click="onSubtract">
      <icon icon="remove" :lib="iconLib" />
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
    exec: { type: Function },
    iconLib: {
      type: String,
      default: IconLib.MATERIAL_ICONS
    },
    isRangeInElement: {type: Function},
  },

  data() {
    return {
      fontSizeSelections: [8, 9, 10, 11, 12, 14, 18, 24, 30, 36, 48, 60, 72, 96],
      selectionRange: null,
      inputValue:  16,
    };
  },

  mounted() {
    this.inputValue = 16
  },

  methods: {
    applyFontSize(nr) {
      this.exec("updateFontSize", nr);
    },
    onAdd() {
      let value = this.inputValue
      value = Number(value);
      if (!value || isNaN(value)) return;
      value += 1;
      this.inputValue = value;
      this.applyFontSize(value)
    },
    onSubtract() {
      let value = this.inputValue;
      value = Number(value);
      if (!value || isNaN(value)) return;
      value -= 1;
      this.inputValue = value;
      this.applyFontSize(value)
    },

    onInput(e) {
      let value = e.target.value;
      value = value.replace(/[^\d]/g, "");
      value = Number(value);
      if (!value || isNaN(value)) return;
    },

    onInputKeyUp(e) {
      console.log(e.key)
      if (e.key === "Enter" || e.key === "Tab") {
        this.$refs.inputRef.parentElement.blur()
        this.$refs.inputRef.blur()
      }
    },
    
    onListSelect(sizePreset) {
      console.log(sizePreset)
      this.inputValue = sizePreset;
      this.applyFontSize(sizePreset)
      this.$nextTick(() => {
        this.$refs.inputRef.parentElement.blur()
        this.$refs.inputRef.blur()
      })
    },
    onFocusOut(e) {
      this.exec("restoreSelection")
      if (this.selectionRange) {
        const selection = window.getSelection()
        selection.removeAllRanges()
        selection.addRange(this.selectionRange)
      }
      this.$nextTick(() => {
        this.applyFontSize(this.inputValue)
      })
    },
    onFocusIn(e) {
      this.exec("saveSelection")
      const range = window.getSelection().getRangeAt(0);
      if (this.isRangeInElement(range)) {
        console.log('onfocusin: ', range)
        this.selectionRange = range
      }
    },
  }
};
</script>

<style scoped>
.font-size-wrapper {
  display: flex;
}
.inputWrapper {
  position: relative;
  margin: 0 2px;
  padding: 0;
}

.inputWrapper input {
  padding: 2px;
  margin: 0;
  width: 40px;
}

.inputWrapper ul {
  position: absolute;
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
