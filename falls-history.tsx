"use client";

import { useEffect, useState } from "react";
import {
  collection,
  limit,
  onSnapshot,
  orderBy,
  query,
} from "firebase/firestore";

import { db } from "@/src/lib/firebase";

type ActivityLog = {
  activity?: string;
  deviceId?: string;
  isAlert?: boolean;
  timestamp?: string;
};

type ActivityLogEntry = ActivityLog & {
  id: string;
};

const FALL_LOG_LIMIT = 50;
const ITEMS_PER_PAGE = 10;

function isFallEvent(log: ActivityLog) {
  const activity = log.activity?.toLowerCase() ?? "";

  return Boolean(log.isAlert) || activity.includes("fall");
}

function formatTimestamp(timestamp?: string) {
  if (!timestamp) {
    return "Waiting for timestamp";
  }

  const parsedDate = new Date(timestamp);

  if (Number.isNaN(parsedDate.getTime())) {
    return timestamp;
  }

  return new Intl.DateTimeFormat("en-GB", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(parsedDate);
}

function getDeviceCount(entries: ActivityLogEntry[]) {
  return new Set(entries.map((entry) => entry.deviceId).filter(Boolean)).size;
}

export default function FallsHistory() {
  const [events, setEvents] = useState<ActivityLogEntry[]>([]);
  const [isLoading, setIsLoading] = useState(() => Boolean(db));
  const [currentPage, setCurrentPage] = useState(1);
  const [errorMessage, setErrorMessage] = useState<string | null>(() =>
    db
      ? null
      : "Firebase is not configured. Set the NEXT_PUBLIC_FIREBASE_* environment variables to load activity_logs."
  );

  useEffect(() => {
    if (!db) {
      return;
    }

    const logsQuery = query(
      collection(db, "activity_logs"),
      orderBy("timestamp", "desc"),
      limit(FALL_LOG_LIMIT)
    );

    const unsubscribe = onSnapshot(
      logsQuery,
      (snapshot) => {
        setEvents(
          snapshot.docs.map((documentSnapshot) => ({
            id: documentSnapshot.id,
            ...(documentSnapshot.data() as ActivityLog),
          }))
        );
        setIsLoading(false);
        setErrorMessage(null);
      },
      (error) => {
        console.error("Failed to load activity logs", error);
        setIsLoading(false);
        setErrorMessage("Unable to read the activity_logs collection right now.");
      }
    );

    return unsubscribe;
  }, []);

  const fallEvents = events.filter(isFallEvent);
  const latestEvent = fallEvents[0];
  const totalFalls = fallEvents.length;
  const activeAlerts = fallEvents.filter((event) => event.isAlert).length;
  const monitoredDevices = getDeviceCount(fallEvents);

  const totalPages = Math.ceil(fallEvents.length / ITEMS_PER_PAGE);
  const startIndex = (currentPage - 1) * ITEMS_PER_PAGE;
  const endIndex = startIndex + ITEMS_PER_PAGE;
  const paginatedEvents = fallEvents.slice(startIndex, endIndex);

  return (
    <section className="relative mx-auto flex min-h-screen w-full max-w-6xl flex-col px-6 py-12 sm:px-8 lg:px-10">
      <header className="mb-12">
        <div className="space-y-4">
          <div className="flex items-baseline gap-2">
            <h1 className="font-[family-name:var(--font-display)] text-5xl font-light tracking-tight text-slate-900">
              Falls Monitor
            </h1>
            <span className="text-sm font-medium text-slate-500 uppercase tracking-wide">
              Real-time Detection
            </span>
          </div>
          <p className="max-w-2xl text-base leading-relaxed text-slate-600">
            Live incident tracking from your edge devices. Each detection is captured and displayed here for immediate review and response.
          </p>
        </div>
      </header>

      <div className="mb-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div className="rounded-lg border border-slate-200 bg-white px-5 py-5 shadow-sm">
          <p className="text-xs font-medium uppercase tracking-wider text-slate-500">
            Total Falls
          </p>
          <p className="mt-3 text-4xl font-light text-slate-900">{totalFalls}</p>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white px-5 py-5 shadow-sm">
          <p className="text-xs font-medium uppercase tracking-wider text-slate-500">
            Alerts
          </p>
          <p className="mt-3 text-4xl font-light text-rose-600">{activeAlerts}</p>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white px-5 py-5 shadow-sm">
          <p className="text-xs font-medium uppercase tracking-wider text-slate-500">
            Devices
          </p>
          <p className="mt-3 text-4xl font-light text-indigo-600">{monitoredDevices}</p>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white px-5 py-5 shadow-sm">
          <p className="text-xs font-medium uppercase tracking-wider text-slate-500">
            Status
          </p>
          <p className="mt-3 text-sm font-medium text-slate-700">
            {errorMessage ? (
              <span className="text-rose-600">Offline</span>
            ) : isLoading ? (
              <span className="text-amber-600">Loading…</span>
            ) : (
              <span className="text-emerald-600">Live</span>
            )}
          </p>
        </div>
      </div>

      <div className="relative">
        <div className="mb-8 rounded-lg border border-slate-200 bg-white shadow-sm">
          <div className="border-b border-slate-200 px-6 py-4">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-900">
              Incident Timeline
            </h2>
          </div>

          <div className="px-6 py-6">
            {errorMessage ? (
              <div className="py-12 text-center">
                <p className="text-sm text-slate-600">{errorMessage}</p>
                <p className="mt-2 text-xs text-slate-500">Check your Firebase configuration in .env</p>
              </div>
            ) : isLoading ? (
              <div className="py-12 text-center">
                <div className="mx-auto h-2 w-48 rounded-full bg-slate-200">
                  <div className="h-full w-1/3 rounded-full bg-indigo-500 animate-pulse" />
                </div>
                <p className="mt-4 text-sm text-slate-600">Connecting to Firestore…</p>
              </div>
            ) : fallEvents.length === 0 ? (
              <div className="py-12 text-center">
                <div className="mx-auto mb-3 h-10 w-10 rounded-full bg-slate-100" />
                <p className="text-sm font-medium text-slate-900">No fall events recorded</p>
                <p className="mt-1 text-xs text-slate-500">Fall detections will appear here when activity is captured</p>
              </div>
            ) : (
              <div className="flex flex-col h-full">
                <div className="max-h-96 overflow-y-auto flex-1">
                  <div className="space-y-3">
                    {paginatedEvents.map((event) => (
                  <div
                    key={event.id}
                    className="flex items-center gap-4 rounded-lg border border-slate-100 bg-slate-50 p-4 transition-colors hover:bg-slate-100"
                  >
                    <div className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-lg bg-indigo-100">
                      <div className="h-2 w-2 rounded-full bg-indigo-600" />
                    </div>

                    <div className="flex-1 min-w-0">
                      <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
                        <div className="flex items-center gap-2">
                          <p className="text-sm font-medium text-slate-900">
                            {event.deviceId ?? "Unknown"}
                          </p>
                          {event.isAlert && (
                            <span className="inline-flex rounded-full bg-rose-100 px-2 py-1 text-xs font-semibold text-rose-700">
                              Alert
                            </span>
                          )}
                        </div>
                        <p className="text-xs text-slate-500">
                          {formatTimestamp(event.timestamp)}
                        </p>
                      </div>
                      <p className="mt-1 text-xs text-slate-600">
                        {event.activity ?? "Fall Detected"}
                      </p>
                    </div>
                  </div>
                ))}
                  </div>
                </div>
                {fallEvents.length > ITEMS_PER_PAGE && (
                  <div className="mt-4 flex items-center justify-between border-t border-slate-200 pt-4">
                    <p className="text-xs text-slate-600">
                      Showing {startIndex + 1}–{Math.min(endIndex, fallEvents.length)} of {fallEvents.length}
                    </p>
                    <div className="flex gap-2">
                      <button
                        onClick={() => setCurrentPage((prev) => Math.max(1, prev - 1))}
                        disabled={currentPage === 1}
                        className="inline-flex items-center gap-1 rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed"
                      >
                        ← Previous
                      </button>
                      <div className="flex items-center gap-1">
                        {Array.from({ length: totalPages }, (_, i) => i + 1).map((page) => (
                          <button
                            key={page}
                            onClick={() => setCurrentPage(page)}
                            className={`h-8 w-8 rounded text-sm font-medium transition-colors ${
                              currentPage === page
                                ? "bg-indigo-600 text-white"
                                : "border border-slate-200 bg-white text-slate-700 hover:bg-slate-50"
                            }`}
                          >
                            {page}
                          </button>
                        ))}
                      </div>
                      <button
                        onClick={() => setCurrentPage((prev) => Math.min(totalPages, prev + 1))}
                        disabled={currentPage === totalPages}
                        className="inline-flex items-center gap-1 rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed"
                      >
                        Next →
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>

        {latestEvent && !errorMessage && !isLoading && (
          <div className="mt-8 rounded-lg border border-indigo-200 bg-indigo-50 p-6">
            <p className="text-xs font-semibold uppercase tracking-wide text-indigo-600">
              Latest Activity
            </p>
            <div className="mt-4 grid gap-4 sm:grid-cols-2">
              <div>
                <p className="text-xs text-indigo-600/70">Device</p>
                <p className="mt-1 text-sm font-medium text-slate-900">
                  {latestEvent.deviceId ?? "Unknown"}
                </p>
              </div>
              <div>
                <p className="text-xs text-indigo-600/70">Time</p>
                <p className="mt-1 text-sm font-medium text-slate-900">
                  {formatTimestamp(latestEvent.timestamp)}
                </p>
              </div>
            </div>
          </div>
        )}
      </div>
    </section>
  );
}