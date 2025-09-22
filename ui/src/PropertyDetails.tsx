// ui/src/PropertyDetails.tsx
import React, {useEffect, useMemo, useState} from "react";
import {goTo} from "./Switcher";

type CategoryRatings = Record<string, number>;

interface NormalizedReview {
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

async function safeJson<T = any>(res: Response): Promise<T | null> {
    if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
    const t = await res.text();
    if (!t) return null;
    try {
        return JSON.parse(t) as T;
    } catch {
        return null;
    }
}

const avg = (xs: number[]) => xs.length ? xs.reduce((a, b) => a + b, 0) / xs.length : NaN;
const ratingOf = (r: NormalizedReview) => r.overallRating ?? avg(Object.values(r.categoryRatings ?? {}));
const fmtDate = (iso?: string | null) => iso ? new Date(iso).toLocaleDateString() : "";

export default function PropertyDetails({listing, reviewId}: { listing: string; reviewId?: string }) {
    const [selectedAll, setSelectedAll] = useState<NormalizedReview[]>([]);
    const [allReviews, setAllReviews] = useState<NormalizedReview[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        let cancelled = false;
        (async () => {
            setLoading(true);
            try {
                const selRes = await fetch("/api/manager/reviews/selected");
                const sel = (await safeJson<NormalizedReview[]>(selRes)) ?? [];
                if (!cancelled) setSelectedAll(sel);

                const allRes = await fetch("/api/reviews/combined?limit=500&offset=0");
                const all = (await safeJson<NormalizedReviewResponse>(allRes))?.reviews ?? [];
                if (!cancelled) setAllReviews(all);
            } finally {
                if (!cancelled) setLoading(false);
            }
        })();
        return () => {
            cancelled = true;
        };
    }, []);

    const approvedForListing = useMemo(
        () => selectedAll.filter(r => (r.listingName ?? "") === listing),
        [selectedAll, listing]
    );

    const fallback = useMemo(() => {
        const list = allReviews.filter(r => (r.listingName ?? "") === listing);
        return list.sort((a, b) =>
            new Date(b.submittedAt ?? 0).getTime() - new Date(a.submittedAt ?? 0).getTime()
        )[0];
    }, [allReviews, listing]);

    const headerAvg = useMemo(() => {
        const arr = (approvedForListing.length ? approvedForListing : allReviews.filter(r => (r.listingName ?? "") === listing));
        const vals = arr.map(ratingOf).filter(n => Number.isFinite(n)) as number[];
        return vals.length ? avg(vals).toFixed(2) : "–";
    }, [approvedForListing, allReviews, listing]);

    const highlighted =
        (reviewId && approvedForListing.find(r => r.id === reviewId)) ||
        (approvedForListing[0] ?? fallback);

    const back = () => {
        if (window.history.length > 1) window.history.back();
        else goTo("", {} as any);
    };

    return (
        <div style={sx.page}>
            <button onClick={back} style={sx.backBtn}>← Back to Dashboard</button>

            <h1 style={sx.title}>{listing}</h1>

            {/* facts row still visible */}
            <div style={sx.factsRow}>
                <Fact label="Guests" value="4"/>
                <Fact label="Bedrooms" value="1"/>
                <Fact label="Bathrooms" value="1"/>
                <Fact label="Beds" value="3"/>
                <Fact label="Avg Rating" value={headerAvg}/>
            </div>

            {/* single-column: only the highlighted review card */}
            <div style={sx.centerWrap}>
                {!highlighted ? (
                    <div style={sx.sideCardMuted}>
                        {loading ? "Loading review…" : "No reviews available for this property yet."}
                    </div>
                ) : (
                    <div style={sx.sideCard}>
                        <div style={{fontWeight: 700, marginBottom: 8}}>Highlighted Review</div>
                        <div style={{display: "flex", justifyContent: "space-between", marginBottom: 6}}>
                            <div style={{fontWeight: 600}}>{highlighted.guestName ?? "Guest"}</div>
                            <div style={sx.pill}>{highlighted.channel ?? "—"}</div>
                        </div>
                        <div style={{color: "#111", lineHeight: 1.6}}>{highlighted.text}</div>
                        <div style={{color: "#667085", fontSize: 12, marginTop: 8}}>
                            {Number.isFinite(ratingOf(highlighted)) ? `Rating ${(ratingOf(highlighted) as number).toFixed(1)} · ` : ""}
                            {fmtDate(highlighted.submittedAt)}
                        </div>
                        {/* tiny note if we’re showing a fallback */}
                        {!approvedForListing.length && highlighted === fallback && (
                            <div style={{marginTop: 8, color: "#9ca3af", fontSize: 12}}>
                                (No approved reviews yet — showing most recent guest review)
                            </div>
                        )}
                    </div>
                )}
            </div>
        </div>
    );
}

function Fact({label, value}: { label: string; value: string }) {
    return <div style={sx.fact}>
        <div style={sx.factValue}>{value}</div>
        <div style={sx.factLabel}>{label}</div>
    </div>;
}

const sx: Record<string, React.CSSProperties> = {
    page: {
        maxWidth: 1120,
        margin: "0 auto",
        padding: "16px 16px 48px",
        fontFamily: "system-ui, sans-serif",
        background: "#faf9f6"
    },
    backBtn: {
        margin: "8px 0 4px",
        border: "1px solid #d0d5dd",
        background: "#fff",
        padding: "6px 10px",
        borderRadius: 8,
        cursor: "pointer"
    },
    title: {fontSize: 32, fontWeight: 700, margin: "8px 0 12px"},
    factsRow: {display: "flex", gap: 16, flexWrap: "wrap", marginBottom: 16},
    fact: {
        background: "#fff",
        border: "1px solid #eee",
        borderRadius: 12,
        padding: "10px 14px",
        minWidth: 110,
        textAlign: "center"
    },
    factValue: {fontSize: 18, fontWeight: 700}, factLabel: {fontSize: 12, color: "#667085"},

    // single-column wrapper
    centerWrap: {maxWidth: 560, margin: "0 auto"},

    sideCard: {
        background: "#fff",
        border: "1px solid #eee",
        borderRadius: 16,
        padding: 16,
        boxShadow: "0 2px 8px rgba(0,0,0,.06)"
    },
    sideCardMuted: {
        background: "#f8fafc",
        border: "1px dashed #d0d5dd",
        borderRadius: 16,
        padding: 16,
        color: "#667085"
    },
    pill: {
        fontSize: 12,
        color: "#155e75",
        background: "#e0f2fe",
        border: "1px solid #bae6fd",
        padding: "2px 8px",
        borderRadius: 999
    }
};
