import { readFileSync, readdirSync, statSync } from "fs";
import { join } from "path";
import fetch from "node-fetch";

const origin = process.argv[2]; // http://localhost:8080
const credentials = process.argv[3]; // admin:admin
const folder = process.argv[4]; // target/classes/etc/felibs/admin/js

const encodedAuth = Buffer.from(credentials).toString("base64");

function patch(dir) {
  const files = readdirSync(dir);
  for (const file of files) {
    const fullPath = join(dir, file);
    const stat = statSync(fullPath);
    if (stat.isDirectory()) {
      patch(fullPath);
    } else if (file.endsWith(".js")) {
      // Normalize path for cross-platform compatibility
      const normalizedPath = fullPath.replace(/\\/g, "/");
      const relativePath = normalizedPath.replace(/^.*\/etc/, "/etc");
      const fileContent = readFileSync(fullPath, "utf8");

      const url = `${origin}/bin/cpm/nodes/property.update.bin${relativePath}/_jcr_content`;

      fetch(url, {
        method: "PUT",
        headers: {
          "content-type": "text/plain;charset=UTF-8",
          authorization: `Basic ${encodedAuth}`
        },
        body: fileContent
      })
        .then(res => {
          console.log(`[${res.status}] ${relativePath} -> ${relativePath}`);
        })
        .catch(err => {
          console.error(`Error updating ${relativePath}:`, err);
        });
    }
  }
}

patch(folder);
