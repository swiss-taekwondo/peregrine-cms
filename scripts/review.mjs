import { execFileSync } from "node:child_process";
import { writeFileSync } from "node:fs";
import { resolve } from "node:path";

const isMain = process.argv[2] === "--main";
const cachedOrMain = isMain ? "main...HEAD" : "--cached";

const diff = execFileSync(
  "git",
  ["diff", cachedOrMain, "--diff-filter=ACMR"],
  { encoding: "utf8", maxBuffer: 10 * 1024 * 1024 },
);

if (!diff.trim()) {
  console.log(isMain ? "No changes against main to review." : "No staged changes to review.");
  process.exit(0);
}

const diffPathWithPrompt = resolve(".gemini-review.diff");

const prompt = `
Review the attached ${isMain ? "main branch" : "staged"} git diff as a senior Peregrine CMS engineer.

The attached .diff file contains all ${isMain ? "changes against main" : "staged changes"} for this review.

The CMS contains Java OSGi services, Vue 2 admin components, browser-side JavaScript,
AEM/JCR content definitions, and Maven/Rollup build integration. Review each changed
file according to its actual stack and consider interactions between the CMS runtime,
the admin UI, persisted dialog data, and generated client bundles.

Only report actual problems:
- bugs
- security vulnerabilities
- type-safety issues
- async/concurrency problems
- performance regressions
- missing important edge cases
- broken AEM/JCR schema or dialog compatibility
- lifecycle/resource leaks in Vue or browser code
- regressions in generated or deployed admin bundles

Do not report formatting or subjective style issues.

Be concise.

Return the review in Markdown format only. Use Markdown headings and bullet lists;
do not return HTML, JSON, or plain-text tables.

For every issue provide:
- severity
- file/location
- problem
- suggested fix

If there are no meaningful issues, respond exactly with:
Looks Good To Me

Important:
Use the attached diff as the source of truth for the changes being reviewed.
Do not assume that unchanged code shown as context in the diff was modified.
`.trim();
writeFileSync(diffPathWithPrompt, prompt + diff, "utf8");

const blueText = (text) => `\x1b[94m${text}\x1b[0m`;
const greenText = (text) => `\x1b[92m${text}\x1b[0m`;

console.log(`${blueText(`============================================================
                  GEMINI CODE REVIEW
============================================================`)}

${blueText("1.")} Open Gemini Chat: ${greenText(`https://gemini.google.com/`)}

${blueText("OPTIONAL:")} Under "More uploads" > "Import code", attach the whole repository.

${blueText("2.")} Attach this file:

   ${greenText(`${diffPathWithPrompt}`)}

${blueText("3.")} Run "${greenText(`npm run review:cleanup`)}" when you're done.
`);
