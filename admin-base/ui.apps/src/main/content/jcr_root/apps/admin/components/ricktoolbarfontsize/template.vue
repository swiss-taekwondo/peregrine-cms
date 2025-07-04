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
    },
  },

  data() {
    return {
      fontSizeSelections: [ 8, 9, 10, 11, 12, 14, 18, 24, 30, 36, 48, 60, 72, 96 ],
      selectionRange: null,
      inputValue: "",
      inlineListenerInterval: null,
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
    document.addEventListener("selectionchange", this.onSelectionChange);

    // this is bad, but it doesn't work with on load/DOMContentLoaded or readystate, the listener just doesn't get triggered for the sub-nav input
    // because it loads before the iframe.
    // this is the most reliable thing I could come up with to circumvent this BS
    document.querySelector("iframe#editview").contentDocument.addEventListener("selectionchange", this.onSelectionChange);
    this.inlineListenerInterval = setInterval(() => {
      // Must queryselect each time, it seems the iframe rerenders sometimes causing event listeners to be lost
      document.querySelector("iframe#editview").contentDocument.removeEventListener("selectionchange", this.onSelectionChange);
      document.querySelector("iframe#editview").contentDocument.addEventListener("selectionchange", this.onSelectionChange);
    }, 1000)
  },
  onBeforeUnmount() {
    document.removeEventListener("selectionchange", this.onSelectionChange);
    document.querySelector("iframe#editview").contentDocument.removeEventListener("selectionchange", this.onSelectionChange);
    clearInterval(this.inlineListenerInterval)
  },

  methods: {

    onSelectionChange(event) {
      const currSelection = event.currentTarget.getSelection()
      if (currSelection.rangeCount <= 0) return
      const iframeDoc = document.querySelector("iframe#editview").contentDocument
      if (event.currentTarget.isEqualNode(iframeDoc)) document.getSelection().removeAllRanges() // remove selection
      if (event.currentTarget.isEqualNode(document)) iframeDoc.getSelection().removeAllRanges() // remove selection

      if (event.currentTarget.isEqualNode(iframeDoc)) clearInterval(this.inlineListenerInterval)
      if (document.activeElement.isEqualNode(this.$refs.inputRef)) return;

      const currRange = currSelection.getRangeAt(0);

      if (!this.isRangeInEditor(currRange)) return

      const htmlEl = currRange.startContainer.closest
        ? currRange.startContainer
        : currRange.startContainer.parentElement;

      const fontSizeParent = htmlEl.closest('[style*="font-size"]');
      if (fontSizeParent && this.isNodeInEditor(fontSizeParent)) {
        const nr = Number(fontSizeParent.style.fontSize.replace("px", ""));
        if (!isNaN(nr)) {
          this.inputValue = nr;
        }
        return;
      }

      const defaultFontSize = Number(
        this.getDefaultFontSize().replace("px", "")
      );
      if (defaultFontSize && !isNaN(defaultFontSize)) {
        this.inputValue = defaultFontSize;
      }
      this.$nextTick(() => {
        this.$nextTick(() => {
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
