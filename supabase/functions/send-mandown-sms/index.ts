/**
 * Invio SMS MAN DOWN via Twilio (fallback quando la SIM dell'utente fallisce).
 * Richiede JWT utente autenticato (Authorization: Bearer).
 *
 * Body JSON:
 *   { "body": "testo SMS", "destinations": ["+39...", "+39..."] }
 *
 * Secret Supabase: TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, TWILIO_SMS_FROM (es. geoHELP)
 */
import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.1";

const corsHeaders: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
};

interface SendRequest {
  body?: string;
  destinations?: string[];
}

function jsonResponse(
  payload: Record<string, unknown>,
  status = 200,
): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function normalizeE164(raw: string): string | null {
  const t = raw.trim().replace(/\s+/g, "");
  if (!/^\+[1-9]\d{6,14}$/.test(t)) return null;
  return t;
}

async function sendTwilioSms(
  accountSid: string,
  authToken: string,
  from: string,
  to: string,
  body: string,
): Promise<{ ok: boolean; sid?: string; error?: string }> {
  const url =
    `https://api.twilio.com/2010-04-01/Accounts/${accountSid}/Messages.json`;
  const params = new URLSearchParams({ To: to, From: from, Body: body });
  const basic = btoa(`${accountSid}:${authToken}`);
  const res = await fetch(url, {
    method: "POST",
    headers: {
      Authorization: `Basic ${basic}`,
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body: params.toString(),
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    const d = data as { message?: string; code?: number; more_info?: string };
    const msg = [d.message, d.code != null ? `code ${d.code}` : "", d.more_info]
      .filter(Boolean)
      .join(" — ");
    return { ok: false, error: msg || res.statusText };
  }
  return { ok: true, sid: (data as { sid?: string }).sid };
}

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (req.method !== "POST") {
    return jsonResponse({ error: "method_not_allowed" }, 405);
  }

  const accountSid = Deno.env.get("TWILIO_ACCOUNT_SID")?.trim() ?? "";
  const authToken = Deno.env.get("TWILIO_AUTH_TOKEN")?.trim() ?? "";
  const from = Deno.env.get("TWILIO_SMS_FROM")?.trim() ?? "geoHELP";
  if (!accountSid || !authToken) {
    return jsonResponse({ error: "twilio_not_configured" }, 503);
  }

  const authHeader = req.headers.get("Authorization");
  if (!authHeader?.startsWith("Bearer ")) {
    return jsonResponse({ error: "unauthorized" }, 401);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const supabaseAnon = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
  if (!supabaseUrl || !supabaseAnon) {
    return jsonResponse({ error: "supabase_env_missing" }, 503);
  }

  const supabase = createClient(supabaseUrl, supabaseAnon, {
    global: { headers: { Authorization: authHeader } },
  });
  const { data: userData, error: userError } = await supabase.auth.getUser();
  if (userError || !userData?.user) {
    return jsonResponse({ error: "unauthorized" }, 401);
  }

  let payload: SendRequest;
  try {
    payload = await req.json();
  } catch {
    return jsonResponse({ error: "invalid_json" }, 400);
  }

  const text = payload.body?.trim() ?? "";
  const rawDests = payload.destinations ?? [];
  if (!text || rawDests.length === 0) {
    return jsonResponse({ error: "body_and_destinations_required" }, 400);
  }

  const destinations = [
    ...new Set(
      rawDests.map(normalizeE164).filter((d): d is string => d != null),
    ),
  ];
  if (destinations.length === 0) {
    return jsonResponse({ error: "no_valid_destinations" }, 400);
  }

  const results: Array<{ to: string; ok: boolean; sid?: string; error?: string }> =
    [];
  for (const to of destinations) {
    const r = await sendTwilioSms(accountSid, authToken, from, to, text);
    results.push({ to, ok: r.ok, sid: r.sid, error: r.error });
  }

  const sent = results.filter((r) => r.ok).length;
  const failed = results.length - sent;

  return jsonResponse({
    ok: sent > 0,
    sent,
    failed,
    from,
    results,
  }, sent > 0 ? 200 : 502);
});
