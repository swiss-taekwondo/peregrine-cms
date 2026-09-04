<template>
	<div class="telephone-input-wrapper">
		<div :class="schema.readonly ? 'wrapper' : 'hidden'">
			<input readonly :value="displayValue" />
		</div>

		<div :class="schema.readonly ? 'hidden' : ''">
			<input
				ref="phoneInput"
				type="tel"
				class="form-control"
				:id="uniqueId"
				autocomplete="tel">
		</div>

		<div v-if="fieldErrors.length > 0" class="errors help-block">
			<span class="tel-error">{{ fieldErrors[0] }}</span>
		</div>
	</div>
</template>

<script>
let scriptPromise = null;

function loadScript(src) {
	return new Promise((resolve) => {
		const script = document.createElement('script');

		script.src = src;
		script.onload = resolve;
		script.onerror = resolve;

		document.head.appendChild(script);
	});
}

async function loadIntlTelInput() {
	if (typeof window.intlTelInput !== 'undefined') {
		return;
	}

	if (!scriptPromise) {
		scriptPromise = loadScript(
			'/etc/felibs/admin/dependencies/intlTelInput.js'
		);
	}

	await scriptPromise;
}

export default {
	mixins: [VueFormGenerator.abstractField],

	data() {
		return {
			fieldErrors: [],
			iti: null,
			updatingFromValue: false
		};
	},

	computed: {
		displayValue() {
			return this.value || '';
		},

		uniqueId() {
			return this._uniqueId;
		}
	},

	created() {
	    this._uniqueId = `tel-${crypto.randomUUID()}`;
	},

	watch: {
		value(newVal) {
			if (!this.iti || this.updatingFromValue) {
				return;
			}

			const currentNumber = this.iti.getNumber();

			if (newVal === currentNumber) {
				return;
			}

			this.updatingFromValue = true;

			try {
				this.iti.setNumber(newVal || '');
			} finally {
				this.$nextTick(() => {
					this.updatingFromValue = false;
				});
			}
		}
	},

	async mounted() {
		await loadIntlTelInput();

		const input = this.$refs.phoneInput;

		if (
			!input ||
			typeof window.intlTelInput === 'undefined'
		) {
			return;
		}

		this.iti = window.intlTelInput(input, {
			separateDialCode: true,
			initialCountry: 'ch'
		});
		input.dataset.model = this.schema.model;

		if (this.value) {
			this.updatingFromValue = true;

			this.iti.setNumber(this.value);

			this.$nextTick(() => {
				this.updatingFromValue = false;
			});
		}

		input.addEventListener(
			'countrychange',
			this.handlePhoneChange
		);

		input.addEventListener(
			'input',
			this.handlePhoneChange
		);

		// this.validate();
	},

	beforeDestroy() {
		const input = this.$refs.phoneInput;

		if (input) {
			input.removeEventListener(
				'countrychange',
				this.handlePhoneChange
			);

			input.removeEventListener(
				'input',
				this.handlePhoneChange
			);
		}

		if (this.iti) {
			this.iti.destroy();
			this.iti = null;
		}
	},

	methods: {
		handlePhoneChange() {
			if (!this.iti || this.updatingFromValue) {
				return;
			}

			const isEmpty = !this.$refs.phoneInput.value.trim();
			const number = isEmpty ? '' : this.iti.getNumber();

			this.toggleDeleteProp(isEmpty);
			this.value = number;

			this.validate();
		},

		toggleDeleteProp(shouldDelete) {
			if (!this.schema.model) {
				return;
			}

			const deleteProps = this.model._opDeleteProps || [];
			const nextDeleteProps = deleteProps.filter((prop) => prop !== this.schema.model);

			if (shouldDelete) {
				nextDeleteProps.push(this.schema.model);
			}

			if (nextDeleteProps.length) {
				this.model._opDeleteProps = nextDeleteProps;
			} else {
				delete this.model._opDeleteProps;
			}
		},

		validate() {
			if (!this.iti) {
				return [];
			}

			const isEmpty = !this.$refs.phoneInput.value.trim();
			const isValid = isEmpty || this.iti.isValidNumber();
			const errorMsg = 'Invalid phone number';

			const fieldWrapper =
				this.$el.closest('.field-material-telephone');

			if (!isValid) {
				this.fieldErrors = [errorMsg];

				if (fieldWrapper) {
					fieldWrapper.classList.add('error');
					fieldWrapper.classList.remove('valid');
				}
			} else {
				this.fieldErrors = [];

				if (fieldWrapper) {
					fieldWrapper.classList.add('valid');
					fieldWrapper.classList.remove('error');
				}
			}

			// Set errors on VFG so it knows validation failed
			if (this.schema && this.schema.model) {
				const vfg = this.$parent;
				while (vfg && !vfg.validate) {
					vfg = vfg.$parent;
				}
				if (vfg && vfg.fieldErrors) {
					if (!isValid) {
						vfg.fieldErrors[this.schema.model] = [errorMsg];
					} else {
						delete vfg.fieldErrors[this.schema.model];
					}
				}
			}

			return this.fieldErrors;
		}
	}
};
</script>

<style>
.telephone-input-wrapper .iti,
.telephone-input-wrapper > .iti--input-container {
	width: 100%;
}

.iti__selected-country {
	display: flex !important;
	padding: 0 !important;
	margin: 0 !important;
}

.iti__selected-country,
.iti__search-clear {
	border: none !important;
	border-radius: 0 !important;
}

.iti__selected-country,
.iti__selected-country[aria-expanded=true],
.iti__selected-country-primary,
.iti__selected-country-primary:hover,
.iti__search-clear:hover {
	background-color: transparent !important;
}

.iti__search-input {
	padding-left: 28px !important;
}

.hidden {
	display: none !important;
}
</style>
