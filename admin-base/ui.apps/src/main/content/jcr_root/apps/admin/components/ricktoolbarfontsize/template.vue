<template>
  <div class="font-size-wrapper" @focusin="onFocusIn" @focusout="onFocusOut">
    <button class="add btn" @click="onAdd">
      <icon icon="add" :lib="iconLib" />
    </button>

    <div class="inputWrapper">
      <input value="16" @input="onInput" @blur="onInputBlur" @keydown="onInputKeyDown" ref="inputRef" type="number" max="250" min="1" />

      <ul>
        <template v-for="item in fontSizeSelections">
          <li data-value="item" @click="onListSelect">{{ item }}</li>
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
    }
  },

  data() {
    return {
      fontSizeSelections: [8, 9, 10, 11, 12, 14, 18, 24, 30, 36, 48, 60, 72, 96]
    };
  },

  methods: {
    applyFontSize(nr) {
      this.exec("updateFontSize", nr);
    },
    onAdd() {
      let value = this.$refs.inputRef.value;
      value = Number(value);
      if (!value || isNaN(value)) return;
      value += 1;
      this.$refs.inputRef.value = value;
      this.applyFontSize(value)
    },
    onSubtract() {
      let value = this.$refs.inputRef.value;
      value = Number(value);
      if (!value || isNaN(value)) return;
      value -= 1;
      this.$refs.inputRef.value = value;
      this.applyFontSize(value)
    },

    onInput(e) {
      let value = e.target.value;
      value = value.replace(/[^\d]/g, "");
      value = Number(value);
      if (!value || isNaN(value)) return;
    },
    onInputBlur() {
      let value = this.$refs.inputRef.value
      value = value.replace(/[^\d]/g, "");
      value = Number(value);
      if (!value || isNaN(value)) return;
      this.exec("restoreSelection")
      this.applyFontSize(value)
    },
    onInputKeyDown(e) {
      if (e.key === "Enter" || e.key === "Tab") {
        e.target.blur()
      }
    },
    
    onListSelect(e) {
      let value = e.target.dataset.value;
      console.log(value);
      this.$refs.inputRef.value = value;
      this.applyFontSize(value)
    },
    onFocusOut(e) {
      this.exec("restoreSelection")
    },
    onFocusIn(e) {
      this.exec("saveSelection")
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
