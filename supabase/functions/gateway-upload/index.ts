// Aegis Protocol — gateway-upload edge function.
//
// Single choke point for SOS uploads from mesh gateway devices.
// Owns DEDUPLICATION: every copy of a message converges here, keyed by
// message_id (PK on public.messages). Returns per-batch receipts
// { received, accepted, duplicates, failed, failed_ids } that the Android
// client (SosSyncWorker) uses to stop retrying uploaded messages.

import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type, x-aegis-gateway",
};

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const isUuid = (v: unknown): v is string =>
  typeof v === "string" && UUID_RE.test(v);

const MAX_BATCH = 200;

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (req.method !== "POST") {
    return json({ success: false, error: "Method not allowed" }, 405);
  }

  let body: Record<string, unknown>;
  try {
    body = await req.json();
  } catch {
    return json({ success: false, error: "Invalid JSON body" }, 400);
  }

  const gatewayDeviceId = body["gateway_device_id"];
  const incoming = Array.isArray(body["messages"]) ? body["messages"] : [];

  if (!isUuid(gatewayDeviceId)) {
    return json(
      { success: false, error: "gateway_device_id must be a UUID" },
      400,
    );
  }
  if (incoming.length === 0) {
    return json({
      success: true,
      received: 0,
      accepted: 0,
      duplicates: 0,
      failed: 0,
      failed_ids: [],
    });
  }
  if (incoming.length > MAX_BATCH) {
    return json(
      { success: false, error: `Batch too large (max ${MAX_BATCH})` },
      413,
    );
  }

  const received = incoming.length;
  let duplicates = 0;
  let failed = 0;
  const failedIds: string[] = [];

  // --- Pass 1: in-batch dedup + shape validation (no DB yet) ---
  const seen = new Set<string>();
  const candidates: Record<string, unknown>[] = [];
  for (const raw of incoming) {
    const m = (raw ?? {}) as Record<string, unknown>;
    const id = m["message_id"];
    if (typeof id !== "string" || id.length === 0) {
      failed++; // unidentifiable: counted but cannot be listed
      continue;
    }
    if (seen.has(id)) {
      duplicates++; // same message_id twice in one payload
      continue;
    }
    seen.add(id);
    candidates.push(m);
  }

  // --- Pass 2: field validation ---
  const valid: Record<string, unknown>[] = [];
  for (const m of candidates) {
    const id = m["message_id"] as string;
    const problems: string[] = [];
    if (!isUuid(id)) problems.push("message_id");
    if (m["victim_id"] != null && !isUuid(m["victim_id"])) {
      problems.push("victim_id");
    }
    if (!isUuid(m["origin_device_id"])) problems.push("origin_device_id");
    if (typeof m["text"] !== "string") problems.push("text");
    if (problems.length > 0) {
      failed++;
      failedIds.push(id);
      continue;
    }
    valid.push(m);
  }

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    { auth: { persistSession: false } },
  );

  // --- Pass 3: DB dedup — which message_ids already exist? ---
  const validIds = valid.map((m) => m["message_id"] as string);
  let alreadyStored = new Set<string>();
  if (validIds.length > 0) {
    const { data, error } = await supabase
      .from("messages")
      .select("message_id")
      .in("message_id", validIds);
    if (error) {
      // Fail closed on read error: report whole batch retryable.
      return json({
        success: false,
        received,
        accepted: 0,
        duplicates,
        failed: failed + valid.length,
        failed_ids: [...failedIds, ...validIds],
        error: `Dedup lookup failed: ${error.message}`,
      });
    }
    alreadyStored = new Set((data ?? []).map((r) => r.message_id as string));
  }

  const fresh = valid.filter(
    (m) => !alreadyStored.has(m["message_id"] as string),
  );
  duplicates += valid.length - fresh.length;

  // --- Pass 4: upsert victims (FK target), then insert messages ---
  // NOTE: victims.first_seen / last_seen are NOT NULL with no default, so
  // new rows need both; existing rows keep their original first_seen.
  const nowIso = new Date().toISOString();
  const victimLatest = new Map<string, Record<string, unknown>>();
  for (const m of fresh) {
    const vid = m["victim_id"] as string | null;
    if (vid && !victimLatest.has(vid)) {
      victimLatest.set(vid, {
        victim_id: vid,
        device_id: m["origin_device_id"],
        latest_latitude: m["latitude"] ?? null,
        latest_longitude: m["longitude"] ?? null,
      });
    }
  }
  if (victimLatest.size > 0) {
    const vids = [...victimLatest.keys()];
    const { data: existingVictims, error: victimLookupError } = await supabase
      .from("victims")
      .select("victim_id")
      .in("victim_id", vids);
    if (victimLookupError) {
      return json({
        success: false,
        received,
        accepted: 0,
        duplicates,
        failed: failed + fresh.length,
        failed_ids: [
          ...failedIds,
          ...fresh.map((m) => m["message_id"] as string),
        ],
        error: `Victim lookup failed: ${victimLookupError.message}`,
      });
    }
    const known = new Set(
      (existingVictims ?? []).map((r) => r.victim_id as string),
    );
    const newVictims = vids
      .filter((v) => !known.has(v))
      .map((v) => ({
        ...(victimLatest.get(v) as Record<string, unknown>),
        first_seen: nowIso,
        last_seen: nowIso,
      }));
    if (newVictims.length > 0) {
      const { error } = await supabase.from("victims").insert(newVictims);
      if (error) {
        return json({
          success: false,
          received,
          accepted: 0,
          duplicates,
          failed: failed + fresh.length,
          failed_ids: [
            ...failedIds,
            ...fresh.map((m) => m["message_id"] as string),
          ],
          error: `Victim insert failed: ${error.message}`,
        });
      }
    }
    for (const v of vids.filter((x) => known.has(x))) {
      const touch = victimLatest.get(v) as Record<string, unknown>;
      await supabase
        .from("victims")
        .update({
          device_id: touch["device_id"],
          latest_latitude: touch["latest_latitude"],
          latest_longitude: touch["latest_longitude"],
          last_seen: nowIso,
        })
        .eq("victim_id", v);
    }
  }

  const messageRows = fresh.map((m) => ({
    message_id: m["message_id"],
    victim_id: (m["victim_id"] as string | null) ?? null,
    origin_device_id: m["origin_device_id"],
    gateway_device_id: gatewayDeviceId,
    message_type: "SOS",
    latitude: m["latitude"] ?? null,
    longitude: m["longitude"] ?? null,
    text: m["text"],
    ttl: typeof m["ttl"] === "number" ? m["ttl"] : 7,
    hop_count: typeof m["hop_count"] === "number" ? m["hop_count"] : 0,
    created_at: m["created_at"] ?? new Date().toISOString(),
    status: "NEW",
  }));

  let accepted = 0;
  if (messageRows.length > 0) {
    const { error } = await supabase.from("messages").insert(messageRows);
    if (error) {
      // Bulk failed (e.g. one bad row): fall back to row-by-row to
      // isolate failures and still accept the good rows.
      for (const row of messageRows) {
        const { error: rowError } = await supabase
          .from("messages")
          .insert(row);
        if (rowError) {
          // 23505 = lost a race with a concurrent upload → duplicate.
          if (rowError.code === "23505") {
            duplicates++;
          } else {
            failed++;
            failedIds.push(row.message_id as string);
          }
        } else {
          accepted++;
        }
      }
    } else {
      accepted = messageRows.length;
    }
  }

  // --- Pass 5: batch receipt ---
  await supabase.from("gateway_uploads").insert({
    gateway_device_id: gatewayDeviceId,
    message_count: received,
    accepted_count: accepted,
    duplicate_count: duplicates,
    failed_count: failed,
  });

  return json({
    success: failed === 0,
    received,
    accepted,
    duplicates,
    failed,
    failed_ids: failedIds,
  });
});
