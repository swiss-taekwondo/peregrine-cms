<template>
	<div class="datetime-input-wrapper">
		<datetime
      v-model="sanitizedValue"
        type="date"
        placeholder="Select date"
      />
		<button
			:disabled="!value || schema.readonly"
			@click="clearValue"
			type="button"
			class="clear-button"
			title="Clear date"
		>
			<i class="material-icons">clear</i>
		</button>
	</div>
</template>

<script>
	export default {
		mixins: [ VueFormGenerator.abstractField ],
			computed: {
			sanitizedValue: {
				get () {
					return this.value ? this.value : ''
				},
				set (newValue) {
					this.value = newValue
				}
			}
		},
		methods: {
			clearValue() {
        this.value = "";
			}
		},
    updated() {
      console.log('updated', this, this.readOnly)
    }
	}
</script>
