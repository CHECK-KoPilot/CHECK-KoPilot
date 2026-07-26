import sharp from "sharp";
import { mkdirSync } from "node:fs";

mkdirSync("public", { recursive: true });

const jobs = [
  { src: "scripts/icon-source.svg", out: "public/pwa-192x192.png", size: 192 },
  { src: "scripts/icon-source.svg", out: "public/pwa-512x512.png", size: 512 },
  { src: "scripts/icon-source.svg", out: "public/apple-touch-icon.png", size: 180 },
  { src: "scripts/icon-maskable.svg", out: "public/maskable-icon-512x512.png", size: 512 },
];

for (const job of jobs) {
  await sharp(job.src).resize(job.size, job.size).png().toFile(job.out);
  console.log("generated", job.out);
}
