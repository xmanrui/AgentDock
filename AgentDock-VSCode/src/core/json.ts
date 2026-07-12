export type JsonRecord = Record<string, unknown>;

export function asRecord(value: unknown): JsonRecord | undefined {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? (value as JsonRecord)
    : undefined;
}

export function objectValue(record: JsonRecord | undefined, name: string): JsonRecord | undefined {
  return asRecord(record?.[name]);
}

export function arrayValue(record: JsonRecord | undefined, name: string): unknown[] | undefined {
  const value = record?.[name];
  return Array.isArray(value) ? value : undefined;
}

export function stringValue(record: JsonRecord | undefined, name: string): string | undefined {
  const value = record?.[name];
  return typeof value === "string" ? value : undefined;
}

export function numberValue(record: JsonRecord | undefined, name: string): number | undefined {
  const value = record?.[name];
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

export function booleanValue(record: JsonRecord | undefined, name: string): boolean | undefined {
  const value = record?.[name];
  return typeof value === "boolean" ? value : undefined;
}

export function parseJsonObject(text: string, maximumLength = 8 * 1024 * 1024): JsonRecord | undefined {
  if (!text || text.length > maximumLength) {
    return undefined;
  }
  try {
    return asRecord(JSON.parse(text));
  } catch {
    return undefined;
  }
}

export function textFromContent(content: unknown): string | undefined {
  if (typeof content === "string") {
    return content;
  }
  if (Array.isArray(content)) {
    const values = content
      .map((item) => {
        if (typeof item === "string") return item;
        const block = asRecord(item);
        return stringValue(block, "text") ?? stringValue(block, "content");
      })
      .filter((value): value is string => Boolean(value?.trim()));
    return values.length > 0 ? values.join("\n") : undefined;
  }
  const block = asRecord(content);
  return stringValue(block, "text") ?? stringValue(block, "content");
}

export function epochMillis(value: unknown): number | undefined {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value > 10_000_000_000 ? value : value * 1_000;
  }
  if (typeof value !== "string" || !value.trim()) {
    return undefined;
  }
  const numeric = Number(value);
  if (Number.isFinite(numeric)) {
    return numeric > 10_000_000_000 ? numeric : numeric * 1_000;
  }
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

export function nonNegativeNumber(record: JsonRecord | undefined, name: string): number | undefined {
  const value = numberValue(record, name);
  return value === undefined ? undefined : Math.max(0, value);
}
