import { rmSync } from "node:fs";
import { resolve } from "node:path";

rmSync(resolve(".gemini-review.diff"), { force: true });
rmSync(resolve(".gemini-review-with-prompt.diff"), { force: true });
