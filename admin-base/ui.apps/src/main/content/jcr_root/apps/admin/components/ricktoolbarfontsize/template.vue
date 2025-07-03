<template>
  <div class="font-size-wrapper">
    <button class="add btn" @click="onAdd">
      <icon icon="add" :lib="iconLib" />
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
      />

      <ul>
        <template v-for="sizePreset in fontSizeSelections">
          <li :key="sizePreset" @click="onListSelect(sizePreset)">
            {{ sizePreset }}
          </li>
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
    isRangeInEditor: { type: Function },
    getDefaultFontSize: {
      type: Function
    },
    isNodeInEditor: {
      type: Function
    },
    getSelection: {
      type: Function
    }
  },

  data() {
    return {
      fontSizeSelections: [ 8, 9, 10, 11, 12, 14, 18, 24, 30, 36, 48, 60, 72, 96 ],
      selectionRange: null,
      inputValue: ""
    };
  },

  mounted() {
    console.log('mounted input:', this.$refs.inpuRef)
    const testOnSel =(event) => {
      this.onSelectionchange(event, this.$refs.inputRef);
    }
    this.$nextTick(() => {
      setTimeout(() => {

        this.inputValue = "";
        // document.addEventListener("selectionchange", this.onSelectionChange);
        document.querySelector("iframe#editview").contentDocument.addEventListener("selectionchange", testOnSel);
      })
    })
  },
  onBeforeUnmount() {
    console.log('mounted:', this.$refs.inpuRef)
    // document.removeEventListener("selectionchange", this.onSelectionChange);
    document.querySelector("iframe#editview").contentDocument.removeEventListener("selectionchange", this.onSelectionChange);
  },

  methods: {
    onPreviewMessage(event) {
      console.log('event', event)
    },

    onSelectionChange(event, inputRef) {
      if (!this.$refs.inputRef) console.log('WTF', this.$refs, inputRef)
      if (this.$refs.inputRef && this.$refs.inputRef.closest('.richtoolbar.on-right-panel')) return
      console.log(event)
      this.$nextTick(() => {
        this.$nextTick(() => {
          if (document.activeElement.isEqualNode(this.$refs.inputRef)) return;
          // const currSelection = this.getSelection(0);
          const currSelection = event.target.getSelection().getRangeAt(0);
          if (this.isRangeInEditor(currSelection)) {
            const htmlEl = currSelection.startContainer.closest
              ? currSelection.startContainer
              : currSelection.startContainer.parentElement;

            const fontSizeParent = htmlEl.closest('[style*="font-size"]');
            if (fontSizeParent && this.isNodeInEditor(fontSizeParent)) {
              const nr = Number(fontSizeParent.style.fontSize.replace("px", ""));
              console.log('number', nr, this.$refs.inputRef.value);
              if (!isNaN(nr)) {
                this.inputValue = nr;
              }
              return;
            }
          }

          const defaultFontSize = Number(
            this.getDefaultFontSize().replace("px", "")
          );
          console.log('onselectionchange going for default', defaultFontSize, this.inputValue, this.$refs.inputRef);
          if (defaultFontSize && !isNaN(defaultFontSize)) {
            this.inputValue = defaultFontSize;
          }
        });
      });
    },

    applyFontSize() {
      if (!this.inputValue) {
        console.warn("tried to set falsy fontsize");
        return;
      }
      this.exec("updateFontSize", this.inputValue);
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
      console.log('oninput', e.target, this.$refs.inputRef);
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
      this.$nextTick(() => {
        this.$refs.inputRef.parentElement.blur();
        this.$refs.inputRef.blur();
      });
    },
    onFocusOut(e) {
      this.exec("restoreSelection");
      if (this.selectionRange) {
        const selection = window.getSelection();
        selection.removeAllRanges();
        selection.addRange(this.selectionRange);
      }
      this.$nextTick(() => {
        this.applyFontSize();
      });
    },
    onFocusIn(e) {
      this.exec("saveSelection");
      const range = window.getSelection().getRangeAt(0);
      if (this.isRangeInEditor(range)) {
        this.selectionRange = range;
      }
    }
  }
};
</script>

<style scoped>
.font-size-wrapper {
  display: flex;
  border-left: 1px solid var(--pcms-gray);
}
.inputWrapper {
  position: relative;
  margin: 0 2px;
  padding: 0;
  background-color: white;
  color: black;
}

.inputWrapper input {
  padding: 2px;
  margin: 0;
  width: 42px;
  height: 32px;
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
