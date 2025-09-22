import React, {useEffect, useMemo, useState} from "react";

/* ---------------- Types ---------------- */
type CategoryRatings = Record<string, number>;

export interface NormalizedReview {
    id: string;
    listingName: string | null;
    guestName: string | null;
    direction: string;
    status: string;
    overallRating: number | null;
    categoryRatings: CategoryRatings | null;
    channel: string | null;
    text: string | null;
    submittedAt: string | null;
}

interface NormalizedReviewResponse {
    source: string;
    count: number;
    reviews: NormalizedReview[];
}

type Listing = { name: string; placeId: string; address?: string };

/* ---------------- Utils ---------------- */
const API_BASE =
    (import.meta as any).env?.VITE_API_BASE_URL?.toString().replace(/\/$/, "") ||
    ""; // if blank, relative /api/* works with Vercel rewrites

async function safeJson<T = any>(res: Response): Promise<T | null> {
    if (!res.ok) return null;
    const t = await res.text();
    if (!t) return null;
    try {
        return JSON.parse(t) as T;
    } catch {
        return null;
    }
}

const toDate = (s?: string | null) => (s ? new Date(s) : null);
const inRange = (d: Date | null, from: Date | null, to: Date | null) => {
    if (!d) return false;
    if (from && d < from) return false;
    if (to && d > to) return false;
    return true;
};
const avg = (xs: number[]) => (xs.length ? xs.reduce((a, b) => a + b, 0) / xs.length : NaN);
const ratingOf = (r: NormalizedReview) => r.overallRating ?? avg(Object.values(r.categoryRatings ?? {}));

/* ---------------- Small UI helpers ---------------- */
function StatCard({
                      label,
                      value,
                      sub,
                  }: {
    label: string;
    value: React.ReactNode;
    sub?: React.ReactNode;
}) {
    return (
        <div className="border rounded-lg p-4 bg-white">
            <div className="text-sm text-gray-500">{label}</div>
            <div className="mt-1 text-2xl font-semibold">{value}</div>
            {sub && <div className="mt-1 text-xs text-gray-500">{sub}</div>}
        </div>
    );
}

function ReviewText({text}: { text: string | null }) {
    const [expanded, setExpanded] = useState(false);
    if (!text) return <span>—</span>;
    const isLong = text.length > 320;

    return (
        <div className="max-w-full">
            <div
                style={
                    expanded
                        ? {overflowWrap: "anywhere", whiteSpace: "pre-wrap"}
                        : {
                            overflow: "hidden",
                            display: "-webkit-box",
                            WebkitLineClamp: 3,
                            WebkitBoxOrient: "vertical",
                            overflowWrap: "anywhere",
                            whiteSpace: "pre-wrap",
                        }
                }
            >
                {text}
            </div>
            {isLong && (
                <button
                    type="button"
                    className="mt-1 text-xs underline"
                    onClick={() => setExpanded((v) => !v)}
                >
                    {expanded ? "Show less" : "Show more"}
                </button>
            )}
        </div>
    );
}

