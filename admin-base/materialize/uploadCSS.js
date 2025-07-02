import { readFileSync } from "node:fs";
import { basename } from "node:path";

const origin = process.argv[2]; // e.g. http://localhost:8080
const credentials = process.argv[3]; // e.g. admin:admin
const cssFile = "target/classes/etc/felibs/materialize/materialize.css";

// Encode credentials
const encodedAuth = Buffer.from(credentials).toString("base64");

// Construct AEM endpoint URL
const relativePath = cssFile.replace(/^.*\/etc/, "/etc");
const url = `${origin}/bin/cpm/nodes/property.update.bin${relativePath}/_jcr_content`;

// Read CSS file content
const fileContent = readFileSync(cssFile, "utf8");

// Send PUT request to AEM
fetch(url, {
  method: "PUT",
  headers: {
    "content-type": "text/plain;charset=UTF-8",
    authorization: `Basic ${encodedAuth}`
  },
  body: fileContent
})
  .then(res => {
    console.log(`[${res.status}] ${basename(cssFile)} -> ${relativePath}`);
  })
  .catch(err => {
    console.error(`Error updating ${relativePath}:`, err);
  });
