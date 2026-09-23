import { rmSync } from "node:fs";
import { resolve } from "node:path";

rmSync(resolve(".gemini-review.diff"), { force: true });