/* ---------------- Component ---------------- */
export default function ManagerDashboard() {
    // data
    const [allListings, setAllListings] = useState<Listing[]>([]);
    const [reviews, setReviews] = useState<NormalizedReview[]>([]);
    const [loading, setLoading] = useState(true);
    const [err, setErr] = useState<string | null>(null);

    // filters
    const [query, setQuery] = useState("");
    const [channel, setChannel] = useState<string>("all");
    const [listing, setListing] = useState<string>("all");
    const [ratingMin, setRatingMin] = useState<number>(0);
    const [ratingMax, setRatingMax] = useState<number>(10);
    const [from, setFrom] = useState<string>("");
    const [to, setTo] = useState<string>("");
    const [sort, setSort] = useState<"submittedAt" | "rating">("submittedAt");
    const [dir, setDir] = useState<"asc" | "desc">("desc");
    const [onlySelected, setOnlySelected] = useState<boolean>(false); // reserved

    // add listing modal
    const [addOpen, setAddOpen] = useState(false);
    const [searchQ, setSearchQ] = useState("");
    const [results, setResults] = useState<{ name: string; placeId: string; address: string }[]>([]);
    const [saving, setSaving] = useState(false);

    // load data
    const fetchAll = async () => {
        setLoading(true);
        setErr(null);
        try {
            const [lres, rres] = await Promise.all([
                fetch(`${API_BASE}/api/listings`),
                fetch(`${API_BASE}/api/reviews/combined?limit=500&offset=0`),
            ]);
            const listings = (await safeJson<Listing[]>(lres)) ?? [];
            const r =
                (await safeJson<NormalizedReviewResponse>(rres)) ??
                ({source: "combined", count: 0, reviews: []} as NormalizedReviewResponse);
            setAllListings(listings);
            setReviews(r.reviews ?? []);
        } catch (e: any) {
            setErr(e?.message || "Failed to load data");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchAll();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    // channels & listings
    const channels = useMemo(() => {
        const s = new Set<string>();
        reviews.forEach((r) => r.channel && s.add(r.channel));
        return ["all", ...Array.from(s)];
    }, [reviews]);

    const listingNames = useMemo(() => {
        const s = new Set<string>();
        allListings.forEach((l) => s.add(l.name));
        reviews.forEach((r) => r.listingName && s.add(r.listingName));
        return ["all", ...Array.from(s)];
    }, [allListings, reviews]);

    /* ---------- filtering & sorting ---------- */
    const filtered = useMemo(() => {
        const q = query.trim().toLowerCase();
        const fromD = from ? new Date(from) : null;
        const toD = to ? new Date(to) : null;

        let arr = reviews.filter((r) => {
            const d = toDate(r.submittedAt);
            const rat = ratingOf(r);
            const okQ =
                !q ||
                (r.text ?? "").toLowerCase().includes(q) ||
                (r.guestName ?? "").toLowerCase().includes(q) ||
                (r.listingName ?? "").toLowerCase().includes(q);
            const okCh = channel === "all" || r.channel === channel;
            const okL = listing === "all" || r.listingName === listing;
            const okR = !Number.isFinite(rat) ? false : (rat as number) >= ratingMin && (rat as number) <= ratingMax;
            const okD = inRange(d, fromD, toD);
            return okQ && okCh && okL && okR && okD;
        });

        arr.sort((a, b) => {
            if (sort === "rating") {
                const ra = ratingOf(a);
                const rb = ratingOf(b);
                const cmp = (rb || -999) - (ra || -999);
                return dir === "asc" ? -cmp : cmp;
            } else {
                const da = toDate(a.submittedAt)?.getTime() ?? -1;
                const db = toDate(b.submittedAt)?.getTime() ?? -1;
                const cmp = db - da;
                return dir === "asc" ? -cmp : cmp;
            }
        });

        return arr;
    }, [reviews, query, channel, listing, ratingMin, ratingMax, from, to, sort, dir]);

    /* ---------- KPIs & derived metrics ---------- */
    const totalReviews = filtered.length;
    const ratings = filtered.map(ratingOf).filter((n) => Number.isFinite(n)) as number[];
    const avgRatingAll = ratings.length ? avg(ratings) : NaN;

    const now = new Date();
    const d30 = new Date(now);
    d30.setDate(d30.getDate() - 30);

    const last30 = filtered.filter((r) => {
        const d = toDate(r.submittedAt);
        return d && d >= d30 && d <= now;
    });
    const last30Ratings = last30.map(ratingOf).filter((n) => Number.isFinite(n)) as number[];
    const avgRating30 = last30Ratings.length ? avg(last30Ratings) : NaN;

    const positiveShare = ratings.length ? (ratings.filter((n) => n >= 8).length / ratings.length) * 100 : NaN;

    const latestDate = useMemo(() => {
        const ds = filtered.map((r) => toDate(r.submittedAt)?.getTime() ?? -1);
        const max = Math.max(...ds);
        return Number.isFinite(max) && max > 0 ? new Date(max) : null;
    }, [filtered]);

    const listingCount = useMemo(() => {
        const s = new Set(filtered.map((r) => r.listingName ?? "__none"));
        return s.has("__none") ? s.size - 1 : s.size;
    }, [filtered]);

    const ratingBuckets = useMemo(() => {
        const buckets = [0, 0, 0, 0, 0]; // 0–2, 2–4, 4–6, 6–8, 8–10
        ratings.forEach((n) => {
            if (n < 2) buckets[0] += 1;
            else if (n < 4) buckets[1] += 1;
            else if (n < 6) buckets[2] += 1;
            else if (n < 8) buckets[3] += 1;
            else buckets[4] += 1;
        });
        const total = ratings.length || 1;
        return buckets.map((c) => ({count: c, pct: (c / total) * 100}));
    }, [ratings]);

    const channelCounts = useMemo(() => {
        const m = new Map<string, number>();
        filtered.forEach((r) => {
            const k = r.channel ?? "unknown";
            m.set(k, (m.get(k) ?? 0) + 1);
        });
        return Array.from(m.entries()).sort((a, b) => b[1] - a[1]);
    }, [filtered]);

    // per-listing table
    const perListing = useMemo(() => {
        const m = new Map<string, { listing: string; count: number; avg: number; last: string | null }>();
        filtered.forEach((r) => {
            const key = r.listingName ?? "(Unassigned)";
            const cur = m.get(key) ?? {listing: key, count: 0, avg: 0, last: null};
            const rat = ratingOf(r);
            if (Number.isFinite(rat)) {
                const newCount = cur.count + 1;
                const newAvg = (cur.avg * cur.count + (rat as number)) / newCount;
                cur.count = newCount;
                cur.avg = newAvg;
            } else {
                cur.count += 1;
            }
            const d = r.submittedAt;
            if (!cur.last || (d && new Date(d) > new Date(cur.last))) cur.last = d ?? cur.last;
            m.set(key, cur);
        });
        return Array.from(m.values()).sort((a, b) => a.listing.localeCompare(b.listing));
    }, [filtered]);

    // nav
    const openProperty = (listingName: string) => {
        const url = `/index.html?view=property&listing=${encodeURIComponent(listingName)}`;
        window.location.href = url;
    };

    // add listing flow
    const doSearch = async () => {
        if (!searchQ.trim()) return setResults([]);
        const r = await fetch(`${API_BASE}/api/listings/search?q=${encodeURIComponent(searchQ.trim())}`);
        setResults(((await safeJson(r)) as any[]) ?? []);
    };
    const addListing = async (name: string, placeId: string) => {
        setSaving(true);
        try {
            await fetch(
                `${API_BASE}/api/listings?name=${encodeURIComponent(name)}&placeId=${encodeURIComponent(placeId)}`,
                {method: "POST"}
            );
            const lres = await fetch(`${API_BASE}/api/listings`);
            setAllListings((await safeJson<Listing[]>(lres)) ?? []);
            setAddOpen(false);
            setSearchQ("");
            setResults([]);
        } finally {
            setSaving(false);
        }
    };

    /* ---------- UI ---------- */
    return (
        <div className="mx-auto max-w-6xl px-4 py-6">
            <h1 className="text-3xl font-bold mb-6">Manager Dashboard</h1>

            {err && (
                <div className="mb-4 rounded border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-700">
                    {err}
                </div>
            )}

            {/* Filters */}
            <div className="grid grid-cols-1 md:grid-cols-12 gap-3 mb-4 items-center">
                <input
                    className="border rounded px-3 py-2 md:col-span-3"
                    placeholder="Search text, guest, listing"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                />
                <select
                    className="border rounded px-3 py-2 md:col-span-2"
                    value={channel}
                    onChange={(e) => setChannel(e.target.value)}
                >
                    {channels.map((c) => (
                        <option key={c} value={c}>
                            {c === "all" ? "All" : c}
                        </option>
                    ))}
                </select>
                <div className="flex gap-2 md:col-span-3">
                    <select
                        className="border rounded px-3 py-2 flex-1"
                        value={listing}
                        onChange={(e) => setListing(e.target.value)}
                    >
                        {listingNames.map((n) => (
                            <option key={n} value={n}>
                                {n === "all" ? "All" : n}
                            </option>
                        ))}
                    </select>
                    <button className="border rounded px-3 py-2" onClick={() => setAddOpen(true)}>
                        + Add
                    </button>
                </div>
                <input
                    className="border rounded px-3 py-2 md:col-span-1"
                    type="number"
                    min={0}
                    max={10}
                    value={ratingMin}
                    onChange={(e) => setRatingMin(Number(e.target.value))}
                    placeholder="Min"
                />
                <input
                    className="border rounded px-3 py-2 md:col-span-1"
                    type="number"
                    min={0}
                    max={10}
                    value={ratingMax}
                    onChange={(e) => setRatingMax(Number(e.target.value))}
                    placeholder="Max"
                />
                <input
                    className="border rounded px-3 py-2 md:col-span-1"
                    type="date"
                    value={from}
                    onChange={(e) => setFrom(e.target.value)}
                />
                <input
                    className="border rounded px-3 py-2 md:col-span-1"
                    type="date"
                    value={to}
                    onChange={(e) => setTo(e.target.value)}
                />
                <div className="flex gap-2 md:col-span-3">
                    <select
                        className="border rounded px-3 py-2"
                        value={sort}
                        onChange={(e) => setSort(e.target.value as any)}
                    >
                        <option value="submittedAt">Date</option>
                        <option value="rating">Rating</option>
                    </select>
                    <select
                        className="border rounded px-3 py-2"
                        value={dir}
                        onChange={(e) => setDir(e.target.value as any)}
                    >
                        <option value="desc">Desc</option>
                        <option value="asc">Asc</option>
                    </select>
                    <label className="flex items-center gap-2 text-sm text-gray-700">
                        <input
                            type="checkbox"
                            className="accent-black"
                            checked={onlySelected}
                            onChange={(e) => setOnlySelected(e.target.checked)}
                        />
                        Only selected
                    </label>
                    <button className="ml-auto border rounded px-3 py-2" onClick={fetchAll}>
                        Refresh
                    </button>
                </div>
            </div>

            {/* KPI rows */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3 mb-3">
                <StatCard label="Total Reviews" value={loading ? "…" : totalReviews}/>
                <StatCard
                    label="Avg Rating"
                    value={loading || !Number.isFinite(avgRatingAll) ? "–" : avgRatingAll.toFixed(2)}
                />
                <StatCard label="# Listings" value={loading ? "…" : listingCount}/>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-4 gap-3 mb-6">
                <StatCard label="Reviews (30 days)" value={loading ? "…" : last30.length}
                          sub="based on submitted date"/>
                <StatCard
                    label="Avg Rating (30 days)"
                    value={loading || !Number.isFinite(avgRating30) ? "–" : avgRating30.toFixed(2)}
                />
                <StatCard
                    label="% Positive (≥ 8)"
                    value={loading || !Number.isFinite(positiveShare) ? "–" : `${positiveShare.toFixed(0)}%`}
                />
                <StatCard
                    label="Latest Review"
                    value={loading ? "…" : latestDate ? latestDate.toLocaleDateString() : "—"}
                />
            </div>

            {/* Channel mix & Rating distribution */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3 mb-6">
                <div className="border rounded-lg p-4 bg-white">
                    <div className="font-semibold mb-3">Channel mix</div>
                    {!channelCounts.length ? (
                        <div className="text-sm text-gray-500">No data.</div>
                    ) : (
                        <div className="flex flex-wrap gap-2">
                            {channelCounts.map(([ch, c]) => (
                                <span key={ch} className="text-xs border rounded-full px-2 py-1">
                  {ch}: {c}
                </span>
                            ))}
                        </div>
                    )}
                </div>
                <div className="border rounded-lg p-4 bg-white">
                    <div className="font-semibold mb-3">Rating distribution</div>
                    <div className="space-y-2">
                        {["0–2", "2–4", "4–6", "6–8", "8–10"].map((label, i) => (
                            <div key={label}>
                                <div className="flex justify-between text-xs text-gray-600 mb-1">
                                    <span>{label}</span>
                                    <span>{Math.round(ratingBuckets[i].pct)}%</span>
                                </div>
                                <div className="h-2 w-full bg-gray-200 rounded">
                                    <div
                                        className="h-2 bg-black rounded"
                                        style={{width: `${ratingBuckets[i].pct}%`}}
                                        title={`${ratingBuckets[i].count} reviews`}
                                    />
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            </div>

            {/* Per-property performance */}
            <div className="border rounded-lg p-4 mb-6 bg-white">
                <div className="font-semibold mb-3">Per-property performance</div>
                <div className="overflow-x-auto">
                    <table className="min-w-full text-sm table-fixed">
                        <thead>
                        <tr className="text-left border-b">
                            <th className="py-2 pr-4 w-2/5">Listing</th>
                            <th className="py-2 pr-4 w-1/5">Reviews</th>
                            <th className="py-2 pr-4 w-1/5">Avg Rating</th>
                            <th className="py-2 pr-4 w-1/5">Last Review</th>
                        </tr>
                        </thead>
                        <tbody>
                        {perListing.map((row) => (
                            <tr
                                key={row.listing}
                                className="border-b hover:bg-gray-50 cursor-pointer"
                                onClick={() => openProperty(row.listing)}
                                title="Open property details"
                            >
                                <td className="py-2 pr-4 break-words">{row.listing}</td>
                                <td className="py-2 pr-4">{row.count}</td>
                                <td className="py-2 pr-4">{Number.isFinite(row.avg) ? row.avg.toFixed(2) : "–"}</td>
                                <td className="py-2 pr-4">
                                    {row.last ? new Date(row.last).toLocaleDateString() : "—"}
                                </td>
                            </tr>
                        ))}
                        {!perListing.length && !loading && (
                            <tr>
                                <td className="py-3 text-gray-500" colSpan={4}>
                                    No reviews match your filters.
                                </td>
                            </tr>
                        )}
                        </tbody>
                    </table>
                </div>
            </div>

            {/* Reviews table */}
            <div className="border rounded-lg p-4 bg-white">
                <div className="font-semibold mb-3">Reviews</div>
                <div className="overflow-x-auto">
                    <table className="min-w-full text-sm table-fixed">
                        <thead>
                        <tr className="text-left border-b">
                            <th className="py-2 pr-4 w-1/6">Listing</th>
                            <th className="py-2 pr-4 w-1/6">Guest</th>
                            <th className="py-2 pr-4 w-1/12">Channel</th>
                            <th className="py-2 pr-4 w-1/12">Rating</th>
                            <th className="py-2 pr-4 w-1/12">Date</th>
                            <th className="py-2 pr-4 w-5/12">Text</th>
                        </tr>
                        </thead>
                        <tbody>
                        {filtered.map((r) => (
                            <tr key={r.id} className="border-b align-top">
                                <td className="py-2 pr-4 break-words">{r.listingName ?? "—"}</td>
                                <td className="py-2 pr-4 break-words">{r.guestName ?? "—"}</td>
                                <td className="py-2 pr-4">
                    <span className="rounded-full border px-2 py-0.5 text-xs">
                      {r.channel ?? "—"}
                    </span>
                                </td>
                                <td className="py-2 pr-4">
                                    {Number.isFinite(ratingOf(r)) ? (ratingOf(r) as number).toFixed(1) : "—"}
                                </td>
                                <td className="py-2 pr-4">
                                    {r.submittedAt ? new Date(r.submittedAt).toLocaleDateString() : "—"}
                                </td>
                                <td className="py-2 pr-4">
                                    <ReviewText text={r.text}/>
                                </td>
                            </tr>
                        ))}
                        {!filtered.length && !loading && (
                            <tr>
                                <td className="py-3 text-gray-500" colSpan={6}>
                                    No reviews match your filters.
                                </td>
                            </tr>
                        )}
                        </tbody>
                    </table>
                </div>
            </div>

            {/* Add listing modal */}
            {addOpen && (
                <div className="fixed inset-0 bg-black/30 flex items-center justify-center z-50">
                    <div className="bg-white rounded-lg shadow-lg w-full max-w-2xl p-4">
                        <div className="flex items-center justify-between mb-3">
                            <div className="font-semibold">Add listing from Google</div>
                            <button className="text-sm underline" onClick={() => setAddOpen(false)}>
                                Close
                            </button>
                        </div>

                        <div className="flex gap-2 mb-3">
                            <input
                                className="border rounded px-3 py-2 flex-1"
                                placeholder="Search text (property name / address)"
                                value={searchQ}
                                onChange={(e) => setSearchQ(e.target.value)}
                                onKeyDown={(e) => e.key === "Enter" && doSearch()}
                            />
                            <button className="border rounded px-3 py-2" onClick={doSearch}>
                                Search
                            </button>
                        </div>

                        <div className="max-h-64 overflow-y-auto border rounded">
                            {!results.length ? (
                                <div className="p-3 text-sm text-gray-500">No results yet.</div>
                            ) : (
                                <table className="min-w-full text-sm">
                                    <thead>
                                    <tr className="text-left border-b">
                                        <th className="py-2 px-3">Name</th>
                                        <th className="py-2 px-3">Address</th>
                                        <th className="py-2 px-3">Action</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    {results.map((r) => (
                                        <tr key={r.placeId} className="border-b">
                                            <td className="py-2 px-3">{r.name}</td>
                                            <td className="py-2 px-3">{r.address}</td>
                                            <td className="py-2 px-3">
                                                <button
                                                    disabled={saving}
                                                    className="border rounded px-2 py-1"
                                                    onClick={() => addListing(r.name, r.placeId)}
                                                >
                                                    {saving ? "Saving…" : "Add"}
                                                </button>
                                            </td>
                                        </tr>
                                    ))}
                                    </tbody>
                                </table>
                            )}
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
