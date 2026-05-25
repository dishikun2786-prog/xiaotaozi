import crypto from "crypto";

const BASE32_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const PREFIX = "XTZ";
const GROUP_SIZE = 4;
const GROUPS = 3;

export function generatePlainCode(): string {
  const bytes = crypto.randomBytes(9); // 72 bits entropy
  let code = "";
  let buffer = 0;
  let bitsInBuffer = 0;

  for (let i = 0; i < bytes.length; i++) {
    buffer = (buffer << 8) | bytes[i];
    bitsInBuffer += 8;
    while (bitsInBuffer >= 5) {
      bitsInBuffer -= 5;
      const index = (buffer >> bitsInBuffer) & 0x1f;
      code += BASE32_ALPHABET[index];
    }
  }
  if (bitsInBuffer > 0) {
    code += BASE32_ALPHABET[(buffer << (5 - bitsInBuffer)) & 0x1f];
  }

  return code.slice(0, 12);
}

export function formatCardCode(plainCode: string): string {
  const groups: string[] = [];
  for (let i = 0; i < plainCode.length; i += GROUP_SIZE) {
    groups.push(plainCode.slice(i, i + GROUP_SIZE));
  }
  return PREFIX + "-" + groups.slice(0, GROUPS).join("-");
}

export function hashCode(plainCode: string): string {
  return crypto.createHash("sha256").update(plainCode).digest("hex");
}

export function generateCardKeys(count: number): { plainCode: string; formattedCode: string; codeHash: string; codePrefix: string; codeLast4: string }[] {
  return Array.from({ length: count }, () => {
    const plainCode = generatePlainCode();
    return {
      plainCode,
      formattedCode: formatCardCode(plainCode),
      codeHash: hashCode(plainCode),
      codePrefix: PREFIX + "-",
      codeLast4: plainCode.slice(-4),
    };
  });
}
